<div align="center" id="top">

<img src="docs/images/featured_image.png" width="820" alt="boxlore Android podcast app and podcast player feature banner"/>

# boxlore

**Free Android podcast app & player** — semantic search, offline downloads, no ads

<br/>

<a href="https://github.com/boxcreate/boxlore/releases/latest/download/boxlore-v0.0.11.apk">
  <img src="docs/images/card_github_v6.svg" height="72" alt="Download boxlore podcast app APK on GitHub"/>
</a>
&nbsp;&nbsp;
<a href="https://play.google.com/store/apps/details?id=cx.aswin.boxlore">
  <img src="docs/images/card_playstore_v6.svg" height="72" alt="Coming soon on Google Play"/>
</a>

<br/><br/>

<a href="LICENSE"><img src="https://img.shields.io/badge/License-PolyForm%20Strict-4F378B?style=flat-square&logo=lock&logoColor=EADDFF" alt="PolyForm Strict License"/></a>
<img src="https://img.shields.io/github/downloads/boxcreate/boxlore/total?style=flat-square&logo=github&logoColor=white&color=6750A4" alt="Total downloads"/>
<img src="https://img.shields.io/badge/Material%203-You-D0BCFF?style=flat-square&labelColor=4F378B&color=D0BCFF" alt="Material 3"/>
<img src="https://img.shields.io/badge/Android-Podcast%20Player-1C1B1F?style=flat-square&logo=android&logoColor=D0BCFF" alt="Android podcast player"/>

<br/><br/>

