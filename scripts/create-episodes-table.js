#!/usr/bin/env node

/**
 * Initialize the separate 'episodes' table and indices in Turso
 */

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
}

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
        throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
    }
    const res = await response.json();
    if (res.results && res.results[0] && res.results[0].type === "error") {
        throw new Error(`SQL execution error: ${res.results[0].error.message}`);
    }
    return res;
}

async function main() {
    console.log("[SCHEMA] Initializing database schema updates...");

    // 1. Episodes table bypass: Episodes and vectors are stored in Qdrant Cloud.
    console.log("[SCHEMA] Skipping 'episodes' table creation (episodes are stored in Qdrant Vector DB).");

    // 3. Create missing performance and feature indexes
    console.log("[SCHEMA] Creating index 'idx_podcasts_itunes_id' (critical root cause fix)...");
    await executeSQL("CREATE INDEX IF NOT EXISTS idx_podcasts_itunes_id ON podcasts(itunes_id)");

    try {
        console.log("[SCHEMA] Adding 'qdrant_vectorized' column to podcasts table...");
        await executeSQL("ALTER TABLE podcasts ADD COLUMN qdrant_vectorized INTEGER DEFAULT 0");
        console.log("[SCHEMA] Column 'qdrant_vectorized' successfully added!");
    } catch (e) {
        console.log("[SCHEMA] Column 'qdrant_vectorized' already exists or podcasts table not loaded yet.");
    }

    console.log("[SCHEMA] Creating index 'idx_charts_country_category_rank'...");
    await executeSQL("CREATE INDEX IF NOT EXISTS idx_charts_country_category_rank ON charts(country, category, rank)");

    console.log("[SCHEMA] Creating index 'idx_charts_itunes_id'...");
    await executeSQL("CREATE INDEX IF NOT EXISTS idx_charts_itunes_id ON charts(itunes_id)");

    // 4. Create feedback table and its index
    console.log("[SCHEMA] Creating table 'feedback'...");
    await executeSQL(`
        CREATE TABLE IF NOT EXISTS feedback (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category TEXT NOT NULL,
            message TEXT NOT NULL,
            app_version TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            ip_hash TEXT,
            email TEXT
        )
    `);

    console.log("[SCHEMA] Creating index 'idx_feedback_ip_hash'...");
    await executeSQL("CREATE INDEX IF NOT EXISTS idx_feedback_ip_hash ON feedback(ip_hash, created_at)");

    // 5. Create FTS5 virtual table for podcasts search
    console.log("[SCHEMA] Checking virtual table 'podcasts_fts' columns...");
    let rebuildFTS = false;
    try {
        const infoRes = await executeSQL("PRAGMA table_info(podcasts_fts)");
        const rows = infoRes?.results?.[0]?.response?.result?.rows || [];
        const columns = rows.map(r => r[1]?.value);
        if (columns.length > 0 && !columns.includes("description")) {
            console.log("[SCHEMA] 'podcasts_fts' exists but is missing the 'description' column. Rebuilding...");
            rebuildFTS = true;
        }
    } catch (e) {
        // Table probably doesn't exist yet, which is fine
    }

    if (rebuildFTS) {
        console.log("[SCHEMA] Dropping old triggers to prepare for FTS rebuild...");
        await executeSQL("DROP TRIGGER IF EXISTS podcasts_ai");
        await executeSQL("DROP TRIGGER IF EXISTS podcasts_ad");
        await executeSQL("DROP TRIGGER IF EXISTS podcasts_au");
        console.log("[SCHEMA] Dropping old podcasts_fts table...");
        await executeSQL("DROP TABLE IF EXISTS podcasts_fts");
    }

    console.log("[SCHEMA] Creating virtual table 'podcasts_fts'...");
    await executeSQL(`
        CREATE VIRTUAL TABLE IF NOT EXISTS podcasts_fts USING fts5(
            podcast_id, title, author, description
        )
    `);

    // 6. Create triggers to automatically keep podcasts_fts in sync
    console.log("[SCHEMA] Dropping obsolete legacy triggers...");
    try {
        await executeSQL("DROP TRIGGER IF EXISTS after_podcast_insert");
    } catch (e) {
        console.warn("[SCHEMA] Note: Dropping legacy after_podcast_insert failed:", e.message);
    }
    try {
        await executeSQL("DROP TRIGGER IF EXISTS after_podcast_delete");
    } catch (e) {
        console.warn("[SCHEMA] Note: Dropping legacy after_podcast_delete failed:", e.message);
    }

    // Always drop and recreate our triggers to make sure they are up-to-date and sync description
    console.log("[SCHEMA] Dropping/recreating triggers for podcasts_fts sync...");
    await executeSQL("DROP TRIGGER IF EXISTS podcasts_ai");
    await executeSQL("DROP TRIGGER IF EXISTS podcasts_ad");
    await executeSQL("DROP TRIGGER IF EXISTS podcasts_au");

    await executeSQL(`
        CREATE TRIGGER IF NOT EXISTS podcasts_ai AFTER INSERT ON podcasts BEGIN
            INSERT INTO podcasts_fts(podcast_id, title, author, description) 
            VALUES (new.id, new.title, new.author, new.description);
        END
    `);

    await executeSQL(`
        CREATE TRIGGER IF NOT EXISTS podcasts_ad AFTER DELETE ON podcasts BEGIN
            DELETE FROM podcasts_fts WHERE podcast_id = old.id;
        END
    `);

    await executeSQL(`
        CREATE TRIGGER IF NOT EXISTS podcasts_au AFTER UPDATE ON podcasts 
        WHEN old.title != new.title OR old.author != new.author OR old.description != new.description
        BEGIN
            UPDATE podcasts_fts SET 
                title = new.title, 
                author = new.author,
                description = new.description
            WHERE podcast_id = old.id;
        END
    `);

    // 7. Populate FTS5 table with existing podcasts
    console.log("[SCHEMA] Populating podcasts_fts with existing podcasts data...");
    await executeSQL(`
        INSERT INTO podcasts_fts(podcast_id, title, author, description)
        SELECT id, title, author, description FROM podcasts
        WHERE id NOT IN (SELECT podcast_id FROM podcasts_fts)
    `);

    console.log("[SCHEMA] Schema initialization complete!");
}

main().catch(err => {
    console.error("[SCHEMA] Schema migration failed:", err);
    process.exit(1);
});
