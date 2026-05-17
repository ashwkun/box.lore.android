#!/usr/bin/env node
/**
 * Build Search Index from Podcast Index Dump
 * 
 * Reads the PI dump SQLite database, computes popularity scores,
 * and builds a search-optimized SQLite database with FTS5 trigram index.
 * 
 * Output: search.db — ready for `turso db import`
 * 
 * Usage: node scripts/build-search-index.js [--limit N]
 */

const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

// Parse CLI args
const args = process.argv.slice(2);
const limitIdx = args.indexOf('--limit');
const LIMIT = limitIdx !== -1 ? parseInt(args[limitIdx + 1], 10) : 0; // 0 = no limit

const PI_DB_PATH = process.env.PI_DB_PATH || 'podcastindex_feeds.db';
const OUTPUT_DB_PATH = process.env.OUTPUT_DB_PATH || 'search.db';
const CHARTS_CSV_PATH = process.env.CHARTS_CSV_PATH || 'chart_itunes_ids.txt';

// Verify PI dump exists
if (!fs.existsSync(PI_DB_PATH)) {
    console.error(`PI dump not found at ${PI_DB_PATH}`);
    process.exit(1);
}

// Remove old output if exists
if (fs.existsSync(OUTPUT_DB_PATH)) {
    fs.unlinkSync(OUTPUT_DB_PATH);
}

console.log("=== Building Search Index ===");
console.log(`Source: ${PI_DB_PATH}`);
console.log(`Output: ${OUTPUT_DB_PATH}`);
console.log(`Limit:  ${LIMIT || 'none (all podcasts)'}`);
console.log("");

// Helper to run sqlite3 commands
function sqlite3(dbPath, sql) {
    return execSync(`sqlite3 "${dbPath}" "${sql}"`, { encoding: 'utf-8', maxBuffer: 1024 * 1024 * 100 }).trim();
}

function sqlite3Multi(dbPath, commands) {
    // Write commands to a temp file to avoid shell escaping issues
    const tmpFile = '/tmp/search_index_commands.sql';
    fs.writeFileSync(tmpFile, commands);
    return execSync(`sqlite3 "${dbPath}" < "${tmpFile}"`, { encoding: 'utf-8', maxBuffer: 1024 * 1024 * 100 }).trim();
}

// Step 1: Get stats from PI dump
const totalPodcasts = sqlite3(PI_DB_PATH, "SELECT COUNT(*) FROM podcasts;");
console.log(`Total podcasts in PI dump: ${totalPodcasts}`);

// Step 2: Get max values for normalization
console.log("Computing normalization values...");
const maxEpisodeCount = parseInt(sqlite3(PI_DB_PATH, "SELECT MAX(episodeCount) FROM podcasts WHERE episodeCount IS NOT NULL;")) || 1;
const maxPopularity = parseInt(sqlite3(PI_DB_PATH, "SELECT MAX(popularityScore) FROM podcasts WHERE popularityScore IS NOT NULL;")) || 1;
console.log(`  Max episode count: ${maxEpisodeCount}`);
console.log(`  Max PI popularity: ${maxPopularity}`);

// Step 3: Load chart iTunes IDs if available
let chartIds = new Set();
if (fs.existsSync(CHARTS_CSV_PATH)) {
    const chartContent = fs.readFileSync(CHARTS_CSV_PATH, 'utf-8');
    chartContent.split('\n').filter(l => l.trim()).forEach(id => chartIds.add(id.trim()));
    console.log(`  Chart iTunes IDs loaded: ${chartIds.size}`);
} else {
    console.log("  No charts file found, skipping charts boost");
}

// Step 4: Create output database schema
console.log("\nCreating output database schema...");
sqlite3Multi(OUTPUT_DB_PATH, `
CREATE TABLE podcasts_search (
    id INTEGER PRIMARY KEY,
    itunes_id TEXT,
    title TEXT NOT NULL,
    author TEXT,
    description TEXT,
    image_url TEXT,
    feed_url TEXT,
    website_url TEXT,
    language TEXT,
    episode_count INTEGER DEFAULT 0,
    newest_pub INTEGER DEFAULT 0,
    popularity_score REAL DEFAULT 0.0,
    categories TEXT,
    explicit INTEGER DEFAULT 0
);
`);

