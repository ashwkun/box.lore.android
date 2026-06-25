#!/usr/bin/env node

/**
 * Script to check Qdrant collections and backfill Turso DB status.
 * Compares Qdrant points against Turso podcasts and sets:
 * - qdrant_podcast_vectorized = 1 (if show is in 'podcasts' collection)
 * - qdrant_vectorized = 1 (if show has episodes in 'episodes' collection)
 * 
 * Runs entirely on read-only scrolling from Qdrant and batch updates in Turso.
 */

const crypto = require('crypto');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const QDRANT_URL = process.env.QDRANT_URL;
const QDRANT_API_KEY = process.env.QDRANT_API_KEY;

if (!TURSO_URL || !TURSO_TOKEN || !QDRANT_URL || !QDRANT_API_KEY) {
    console.error("Missing required environment variables (TURSO_URL, TURSO_AUTH_TOKEN, QDRANT_URL, QDRANT_API_KEY)");
    process.exit(1);
}

function generateUUID(strId) {
    const hash = crypto.createHash('md5').update(String(strId)).digest('hex');
    return `${hash.substring(0,8)}-${hash.substring(8,12)}-${hash.substring(12,16)}-${hash.substring(16,20)}-${hash.substring(20)}`;
}

function mapArgType(val) {
    if (val === null || val === undefined || val === "") {
        return { type: "null", value: null };
    }
    if (typeof val === 'number') {
        return { type: "integer", value: String(val) };
    }
    if (typeof val === 'string' && /^\d+$/.test(val)) {
        return { type: "integer", value: val };
    }
    return { type: "text", value: String(val) };
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
                stmt: { sql, args: args.map(mapArgType) }
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

async function executeBatch(statements) {
    if (statements.length === 0) return;
    const requests = statements.map(stmt => ({
        type: "execute",
        stmt: { sql: stmt.sql, args: stmt.args.map(mapArgType) }
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
    if (!response.ok) throw new Error(`Turso HTTP error: ${response.status}`);
    const res = await response.json();
    let totalRowsWritten = 0;
    if (res.results) {
        for (const result of res.results) {
            if (result.type === "error") {
                throw new Error(`Turso SQL batch error: ${result.error.message}`);
            }
            if (result.response?.result) {
                totalRowsWritten += result.response.result.rows_written || 0;
            }
        }
    }
    console.log(`  -> Batch completed. Rows written/affected: ${totalRowsWritten}`);
}

async function scrollQdrant(collection, fields, limit = 5000) {
    const idsSet = new Set();
    const payloadIdsSet = new Set();
    let offset = null;
    let page = 1;

    console.log(`[QDRANT] Scrolling collection '${collection}'...`);

    while (true) {
        const body = {
            limit,
            with_payload: fields,
            with_vector: false
        };
        if (offset) {
            body.offset = offset;
        }

        const res = await fetch(`${QDRANT_URL}/collections/${collection}/points/scroll`, {
            method: "POST",
            headers: {
                "api-key": QDRANT_API_KEY,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(body)
        });

        if (!res.ok) {
            const txt = await res.text();
            throw new Error(`Scroll failed for page ${page}: ${res.status} - ${txt}`);
        }

        const data = await res.json();
        const points = data.result?.points || [];
        
        console.log(`  - Page ${page}: fetched ${points.length} points.`);

        for (const p of points) {
            idsSet.add(String(p.id));
            if (p.payload && p.payload.podcast_id !== undefined) {
                payloadIdsSet.add(String(p.payload.podcast_id));
            }
        }

        offset = data.result?.next_page_offset;
        if (!offset || points.length === 0) {
            break;
        }
        page++;
    }

    console.log(`[QDRANT] Scroll completed for '${collection}'. Total unique point IDs: ${idsSet.size}. Unique podcast_ids in payload: ${payloadIdsSet.size}`);
    return { idsSet, payloadIdsSet };
}

async function main() {
    console.log("=== Starting Database Status Backfill ===");
    console.log(`[DATABASE] Connecting to Turso URL: ${TURSO_URL?.substring(0, 30)}...`);

    // 1. Scroll 'podcasts' collection to get all show UUIDs
    const { idsSet: qdrantPodcastsUuids } = await scrollQdrant('podcasts', false);

    // 2. Scroll 'episodes' collection to get all podcast_ids with vectorized episodes
    const { payloadIdsSet: qdrantEpisodePodcastIds } = await scrollQdrant('episodes', ['podcast_id']);

    // 3. Fetch all podcasts from Turso
    console.log("[DATABASE] Fetching all podcasts from Turso...");
    const podcastsRes = await executeSQL("SELECT id, title, qdrant_vectorized, qdrant_podcast_vectorized FROM podcasts;");
    const rows = podcastsRes?.results?.[0]?.response?.result?.rows || [];
    console.log(`[DATABASE] Found ${rows.length} total podcasts in DB.`);

    const updates = [];

    for (const r of rows) {
        const id = String(r[0].value);
        const title = r[1].value || "Unknown";
        const currentVec = r[2].value ? parseInt(r[2].value) : 0;
        const currentPodVec = r[3].value ? parseInt(r[3].value) : 0;

        const uuid = generateUUID(id);

        const shouldPodVec = qdrantPodcastsUuids.has(uuid) ? 1 : 0;
        const shouldVec = qdrantEpisodePodcastIds.has(id) ? 1 : 0;

        if (shouldVec !== currentVec || shouldPodVec !== currentPodVec) {
            updates.push({
                id,
                title,
                qdrant_vectorized: shouldVec,
                qdrant_podcast_vectorized: shouldPodVec
            });
        }
    }

    console.log(`[BACKFILL] Identified ${updates.length} podcasts needing database status updates.`);

    if (updates.length === 0) {
        console.log("[BACKFILL] All database status records match Qdrant state! No updates needed.");
        return;
    }

    // 4. Run batch updates to Turso
    const BATCH_SIZE = 100;
    for (let i = 0; i < updates.length; i += BATCH_SIZE) {
        const chunk = updates.slice(i, i + BATCH_SIZE);
        const statements = chunk.map(u => ({
            sql: "UPDATE podcasts SET qdrant_vectorized = ?, qdrant_podcast_vectorized = ? WHERE id = ?;",
            args: [u.qdrant_vectorized, u.qdrant_podcast_vectorized, u.id]
        }));
        
        console.log(`[DATABASE] Executing batch ${Math.floor(i / BATCH_SIZE) + 1}/${Math.ceil(updates.length / BATCH_SIZE)} (${chunk.length} updates)...`);
        await executeBatch(statements);
    }

    console.log("=== Backfill Complete! ===");
}

main().catch(err => {
    console.error("Backfill failed:", err);
    process.exit(1);
});
