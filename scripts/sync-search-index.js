#!/usr/bin/env node
/**
 * sync-search-index.js
 * 
 * Builds a full-text search index in Turso from the Podcast Index database dump.
 * 
 * What it does:
 *   1. Reads the PI SQLite dump (podcastindex_feeds.db)
 *   2. Filters: dead=0, episodeCount >= 5
 *   3. Extracts: id, title, author, description (truncated), artwork, categories, language, episodeCount
 *   4. Upserts into `podcast_search` table in Turso
 *   5. Rebuilds FTS5 index for full-text search
 * 
 * Designed to run in GitHub Actions on a weekly schedule.
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const PI_DB_PATH = process.env.PI_DB_PATH || 'podcastindex_feeds.db';
const MIN_EPISODES = parseInt(process.env.MIN_EPISODES || '5', 10);
const BATCH_SIZE = 1000; // Increased batch size for faster inserts
const DESC_MAX_LENGTH = 300; // Truncate descriptions to save space

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("❌ Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
}

// ── Turso HTTP helpers ──────────────────────────────────────────────

async function executeSQL(sql, args = []) {
    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            requests: [{
                type: "execute",
                stmt: {
                    sql,
                    args: args.map(a => {
                        if (a === null || a === undefined) return { type: "null" };
                        if (typeof a === "number") {
                            if (Number.isNaN(a)) return { type: "null" };
                            return { type: "integer", value: String(Math.round(a)) };
                        }
                        return { type: "text", value: String(a) };
                    })
                }
            }, { type: "close" }]
        })
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Turso HTTP ${response.status}: ${text}`);
    }
    
    const data = await response.json();
    for (const res of data.results || []) {
        if (res.type === "error") {
            throw new Error(`Turso SQL error: ${res.error?.message}`);
        }
    }
    return data;
}

async function executeBatch(statements) {
    if (statements.length === 0) return;

    const requests = statements.map(stmt => ({
        type: "execute",
        stmt: {
            sql: stmt.sql,
            args: stmt.args.map(a => {
                if (a === null || a === undefined) return { type: "null" };
                if (typeof a === "number") {
                    if (Number.isNaN(a)) return { type: "null" };
                    return { type: "integer", value: String(Math.round(a)) };
                }
                return { type: "text", value: String(a) };
            })
        }
    }));
    requests.push({ type: "close" });

    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ requests })
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Turso batch error ${response.status}: ${text}`);
    }

    const data = await response.json();
    let successCount = 0;
    for (let i = 0; i < (data.results || []).length; i++) {
        const res = data.results[i];
        if (res.type === "error") {
            console.error(`⚠️ SQL error at statement ${i}: ${res.error?.message}`);
            console.error(`   Statement: ${statements[i].sql.trim()} | Args: ${JSON.stringify(statements[i].args)}`);
        } else {
            successCount++;
        }
    }
    data.successCount = successCount;
    return data;
}

// ── Schema setup ────────────────────────────────────────────────────

async function ensureSchema() {
    console.log("📐 Ensuring Turso schema...");

    // Main search table
    await executeSQL(`
        CREATE TABLE IF NOT EXISTS podcast_search (
            id INTEGER PRIMARY KEY,
            title TEXT NOT NULL,
            author TEXT,
            description TEXT,
            artwork TEXT,
            categories TEXT,
            language TEXT,
            episode_count INTEGER DEFAULT 0,
            newest_pub_date INTEGER DEFAULT 0,
            popularity_score INTEGER DEFAULT 0,
            updated_at TEXT DEFAULT (datetime('now'))
        )
    `);

    // FTS5 virtual table for full-text search
    // content= syncs with the main table, content_rowid maps to id
    try {
        await executeSQL(`
            CREATE VIRTUAL TABLE IF NOT EXISTS podcast_search_fts USING fts5(
                title,
                author,
                description,
                categories,
                content='podcast_search',
                content_rowid='id',
                tokenize='porter unicode61 remove_diacritics 2'
            )
        `);
    } catch (e) {
        // FTS5 table may already exist — that's fine
        if (!e.message.includes('already exists')) {
            console.warn("⚠️ FTS5 creation warning:", e.message);
        }
    }

    console.log("✅ Schema ready");
}

// ── Extract from PI dump to CSV ─────────────────────────────────────

const CSV_EXPORT_PATH = '/tmp/podcast_search_export.csv';

function extractToCSV() {
    console.log(`📦 Extracting from ${PI_DB_PATH} (episodeCount >= ${MIN_EPISODES}, dead=0)...`);

    if (!fs.existsSync(PI_DB_PATH)) {
        throw new Error(`PI database not found at ${PI_DB_PATH}`);
    }

    // Inspect schema for debugging
    try {
        const schema = execSync(
            `sqlite3 "${PI_DB_PATH}" "PRAGMA table_info(podcasts);"`,
            { encoding: 'utf-8' }
        ).trim();
        console.log("📋 PI dump columns:", schema.split('\n').map(l => l.split('|')[1]).join(', '));
    } catch (e) {
        console.warn("⚠️ Could not inspect schema:", e.message);
    }

    // Get total count first
    const countResult = execSync(
        `sqlite3 "${PI_DB_PATH}" "SELECT COUNT(*) FROM podcasts WHERE dead = 0 AND episodeCount >= ${MIN_EPISODES};"`,
        { encoding: 'utf-8' }
    ).trim();
    console.log(`📊 Total qualifying podcasts: ${countResult}`);

    // Export to CSV file (avoids loading everything into memory)
    const exportSQL = `
.mode csv
.headers on
.output ${CSV_EXPORT_PATH}
SELECT 
    id,
    title,
    COALESCE(itunesAuthor, '') as author,
    SUBSTR(COALESCE(description, ''), 1, ${DESC_MAX_LENGTH}) as description,
    COALESCE(imageUrl, '') as artwork,
    TRIM(
        COALESCE(category1, '') || 
        CASE WHEN category2 != '' THEN ',' || category2 ELSE '' END ||
        CASE WHEN category3 != '' THEN ',' || category3 ELSE '' END ||
        CASE WHEN category4 != '' THEN ',' || category4 ELSE '' END ||
        CASE WHEN category5 != '' THEN ',' || category5 ELSE '' END
    ) as categories,
    COALESCE(language, 'en') as language,
    episodeCount,
    COALESCE(newestItemPubdate, 0) as newest_pub_date,
    COALESCE(popularityScore, 0) as popularity_score
FROM podcasts
WHERE dead = 0
  AND episodeCount >= ${MIN_EPISODES}
  AND title IS NOT NULL
  AND title != ''
  AND COALESCE(language, '') NOT LIKE 'zh%'
  AND COALESCE(language, '') NOT LIKE 'ja%'
  AND COALESCE(language, '') NOT LIKE 'ko%'
ORDER BY popularityScore DESC, episodeCount DESC;
.output stdout
`;

    const sqlFile = '/tmp/search_export.sql';
    fs.writeFileSync(sqlFile, exportSQL.trim());

    console.log("📝 Exporting to CSV...");
    execSync(`sqlite3 "${PI_DB_PATH}" < "${sqlFile}"`, {
        encoding: 'utf-8',
        timeout: 300000 // 5 min timeout
    });

    const stats = fs.statSync(CSV_EXPORT_PATH);
    console.log(`✅ CSV exported: ${(stats.size / 1024 / 1024).toFixed(1)} MB`);
    return parseInt(countResult, 10);
}

// ── CSV line parser (handles quoted fields with commas/newlines) ─────

function parseCSVLine(line) {
    const result = [];
    let current = '';
    let inQuotes = false;

    for (let i = 0; i < line.length; i++) {
        const char = line[i];
        if (char === '"') {
            if (inQuotes && line[i + 1] === '"') {
                current += '"';
                i++;
            } else {
                inQuotes = !inQuotes;
            }
        } else if (char === ',' && !inQuotes) {
            result.push(current);
            current = '';
        } else {
            current += char;
        }
    }
    result.push(current);
    return result;
}

// ── Sanitize text for Turso insertion ───────────────────────────────

function sanitize(text) {
    if (!text) return null;
    return String(text)
        .replace(/\0/g, '')          // Remove null bytes
        .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '') // Remove control chars (keep \n, \r, \t)
        .trim();
}

// ── Stream import to Turso ──────────────────────────────────────────

async function streamImportToTurso(expectedCount) {
    console.log(`\n🚀 Streaming import to Turso (~${expectedCount} podcasts)...`);

    // Clear existing data for clean rebuild
    console.log("🗑️  Clearing existing search index...");
    await executeSQL("DELETE FROM podcast_search");

    const readline = require('readline');
    const fileStream = fs.createReadStream(CSV_EXPORT_PATH, { encoding: 'utf-8' });
    const rl = readline.createInterface({ input: fileStream, crlfDelay: Infinity });

    let headers = null;
    let batch = [];
    let imported = 0;
    let errors = 0;
    let lineNum = 0;
    const startTime = Date.now();

    // Dictionary tracking
    const wordStats = new Map();

    for await (const line of rl) {
        lineNum++;

        // First line = headers
        if (!headers) {
            headers = parseCSVLine(line);
            console.log("📋 CSV headers:", headers.join(', '));
            continue;
        }

        // Parse CSV line
        const values = parseCSVLine(line);
        if (values.length < headers.length) continue; // Skip malformed lines

        const row = {};
        headers.forEach((h, i) => { row[h] = values[i]; });

        const title = sanitize(row.title);
        if (!title) continue;

        // Skip titles with non-Latin scripts (Chinese, Japanese, Hindi, Thai, Arabic, Cyrillic, etc.)
        const nonLatinRegex = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Hangul}\p{Script=Thai}\p{Script=Devanagari}\p{Script=Arabic}\p{Script=Cyrillic}\p{Script=Hebrew}\p{Script=Tamil}\p{Script=Telugu}]/u;
        if (nonLatinRegex.test(title)) {
            continue;
        }

        const eps = parseInt(row.episodeCount || row.episode_count || 0, 10);

        // Build dictionary stats
        // Strip punctuation, make lowercase, split by whitespace
        const words = title.toLowerCase().replace(/['"]/g, "").replace(/[^\w\s]/g, " ").split(/\s+/);
        // Unique words per title so "the the" counts as 1
        const uniqueWords = new Set(words);
        for (const w of uniqueWords) {
            // Only keep alphabetical words, length >= 4
            if (w.length >= 4 && /^[a-z]+$/.test(w)) {
                const stat = wordStats.get(w) || { count: 0, maxEpisodes: 0 };
                stat.count += 1;
                if (eps > stat.maxEpisodes) stat.maxEpisodes = eps;
                wordStats.set(w, stat);
            }
        }

        batch.push([
            parseInt(row.id, 10),
            title,
            sanitize(row.author),
            sanitize(row.description),
            sanitize(row.artwork),
            sanitize(row.categories),
            sanitize(row.language),
            eps,
            parseInt(row.newest_pub_date || '0', 10),
            parseInt(row.popularity_score || '0', 10)
        ]);

        // Flush batch
        if (batch.length >= BATCH_SIZE) {
            try {
                let sql = `INSERT OR REPLACE INTO podcast_search (id, title, author, description, artwork, categories, language, episode_count, newest_pub_date, popularity_score, updated_at) VALUES `;
                const args = [];
                const placeholders = [];
                for (const r of batch) {
                    placeholders.push(`(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))`);
                    args.push(...r);
                }
                sql += placeholders.join(", ");
                await executeSQL(sql, args);
                imported += batch.length;
            } catch (e) {
                console.error(`⚠️ Bulk insert error at line ${lineNum}:`, e.message);
                errors += batch.length;
            }
            batch = [];

            // Progress log every 5000 rows
            if (imported % 5000 < BATCH_SIZE) {
                const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
                const rate = (imported / elapsed).toFixed(0);
                const pct = Math.round((imported / expectedCount) * 100);
                console.log(`  📥 ${imported}/${expectedCount} (${pct}%) | ${rate} rows/s | Errors: ${errors}`);
            }
        }
    }

    // Flush remaining
    if (batch.length > 0) {
        try {
            let sql = `INSERT OR REPLACE INTO podcast_search (id, title, author, description, artwork, categories, language, episode_count, newest_pub_date, popularity_score, updated_at) VALUES `;
            const args = [];
            const placeholders = [];
            for (const r of batch) {
                placeholders.push(`(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))`);
                args.push(...r);
            }
            sql += placeholders.join(", ");
            await executeSQL(sql, args);
            imported += batch.length;
        } catch (e) {
            errors += batch.length;
        }
    }

    const totalElapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n✅ Import complete: ${imported} rows in ${totalElapsed}s (${errors} errors)`);
    return { imported, wordStats };
}

// ── Rebuild FTS5 index ──────────────────────────────────────────────

async function rebuildFTS() {
    console.log("\n🔍 Rebuilding FTS5 search index...");
    const startTime = Date.now();

    try {
        // Rebuild the FTS5 content from the main table
        await executeSQL("INSERT INTO podcast_search_fts(podcast_search_fts) VALUES('rebuild')");
        const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
        console.log(`✅ FTS5 index rebuilt in ${elapsed}s`);
    } catch (e) {
        console.error("❌ FTS5 rebuild failed:", e.message);
        throw e;
    }
}

// ── Verify ──────────────────────────────────────────────────────────

async function verify() {
    console.log("\n🧪 Verification...");

    const countRes = await executeSQL("SELECT COUNT(*) as c FROM podcast_search");
    const count = countRes?.results?.[0]?.response?.result?.rows?.[0]?.[0]?.value || '0';
    console.log(`  📊 Total rows in podcast_search: ${count}`);

    const ftsRes = await executeSQL("SELECT COUNT(*) as c FROM podcast_search_fts");
    const ftsCount = ftsRes?.results?.[0]?.response?.result?.rows?.[0]?.[0]?.value || '0';
    console.log(`  📊 Total rows in FTS index: ${ftsCount}`);

    // Test a search
    const testRes = await executeSQL(
        "SELECT ps.id, ps.title, ps.author FROM podcast_search_fts fts JOIN podcast_search ps ON fts.rowid = ps.id WHERE fts MATCH 'joe rogan' LIMIT 5"
    );
    const testRows = testRes?.results?.[0]?.response?.result?.rows || [];
    console.log(`  🔎 Test search "joe rogan": ${testRows.length} results`);
    for (const r of testRows) {
        console.log(`     → ${r[1]?.value} by ${r[2]?.value}`);
    }

    // Test prefix search
    const prefixRes = await executeSQL(
        "SELECT ps.id, ps.title FROM podcast_search_fts fts JOIN podcast_search ps ON fts.rowid = ps.id WHERE fts MATCH 'tech*' LIMIT 5"
    );
    const prefixRows = prefixRes?.results?.[0]?.response?.result?.rows || [];
    console.log(`  🔎 Test prefix "tech*": ${prefixRows.length} results`);
    for (const r of prefixRows) {
        console.log(`     → ${r[1]?.value}`);
    }
}

// ── Main ────────────────────────────────────────────────────────────

async function main() {
    console.log("═══════════════════════════════════════════════");
    console.log("  BoxCast Search Index Sync");
    console.log(`  ${new Date().toISOString()}`);
    console.log("═══════════════════════════════════════════════\n");

    // 1. Ensure schema
    await ensureSchema();

    // 2. Extract from PI dump to CSV file
    const expectedCount = extractToCSV();

    // 3. Stream import to Turso
    const { imported, wordStats } = await streamImportToTurso(expectedCount);

    // Build and save dictionary
    console.log(`\n📚 Building spell-check dictionary from ${wordStats.size} unique words...`);
    const validWords = [];
    for (const [word, stat] of wordStats.entries()) {
        if (stat.count > 3 || stat.maxEpisodes > 50) {
            validWords.push(word);
        }
    }
    console.log(`  Filtered down to ${validWords.length} valid words.`);
    fs.writeFileSync('spell_dict.json', JSON.stringify(validWords));
    console.log(`  Saved dictionary to spell_dict.json`);

    // 4. Rebuild FTS5 index
    await rebuildFTS();

    // 5. Verify
    await verify();

    console.log("\n═══════════════════════════════════════════════");
    console.log(`  ✅ Done! ${imported} podcasts indexed for search`);
    console.log("═══════════════════════════════════════════════\n");
}

main().catch(err => {
    console.error("❌ Search index sync failed:", err);
    process.exit(1);
});