// Step 5: Attach PI dump and insert with computed popularity scores
console.log("Exporting podcasts and computing popularity scores...");

const nowEpoch = Math.floor(Date.now() / 1000);
const oneYearAgo = nowEpoch - (365 * 24 * 60 * 60);
const logMaxEp = Math.log(maxEpisodeCount + 1);

// Build the chart IDs as a temp table in the output DB
if (chartIds.size > 0) {
    sqlite3Multi(OUTPUT_DB_PATH, `
CREATE TABLE chart_ids (itunes_id TEXT PRIMARY KEY);
`);
    // Insert chart IDs in batches
    const chartArray = Array.from(chartIds);
    const CHART_BATCH = 500;
    for (let i = 0; i < chartArray.length; i += CHART_BATCH) {
        const batch = chartArray.slice(i, i + CHART_BATCH);
        const values = batch.map(id => `('${id}')`).join(',');
        sqlite3Multi(OUTPUT_DB_PATH, `INSERT OR IGNORE INTO chart_ids (itunes_id) VALUES ${values};`);
    }
    console.log(`  Inserted ${chartIds.size} chart IDs into output DB`);
}

// Now do the main export using ATTACH
// Popularity score formula:
// 0.35 * recencyDecay + 0.30 * episodeNorm + 0.20 * piPopNorm + 0.15 * chartsBoost
//
// recencyDecay = MAX(0, 1.0 - (now - newestItemPubdate) / (2 * 365 * 24 * 3600))
//   → 1.0 for today, 0.5 for 1 year ago, 0.0 for 2+ years ago
//
// episodeNorm = ln(episodeCount + 1) / ln(maxEpisodeCount + 1)
//   → logarithmic normalization (diminishing returns)
// piPopNorm = popularityScore / maxPopularity
//
// chartsBoost = 1.0 if in Apple Charts, else 0.0

const limitClause = LIMIT > 0 ? `LIMIT ${LIMIT}` : '';

const insertSQL = `
ATTACH DATABASE '${PI_DB_PATH}' AS pi;

INSERT INTO podcasts_search (id, itunes_id, title, author, description, image_url, feed_url, website_url, language, episode_count, newest_pub, popularity_score, categories, explicit)
SELECT
    p.id,
    CAST(p.itunesId AS TEXT),
    p.title,
    p.itunesAuthor,
    p.description,
    p.imageUrl,
    p.url,
    p.link,
    p.language,
    COALESCE(p.episodeCount, 0),
    COALESCE(p.newestItemPubdate, 0),
    -- Popularity score computation
    ROUND(
        0.35 * MAX(0.0, MIN(1.0, 1.0 - CAST((${nowEpoch} - COALESCE(p.newestItemPubdate, 0)) AS REAL) / (2.0 * 365.0 * 24.0 * 3600.0)))
      + 0.30 * (LOG(COALESCE(p.episodeCount, 0) + 1) / ${logMaxEp})
      + 0.20 * (CAST(COALESCE(p.popularityScore, 0) AS REAL) / ${maxPopularity}.0)
      + 0.15 * CASE WHEN ci.itunes_id IS NOT NULL THEN 1.0 ELSE 0.0 END
    , 6),
    COALESCE(p.category1, '') || CASE WHEN p.category2 IS NOT NULL AND p.category2 != '' THEN ', ' || p.category2 ELSE '' END,
    COALESCE(p.explicit, 0)
FROM pi.podcasts p
LEFT JOIN chart_ids ci ON CAST(p.itunesId AS TEXT) = ci.itunes_id
WHERE p.title IS NOT NULL AND p.title != ''
  AND p.episodeCount >= 10
  AND p.language LIKE 'en%'
  AND COALESCE(p.dead, 0) = 0
${limitClause};

DETACH DATABASE pi;
`;

