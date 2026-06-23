This document provides a detailed technical breakdown of the boxlore personalized recommendation engine and vector lookup system. It covers the client-side payloads, server-side processing, mathematical weights, recency decay algorithms, and the background ETL (Extract, Transform, Load) pipelines that build the vector pool.

**Client-Side Payloads and Transmission Schemas**
Personalization begins on the client device, which transmits listening state metadata to the Cloudflare Worker proxy via a POST request to https://api.aswin.cx/recommendations. 

The client-side request includes tracking and caching headers:
* x-device-uuid: A unique identifier for the device, used to cache generated recommendations.

The body of the POST request is a JSON object containing the following structure:
* country: String representing the client's current geolocation code (e.g., "us", "in", "gb", "fr").
* subscribedPodcastIds: Array of integers representing the iTunes feed IDs of the podcasts the user has subscribed to.
* interests: Array of strings representing user-selected onboarding genres, used as a cold-start fallback.
* history: Array of listening history objects representing the user's recent playback activity. Each history item contains:
  - episodeId: Integer representing the unique Podcast Index ID of the episode.
  - episodeTitle: String representing the title of the episode.
  - episodeDescription: String (nullable) containing the cached description of the episode.
  - podcastId: Integer representing the unique ID of the parent podcast feed.
  - podcastTitle: String representing the title of the podcast show.
  - progressMs: Long integer representing the current playback position in milliseconds.
  - durationMs: Long integer representing the total length of the episode in milliseconds.
  - isCompleted: Boolean flag indicating if the episode has been played to completion.
  - isLiked: Boolean flag indicating if the user explicitly liked the episode.
  - genre: String representing the category or genre of the podcast.

**Proxy Server-Side Processing Flow**
Upon receiving the client payload, the Cloudflare Worker executes the following sequence:

1. History Payload Enrichment: The worker collects all numeric episodeIds from the user's history and queries the Qdrant Cloud point retrieval API (POST /collections/episodes/points/scroll) in a batch. If Qdrant returns a point payload containing an episode description, the server uses it as the primary text source. If not, the worker falls back to the descriptions provided by the client (checking episodeDescription, episode_description, and description).

2. Description Cleaning: Raw descriptions are sanitized through a multi-regex cleaning function to maximize semantic similarity accuracy:
  - HTML tags are stripped.
  - The text is truncated at the first occurrence of boilerplate keywords (e.g., links:, follow us, socials, sponsors, ad choices, patreon, instagram, twitter, facebook, discord, website, support the show, merch, subscribe).
  - Remaining URLs and email addresses are stripped.
  - The description is capped at 1000 characters.

3. Prompt Construction: A consolidated metadata string is built for embedding generation:
   "Episode: {Episode Title}. Description: {Cleaned Description}. Podcast: {Podcast Title}. Genres: {Categories}. Host: {Author}"

4. User Taste Vector Calculation: History items are grouped by genre (up to 3 clusters). Within each cluster, the worker fetches 1024-dimensional dense vectors using Cloudflare Workers AI running the @cf/baai/bge-large-en-v1.5 model. It then performs an element-wise weighted average of the embeddings in the cluster based on engagement.

Let R be the completion ratio defined as:
R = min(progressMs / durationMs, 1.0)

The raw engagement weight (w_raw) is:
w_raw = 0.3 + 0.7 * R

If the episode is completed (isCompleted is true), the weight is adjusted:
w_completed = max(w_raw, 0.9) [else w_completed = w_raw]

If the episode is liked (isLiked is true), an explicit positive signal boost is added:
w_liked = w_completed + 0.3 [else w_liked = w_completed]

The final weight (w_final) is capped:
w_final = min(w_liked, 1.5)

For a cluster containing N episodes, the taste vector (V_cluster) is calculated as:
V_cluster = (sum_{i=1}^N (w_final_i * v_i)) / (sum_{i=1}^N w_final_i)
where v_i represents the 1024-dimensional embedding vector of episode i.

5. Qdrant Similarity Search: The Worker queries Qdrant Cloud using cosine similarity on the episodes collection, querying in parallel with each V_cluster. It retrieves up to 600 / (number of clusters) matches. The query enforces language filters (e.g., permitting Hindi "hi" only for Indian users, and French "fr" only for non-English countries).