**[About](#about)** ·
**[Features](#features)** ·
**[First launch](#first-launch)** ·
**[Screenshots](#screenshots)** ·
**[Install](#install--build)** ·
**[Developers](#for-developers)**

<img src="docs/images/m3/divider.svg" width="820" alt=""/>

</div>

<p align="center">
  <img src="docs/images/m3/header-about.svg" width="820" alt="About"/>
</p>

<a id="about"></a>

Every podcast app I've used feels the same. They call an open API, do word-for-word search, let you subscribe, and show Apple charts. None of them really recommend things or get personal.

Spotify and Pocket Casts do personalize, but you're paying with ads or a subscription — and Spotify's podcast UI is rough.

**boxlore** is a free Android podcast player that learns as you listen: natural-language search, personalized picks, podcast discovery beyond typing a show title, stream or download episodes for offline listening, and a queue you can actually manage. No ads, no paywall for the stuff that matters.

The smart layer runs on a search index that is rebuilt daily and covers popular chart podcasts — not every podcast on earth yet. It evolves every day and gets bigger. Recommendations and semantic search work best within that catalog; everything else still works as a normal podcast client (subscribe, play, download, OPML import/export).

<!-- upcoming-changes:start -->
<div align="center">

<details open>
<summary><b>🔮 Upcoming in the Next Release</b></summary>
<b>⚡ Improvements:</b>
<ul align="left">
<li>Explore For You now loads recommendations from cache, preventing blank screens on fresh installs <a href="https://github.com/boxcreate/boxlore/pull/947"><img src="https://img.shields.io/badge/PR-947-6750A4?style=flat-square" alt="PR #947" height="18"/></a></li>
</ul>
<p align="center"><sub><sub>AI-generated summary; may contain mistakes.<br/>Verify details in the <a href="CHANGELOG.md">changelog</a> and linked pull requests.</sub></sub></p>
</details>

<br/>

<details open>
<summary><b>🎉 What's New (v0.0.11) - 2026-07-22</b></summary>
<b>🆕 New features:</b>
<ul align="left">
<li>Your listening history now shows session summaries, a calendar view, and stats. <a href="https://github.com/boxcreate/boxlore/pull/932"><img src="https://img.shields.io/badge/PR-932-6750A4?style=flat-square" alt="PR #932" height="18"/></a></li>
<li>Home displays up to three curated editorial rows each daypart, giving fresh picks. <a href="https://github.com/boxcreate/boxlore/pull/942"><img src="https://img.shields.io/badge/PR-942-6750A4?style=flat-square" alt="PR #942" height="18"/></a></li>
</ul>
<b>⚡ Improvements:</b>
<ul align="left">
<li>Playback speed and seek position are remembered after you clear or restart the app. <a href="https://github.com/boxcreate/boxlore/pull/933"><img src="https://img.shields.io/badge/PR-933-6750A4?style=flat-square" alt="PR #933" height="18"/></a></li>
</ul>
<b>🐛 Fixes:</b>
<ul align="left">
<li>When you reopen the app, playback resumes correctly and mini‑player stays in sync. <a href="https://github.com/boxcreate/boxlore/pull/904"><img src="https://img.shields.io/badge/PR-904-6750A4?style=flat-square" alt="PR #904" height="18"/></a></li>
<li>Android Auto now always shows the correct episode artwork without blanks. <a href="https://github.com/boxcreate/boxlore/pull/939"><img src="https://img.shields.io/badge/PR-939-6750A4?style=flat-square" alt="PR #939" height="18"/></a></li>
</ul>
<p align="center"><sub><sub>AI-generated summary; may contain mistakes.<br/>Verify details in the <a href="CHANGELOG.md">changelog</a> and linked pull requests.</sub></sub></p>
</details>

</div>
<!-- upcoming-changes:end -->

<p align="center">
  <img src="docs/images/m3/header-features.svg" width="820" alt="What makes it different"/>
</p>

<a id="features"></a>

<table>
<tr>
<td align="center" width="50%" valign="top">
<h3>Semantic search</h3>
Search episodes by <em>meaning</em>, not exact keywords.<br/>
<em>"stories about startup failure"</em> → relevant episodes, not title matches.
</td>
<td align="center" width="50%" valign="top">
<h3>Personalization engine</h3>
On-device learning that re-ranks Home rails, Explore, queue, and downloads as you listen — taste stays on your phone.<br/>
Themed <strong>discovery rails</strong> under the daypart greeting, plus <strong>Because You Like</strong> and For You picks.<br/>
<a href="docs/recommendation-system.md">Read more →</a>
</td>
</tr>
<tr>
<td align="center" width="50%" valign="top">
<h3>Curiosity cards</h3>
Swipe question cards on Learn that point you at episodes you'd never search for.<br/>
Right to queue · left to dismiss · tap to play.
</td>
<td align="center" width="50%" valign="top">
<h3>No ads, forever</h3>
No banners, no sponsored inserts, no premium tier to unlock search or recommendations.
</td>
</tr>
</table>

<br/>

<div align="center">
<table>
  <tr>
    <td align="center">
      <b>Home</b><br/><sub>For You &amp; your queue</sub><br/><br/>
      <img src="docs/images/homescreen.png" width="200" alt="Home screen" style="border-radius: 16px;"/>
    </td>
    <td align="center">
      <b>Semantic Search</b><br/><sub>Natural-language discovery</sub><br/><br/>
      <img src="docs/images/semantic_search.png" width="200" alt="Semantic search" style="border-radius: 16px;"/>
    </td>
    <td align="center">
      <b>Curiosity Cards</b><br/><sub>Swipe to discover</sub><br/><br/>
      <img src="docs/images/curiosity_cards.png" width="200" alt="Curiosity cards" style="border-radius: 16px;"/>
    </td>
  </tr>
</table>
</div>

<p align="center">
  <img src="docs/images/m3/header-first-launch.svg" width="820" alt="First launch"/>
</p>

<a id="first-launch"></a>

First launch gives you a few ways in — pick what fits how you already listen.

<table>
<tr>
<td width="33%" valign="top">
<h3>New to podcasts?</h3>
<p><strong>AI onboarding</strong> is the default path. A short chat about your preferences — natural language or suggested options — turns into semantic search queries, matching shows from the index, and a personalized feed to subscribe to before you enter the app.</p>
</td>
<td width="33%" valign="top">
<h3>Switching apps?</h3>
<p>Import from Pocket Casts, Apple Podcasts, AntennaPod, or any app that exports <strong>OPML</strong>. Tap <strong>Import library</strong>, pick your file, and get similar-show recommendations based on what you brought over.</p>
<p><sub>Export anytime via <strong>Profile → Backup & Restore</strong> (OPML or full JSON).</sub></p>
</td>
<td width="33%" valign="top">
<h3>Know your shows?</h3>
<p><strong>I know my shows</strong> opens search during setup — subscribe manually, grab similar-show suggestions if you want, or <strong>Skip Setup</strong> and explore on your own.</p>
</td>
</tr>
</table>

<p align="center">
  <img src="docs/images/m3/header-more.svg" width="820" alt="More features"/>
</p>

<details>
<summary><b>Listening &amp; playback</b></summary>
<br/>

**Mixtapes** — Your home queue: up to 15 episodes from subscriptions (in-progress + unplayed new drops), scored so you can press play and keep going.

**Player** — Mini and full player, queue, 0.5×–1.5× speed, sleep timer, skip controls, synced transcripts, chapters, video podcasts, Android Auto.

**Podcasting 2.0** — Native chapters and transcripts when publishers provide them; AI-generated fallback when they don't (beta, daily limit). Video in 16:9.

</details>

<details>
<summary><b>Library &amp; offline</b></summary>
<br/>

Subscriptions, downloads, history, and liked episodes in one place. Launch offline → land on your downloads.

**Profile → Backup & Restore:** OPML (any podcast app) or JSON (full boxlore backup — subs, history, likes, settings).

</details>

<details>
<summary><b>Smart automation</b></summary>
<br/>

**Daily briefing** — Optional region-specific AI news audio with script, sources, and chapter stories.

**Smart Downloads** *(app-wide, off by default)* — Curated offline pool from subs, recommendations, and trending, within limits you set.

**Auto-download** *(per podcast, off by default)* — New episode drops → downloads automatically (notifications required).

**New episode notifications** — Per-podcast bell. Off by default.

**Design** — Material 3 / Material You, shimmer skeletons, stable lists, smooth transitions, fast image loading.

</details>

<p align="center">
  <img src="docs/images/m3/header-screenshots.svg" width="820" alt="Screenshots"/>
</p>

<a id="screenshots"></a>

<div align="center">
<table>
  <tr>
    <td align="center" width="25%">
      <b>Onboarding</b><br/><sub>AI · OPML · search</sub><br/><br/>
      <img src="docs/images/onboarding.png" width="180" alt="Onboarding" style="border-radius: 14px;"/>
    </td>
    <td align="center" width="25%">
      <b>Home</b><br/><sub>Mixtape · For You</sub><br/><br/>
      <img src="docs/images/homescreen.png" width="180" alt="Home" style="border-radius: 14px;"/>
    </td>
    <td align="center" width="25%">
      <b>Daily Briefing</b><br/><sub>AI news audio</sub><br/><br/>
      <img src="docs/images/daily_brief.png" width="180" alt="Daily briefing" style="border-radius: 14px;"/>
    </td>
    <td align="center" width="25%">
      <b>Curiosity Cards</b><br/><sub>Swipe to discover</sub><br/><br/>
      <img src="docs/images/curiosity_cards.png" width="180" alt="Curiosity cards" style="border-radius: 14px;"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>Semantic Search</b><br/><sub>Meaning, not keywords</sub><br/><br/>
      <img src="docs/images/semantic_search.png" width="180" alt="Semantic search" style="border-radius: 14px;"/>
    </td>
    <td align="center">
      <b>For You</b><br/><sub>Personalized picks</sub><br/><br/>
      <img src="docs/images/recommendation_engine.png" width="180" alt="For You" style="border-radius: 14px;"/>
    </td>
    <td align="center">
      <b>Library</b><br/><sub>Subs · downloads</sub><br/><br/>
      <img src="docs/images/library.png" width="180" alt="Library" style="border-radius: 14px;"/>
    </td>
    <td align="center">
      <b>Player</b><br/><sub>Artwork-matched · expressive</sub><br/><br/>
      <img src="docs/images/player.png" width="180" alt="Artwork-matched expressive podcast player" style="border-radius: 14px;"/>
    </td>
  </tr>
</table>
</div>

<p align="center">
  <img src="docs/images/m3/header-install-v2.svg" width="820" alt="Install and build"/>
</p>

<a id="install--build"></a>

<div align="center">
  <a href="https://github.com/boxcreate/boxlore/releases/latest/download/boxlore-v0.0.11.apk">
    <img src="docs/images/card_github_v6.svg" height="72" alt="Download boxlore podcast app APK on GitHub"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://play.google.com/store/apps/details?id=cx.aswin.boxlore">
    <img src="docs/images/card_playstore_v6.svg" height="72" alt="Coming soon on Google Play"/>
  </a>
</div>

<br/>

Enable *Install from unknown sources* in Android settings for sideloading.

**Build from source**

```bash
git clone https://github.com/boxcreate/boxlore.git
cd boxlore
./gradlew assembleDebug
./gradlew installDebug
```

**Requirements:** Android Studio Ladybug+ · Android SDK 35+ · JDK 17 · Kotlin 1.9+

<p align="center">
  <img src="docs/images/m3/header-developers.svg" width="820" alt="For developers"/>
</p>

<details id="for-developers">
<summary><b>Codebase &amp; stack</b></summary>
<br/>

**Codebase structure**

| Module | Role |
|--------|------|
| `:core:catalog` | Repositories, mappers |
| `:core:designsystem` | Themes, shared composables |
| `:core:model` | Domain models |
| `:core:network` | Podcast Index + edge proxy |
| `:feature:explore` | Search, For You, curiosity cards |
| `:feature:home` | Mixtape, charts, briefing |
| `:feature:player` | Playback UI |
| `:feature:briefing` | Daily briefing screen |
| `:feature:library` | Downloads, subs, history |
| `:feature:info` | Podcast & episode detail |

**Tech stack**

| Technology | Purpose |
|-----------|---------|
| **Kotlin** | 100% Kotlin codebase |
| **Jetpack Compose** | UI with Material 3 |
| **Coroutines & Flow** | Async and reactive state |
| **Retrofit 2** | REST API client |
| **Room** | Local database |
| **ExoPlayer (Media3)** | Audio and video playback |
| **Coil** | Image loading |
| **Cloudflare Workers** | Edge proxy for search & recommendations |
| **bge-m3** | Embeddings for semantic search (1024-dim) |

**Data sources**

| Source | Data |
|--------|------|
| **Podcast Index API** | Catalog, keyword search |
| **Apple Podcast Charts** | Daily trending (US, IN, GB, FR) |

</details>

<p align="center">
  <img src="docs/images/m3/header-contributing.svg" width="820" alt="Contributing"/>
</p>

Contributions welcome.

1. **Report bugs** — [Open an issue](https://github.com/boxcreate/boxlore/issues) with steps to reproduce
2. **Suggest features** — [Discussions](https://github.com/boxcreate/boxlore/discussions)
3. **Submit PRs** — Fork, code, open a pull request

Agent / maintainer notes: see [`AGENTS.md`](AGENTS.md).

<p align="center">
  <img src="docs/images/m3/header-license.svg" width="820" alt="License"/>
</p>

boxlore is source-available under the [PolyForm Strict License 1.0.0](LICENSE). You may read and use the code for noncommercial purposes; redistribution and derivative works are not permitted. See [LICENSE](LICENSE) for details.

## Contributors

<div align="center">
  <a href="https://github.com/boxcreate/boxlore/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=boxcreate/boxlore" alt="Contributors"/>
  </a>
</div>

<br/>

<div align="center">

<img src="docs/images/m3/divider.svg" width="820" alt=""/>

because antigravity is free and i love podcasts.

**[⬆ Back to top](#top)**

</div>