const startInsert = Date.now();
sqlite3Multi(OUTPUT_DB_PATH, insertSQL);
const insertTime = ((Date.now() - startInsert) / 1000).toFixed(1);

const insertedCount = sqlite3(OUTPUT_DB_PATH, "SELECT COUNT(*) FROM podcasts_search;");
console.log(`  Inserted ${insertedCount} podcasts in ${insertTime}s`);

// Step 6: Create indexes on the base table
console.log("\nCreating indexes...");
sqlite3Multi(OUTPUT_DB_PATH, `
CREATE INDEX IF NOT EXISTS idx_ps_itunes_id ON podcasts_search(itunes_id);
CREATE INDEX IF NOT EXISTS idx_ps_popularity ON podcasts_search(popularity_score DESC);
`);

// Step 7: Generate clean dictionary and build FTS5 index
console.log("Building FTS5 index (standard tokenization)...");
const startFTS = Date.now();

sqlite3Multi(OUTPUT_DB_PATH, `
CREATE VIRTUAL TABLE search_fts USING fts5(
    title,
    author,
    content='podcasts_search',
    content_rowid='id'
);

INSERT INTO search_fts(search_fts) VALUES('rebuild');
`);

const ftsTime = ((Date.now() - startFTS) / 1000).toFixed(1);
console.log(`  FTS5 index built in ${ftsTime}s`);

console.log("Extracting dictionary words...");
// Extract all words from the FTS5 vocab table
sqlite3Multi(OUTPUT_DB_PATH, `
CREATE VIRTUAL TABLE search_vocab USING fts5vocab('main', 'search_fts', 'row');
`);
// Read the vocab table into JSON using sqlite3 JSON output
try {
    const vocabJson = execSync(\`sqlite3 "\${OUTPUT_DB_PATH}" -json "SELECT term FROM search_vocab WHERE length(term) >= 4;"\`, { encoding: 'utf-8', maxBuffer: 1024 * 1024 * 100 }).trim();
    if (vocabJson) {
        const words = JSON.parse(vocabJson).map(row => row.term);
        fs.writeFileSync('proxy/src/dictionary.json', JSON.stringify(words));
        console.log(\`  Extracted \${words.length} words to proxy/src/dictionary.json\`);
    }
} catch (e) {
    console.error("Failed to generate dictionary:", e.message);
}

// Step 8: Clean up chart_ids temp table
if (chartIds.size > 0) {
    sqlite3(OUTPUT_DB_PATH, "DROP TABLE IF EXISTS chart_ids;");
}

// Step 9: Optimize
console.log("\nOptimizing database...");
sqlite3Multi(OUTPUT_DB_PATH, `
INSERT INTO search_fts(search_fts) VALUES('optimize');
VACUUM;
PRAGMA journal_mode=WAL;
PRAGMA wal_checkpoint(TRUNCATE);
`);

// Step 10: Report
const dbSize = fs.statSync(OUTPUT_DB_PATH).size;
const dbSizeMB = (dbSize / 1024 / 1024).toFixed(1);

// Quick test: search for "waveform"
const testResult = sqlite3(OUTPUT_DB_PATH, 
    "SELECT ps.title, ps.author, ps.popularity_score FROM search_fts sf JOIN podcasts_search ps ON sf.rowid = ps.id WHERE search_fts MATCH 'wav' ORDER BY ps.popularity_score DESC LIMIT 5;"
);

console.log("\n=== Build Complete ===");
console.log(`Database:   ${OUTPUT_DB_PATH}`);
console.log(`Size:       ${dbSizeMB} MB`);
console.log(`Podcasts:   ${insertedCount}`);
console.log(`FTS build:  ${ftsTime}s`);
console.log(`Total time: ${((Date.now() - startInsert) / 1000).toFixed(1)}s`);
console.log(`\nTest query (MATCH 'wav'):`);
console.log(testResult || "(no results)");
console.log("======================\n");
