<div align="center">

<img src="images/featured_image.jpg" width="820" alt="BoxLore — The Ultimate Podcast App for Android"/>

# BoxLore

**A beautiful, intelligent podcast client for Android — 100% Jetpack Compose, Material 3 Expressive, and a semantic brain in the cloud.**

<br/>

<!-- Primary CTAs -->
<a href="https://play.google.com/store/apps/details?id=cx.aswin.boxlore">
  <img src="https://img.shields.io/badge/GET_IT_ON-Google_Play-34A853?style=for-the-badge&logo=googleplay&logoColor=white&labelColor=0f172a" alt="Get it on Google Play" height="42"/>
</a>
&nbsp;&nbsp;
<a href="https://github.com/ashwkun/box.lore.android/releases/latest/download/app-release.apk">
  <img src="https://img.shields.io/badge/DOWNLOAD-APK_Direct-2ebbca?style=for-the-badge&logo=android&logoColor=white&labelColor=0f172a" alt="Download APK" height="42"/>
</a>
&nbsp;&nbsp;
<a href="#-features">
  <img src="https://img.shields.io/badge/EXPLORE-Features_↓-7F52FF?style=for-the-badge&logo=materialdesign&logoColor=white&labelColor=0f172a" alt="Explore Features" height="42"/>
</a>

<br/><br/>

<!-- Tech stack -->
<img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>
<img src="https://img.shields.io/badge/Jetpack_Compose-Material_3_Expressive-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
<img src="https://img.shields.io/badge/ExoPlayer-Media3-FF6F00?style=flat-square&logo=android&logoColor=white" alt="Media3"/>
<img src="https://img.shields.io/badge/Turso-libSQL_Edge-00C853?style=flat-square&logo=sqlite&logoColor=white" alt="Turso"/>
<img src="https://img.shields.io/badge/Qdrant-Vector_Search-FF4500?style=flat-square&logo=qdrant&logoColor=white" alt="Qdrant"/>
<img src="https://img.shields.io/badge/Cloudflare-Workers_AI-F38020?style=flat-square&logo=cloudflare&logoColor=white" alt="Cloudflare Workers"/>
<img src="https://img.shields.io/badge/License-GPL_v3-ff0080?style=flat-square&logo=gnu&logoColor=white" alt="GPLv3"/>

<!-- Live repo stats -->
<br/>
<img src="https://img.shields.io/github/downloads/ashwkun/box.lore.android/total?style=flat-square&logo=github&logoColor=white&color=2ebbca&label=Downloads" alt="Downloads"/>
<img src="https://img.shields.io/github/v/release/ashwkun/box.lore.android?style=flat-square&logo=github&logoColor=white&color=7F52FF&label=Latest" alt="Latest Release"/>
<img src="https://img.shields.io/github/last-commit/ashwkun/box.lore.android?style=flat-square&logo=git&logoColor=white&color=2ebbca" alt="Last Commit"/>
<img src="https://img.shields.io/github/stars/ashwkun/box.lore.android?style=flat-square&logo=github&logoColor=white&color=f5c518&label=Stars" alt="Stars"/>

<!-- Pipeline health -->
<br/>
<a href="https://github.com/ashwkun/box.lore.android/actions/workflows/sync-pi-data.yml"><img src="https://github.com/ashwkun/box.lore.android/actions/workflows/sync-pi-data.yml/badge.svg" alt="Data Pipeline"/></a>
<a href="https://github.com/ashwkun/box.lore.android/actions/workflows/new-episode-check.yml"><img src="https://github.com/ashwkun/box.lore.android/actions/workflows/new-episode-check.yml/badge.svg" alt="Episode Notifications"/></a>
<a href="https://sonarcloud.io/summary/overall?id=ashwkun_box.lore.android"><img src="https://sonarcloud.io/api/project_badges/measure?project=ashwkun_box.lore.android&metric=alert_status" alt="Quality Gate"/></a>

</div>

<br/>

