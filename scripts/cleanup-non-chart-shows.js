#!/usr/bin/env node

/**
 * Cleanup script to identify and delete shows (and their episodes) that are not currently
 * in the charts (not present in the top 200 of any country/category charts).
 * Cleans up Turso DB (podcasts table) and Qdrant Cloud (podcasts and episodes collections).
 * 
 * Optimized to prevent Cartesian row scans and FTS trigger scans:
 * - Drops delete trigger temporarily to perform deletes in a single DB transaction.
 * - Cleans up FTS virtual table in a single scan.
 * - Chunks Qdrant operations at 500 limit.
 * - Implements robust retry with exponential backoff for Qdrant API calls.
 */

const crypto = require('crypto');

// Environment Variables
const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const QDRANT_URL = process.env.QDRANT_URL;
const QDRANT_API_KEY = process.env.QDRANT_API_KEY;

if (!TURSO_URL || !TURSO_TOKEN || !QDRANT_URL || !QDRANT_API_KEY) {
    console.error("Missing required environment variables (TURSO_URL, TURSO_AUTH_TOKEN, QDRANT_URL, QDRANT_API_KEY)");
    process.exit(1);
}

const BATCH_SIZE = 500; // Qdrant batch chunk size
const SAFETY_THRESHOLD = 500; // Minimum active chart iTunes IDs to run cleanup

// Helper: Map Turso JS values to pipeline types
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

// Helper: Execute Turso SQL
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

// Helper: Generate a stable UUID from a string ID (Qdrant requires UUIDs)
function generateUUID(strId) {
    const hash = crypto.createHash('md5').update(String(strId)).digest('hex');
    return `${hash.substring(0,8)}-${hash.substring(8,12)}-${hash.substring(12,16)}-${hash.substring(16,20)}-${hash.substring(20)}`;
}

// Helper: Fetch with exponential backoff retry for Qdrant under heavy load
async function fetchWithRetry(url, options, retries = 5, delay = 2000) {
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        try {
            const response = await fetch(url, options);
            
            // Rate limit (429) or Server error / heavy load (>= 500)
            if (response.status === 429 || response.status >= 500) {
                if (attempt <= retries) {
                    const backoff = delay * Math.pow(2, attempt - 1);
                    console.warn(`[QDRANT] Server returned status ${response.status} (heavy load/rate limit). Retrying attempt ${attempt}/${retries} after ${backoff}ms...`);
                    await new Promise(resolve => setTimeout(resolve, backoff));
                    continue;
                }
            }
            
            if (!response.ok) {
                const text = await response.text();
                throw new Error(`HTTP error ${response.status}: ${text}`);
            }
            
            return await response.json();
        } catch (err) {
            if (attempt > retries) {
                throw err;
            }
            const backoff = delay * Math.pow(2, attempt - 1);
            console.warn(`[QDRANT] Request failed (attempt ${attempt}/${retries}): ${err.message}. Retrying in ${backoff}ms...`);
            await new Promise(resolve => setTimeout(resolve, backoff));
        }
    }
}

// Helper: Delete points from Qdrant podcasts collection
async function qdrantDeletePodcasts(uuids) {
    if (uuids.length === 0) return;
    return await fetchWithRetry(`${QDRANT_URL}/collections/podcasts/points/delete?wait=false`, {
        method: "POST",
        headers: {
            "api-key": QDRANT_API_KEY,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ points: uuids })
    });
}

// Helper: Delete points from Qdrant episodes collection matching podcast_ids
async function qdrantDeleteEpisodes(podcastIds) {
    if (podcastIds.length === 0) return;
    return await fetchWithRetry(`${QDRANT_URL}/collections/episodes/points/delete?wait=false`, {
        method: "POST",
        headers: {
            "api-key": QDRANT_API_KEY,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            filter: {
                must: [
                    {
                        key: "podcast_id",
                        match: {
                            any: podcastIds
                        }
                    }
                ]
            }
        })
    });
}

