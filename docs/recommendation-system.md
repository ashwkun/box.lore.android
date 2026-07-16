# boxlore Recommendation & Personalization System

> How boxlore decides what to show you, and how it learns from what you do.

This document is a deep, implementation-accurate walkthrough of boxlore's recommendation
engine. It covers the on-device machine-learning models, the retrieval → ranking →
diversification → layout pipeline, the reward/feedback loop that makes the app "learn",
how every user surface plugs into the system, and where the server (the private `proxy`
repo) fits in.

Everything described here — with the exception of the server endpoints in the "Server
boundary" section — runs **entirely on the device**. There is no cloud user-profile
store; the model that personalizes your feed lives in a local Room database and is only
ever uploaded as part of an opt-in encrypted backup.

---

## 1. TL;DR / mental model

boxlore personalizes in two complementary ways:

1. **A per-objective contextual bandit** (`AdaptiveLinearModel`) — a small online
   linear model (LinUCB-style ridge regression with an optional exploration bonus) that
   learns *how much each signal matters to you*. It takes an 18-dimensional feature
   vector describing a candidate episode/podcast and produces a "learned" score, which is
   blended with a hand-tuned prior score.

2. **Bayesian preference facets** (`BayesianPreferenceFacet`) — per-show, per-genre and
   per-source "taste meters" that accumulate positive/negative evidence with time-decay
   and produce an affinity in `[-1, 1]`. These affinities are themselves *features* fed
   into the bandit, so the two systems compose.

The learning loop is:

```
show item  ──▶  record EXPOSURE (feature vector snapshot, unresolved)
                         │
user acts (play/like/subscribe/skip/queue/download/dismiss…)
                         │
                         ▼
             compute REWARD  ∈ [-1, 1]
                         │
        ┌────────────────┴─────────────────┐
        ▼                                   ▼
  update bandit model              update preference facets
  (resolve the exposure)           (SHOW / GENRE / SOURCE)
```

Because the exposure captured the exact feature vector the item was shown with, the model
learns from the *state at the moment of the decision*, not a reconstructed one.

Key source packages:

- `core/data/.../ranking/` — the ML core (bandit, facets, reward, features, persistence).
- `core/data/.../content/` — the retrieval→ranking→layout orchestration used by Home.
- `core/data/MixtapeEngine.kt`, `core/data/SmartQueueEngine.kt` — surface-specific engines.
- `core/network/.../BoxLoreApi.kt` — the boundary to the server (candidate retrieval,
  semantic search). The server implementation lives in the separate private `proxy` repo.

---

## 2. Architecture at a glance