6. Recency Re-ranking and Decay: A mild freshness decay penalty is applied to the raw cosine similarity score.
Let t_age represent the age of the episode in days since publication:
t_age = max(0, (t_now - t_published) / 86400)

The recency boost multiplier is defined as:
b_recency = 1.0 / (1.0 + 0.003 * t_age)

The final adjusted score (S_adjusted) used for sorting is:
S_adjusted = S_similarity * b_recency

This soft decay curves results so that an episode published 100 days ago incurs a ~30% score penalty, prioritizing fresh content while retaining strong semantic matches.

7. De-duplication and Filtering: The candidate lists from all clusters are interleaved to ensure genre diversity. Candidates undergo strict filtering:
  - Exclusion of NSFW keywords.
  - Exclusion of subscribed podcasts.
  - Exclusion of exact episode titles in the listening history.
  - In Pass 1 (Strict Discovery Mode), any podcast show (by ID or title) in the user's history is excluded, and only 1 episode per show is permitted.
  - If Pass 1 returns fewer than 15 items, the engine triggers Pass 2 (Fallback Discovery Mode), which relaxes the show exclusion filter, allowing other episodes from shows in history to be recommended while still excluding already-listened episodes.
  The final response is capped at the top 60 recommendations.

**ETL Recommendation Pool Construction (GitHub Actions)**
The pool of recommendations queryable in Qdrant is built and maintained by the GitHub Actions workflow file .github/workflows/sync-pi-data.yml. The workflow runs on a schedule (every 6 hours) or can be triggered manually, processing a matrix of regions: [us, in, gb, fr].

The workflow executes the following pipeline:

1. Chart Population (populate-charts.js): Queries the Apple Marketing RSS API for the country's top 200 trending podcasts each * 16 different genres . These are saved or updated in the charts table of the Turso SQL database.

2. Pre-check Sync (pre-check-sync.js): Compares local DB states to determine if a full Podcast Index database dump download can be skipped to conserve bandwidth.

3. Podcast Index SQLite Dump (curl & tar): If the pre-check requires a full sync, the workflow downloads a compressed SQLite database dump containing the index of all podcasts globally (~1.5 GB tgz) from https://public.podcastindex.org/podcastindex_feeds.db.tgz.

4. Export Matching Feeds (get-chart-itunes-ids.js): Pulls active iTunes IDs from the Turso charts table. It creates a temporary table in the downloaded SQLite index, imports these IDs, and queries the index to export podcast metadata into podcasts_export.csv. The exported columns include iTunes ID, title, author, description, feed image URL, feed URL, categories, and language.

5. Database Schema Initialization (create-episodes-table.js): Checks that the core podcasts and episodes tables, indices, and triggers exist in the remote Turso DB.

6. Relational Import (import-pi-data.js & sync-episodes.js): Loads the metadata from podcasts_export.csv into Turso. It then queries the live Podcast Index API to verify the latest episode details (URLs, descriptions, duration, publish timestamps) for these podcasts, writing them to the local relational database.

7. Vector Embedding Pipeline (vectorize.js):
  - Fetches the active trending podcasts in Turso that have not been vectorized yet (qdrant_vectorized = 0 or NULL).
  - For each podcast, retrieves the latest 30 episodes from the Podcast Index API.
  - Queries the Qdrant Cloud REST API using MD5 hashes of episode IDs as UUIDs to check if they have already been vectorized, skipping existing points.
  - For new episodes, cleans the description text, builds the standard prompt string, and generates a 1024-dimensional vector embedding using CPU-based execution of Xenova/bge-large-en-v1.5 from the transformers.js pipeline.
  - Upserts the generated vectors and detailed payload metadata (numeric ID, titles, authors, images, publish dates, language, durations) to the Qdrant Cloud collection.
  - Updates the Turso database to set qdrant_vectorized = 1 for the podcast.

8. Rolling Window Cleanup:
  - After updating, the script triggers a Qdrant pruning command (POST /collections/episodes/points/delete) to delete any episode for the current podcast whose ID is not within the top 30 active list. This ensures the index remains strictly capped at the latest 30 episodes per show, staying within free-tier storage limits.