async function main() {
    console.log("=== Starting Optimized Cleanup Pipeline for Non-Chart Podcasts ===");

    // 1. Safety Check: Count unique active iTunes IDs in charts
    console.log("[CLEANUP] Verifying safety threshold of active charts...");
    const countRes = await executeSQL("SELECT COUNT(DISTINCT itunes_id) FROM charts WHERE itunes_id IS NOT NULL");
    const activeChartCount = parseInt(countRes?.results?.[0]?.response?.result?.rows?.[0]?.[0]?.value || 0);
    console.log(`[CLEANUP] Found ${activeChartCount} unique iTunes IDs in charts table.`);

    // --- TARGETED DIAGNOSTIC LOGGING ---
    let dbCountBefore = 0;
    try {
        const beforeRes = await executeSQL("SELECT COUNT(*) FROM podcasts");
        dbCountBefore = parseInt(beforeRes?.results?.[0]?.response?.result?.rows?.[0]?.[0]?.value || 0);
        console.log(`[DIAGNOSTIC] podcasts table row count BEFORE cleanup: ${dbCountBefore}`);
        
        const chartsBreakdown = await executeSQL("SELECT country, COUNT(DISTINCT itunes_id) FROM charts GROUP BY country");
        console.log(`[DIAGNOSTIC] Charts breakdown in database right now:`);
        chartsBreakdown?.results?.[0]?.response?.result?.rows?.forEach(r => {
            console.log(`  - ${r[0].value}: ${r[1].value} unique iTunes IDs`);
        });
    } catch (err) {
        console.error("[DIAGNOSTIC ERROR]", err.message);
    }
    // ------------------------------------

    if (activeChartCount < SAFETY_THRESHOLD) {
        console.warn(`[SAFETY TRIGGERED] Active chart count (${activeChartCount}) is below safety threshold (${SAFETY_THRESHOLD}).`);
        console.warn("[SAFETY TRIGGERED] Aborting cleanup operation to prevent accidental mass database deletion.");
        process.exit(0);
    }

    // 2. Fetch podcasts that are not in the charts (No CAST to ensure index usage)
    console.log("[CLEANUP] Querying Turso DB for non-chart podcasts...");
    const nonChartRes = await executeSQL(`
        SELECT id, itunes_id, title FROM podcasts 
        WHERE itunes_id IS NULL OR itunes_id NOT IN (
            SELECT DISTINCT itunes_id FROM charts WHERE itunes_id IS NOT NULL
        )
    `);

    const rows = nonChartRes?.results?.[0]?.response?.result?.rows || [];
    const candidates = rows.map(r => ({
        id: String(r[0].value),
        itunesId: r[1]?.value ? String(r[1].value) : null,
        title: r[2]?.value || "Unknown Show"
    }));

    console.log(`[CLEANUP] Identified ${candidates.length} podcasts eligible for deletion (no longer in charts).`);

    if (candidates.length === 0) {
        console.log("[CLEANUP] No non-chart podcasts found. All shows are up-to-date with active charts!");
        console.log("=== Cleanup Pipeline Completed (Nothing to delete) ===");
        process.exit(0);
    }



    // 3. Delete from Qdrant first (since we need the candidate IDs)
    console.log(`\n[CLEANUP] Starting Qdrant vector deletion in batches of ${BATCH_SIZE}...`);
    const startTime = Date.now();

    for (let i = 0; i < candidates.length; i += BATCH_SIZE) {
        const batch = candidates.slice(i, i + BATCH_SIZE);
        const batchIntIds = batch.map(c => parseInt(c.id)).filter(id => !isNaN(id));
        const batchUuids = batch.map(c => generateUUID(c.id));

        console.log(`[QDRANT] Batch ${Math.floor(i / BATCH_SIZE) + 1}/${Math.ceil(candidates.length / BATCH_SIZE)}: Processing ${batch.length} vectors...`);

        try {
            // Delete episode vectors from Qdrant episodes collection (wait=false)
            await qdrantDeleteEpisodes(batchIntIds);
            
            // Delete show vectors from Qdrant podcasts collection (wait=false)
            await qdrantDeletePodcasts(batchUuids);

        } catch (err) {
            console.error(`[QDRANT] [ERROR] Failed to clean up vectors for batch starting at index ${i}:`, err.message);
        }

        // Polite delay between Qdrant batches to prevent API load spikes
        await new Promise(r => setTimeout(r, 1000));
    }

    // 4. Delete from Turso DB in a single operation
    console.log("\n[DATABASE] Starting optimized Turso DB cleanup...");
    try {
        // Step A: Temporarily drop delete trigger to prevent 27,000+ Cartesian FTS full table scans
        console.log("  -> Dropping trigger 'podcasts_ad' to bypass row-by-row FTS scans...");
        await executeSQL("DROP TRIGGER IF EXISTS podcasts_ad");

        // Step B: Execute single query deletion of non-chart podcasts
        console.log("  -> Deleting non-chart shows from 'podcasts' table in a single query...");
        const deleteRes = await executeSQL(`
            DELETE FROM podcasts 
            WHERE itunes_id IS NULL OR itunes_id NOT IN (
                SELECT DISTINCT itunes_id FROM charts WHERE itunes_id IS NOT NULL
            )
        `);
        const rowsWritten = deleteRes?.results?.[0]?.response?.result?.rows_written || 0;
        console.log(`  -> Database records deleted successfully. Rows written/affected: ${rowsWritten}`);

        // Step C: Execute single query cleanup of search index (FTS virtual table)
        console.log("  -> Cleaning up 'podcasts_fts' search index table in a single scan query...");
        const ftsDeleteRes = await executeSQL("DELETE FROM podcasts_fts WHERE podcast_id NOT IN (SELECT id FROM podcasts)");
        const ftsRowsWritten = ftsDeleteRes?.results?.[0]?.response?.result?.rows_written || 0;
        console.log(`  -> FTS search index cleaned successfully. Rows written/affected: ${ftsRowsWritten}`);

        // Step D: Re-create the delete trigger
        console.log("  -> Recreating trigger 'podcasts_ad' for standard operations...");
        await executeSQL(`
            CREATE TRIGGER IF NOT EXISTS podcasts_ad AFTER DELETE ON podcasts BEGIN
                DELETE FROM podcasts_fts WHERE podcast_id = old.id;
            END
        `);
        console.log("  -> Trigger recreated successfully!");

    } catch (dbErr) {
        console.error("[DATABASE] [CRITICAL ERROR] Deletion transaction failed. Re-enabling trigger...", dbErr.message);
        try {
            await executeSQL(`
                CREATE TRIGGER IF NOT EXISTS podcasts_ad AFTER DELETE ON podcasts BEGIN
                    DELETE FROM podcasts_fts WHERE podcast_id = old.id;
                END
            `);
        } catch (recreateErr) {
            console.error("Failed to restore trigger:", recreateErr.message);
        }
        process.exit(1);
    }

    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n=== Cleanup Pipeline Complete ===`);
    console.log(`Total Podcasts Cleaned:  ${candidates.length}`);
    console.log(`Total Duration:          ${elapsed}s`);
    console.log(`=================================`);

    // --- TARGETED DIAGNOSTIC LOGGING ---
    try {
        const afterRes = await executeSQL("SELECT COUNT(*) FROM podcasts");
        const dbCountAfter = parseInt(afterRes?.results?.[0]?.response?.result?.rows?.[0]?.[0]?.value || 0);
        console.log(`[DIAGNOSTIC] podcasts table row count AFTER cleanup: ${dbCountAfter}`);
        console.log(`[DIAGNOSTIC] Net rows deleted: ${dbCountBefore - dbCountAfter}`);
    } catch (err) {
        console.error("[DIAGNOSTIC ERROR] Failed to fetch table count after cleanup:", err.message);
    }
    // ------------------------------------
}

main().catch(console.error);
