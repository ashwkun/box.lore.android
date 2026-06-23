# BoxLore Android - Major Release Changelog (v1.4.1 ➔ HEAD)
*Features sorted by direct user impact, comparing the current repository state against the last v1 release.*

---

## 💥 Tier 1: Highest Impact Features (Transformative Upgrades)

### 1. ⚡ Complete Homescreen Overhaul & Dynamic Library Filtering
We have replaced the basic, static layout of the old Home screen—which only showed a simple list of your subscriptions and a single chronological row of recent episodes—with a completely restructured, highly immersive hub:
- **Personalized "Mixtape" Curation**: A smart, auto-generated listening queue that dynamically surfaces your in-progress episodes alongside fresh, unplayed drops from your subscriptions, keeping your playback ready at a moment's notice.
- **Responsive Library Filtering**: Integrated fluid section filters directly into your library view, letting you instantly toggle between your active subscriptions, in-progress tracks, completed episodes, and brand-new unplayed drops.
- **Robust Navigation & State Preservation**: Deeply nested navigation (e.g., Home ➔ Podcast Info ➔ Episode details) is now backstack-aware. The navigation bar correctly tracks your parent tab highlights, and pressing Back seamlessly preserves your active homepage filters rather than resetting them.
- **Premium Cards & Layout Spacing**: Re-engineered library and spotlight cards with dedicated action rows. Play buttons are positioned to prevent layout squeezing and ensure titles and subtitles never clip.

### 2. 🧠 Intelligent Subscription Sorting & Personalized Curation
We have completely re-engineered how your feed list and homepage are prioritized. Rather than a simple chronological timeline that floods your screen with daily news and pushes weekly serials out of sight, BoxLore now computes an engagement-driven hierarchy:
- **Listening Affinity & Engagement Curation**: Feeds are ranked dynamically based on your actual listening habits—incorporating how often you play a show, the episodes you like, and your subscription recency.
- **Serial Continuation Mode**: For podcasts marked as serial (best enjoyed in order), the engine automatically tracks and queues the *next chronological episode* you need to listen to, ensuring a smooth, spoiler-free narrative journey.
- **Hiatus & Inactivity Guard**: Dynamically detects when shows have gone on hiatus or ended, down-ranking stale feeds so they never clutter your active updates.

### 3. 📊 Dynamic Update Frequency Engine & Information Modal
For podcast feeds that do not natively share their update schedule:
- **Intelligent Release Pattern Estimation**: Our engine crawls the publication dates of recent episodes to determine the average release cadence (e.g., daily, weekly, bi-weekly, or monthly). It even maps the exact days of the week when a podcast is most likely to publish, showing you smart labels like *"Weekly on Fridays"*.
- **Hiatus & Seasonal Transition Detection**: Automatically calculates if a show is between seasons, on an extended hiatus, or completely inactive by comparing the time elapsed since the last release against the show's typical interval.
- **Premium Episode Frequency Modal**: Tapping the new frequency indicator badge on any Podcast Details page opens an interactive, premium details modal. It breaks down the show's activity status, typical intervals, and publication trends so you always know when to expect the next drop.

### 4. 🎬 Immersive Video Podcasts & Full Podcasting 2.0 Integration
BoxLore is now a fully featured, modern client supporting the rich features of the new Podcasting 2.0 standard:
- **Interactive, Synced Transcripts**: Read along with live-scrolling text sheets. Tap on any word or sentence to instantly jump the audio playback to that exact millisecond.
- **Seekbar Chapter Notches & Lists**: Clickable chapter lists let you easily jump sections. The seekbar overlays custom tick notches matching chapter boundaries, featuring a safety buffer so your thumb navigation never overlaps adjacent chapters.
- **Fullscreen 16:9 Video Podcasts**: Built-in high-performance video media support with an orientation lock system. Rotating your device between landscape and portrait remains completely smooth and never interrupts or pauses playback.
- **Cast & Crew Carousels**: Swipe through high-quality host and guest avatars right from the podcast and episode detail screens.
- **Promotional Trailers**: Quick-play promotional trailers available directly via a modern miniplayer, letting you preview new shows instantly.

### 5. 🤖 AI-Powered Feed Healing
When publishers fail to host transcripts or chapters:
- **On-Demand AI Chaptering**: Automatically parses audio to generate clean semantic chapters with structured title blocks.
- **AI Synced Transcripts**: High-fidelity speech-to-text models that generate accurate, word-by-word scrolling transcripts.
- **Timing Jump Healer**: Automatically smooths out broken or discontinuous timestamp jumps in older RSS feeds.

---

## ⚙️ Tier 2: High Impact Features (Core Infrastructure & Speed)

### 6. ⚡ Re-Architected Search & Edge Spellchecking
Finding your next favorite show is now instantaneous, even on slower cellular networks:
- **Edge Spellchecker**: Deployed spelling correction workers running on Cloudflare Edge to suggest corrections in real-time.
- **FTS5 Pruning & Database Tuning**: Shrank search database size by 40% while preserving ultra-fast query speedups, keeping the app lightweight and snappy.

### 7. 📂 Optimized OPML Imports & Bulk Tagging
- **Refined OPML Importer**: Migrate all your subscriptions from other apps instantly with zero failures or skipped shows.
- **Bulk Completed Tagging**: Easily organize and clean up large backlogs by marking multiple episodes or entire series as completed in a single tap.

---

## 🎨 Tier 3: Medium Impact (UI/UX Refinements & Aesthetics)

### 8. 🎨 Material 3 Layout & Curated Vibes (TimeBlock)
- **TimeBlock Curated Vibes**: Replaced standard designs with themed accent indicator bars, soft gradient glows, and dynamic weather icons that animate based on the local conditions.
- **Social Link Extraction**: Episode descriptions parse and format raw links into beautiful, brand-colored social chips.
- **Spotlight Region Fixes**: Fixed regional trending filters by mapping `"gb"` and `"uk"` codes case-insensitively, ensuring skipped Mixtape items never break the `#1 IN UK` trending label on the first visible card.
- **Smooth 60fps Scrolling**: Stabilized Compose list layouts and optimized image loaders, completely eliminating scrolling jitters or image flickers on the homepage.

---
*BoxLore Android - Engineered for the modern podcast enthusiast.*