```
        ┌──────────────────────────── RETRIEVAL (candidates) ─────────────────────────────┐
        │  Subscriptions   Listening history   Server recs   Semantic search   Trending    │
        │  (SUBSCRIPTION)  (LOCAL_HISTORY)      (SERVER_REC/  (CURATED_INTENT)  (TRENDING)  │
        │                                        CURATED)                        Liked/DL   │
        └───────────────────────────────────────┬──────────────────────────────────────────┘
                                                 │ each candidate carries a `retrievalScore`
                                                 ▼
        ┌──────────────────────────── SCORING (per objective) ────────────────────────────┐
        │  1. Legacy/prior score  → normalizePriors() → [0,1]                              │
        │  2. Build 18-dim feature vector (incl. Bayesian facet affinities)               │
        │  3. AdaptiveLinearModel.score(): final = (1-b)·prior + b·tanh(θ·x) + explore     │
        └───────────────────────────────────────┬──────────────────────────────────────────┘
                                                 ▼
        ┌──────────────────────────── DIVERSIFICATION ────────────────────────────────────┐
        │  DiversityReranker: max-per-show cap, genre-repeat penalty, recent-show penalty, │
        │  reserved "novel" slot, de-duplication.                                          │
        └───────────────────────────────────────┬──────────────────────────────────────────┘
                                                 ▼
        ┌──────────────────────────── LAYOUT (Home only) ─────────────────────────────────┐
        │  SlateComposer: group into sections per "intent", enforce cross-section          │
        │  de-dup + per-show caps, keep "protected" sections, order optional sections by   │
        │  utility, cache per session/daypart/day.                                         │
        └───────────────────────────────────────┬──────────────────────────────────────────┘
                                                 ▼
                                        Rendered UI + exposure logging
                                                 │
                                                 ▼  (user interacts)
        ┌──────────────────────────── FEEDBACK / LEARNING ────────────────────────────────┐
        │  RankingFeedbackRepository → RankingReward → AdaptiveRankingRepository:          │
        │  resolve exposure + model.update() + facet.update()                              │
        └──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. The learned model — `AdaptiveLinearModel`

File: `core/data/.../ranking/AdaptiveLinearModel.kt`

This is a **regularized online linear model with optional upper-confidence-bound
exploration** — essentially a per-objective LinUCB / Bayesian ridge regression bandit.

### 3.1 State (`AdaptiveModelState`)

Each objective owns one state:

| Field | Meaning |
|-------|---------|
| `covariance` (`A`) | `d×d` matrix, initialized to `RIDGE · I` (ridge = `1.0`). Accumulates `Σ xxᵀ`. |
| `inverseCovariance` (`A⁻¹`) | Cached inverse of `A`, recomputed on every update via Gauss-Jordan elimination. |
| `rewardVector` (`b`) | Length-`d` vector accumulating `Σ x · reward`. |
| `updateCount` | Number of resolved outcomes folded into the model. |
| `featureSchemaVersion` / `dimension` | Guards against schema drift (`dimension = 18`). |

The learned weight vector is derived on demand: **`θ = A⁻¹ · b`**.

### 3.2 Scoring

```
rawLearned  = θ · x                       // linear response
learned     = tanh(rawLearned)            // squash to (-1, 1)
uncertainty = α · sqrt(xᵀ A⁻¹ x)          // exploration bonus (α = 0.15)
blend       = min(updateCount/50, 1) · 0.65
final       = clamp( (1-blend)·prior + blend·learned + uncertainty , -1, 1)
```

- **`prior`** is the hand-tuned/server score, clamped to `[-1, 1]`.
- **`blend`** ramps the learned model in *gradually*: at 0 outcomes it is `0` (pure
  prior); at ≥50 outcomes it saturates at `maximumLearnedBlend = 0.65`. The model never
  fully overrides the prior — the prior always keeps ≥35% weight.
- **`uncertainty`** (the UCB term) is only added when the objective
  `allowsExploration` **and** `updateCount ≥ explorationThreshold (50)`. It is larger for
  feature combinations the model has seen little of, nudging exploratory items up.
- `contributions` is a lazily-computed per-feature breakdown (`θᵢ · xᵢ`) used for
  debugging/telemetry, not for scoring.

### 3.3 Learning (`update`)

On each resolved outcome with bounded `reward ∈ [-1, 1]`:

```
A ← forgetting·A  +  (1-forgetting)·RIDGE·I(diagonal)  +  x·xᵀ
b ← forgetting·b  +  x·reward
A⁻¹ ← invert(A)
updateCount += 1
```

- **`forgettingFactor = 0.995`** implements *exponential forgetting*: old evidence slowly
  decays so the model tracks changing taste rather than averaging your entire history
  forever. The ridge term is continuously "refreshed" on the diagonal so `A` stays
  well-conditioned (invertible) even as older `xxᵀ` mass decays.
- The inverse is recomputed exactly each time (the feature dimension is small, `d=18`, so
  a full Gauss-Jordan inversion with partial pivoting is cheap). A singular matrix throws,
  which is guarded against by the ridge refresh.

### 3.4 Why this design

- **Interpretable & tiny.** 18 features, one small matrix per objective — trivial to run
  on-device, and `contributions` makes decisions explainable.
- **Cold-start safe.** New users get the curated prior; the learned model earns influence
  only as evidence accrues (`blend`).
- **Exploration without wrecking the feed.** UCB exploration is opt-in per objective and
  only kicks in after the learning threshold, and even then is a bounded additive nudge.
- **Non-stationary.** Forgetting keeps it responsive to changing tastes.

Tests that pin this behavior: `AdaptiveRankingTest`
(`cold start uses legacy prior and grows learned influence gradually`,
`offline objective never explores`,
`matrix update learns opposite outcomes in opposite directions`).

---

## 4. The taste model — `BayesianPreferenceFacet`

File: `core/data/.../ranking/BayesianPreferenceFacet.kt`

While the bandit learns *how signals combine*, facets track *which specific shows/genres/
sources you like*. There is one facet per key, of type:

`SHOW`, `GENRE`, `SOURCE`, `DURATION_BUCKET`, `TIME_CONTEXT`, `INTENT` (`PreferenceFacetType`).

Each facet is a Beta-style counter of `positiveEvidence` / `negativeEvidence`:

- **Update:** positive reward adds to `positiveEvidence`, negative to `negativeEvidence`
  (`update()` splits a `[-1,1]` reward into the two buckets).
- **Time decay:** before every read/update, evidence is decayed with a **90-day
  half-life** (`decayed()`, `factor = 2^(-elapsed/halfLife)`). Stale preferences fade
  toward neutral.
- **Affinity** (`affinity()`): with a symmetric prior (`priorStrength = 2.0`):

  ```
  α = prior/2 + positiveEvidence
  β = prior/2 + negativeEvidence
  posterior = α / (α + β)              // Beta mean, in (0,1)
  affinity  = clamp((posterior - 0.5)·2, -1, 1)
  ```

  So with no evidence, affinity = 0 (neutral); consistent likes push toward `+1`,
  consistent skips/removals toward `-1`, and the prior keeps early estimates conservative.

These affinities are mapped from `[-1,1]` into `[0,1]` (`toUnitAffinity`) and injected into
the bandit feature vector as `SHOW_AFFINITY`, `GENRE_AFFINITY`, `SOURCE_AFFINITY`. This is
the mechanism by which "I keep liking this show" becomes a ranking signal that also
generalizes (via genre/source) to shows you've never heard.

Test: `bayesian facets decay toward neutral and learn both signs`.

---

## 5. The feature vector (18 dimensions)

Files: `RankingModels.kt` (`FeatureSlot`, `CandidateSignals`, `CandidateFeatureBuilder`),
consumed by `AdaptiveCandidateScorer.kt`.

The **slot order is a persisted contract** (schema `VERSION = 1`, `dimension = 18`) — it
must never be reordered, because stored covariance matrices are indexed by ordinal. The
test `feature schema preserves exact persisted slot order` locks it.

| # | Slot | Source signal | Transform |
|---|------|---------------|-----------|
| 0 | `INTERCEPT` | — | constant `1.0` (bias term) |
| 1 | `SHOW_AFFINITY` | `SHOW` facet affinity | mapped to `[0,1]` |
| 2 | `GENRE_AFFINITY` | avg `GENRE` facet affinities | `[0,1]` |
| 3 | `SOURCE_AFFINITY` | `SOURCE` facet affinity | `[0,1]` |
| 4 | `FRESHNESS` | episode age | `exp(-ageHours / (24·14))` → 14-day time constant |
| 5 | `NOVELTY` | is an unseen/unsubscribed show | `0/1` |
| 6 | `DURATION_FIT` | episode length vs. ~45 min ideal | `[0,1]` triangle around 45 min |
| 7 | `SUBSCRIBED` | user subscribes | `0/1` |
| 8 | `RESUME_PROGRESS` | fraction already listened | `[0,1]` |
| 9 | `UNPLAYED` | never started & not completed | `0/1` |
| 10 | `SERIAL_MATCH` | serial vs. episodic fit | `[0,1]` |
| 11 | `SERVER_RELEVANCE` | normalized retrieval/prior score | `[0,1]` |
| 12 | `EXPOSURE_FATIGUE` | times recently shown | `-(1 - exp(-count/3))` (negative) |
| 13 | `TIME_CONTEXT` | daypart match | `[0,1]` |
| 14 | `OFFLINE_SUITABILITY` | fit for offline/downloaded listening | `[0,1]` |
| 15 | `EXPLICIT_PREFERENCE` | auto-download (1.0) / notifications (0.7) | `[-1,1]` |
| 16 | `RECENT_SUBSCRIPTION` | recency of subscribing | `exp(-hours / (24·14))` |
| 17 | `CURRENT_SHOW` | is the currently playing show | `0/1` |

`CandidateFeatureBuilder.build()` clamps every value to be finite and bounded, so a
malformed signal can never poison the matrix. Test: `feature builder returns finite
bounded schema`.

---

## 6. The reward model — turning behavior into a number

File: `core/data/.../ranking/RankingReward.kt`

Every learnable interaction is converted to a scalar reward in `[-1, 1]`.

### 6.1 Action weights (`RankingAction`)

| Action | Weight | | Action | Weight |
|--------|-------:|-|--------|-------:|
| `SUBSCRIBE` | +0.80 | | `EARLY_SKIP` | −0.70 |
| `LIKE` | +0.65 | | `UNSUBSCRIBE` | −0.70 |
| `EXPLICIT_QUEUE` | +0.55 | | `REMOVE_AUTOFILLED` | −0.80 |
| `MANUAL_DOWNLOAD` | +0.55 | | `DISMISS` | −0.75 |
| `COMPLETE` | +0.35 | | `UNLIKE` | −0.50 |
| `MOVE_UP` | +0.25 | | `MOVE_DOWN` | −0.25 |
| `MEANINGFUL_PLAY` | +0.22 | | | |
| `OPEN_DETAILS` | +0.08 | | | |

### 6.2 Listening value

Added on top of action weights when playback happened:

```
absolute = (ln(1 + min(listenSeconds, 3600)) / ln(3601)) · 0.2   // rewards absolute time, saturating at 1h
progress = clamp(listenSeconds / durationSeconds, 0, 1) · 0.2     // rewards finishing
reward   = clamp(Σ actionWeights + absolute + progress, -1, 1)
```

So a full listen of a liked, newly-subscribed show approaches the `+1` ceiling; an early
skip plus removing an auto-filled item approaches `−1`. Test: `reward is bounded and
ignored exposure has no penalty` (note: an item that was merely *shown* but not acted on
yields reward `0`, not a penalty — absence of engagement is neutral, not negative).

### 6.3 What "meaningful play" means

`RankingFeedbackRepository`: playback counts as `MEANINGFUL_PLAY` when the user listens
≥ 60s **or** ≥ 20% of the episode. `EARLY_SKIP` and `COMPLETE` are derived from the
playback service. A 5-second per-(episode, action) dedup window prevents double-counting.

---

## 7. The learning loop end-to-end

### 7.1 Exposure (the decision snapshot)

When an item is actually shown, the surface calls
`RankingFeedbackRepository.recordExposure(...)`, which persists a `RankingExposureEntity`
containing the **exact feature vector**, objective, surface, source, and timestamp, with
`resolvedAt = null`. Example call site: `HomeViewModel` logs exposures for newly-visible
Home slate items; `LearnViewModel` logs an exposure for each curiosity card shown.

Exposures are pruned aggressively (see §10): 30-day retention, max 1000 rows.

### 7.2 Resolution (the outcome)

When the user acts, one of these fires:

- `recordAction(target, action, …)` — discrete actions (like, subscribe, queue,
  download, move, remove, dismiss, open-details).
- `recordPlayback(target, listenSeconds, durationSeconds, completed, earlySkip)` — from
  the playback service (`BoxLorePlaybackService`).

Both:

1. Compute the reward (`RankingReward.calculate`).
2. **Update the preference facets** for the item's SHOW (always), GENRE and SOURCE
   (`updateTasteFacets`).
3. For *terminal* actions (play/like/subscribe/queue/download/skip/remove/move/dismiss),
   **resolve the latest unresolved exposure** for that episode
   (`AdaptiveRankingRepository.resolveLatestExposure`), which loads the stored feature
   vector and calls `AdaptiveLinearModel.update()` for that exposure's objective.

If no exposure was recorded (e.g. the user found the episode via search rather than a
ranked surface), the facets still learn even though the bandit doesn't — so taste signal
is never lost.

### 7.3 Where signals originate (call-site map)

| Signal | Emitted from |
|--------|--------------|
| `EXPLICIT_QUEUE` | `PlaybackRepository.addToQueue`, `QueueRepository` (manual/Lore queue adds) |
| `REMOVE_AUTOFILLED` | `PlaybackRepository` (removing an auto-filled queue item) |
| `MOVE_UP` / `MOVE_DOWN` | `PlaybackRepository` (reordering the queue) |
| `LIKE` / `UNLIKE` | `PlaybackRepository.toggleLike` |
| `SUBSCRIBE` / `UNSUBSCRIBE` | `SubscriptionRepository` |
| `MANUAL_DOWNLOAD` | `DownloadRepository` (only non-smart downloads) |
| `MEANINGFUL_PLAY` / `COMPLETE` / `EARLY_SKIP` | `BoxLorePlaybackService.recordPlayback` |
| `OPEN_DETAILS` / `DISMISS` | `LearnViewModel` (curiosity cards) |

---

## 8. Retrieval → ranking → diversification → layout

### 8.1 Candidate sources (`CandidateSource`)

`SUBSCRIPTION`, `LOCAL_HISTORY`, `SERVER_RECOMMENDATION`, `CURATED_INTENT`, `TRENDING`,
`LIKED`, `DOWNLOADED`. Providers wrap these into `ContentCandidate`s with a
`retrievalScore` (server-supplied `retrievalScore`/`semanticScore`, or reciprocal-rank
`1/(index+1)` when the source is just an ordered list).

### 8.2 Scoring (`AdaptiveCandidateScorer`)

File: `core/data/.../ranking/AdaptiveCandidateScorer.kt`.

- `scorePodcasts` / `scoreEpisodes` build features (pulling facet affinities in a single
  batched DB read via `facetAffinities`), then call the bandit's `scoreBatch` for the
  objective.
- Priors are normalized with `normalizePriors` — a log1p normalization
  (`ln(1+v)/ln(1+max)`) that squashes heavy-tailed server scores into `[0,1]` so they sit
  in the same range as the learned score before blending.
- If adaptive ranking is disabled for the (objective, surface) pair, it **transparently
  falls back to the normalized prior order** (and, for `scorePodcasts`, the legacy
  `PodcastScoring` output). Nothing breaks; you just get the non-personalized ranking.

### 8.3 The prior — `PodcastScoring`

File: `core/data/PodcastScoring.kt`. A transparent, hand-tuned heuristic scoring
subscriptions by play count, like count, play recency, freshness of the latest unplayed
episode, subscription recency, and notification/auto-download boosts. This is the "prior"
the bandit blends against and the cold-start behavior.

### 8.4 Diversification (`DiversityReranker`)

File: `RankingModels.kt`. Greedy re-ranking under a `DiversityPolicy`:

- de-duplicates by episode,
- caps items per show (`maxPerShow`),
- applies a `genreRepeatPenalty` per repeated genre and a `recentShowPenalty` for shows
  played recently,
- can **reserve a slot for a "novel"** (unsubscribed/unseen) candidate, even evicting the
  weakest pick to guarantee discovery.

Tests: `diversity reranker removes duplicates caps shows and reserves novelty`,
`novel candidate can replace capped item from the same show`.

### 8.5 Layout / slate composition (Home) — `ContentOrchestrator`

Files: `core/data/.../content/*`.

The Home feed is assembled by the `ContentOrchestrator`:

1. **Intent resolution** (`ContentIntentResolver`) — a server-delivered
   `ContentCatalogSnapshot` (endpoint `/content/catalog/v1`) defines "intents" (rows) with
   objectives, eligible surfaces/dayparts, layouts, item counts and refresh policies. If
   the catalog is missing/expired/unsupported, it falls back to an embedded
   `anytimeFallback` intent so the feed always renders.
2. **Per-intent retrieval + ranking** run concurrently (`supervisorScope`), each producing
   a ranked candidate list via `AdaptiveContentCandidateRanker` (which delegates to
   `AdaptiveCandidateScorer`). Provider failures are isolated (one dead source can't blank
   the feed); ranker failure falls back to retrieval order.
3. **Slate composition** (`SlateComposer`):
   - respects a `SharedExposureBudget` so the same item/show doesn't repeat across rows,
   - keeps `protected` sections first, orders the rest by a `utility` score
     (top-item quality 70% + novelty 20% + online/offline fit 10%),
   - enforces cross-section de-dup and a global per-show cap,
   - drops sections that fall below their `minimumItems`.
4. **Caching**: slates are cached per session, keyed by a fingerprint that honors each
   intent's `refreshPolicy` (`SESSION` / `MANUAL` / `DAYPART` / `DAILY`) — e.g. a `DAILY`
   row is reused within a day and regenerated the next.

Tests: `ContentOrchestratorTest` covers fallback, provider isolation, protected/dedup,
ranker-failure fallback, cancellation safety, and daily-refresh invalidation.

---

## 9. Objectives, surfaces, and rollout controls

### 9.1 Objectives (`RankingObjective`) — one model each

| Objective | Explores? | Used for |
|-----------|:--------:|----------|
| `YOUR_SHOWS` | no | Ranking your subscriptions (Mixtape/Home "your shows") |
| `DISCOVERY` | yes | For You, Explore search, trending, recommendations |
| `CONTINUATION` | yes | Mixtape ordering, Smart Queue fallback refills |
| `OFFLINE` | no | Downloads ordering |
| `SLATE` | yes | Generic slate composition |

Separate models mean "what makes a good next-in-queue pick" is learned independently from
"what makes a good discovery pick".

### 9.2 Surfaces (`RankingSurface`)

`HOME`, `EXPLORE`, `LIBRARY`, `QUEUE`, `DOWNLOADS`, `ANDROID_AUTO`.

### 9.3 Runtime gating (`RankingRuntimeControls`)

Adaptive ranking is enabled only when **all** of: the global toggle (default on), the
per-objective toggle (default on), and the per-surface toggle are true. The per-surface
default comes from `RankingRolloutPolicy`, which **enables only `HOME` by default** — so
out of the box, the learned model actively re-ranks Home, while other surfaces compute
features/priors but return the prior order until explicitly enabled. Test: `adaptive
rollout starts on home only`. All toggles are stored in `SharedPreferences` and can be
flipped (e.g. via debug/settings) without touching the model state.

---

## 10. Persistence, backup, pruning, reset

Files: `ranking/database/*`, `RankingSerialization.kt`, `AdaptiveRankingRepository.kt`.

- **Room DB** (`AdaptiveRankingDatabase`) with three tables:
  `adaptive_models` (one row per objective), `preference_facets` (one row per
  type+key), `ranking_exposures` (decision log).
- `DoubleArray`s (covariance, inverse, reward vector, feature vectors) are stored as
  little-endian `ByteArray` blobs (`RankingSerialization`) — exact round-trip, no precision
  loss. Test: `serialization preserves doubles exactly`.
- **Exposure hygiene:** pruned to a 30-day retention window and a hard cap of 1000 rows,
  re-checked every 25 inserts.
- **Concurrency:** all model reads/writes for an objective are serialized behind a
  per-objective `Mutex`, so scoring and updates never race on the matrix.
- **Backup/restore:** `exportBackup` / `restoreBackup` (version-checked, fully validated —
  finite doubles, known enums, size caps) let the learned personalization travel with the
  user's OPML/JSON backup (`Profile → Backup & Restore`). Test: `adaptive learning backup
  survives json round trip`.
- **Reset:** `RankingFeedbackRepository.reset()` (surfaced in Settings, e.g.
  `ResetAnalyticsDialog`) clears all models, facets, exposures and shadow diagnostics —
  a true "forget me" for recommendations.

---

## 11. Where it shows up in the app (surface by surface)

- **Home — "For You" / Mixtape.** `HomeViewModel` wires a `ContentOrchestrator` (server
  intent + subscription providers, `AdaptiveContentCandidateRanker`) for the For You rows,
  ranks trending and server recommendations with `DISCOVERY`, ranks subscriptions with
  `YOUR_SHOWS`, then hands everything to `MixtapeEngine.build(...)` which produces the
  home queue. `MixtapeEngine` first builds resume + unplayed candidates with a transparent
  freshness/affinity heuristic, then (when an `AdaptiveRanking` is supplied) re-scores them
  with the `CONTINUATION` model before taking the top 15.
- **Explore — search.** `ExploreViewModel` re-ranks search results in tie-windows with
  the `DISCOVERY`/`EXPLORE` model (`rankPodcasts` / `scoreEpisodes`), so results that match
  your taste float up within each relevance band.
- **Learn — curiosity cards.** `LearnViewModel` records an exposure per card, then
  `OPEN_DETAILS` (tap/queue/play) or `DISMISS` (swipe-away after meaningful dwell) as
  feedback — this is the clearest "swipe to teach" loop.
- **Smart Queue.** `DefaultSmartQueueEngine` is a tiered, offline-first refill engine
  (same-show continuation → resume → subscriptions → network recs/similar → liked-similar
  → trending). Its fallback tiers are re-ranked by the `CONTINUATION`/`QUEUE` model with a
  diversity policy that reserves a novel slot. A `QueueSkipMemory` down-ranks shows you
  keep skipping.
- **Because You Like.** `BecauseYouLikeSection` / `ChangeRecommendationPodcastSheet` use
  the server `recommendations/because-you-like` endpoint seeded by a show you like.
- **Downloads / Smart Downloads.** Use the `OFFLINE` objective (no exploration) and
  `DOWNLOADED`/`LIKED` sources.

---

## 12. Server boundary (the private `proxy` repo)

The **retrieval/candidate-generation and semantic layer runs server-side** in the separate
private `proxy` repository (a Cloudflare Workers edge proxy, per the README). This Android
client only talks to it over the `BoxLoreApi` (`core/network/.../BoxLoreApi.kt`). The
ranking/learning described above happens **on the client**, on top of whatever candidates
the server returns.

Relevant endpoints:

| Endpoint | Purpose |
|----------|---------|
| `GET /search/semantic` | Natural-language ("meaning") episode/show search (bge-m3 1024-dim embeddings per README). |
| `POST /recommendations`, `POST /recommendations/v2` | Personalized candidate episodes. |
| `POST /recommendations/because-you-like` | Similar shows/episodes seeded by one you like. |
| `POST /episodes/similar` | Nearest-neighbor episodes for queue/similar. |
| `GET /content/catalog/v1` | The intent catalog that defines Home rows/dayparts/layouts. |
| `POST /home/bootstrap` | One-shot Home payload (briefing + trending + curated + recs). |
| `GET /trending`, `GET /curated/*` | Chart/curated candidates. |

**Privacy of the server contract.** The v2 recommendation request
(`RecommendationsV2Request`) is deliberately **seed-based**: it sends only episode/podcast
IDs with weights (`RecommendationSeedV2`) plus excluded IDs — **not** raw behavioral
history (no `progressMs`, no `isLiked`, no per-episode history rows). The test
`recommendation v2 request excludes raw behavioral history` enforces this. The device
turns your history into a compact seed set locally; the personalization *model* never
leaves the device.

Server-returned `retrievalScore` / `semanticScore` / `recommendationReason` flow into the
candidate's prior (`SERVER_RELEVANCE` feature + prior score) and the "reason" chips shown
in the UI.

---

## 13. Diagnostics & safety

- **Shadow diagnostics** (`RankingShadowDiagnostics`): whenever adaptive ranking runs, it
  compares the adaptive order against the prior order and stores only *aggregate* movement
  (top-5 overlap, mean absolute rank shift) per objective — enough to monitor "how much is
  the model changing things?" without logging content. Test: `shadow diagnostics retain
  only aggregate rank movement`.
- **Aggregate telemetry** (`aggregateTelemetry`): buckets each objective into
  `cold_start` / `learning` / `adaptive` and coarse outcome-count buckets — no raw
  identifiers.
- **Fail-safe everywhere:** every feedback path is wrapped so a failure logs and returns a
  neutral fallback; the model is initialized lazily and, if it can't init, the app simply
  runs without personalization.

---

## 14. Learning lifecycle (stages)

Per objective (`aggregateTelemetry` / the `blend` schedule):

| Stage | `updateCount` | Behavior |
|-------|--------------|----------|
| **cold_start** | 0 | Pure prior (`blend = 0`). No exploration. |
| **learning** | 1–49 | Learned score fades in linearly (`blend = count/50 · 0.65`). No exploration yet. |
| **adaptive** | ≥50 | `blend` saturated at 0.65; UCB exploration active for exploring objectives. Forgetting keeps it fresh. |

---

## 15. Type/file quick reference

| Concern | Type(s) | File |
|---------|---------|------|
| Online linear bandit | `AdaptiveLinearModel`, `AdaptiveModelState` | `ranking/AdaptiveLinearModel.kt` |
| Taste meters | `BayesianPreferenceFacet`, `PreferenceFacetType` | `ranking/BayesianPreferenceFacet.kt` |
| Reward mapping | `RankingReward`, `RankingAction`, `RankingOutcome` | `ranking/RankingReward.kt` |
| Features | `FeatureSlot`, `CandidateSignals`, `CandidateFeatureBuilder`, `RankingFeatureSchema` | `ranking/RankingModels.kt` |
| Diversity | `DiversityReranker`, `DiversityPolicy` | `ranking/RankingModels.kt` |
| Objectives/surfaces | `RankingObjective`, `RankingSurface`, `CandidateSource` | `ranking/RankingModels.kt` |
| Model + facet persistence | `AdaptiveRankingRepository`, `RankingExposure` | `ranking/AdaptiveRankingRepository.kt` |
| Scoring adapter | `AdaptiveCandidateScorer` | `ranking/AdaptiveCandidateScorer.kt` |
| Feedback intake | `RankingFeedbackRepository`, `FeedbackTarget` | `ranking/RankingFeedbackRepository.kt` |
| Runtime toggles / diagnostics | `RankingRuntimeControls`, `RankingRolloutPolicy`, `RankingShadowDiagnostics` | `ranking/RankingRuntimeControls.kt` |
| Persistence | `AdaptiveRanking{Database,Dao,Entities}`, `RankingSerialization` | `ranking/database/*`, `ranking/RankingSerialization.kt` |
| Home slate pipeline | `ContentOrchestrator`, `SlateComposer`, `ContentIntentResolver`, providers | `content/*` |
| Home queue | `MixtapeEngine` | `MixtapeEngine.kt` |
| Playback queue refill | `DefaultSmartQueueEngine` | `SmartQueueEngine.kt` |
| Prior heuristic | `PodcastScoring` | `PodcastScoring.kt` |
| Server API | `BoxLoreApi`, `Recommendations*Request/Response` | `core/network/.../BoxLoreApi.kt`, `.../model/SyncModels.kt` |

---

## 16. Worked example

You open Home in the morning. The `ContentOrchestrator` resolves the server intent catalog
into rows like "Because you finished X" and "New for you". For a candidate episode of a
show you've liked twice recently:

1. Its `SHOW` facet has accumulated positive evidence → affinity ≈ +0.6 → `SHOW_AFFINITY`
   feature ≈ 0.8.
2. It's a fresh, unplayed episode from a subscription → high `FRESHNESS`, `UNPLAYED`,
   `SUBSCRIBED`.
3. The `DISCOVERY` model has seen 120 outcomes → `blend = 0.65`; `θ` has learned you
   respond to `SHOW_AFFINITY` and `FRESHNESS`, so `learned = tanh(θ·x)` is high.
4. `final = 0.35·prior + 0.65·learned + smallExplorationBonus` → the episode ranks near
   the top; diversity rules keep it from being followed by three more from the same show.
5. Home logs an **exposure** with this exact feature vector.
6. You play it for 40 minutes and like it → reward ≈ `+0.65 (LIKE) + listen value`,
   clamped toward `+1`. The exposure resolves: the `DISCOVERY` matrix updates, and the
   `SHOW`/`GENRE`/`SOURCE` facets tick further positive.
7. Tomorrow, that show — and, via genre/source affinity, *similar* shows you haven't heard
   — get a bit more lift. That is boxlore "learning".
