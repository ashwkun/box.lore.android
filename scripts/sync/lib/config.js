'use strict';

/**
 * Central pipeline configuration.
 * Countries: tier 'full' = charts + episode sync + vectors.
 *            tier 'charts-only' = charts + podcast import + latest_ep sync, NO vectors.
 * Phase 2 country additions happen HERE only - no country knowledge lives in
 * the workflow YAML or individual stage scripts.
 */
const COUNTRIES = [
    { code: 'us', tier: 'full' },
    { code: 'in', tier: 'full' },
    { code: 'gb', tier: 'full' },
    { code: 'fr', tier: 'full' },
];

const FULL_TIER_COUNTRIES = COUNTRIES.filter(c => c.tier === 'full').map(c => c.code);
const ALL_COUNTRIES = COUNTRIES.map(c => c.code);

// iTunes chart categories (genre id map used by stage 1)
const GENRE_MAP = {
    'News': '1489',
    'Technology': '1318',
    'Business': '1321',
    'Comedy': '1303',
    'True Crime': '1488',
    'Sports': '1545',
    'Health': '1512',
    'History': '1487',
    'Arts': '1301',
    'Society & Culture': '1324',
    'Education': '1304',
    'Science': '1533',
    'TV & Film': '1309',
    'Fiction': '1483',
    'Music': '1310',
    'Religion & Spirituality': '1314',
    'Kids & Family': '1305',
    'Leisure': '1502',
    'Government': '1511',
};
const CATEGORIES = ['all', ...Object.keys(GENRE_MAP)];

module.exports = {
    COUNTRIES,
    ALL_COUNTRIES,
    FULL_TIER_COUNTRIES,
    GENRE_MAP,
    CATEGORIES,

    // --- Vectorization ---
    // bge-m3: multilingual, 1024-dim, no query instruction prefix needed.
    // Runtime queries use @cf/baai/bge-m3 on Workers AI (must stay in sync).
    EMBED_MODEL: 'Xenova/bge-m3',
    EPISODES_COLLECTION: 'episodes',
    PODCASTS_COLLECTION: 'podcasts',
    VECTOR_DIM: 1024,
    EPISODES_PER_SHOW: 30,          // strict: latest 30 episodes per show
    // Per-run embedding budget (NOT a show cap). Incremental updates cost ~1
    // embedding per show; cold-start shows cost up to EPISODES_PER_SHOW.
    MAX_EMBEDDINGS_PER_RUN: parseInt(process.env.MAX_EMBEDDINGS_PER_RUN || '3000', 10),
    PAYLOAD_DESCRIPTION_MAX: 1000,  // cap description text stored in Qdrant payloads

    // --- Episode sync staleness tiers ---
    NEWS_STALE_MS: 8 * 60 * 60 * 1000,
    REGULAR_STALE_MS: 24 * 60 * 60 * 1000,
    // Hard cap on shows checked per run. Oldest-first ordering means deferred
    // shows lead the queue next run, so a backlog self-distributes across the
    // 5 daily runs instead of blowing one run's wall time (~20 min at 3.5 rps).
    MAX_CHECKS_PER_RUN: parseInt(process.env.MAX_CHECKS_PER_RUN || '4000', 10),
    // Deterministic per-show jitter (+/-10%) on staleness thresholds so
    // check times spread instead of re-synchronizing into waves.
    STALENESS_JITTER: 0.10,

    // --- Import ---
    // If more than this many chart shows are missing from Turso, use the PI
    // dump; otherwise fetch individually from the PI API.
    DUMP_THRESHOLD: 300,
    API_IMPORT_CAP: 200,

    // --- Cleanup ---
    CLEANUP_GRACE_DAYS: 7,          // only delete shows absent from charts this long
    CLEANUP_SAFETY_MIN_CHARTS: 500, // abort cleanup if charts look wiped

    // --- State / files ---
    STATE_FILE: 'data/sync_cache.json',
    HISTORY_FILE: 'data/db_cost_history.json',
    REPORT_FILE: 'data/db_cost_report.md',
    RUN_STATS_FILE: '/tmp/db_run_stats.json',

    // Fixed column whitelist for the podcasts table import path (no auto-ALTER).
    PODCAST_IMPORT_COLUMNS: [
        'id', 'itunes_id', 'title', 'author', 'description', 'image_url',
        'feed_url', 'website_url', 'categories', 'language', 'explicit', 'type',
    ],
};
