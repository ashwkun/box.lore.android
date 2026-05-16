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
const BATCH_SIZE = 80; // Turso pipeline limit-safe batch size
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
                    args: args.map(a => ({
                        type: a === null ? "null" : "text",
                        value: a === null ? null : String(a)
                    }))
                }
            }, { type: "close" }]
        })
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Turso HTTP ${response.status}: ${text}`);
    }
    return response.json();
}

async function executeBatch(statements) {
    if (statements.length === 0) return;

    const requests = statements.map(stmt => ({
        type: "execute",
        stmt: {
            sql: stmt.sql,
            args: stmt.args.map(a => ({
                type: a === null ? "null" : "text",
                value: a === null ? null : String(a || "")
            }))
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
    return response.json();
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

// ── Extract from PI dump ────────────────────────────────────────────

function extractFromPIDump() {
    console.log(`📦 Extracting from ${PI_DB_PATH} (episodeCount >= ${MIN_EPISODES}, dead=0)...`);

    if (!fs.existsSync(PI_DB_PATH)) {
        throw new Error(`PI database not found at ${PI_DB_PATH}`);
    }

    // Get total count first
    const countResult = execSync(
        `sqlite3 "${PI_DB_PATH}" "SELECT COUNT(*) FROM podcasts WHERE dead = 0 AND episodeCount >= ${MIN_EPISODES};"`,
        { encoding: 'utf-8' }
    ).trim();
    console.log(`📊 Total qualifying podcasts: ${countResult}`);

    // Export as CSV with pipe delimiter (avoids comma-in-description issues)
    // Using JSON output for safer parsing
    const exportSQL = `
        SELECT 
            id,
            title,
            COALESCE(itunesAuthor, author, '') as author,
            SUBSTR(COALESCE(description, ''), 1, ${DESC_MAX_LENGTH}) as description,
            COALESCE(artwork, imageUrl, '') as artwork,
            COALESCE(category1, '') || CASE WHEN category2 IS NOT NULL AND category2 != '' THEN ',' || category2 ELSE '' END as categories,
            COALESCE(language, 'en') as language,
            episodeCount,
            COALESCE(newestItemPubdate, 0) as newest_pub_date
        FROM podcasts
        WHERE dead = 0
          AND episodeCount >= ${MIN_EPISODES}
          AND title IS NOT NULL
          AND title != ''
        ORDER BY episodeCount DESC;
    `;

    // Use JSON mode for reliable parsing
    const rawOutput = execSync(
        `sqlite3 -json "${PI_DB_PATH}" "${exportSQL.replace(/\n/g, ' ').replace(/"/g, '\\"')}"`,
        { encoding: 'utf-8', maxBuffer: 1024 * 1024 * 500 } // 500MB buffer
    ).trim();

    let rows;
    try {
        rows = JSON.parse(rawOutput);
    } catch (e) {
        console.error("❌ Failed to parse sqlite3 JSON output");
        throw e;
    }

    console.log(`✅ Extracted ${rows.length} podcasts from PI dump`);
    return rows;
}

// ── Sanitize text for Turso insertion ───────────────────────────────

function sanitize(text) {
    if (!text) return null;
    return String(text)
        .replace(/\0/g, '')          // Remove null bytes
        .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '') // Remove control chars (keep \n, \r, \t)
        .trim();
}

// ── Import to Turso ─────────────────────────────────────────────────

async function importToTurso(rows) {
    const total = rows.length;
    console.log(`\n🚀 Importing ${total} podcasts to Turso...`);

    // Clear existing data for clean rebuild
    console.log("🗑️  Clearing existing search index...");
    await executeSQL("DELETE FROM podcast_search");

    let imported = 0;
    let errors = 0;
    const startTime = Date.now();

    for (let i = 0; i < total; i += BATCH_SIZE) {
        const batch = rows.slice(i, i + BATCH_SIZE);
        const statements = [];

        for (const row of batch) {
            const title = sanitize(row.title);
            if (!title) continue;

            statements.push({
                sql: `INSERT OR REPLACE INTO podcast_search 
                      (id, title, author, description, artwork, categories, language, episode_count, newest_pub_date, updated_at)
                      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))`,
                args: [
                    row.id,
                    title,
                    sanitize(row.author),
                    sanitize(row.description),
                    sanitize(row.artwork),
                    sanitize(row.categories),
                    sanitize(row.language),
                    row.episodeCount || row.episode_count || 0,
                    row.newest_pub_date || 0
                ]
            });
        }

        try {
            await executeBatch(statements);
            imported += statements.length;
        } catch (e) {
            console.error(`⚠️ Batch error at offset ${i}:`, e.message);
            errors += batch.length;
        }

        // Progress log every 5000 rows
        if (imported % 5000 < BATCH_SIZE) {
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            const rate = (imported / elapsed * 1).toFixed(0);
            const pct = Math.round((i / total) * 100);
            console.log(`  📥 ${imported}/${total} (${pct}%) | ${rate} rows/s | Errors: ${errors}`);
        }
    }

    const totalElapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n✅ Import complete: ${imported} rows in ${totalElapsed}s (${errors} errors)`);
    return imported;
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

    // 2. Extract from PI dump
    const rows = extractFromPIDump();

    // 3. Import to Turso
    const imported = await importToTurso(rows);

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