> [!TIP]
> **New here?** Grab the app from [Google Play](https://play.google.com/store/apps/details?id=cx.aswin.boxlore), or jump to [How It Works](#%EF%B8%8F-how-it-works) to see the architecture behind the semantic search and recommendation engine.

---

## 📱 What is BoxLore?

BoxLore is an open-source podcast client that treats listening as a *discovery* experience, not just a play queue. It pairs a fully expressive Material 3 interface with a serverless intelligence layer: every trending show across 4 countries is embedded into a vector space nightly, so search understands *meaning* ("podcasts about the psychology of money") instead of just matching keywords.

**Why it stands out:**

- 🎨 **Living UI** — album artwork drives the entire color story. Dominant colors are extracted in real time (Palette API), saturation-boosted, and lightness-bounded for rich ambient gradients that never look muddy.
- 🧠 **Semantic everything** — search, recommendations, and "For You" feeds run on 1024-dim BGE embeddings served from Qdrant Cloud via Cloudflare edge workers.
- ⚡ **Obsessive performance** — deferred below-the-fold composition during tab slides, JankStats-audited scrolling, locked 60fps navigation.
- 🌍 **Multi-region charts** — trending feeds for 🇺🇸 US, 🇮🇳 India, 🇬🇧 UK, and 🇫🇷 France, refreshed daily by an autonomous GitHub Actions pipeline.

---

## ✨ Features

<div align="center">
<table>
  <tr>
    <td align="center" width="25%"><b>🚀 Onboarding</b><br/><sub>Pick interests & region</sub><br/><br/><img src="images/onboarding.png" width="170" alt="Onboarding"/></td>
    <td align="center" width="25%"><b>🏠 Home Mixtapes</b><br/><sub>Time-aware curated queues</sub><br/><br/><img src="images/homescreen.png" width="170" alt="Homescreen"/></td>
    <td align="center" width="25%"><b>📰 Daily Briefing</b><br/><sub>Your morning audio digest</sub><br/><br/><img src="images/daily_brief.png" width="170" alt="Daily Briefing"/></td>
    <td align="center" width="25%"><b>🃏 Curiosity Cards</b><br/><sub>Swipeable discovery deck</sub><br/><br/><img src="images/curiosity_cards.png" width="170" alt="Curiosity Cards"/></td>
  </tr>
  <tr>
    <td align="center"><b>🔮 Semantic Search</b><br/><sub>Search by meaning, not words</sub><br/><br/><img src="images/semantic_search.png" width="170" alt="Semantic Search"/></td>
    <td align="center"><b>🎯 For You</b><br/><sub>Taste-based recommendations</sub><br/><br/><img src="images/recommendation_engine.png" width="170" alt="Recommendations"/></td>
    <td align="center"><b>📚 Library</b><br/><sub>Subscriptions & smart downloads</sub><br/><br/><img src="images/library.png" width="170" alt="Library"/></td>
    <td align="center"><b>🎧 Player</b><br/><sub>Ambient glow & live transcripts</sub><br/><br/><img src="images/player.png" width="170" alt="Player"/></td>
  </tr>
</table>
</div>

<details>
<summary><b>🏠 &nbsp;Home & Discovery — the full list</b></summary>
<br/>

- **Mixtape queues** — dynamically curated listening sessions scored on your subscriptions, play history, and genres
- **Time-block curation** — morning briefings, evening deep-dives; the feed adapts to your clock
- **Curiosity card deck** — swipeable, ambient-colored discovery stack with pill controls
- **Dismissible new-episode banners** and granular completed-episode filtering
- **Trending charts** for US, India, UK, and France with localized badges

</details>

<details>
<summary><b>🎧 &nbsp;Player & Podcasting 2.0</b></summary>
<br/>

- **Dynamic ambient glow** — real-time HSL extraction from cover art feeds the player's background
- **Live transcripts** — synced reading with tap-to-seek on any line
- **Chapter notches** — clickable chapter markers directly on the seekbar
- **Video podcasts** — 16:9 orientation-locked layouts
- **Variable speed** 0.5x–3x with pitch correction, sleep timer, circular wavy buffering loader
- **Headphone gestures** — double-click mapped to +30s / −10s
- **MediaSession integration** — lockscreen, Android Auto-style controls, swipe-away protection during playback

</details>

<details>
<summary><b>🔍 &nbsp;Search, Recommendations & Intelligence</b></summary>
<br/>

- **Semantic search** — queries embedded at the edge (Cloudflare Workers AI), matched against 250K+ episode vectors in Qdrant
- **Edge spell-check** — real-time query correction before it ever hits the index
- **Hybrid retrieval** — instant SQLite FTS5 results layered under semantic hits
- **"For You" engine** — recommendations scored on played episodes, subscriptions, notification/auto-download signals, and genre affinity

</details>

<details>
<summary><b>⬇️ &nbsp;Library, Offline & Backup</b></summary>
<br/>

- **Smart downloads** — WorkManager-driven auto-download with automated background purging
- **Collapsible download sections** with multi-select batch operations
- **Full JSON backup/restore** — theme, region, and subscriptions restore reactively without an app restart
- **Persistent sort preferences** and continuation logic for serialized shows

</details>

---

## ⚙️ How It Works

The app is only half the story — a fully autonomous, zero-server data platform keeps the catalog fresh:

```mermaid
graph LR
    subgraph "🤖 GitHub Actions (5x daily)"
        A[iTunes Charts<br/>4 countries] --> B[Turso Edge DB<br/>charts + catalog]
        C[Podcast Index API] --> B
        B --> D[BGE-large embeddings<br/>CPU, in-runner]
        D --> E[(Qdrant Cloud<br/>episode + show vectors)]
    end

    subgraph "📱 Runtime"
        F[BoxLore App] --> G[Cloudflare Worker<br/>edge proxy + AI]
        G --> E
        G --> B
        G --> C
    end
```

- **Staged pipeline** (`scripts/sync/01…07`) — charts refresh, catalog import, staleness-gated episode sync, budget-capped vectorization, grace-period cleanup, and per-day cost accounting, all logged with progress bars and cost footers in the Actions UI
- **Self-limiting** — every show holds exactly its latest 30 episodes in the vector index; embedding budgets keep each run bounded
- **Cost-aware** — the pipeline tracks its own Turso read/write spend daily against free-tier budgets and warns at 80% ([live report](data/db_cost_report.md))
- **Edge-served** — queries are embedded and answered from Cloudflare's network; the app never talks to a heavyweight backend

<details>
<summary><b>🛠️ &nbsp;Tech stack details</b></summary>
<br/>

| Layer | Technology |
| :--- | :--- |
| **UI** | Jetpack Compose, Material 3 Expressive, Coil, Palette API |
| **Architecture** | Multi-module Clean Architecture (`:core:*`, `:feature:*`), Coroutines + Flow |
| **Playback** | ExoPlayer (Media3), MediaSession, WorkManager |
| **Local data** | Room, SQLite FTS5, DataStore |
| **Edge & cloud** | Cloudflare Workers (TypeScript) + Workers AI, Turso (libSQL), Qdrant Cloud |
| **Embeddings** | BAAI bge-large-en-v1.5 (1024-dim), transformers.js in CI |
| **Data sources** | Podcast Index API, Apple Podcast Charts |
| **Quality** | SonarCloud, Qodo, Gitleaks, JankStats |

</details>

<details>
<summary><b>📦 &nbsp;Module map</b></summary>
<br/>

```
├── app/                  # entry point, navigation, DI graph
├── core/
│   ├── data/             # repositories, mappers, sync
│   ├── designsystem/     # theme, typography, shared composables
│   ├── model/            # pure Kotlin domain models
│   └── network/          # Podcast Index + edge proxy clients
├── feature/
│   ├── home/             # mixtapes, time blocks, charts
│   ├── explore/          # curiosity cards, semantic search, For You
│   ├── player/           # playback UI, transcripts, chapters
│   └── info/             # show & episode detail pages
├── proxy/                # Cloudflare Worker (edge AI + API proxy)
├── scripts/sync/         # autonomous data pipeline (01–07 + lib)
└── web/                  # share-link landing pages
```

</details>

---

## 🚀 Getting Started

**Just want the app?** → [Google Play](https://play.google.com/store/apps/details?id=cx.aswin.boxlore) or the [latest APK](https://github.com/ashwkun/box.lore.android/releases/latest/download/app-release.apk)

**Build it yourself:**

```bash
git clone https://github.com/ashwkun/box.lore.android.git
cd box.lore.android
./gradlew assembleDebug      # build
./gradlew installDebug       # install to a connected device
```

> [!NOTE]
> **Requirements:** Android Studio Ladybug+, Android SDK 35, JDK 17, Kotlin 1.9+. The app runs fully against public data sources out of the box — no API keys needed for a debug build.

---

## 🤝 Contributing

Contributions are very welcome — this project is a great playground for Compose, media, and vector-search experimentation.

| I want to… | Here's how |
| :--- | :--- |
| 🐛 Report a bug | [Open an issue](../../issues/new/choose) with repro steps |
| 💡 Suggest a feature | Start a thread in [Discussions](../../discussions) |
| 🔧 Submit code | Fork → branch → PR (SonarCloud + Qodo review every PR) |
| 📖 Read the guidelines | [CONTRIBUTING.md](CONTRIBUTING.md) |

---

## 📄 License

**GNU GPL v3** — use it, learn from it, fork it; derivatives stay open source. See [LICENSE](LICENSE).

---

<div align="center">

## ⭐ Star History

<a href="https://star-history.com/#ashwkun/box.lore.android&Date">
  <img src="https://api.star-history.com/svg?repos=ashwkun/box.lore.android&type=Date&theme=dark" width="600" alt="Star History Chart"/>
</a>

<br/><br/>

<a href="https://github.com/ashwkun/box.lore.android/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=ashwkun/box.lore.android" alt="Contributors"/>
</a>

<br/><br/>

### Made with ❤️ and ☕ by a podcast fan

**If BoxLore made your commute better, [drop a ⭐](../../stargazers) — it genuinely helps.**

[⬆ Back to top](#boxlore)

</div>
