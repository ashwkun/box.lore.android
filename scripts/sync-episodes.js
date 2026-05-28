#!/usr/bin/env node
/**
 * Sync episodes for chart podcasts using Podcast Index API
 * Run this AFTER importing podcasts from the dump.
 */

const crypto = require('crypto');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const API_KEY = process.env.PODCAST_INDEX_API_KEY;
const API_SECRET = process.env.PODCAST_INDEX_API_SECRET;
const API_BASE = "https://api.podcastindex.org/api/1.0";

if (!TURSO_URL || !TURSO_TOKEN || !API_KEY || !API_SECRET) {
    console.error("Missing required environment variables (TURSO_*, PODCAST_INDEX_*)");
    process.exit(1);
}

function generateAuthHeaders() {
    const apiHeaderTime = Math.floor(Date.now() / 1000);
    const data4Hash = API_KEY + API_SECRET + apiHeaderTime;
    const hash = crypto.createHash('sha1').update(data4Hash).digest('hex');

    return {
        "User-Agent": "BoxCast/1.0",
        "X-Auth-Key": API_KEY,
        "X-Auth-Date": "" + apiHeaderTime,
        "Authorization": hash
    };
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

/**
 * Clean episode description for better vectorization quality.
 * Removes HTML, URLs, sponsor blocks, emails, timestamps, social handles, and boilerplate.
 */
function cleanDescription(raw) {
    if (!raw || typeof raw !== 'string') return "";

    let text = raw;

    // 1. Strip HTML tags
    text = text.replace(/<[^>]+>/g, ' ');

    // 2. Decode common HTML entities
    text = text
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&#x27;/g, "'")
        .replace(/&#\d+;/g, ' ')
        .replace(/&\w+;/g, ' ');

    // 3. Remove URLs (http/https/www)
    text = text.replace(/https?:\/\/[^\s)"\]]+/gi, '');
    text = text.replace(/www\.[^\s)"\]]+/gi, '');

    // 4. Remove email addresses
    text = text.replace(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, '');

    // 5. Remove social media handles (@user, #hashtag)
    text = text.replace(/[@#]\w+/g, '');

    // 6. Remove timestamps (e.g., "01:23:45", "12:34")
    text = text.replace(/\b\d{1,2}:\d{2}(:\d{2})?\b/g, '');

    // 7. Remove sponsor/ad blocks — lines starting with common ad markers
    const sponsorPatterns = [
        /^.*sponsored by.*$/gim,
        /^.*brought to you by.*$/gim,
        /^.*use code\b.*$/gim,
        /^.*promo code\b.*$/gim,
        /^.*discount code\b.*$/gim,
        /^.*sign up at\b.*$/gim,
        /^.*go to\b.*for\b.*$/gim,
        /^.*visit\b.*\.com.*$/gim,
    ];
    for (const pattern of sponsorPatterns) {
        text = text.replace(pattern, '');
    }

    // 8. Remove boilerplate phrases
    const boilerplate = [
        /learn more at\b.*/gi,
        /for more info(rmation)?\b.*/gi,
        /subscribe (to|on|at|in)\b.*/gi,
        /follow us (on|at)\b.*/gi,
        /rate (and|&) review\b.*/gi,
        /leave a review\b.*/gi,
        /support (the|this) (show|podcast)\b.*/gi,
        /available on\b.*/gi,
        /listen on\b.*/gi,
        /download the app\b.*/gi,
        /all rights reserved\.?/gi,
        /copyright ©?\s*\d{4}.*/gi,
        /see privacy policy at\b.*/gi,
        /see omnystudio\.com.*/gi,
        /advertising inquiries\b.*/gi,
    ];
    for (const pattern of boilerplate) {
        text = text.replace(pattern, '');
    }

    // 9. Normalize whitespace
    text = text.replace(/\s+/g, ' ').trim();

    // 10. Truncate to 1000 chars (for DB storage + embedding input)
    return text.substring(0, 1000);
}

async function executeSQL(sql, args = []) {
    const startTime = Date.now();
    try {
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
        const duration = Date.now() - startTime;
        if (!response.ok) {
            throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
        }
        const res = await response.json();
        if (res.results && res.results[0] && res.results[0].type === "error") {
            throw new Error(`SQL execution error: ${res.results[0].error.message}`);
        }
        return { ...res, _durationMs: duration };
    } catch (e) {
        const duration = Date.now() - startTime;
        console.error(`[FAIL] DB query failed after ${duration}ms: ${e.message}`);
        throw e;
    }
}

async function executeBatch(statements) {
    if (statements.length === 0) return { _durationMs: 0, _rowsRead: 0, _rowsWritten: 0 };
    const startTime = Date.now();
    try {
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
        const duration = Date.now() - startTime;
        if (!response.ok) throw new Error(`Turso HTTP error: ${response.status}`);
        
        const res = await response.json();
        let totalRowsRead = 0, totalRowsWritten = 0;
        if (res.results) {
            for (const result of res.results) {
                if (result.type === "error") {
                    throw new Error(`Turso SQL batch error: ${result.error.message}`);
                }
                if (result.response?.result) {
                    totalRowsRead += result.response.result.rows_read || 0;
                    totalRowsWritten += result.response.result.rows_written || 0;
                }
            }
        }
        return { _durationMs: duration, _rowsRead: totalRowsRead, _rowsWritten: totalRowsWritten };
    } catch (e) {
        const duration = Date.now() - startTime;
        console.error(`[FAIL] DB batch (${statements.length} stmts) failed after ${duration}ms: ${e.message}`);
        throw e;
    }
}

async function fetchEpisodes(feedId, retries = 3, delay = 1000) {
    const startTime = Date.now();
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        try {
            const headers = generateAuthHeaders();
            const res = await fetch(`${API_BASE}/episodes/byfeedid?id=${feedId}&max=100`, { headers });
            
            if (res.status === 429) {
                if (attempt <= retries) {
                    const backoff = delay * Math.pow(2, attempt - 1);
                    console.warn(`  [WARN] Rate limited (429) fetching episodes for pod=${feedId}. Retrying attempt ${attempt}/${retries} after ${backoff}ms...`);
                    await new Promise(resolve => setTimeout(resolve, backoff));
                    continue;
                }
                throw new Error(`API error 429: Too Many Requests`);
            }
            
            if (!res.ok) throw new Error(`API error: ${res.status}`);
            const data = await res.json();
            const duration = Date.now() - startTime;
            return { items: data.items || [], _durationMs: duration };
        } catch (e) {
            if (attempt > retries) {
                const duration = Date.now() - startTime;
                console.error(`[FAIL] fetchEpisodes pod=${feedId} failed after ${attempt} attempts: ${e.message}`);
                return { items: [], _durationMs: duration, _failed: true };
            }
            const backoff = delay * Math.pow(2, attempt - 1);
            console.warn(`  [WARN] fetchEpisodes pod=${feedId} failed (attempt ${attempt}/${retries}): ${e.message}. Retrying in ${backoff}ms...`);
            await new Promise(resolve => setTimeout(resolve, backoff));
        }
    }
    return { items: [], _durationMs: Date.now() - startTime, _failed: true };
}

async function fetchFeedInfo(feedId, retries = 3, delay = 1000) {
    const startTime = Date.now();
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        try {
            const headers = generateAuthHeaders();
            const res = await fetch(`${API_BASE}/podcasts/byfeedid?id=${feedId}`, { headers });
            
            if (res.status === 429) {
                if (attempt <= retries) {
                    const backoff = delay * Math.pow(2, attempt - 1);
                    console.warn(`  [WARN] Rate limited (429) fetching feed info for pod=${feedId}. Retrying attempt ${attempt}/${retries} after ${backoff}ms...`);
                    await new Promise(resolve => setTimeout(resolve, backoff));
                    continue;
                }
                throw new Error(`API error 429: Too Many Requests`);
            }
            
            if (!res.ok) throw new Error(`API error: ${res.status}`);
            const data = await res.json();
            const duration = Date.now() - startTime;
            return { feed: data.feed || null, _durationMs: duration };
        } catch (e) {
            if (attempt > retries) {
                const duration = Date.now() - startTime;
                console.error(`[FAIL] fetchFeedInfo pod=${feedId} failed after ${attempt} attempts: ${e.message}`);
                return { feed: null, _durationMs: duration, _failed: true };
            }
            const backoff = delay * Math.pow(2, attempt - 1);
            console.warn(`  [WARN] fetchFeedInfo pod=${feedId} failed (attempt ${attempt}/${retries}): ${e.message}. Retrying in ${backoff}ms...`);
            await new Promise(resolve => setTimeout(resolve, backoff));
        }
    }
    return { feed: null, _durationMs: Date.now() - startTime, _failed: true };
}

async function main() {
    console.log("Starting Episode Sync via API...");

    // === DIAGNOSTIC FAILSAFE LOGGING ===
    console.log("[DIAGNOSTIC] Verifying GHA Environment Configuration...");
    console.log(`  - TURSO_URL:     ${TURSO_URL ? 'PRESENT' : 'MISSING'}`);
    console.log(`  - TURSO_TOKEN:   ${TURSO_TOKEN ? 'PRESENT (Masked: ' + TURSO_TOKEN.substring(0, 8) + '...)' : 'MISSING'}`);
    console.log(`  - API_KEY:       ${API_KEY ? 'PRESENT' : 'MISSING'}`);

    console.log("[DIAGNOSTIC] Testing Turso Database Connection & Health...");
    try {
        const dbRes = await executeSQL("SELECT 1");
        if (dbRes && dbRes.results && dbRes.results[0] && dbRes.results[0].type === "ok") {
            console.log(`[STATUS] Turso database connection SUCCESSFUL!`);
        } else {
            throw new Error("Database query returned an unexpected response.");
        }
    } catch (e) {
        console.error(`[CRITICAL] Turso database connection check FAILED: ${e.message}`);
        console.error("  -> Please verify TURSO_URL and TURSO_AUTH_TOKEN secrets in GitHub Repository Secrets!");
        process.exit(1);
    }
    // ===================================


    // 2. Ensure podcasts table has new columns
    const newColumns = [
        "latest_ep_id TEXT",
        "latest_ep_title TEXT",
        "latest_ep_date INTEGER",
        "latest_ep_duration INTEGER",
        "latest_ep_url TEXT",
        "latest_ep_image TEXT",
        "latest_ep_type TEXT",
        "latest_ep_description TEXT",
        "latest_ep_chapters_url TEXT",    // P2.0
        "latest_ep_transcript_url TEXT",  // P2.0
        "latest_ep_persons TEXT",         // P2.0 JSON
        "latest_ep_transcripts TEXT",     // P2.0 JSON
        "medium TEXT",
        "vector F32(1024)",
        "last_ep_sync INTEGER"
    ];

    console.log("Ensuring schema columns exist...");
    try {
        const infoRes = await executeSQL("PRAGMA table_info(podcasts)");
        const rows = infoRes?.results?.[0]?.response?.result?.rows || [];
        const existingColumns = new Set(rows.map(r => r[1]?.value?.toLowerCase()));

        for (const colDef of newColumns) {
            const colName = colDef.split(" ")[0].toLowerCase();
            if (!existingColumns.has(colName)) {
                console.log(`Adding missing column: ${colName}`);
                try {
                    await executeSQL(`ALTER TABLE podcasts ADD COLUMN ${colDef}`);
                } catch (e) {
                    console.error(`Failed to add column ${colDef}: ${e.message}`);
                }
            }
        }
    } catch (err) {
        console.warn("[WARNING] Could not verify schema columns using PRAGMA table_info:", err.message);
        // Fallback to old behavior if PRAGMA fails
        for (const colDef of newColumns) {
            try {
                await executeSQL(`ALTER TABLE podcasts ADD COLUMN ${colDef}`);
            } catch (e) { /* Ignore */ }
        }
    }

    // 3. Get podcasts needing sync (Priority: New > News(4h) > Others(24h))
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    const ONE_DAY_MS = 24 * 60 * 60 * 1000;
    const FOUR_HOURS_MS = 4 * 60 * 60 * 1000;

    const CUTOFF_STANDARD = Date.now() - ONE_DAY_MS;
    const CUTOFF_NEWS = Date.now() - FOUR_HOURS_MS;

    let sql = `
        SELECT DISTINCT p.id, p.itunes_id, p.title, p.last_ep_sync, p.medium, c.category, p.latest_ep_id
        FROM charts c
        JOIN podcasts p ON c.itunes_id = p.itunes_id
        WHERE 
            (
                p.last_ep_sync IS NULL 
                OR p.medium IS NULL
                OR (c.category = 'News' AND p.last_ep_sync < ?)
                OR (p.last_ep_sync < ?)
            )
    `;
    let args = [CUTOFF_NEWS, CUTOFF_STANDARD];

    if (country) {
        sql += ` AND c.country = ?`;
        args.push(country);
        console.log(`Filtering sync candidates for country: ${country}`);
    }

    sql += `
        ORDER BY p.last_ep_sync ASC
        LIMIT 2000
    `;

    console.log("Fetching sync candidates...");
    const res = await executeSQL(sql, args);
    const podcasts = res?.results?.[0]?.response?.result?.rows?.map(r => {
        const id = r[0].value;
        const itunesId = r[1].value;
        const title = r[2].value || "Unknown Title";
        const lastSync = r[3].value ? parseInt(r[3].value) : null;
        const medium = r[4].value;
        const category = r[5].value;
        const latestEpId = r[6]?.value;
        return { id, itunesId, title, lastSync, medium, category, latestEpId };
    }) || [];

    let neverSyncedCount = 0;
    let missingMediumCount = 0;
    let staleNewsCount = 0;
    let staleRegularCount = 0;

    const podcastReasons = {};

    for (const p of podcasts) {
        const reasonsList = [];
        if (p.lastSync === null) {
            reasonsList.push("Never Synced");
            neverSyncedCount++;
        } else {
            if (p.medium === null) {
                reasonsList.push("Missing Medium Field");
                missingMediumCount++;
            }
            if (p.category === 'News' && p.lastSync < CUTOFF_NEWS) {
                reasonsList.push(`Stale News Feed (last synced ${Math.round((Date.now() - p.lastSync) / 3600000)}h ago)`);
                staleNewsCount++;
            } else if (p.lastSync < CUTOFF_STANDARD) {
                reasonsList.push(`Stale Regular Feed (last synced ${Math.round((Date.now() - p.lastSync) / 3600000)}h ago)`);
                staleRegularCount++;
            }
        }
        podcastReasons[p.id] = reasonsList.join(", ") || "Forced update / other reasons";
    }

    console.log(`\n=== Detailed Sync Plan ===`);
    console.log(`Total Candidates: ${podcasts.length}`);
    console.log(`- Never Synced:                ${neverSyncedCount}`);
    console.log(`- Missing Medium Column:       ${missingMediumCount}`);
    console.log(`- Stale News Feeds (>4h old):  ${staleNewsCount}`);
    console.log(`- Stale Regular Feeds (>24h):  ${staleRegularCount}`);
    console.log(`==========================\n`);

    // 4. Process with full concurrency - each podcast fetches + writes independently
    let totalPodcastsUpdated = 0;
    let totalEpisodesWritten = 0;
    let totalSkipped = 0;
    let totalEmpty = 0;
    let totalRowsRead = 0;
    let totalRowsWritten = 0;
    let errors = 0;
    const CONCURRENCY = 5;

    console.log(`\nStarting sync: ${podcasts.length} podcasts | Concurrency: ${CONCURRENCY} | ${new Date().toISOString()}`);
    console.log('─'.repeat(120));
    const startTime = Date.now();

    for (let i = 0; i < podcasts.length; i += CONCURRENCY) {
        const batch = podcasts.slice(i, i + CONCURRENCY);
        const batchStart = Date.now();

        // Per-batch accumulators
        let batchUpdated = 0;
        let batchSkipped = 0;
        let batchEmpty = 0;
        let batchEpisodes = 0;
        let batchErrors = 0;
        let batchApiMs = 0;
        let batchDbMs = 0;
        let batchRowsRead = 0;
        let batchRowsWritten = 0;
        let batchApiFails = 0;
        const skipPodIds = []; // Collect skip-path IDs for batched timestamp update

        await Promise.all(batch.map(async (pod, idx) => {
            try {
                const epResult = await fetchEpisodes(pod.id);
                batchApiMs += epResult._durationMs;
                if (epResult._failed) batchApiFails++;
                const episodes = epResult.items;

                if (episodes.length === 0) {
                    batchEmpty++;
                    return;
                }

                const latestEp = episodes[0];

                // OPTIMIZATION: If latest episode unchanged, collect for batched timestamp update
                if (pod.latestEpId && String(latestEp.id) === String(pod.latestEpId)) {
                    skipPodIds.push(String(pod.id));
                    batchSkipped++;
                    batchUpdated++;
                    return;
                }

                // New/changed episode — fetch feed info only if medium is missing + write everything
                let feedInfo = null;
                if (!pod.medium) {
                    const feedResult = await fetchFeedInfo(pod.id);
                    batchApiMs += feedResult._durationMs;
                    if (feedResult._failed) batchApiFails++;
                    feedInfo = feedResult.feed;
                }

                const podStatements = [];

                // 1. Update podcast metadata
                const chaptersUrl = latestEp.chaptersUrl || null;
                const transcriptUrl = latestEp.transcriptUrl || null;
                const personsJson = latestEp.persons ? JSON.stringify(latestEp.persons) : null;
                const transcriptsJson = latestEp.transcripts ? JSON.stringify(latestEp.transcripts) : null;
                const medium = pod.medium || feedInfo?.medium || "podcast";

                podStatements.push({
                    sql: `
                        UPDATE podcasts SET 
                            latest_ep_id = ?,
                            latest_ep_title = ?,
                            latest_ep_date = ?,
                            latest_ep_duration = ?,
                            latest_ep_url = ?,
                            latest_ep_image = ?,
                            latest_ep_type = ?,
                            latest_ep_description = ?,
                            latest_ep_chapters_url = ?,
                            latest_ep_transcript_url = ?,
                            latest_ep_persons = ?,
                            latest_ep_transcripts = ?,
                            medium = ?,
                            vector = NULL,
                            last_ep_sync = ?,
                            qdrant_vectorized = 0
                        WHERE id = ?
                    `,
                    args: [
                        String(latestEp.id),
                        latestEp.title || "",
                        latestEp.datePublished || 0,
                        latestEp.duration || 0,
                        latestEp.enclosureUrl || "",
                        latestEp.image || latestEp.feedImage || "",
                        latestEp.enclosureType || "audio/mpeg",
                        cleanDescription(latestEp.description),
                        chaptersUrl,
                        transcriptUrl,
                        personsJson,
                        transcriptsJson,
                        medium,
                        Date.now(),
                        String(pod.id)
                    ]
                });

                // Fire this podcast's DB metadata write immediately (concurrent with other pods)
                const dbRes = await executeBatch(podStatements);
                batchDbMs += dbRes._durationMs;
                batchRowsRead += dbRes._rowsRead;
                batchRowsWritten += dbRes._rowsWritten;
                batchEpisodes += 1;
                batchUpdated++;

            } catch (err) {
                batchErrors++;
                console.error(`  ✗ pod ${pod.id} (${pod.title?.substring(0, 30)}): ${err.message}`);
            }
        }));

        // Batch all skip-path timestamp updates into ONE DB call
        if (skipPodIds.length > 0) {
            try {
                const placeholders = skipPodIds.map(() => '?').join(',');
                const dbRes = await executeBatch([{
                    sql: `UPDATE podcasts SET last_ep_sync = ? WHERE id IN (${placeholders})`,
                    args: [Date.now(), ...skipPodIds]
                }]);
                batchDbMs += dbRes._durationMs;
                batchRowsRead += dbRes._rowsRead;
                batchRowsWritten += dbRes._rowsWritten;
            } catch (err) {
                batchErrors += skipPodIds.length;
                console.error(`  ✗ Batch timestamp update failed for ${skipPodIds.length} pods: ${err.message}`);
            }
        }

        // Update global counters
        totalPodcastsUpdated += batchUpdated;
        totalSkipped += batchSkipped;
        totalEmpty += batchEmpty;
        totalEpisodesWritten += batchEpisodes;
        totalRowsRead += batchRowsRead;
        totalRowsWritten += batchRowsWritten;
        errors += batchErrors;

        // Print batch summary
        const batchDuration = Date.now() - batchStart;
        const done = Math.min(i + CONCURRENCY, podcasts.length);
        const pct = Math.round((done / podcasts.length) * 100);
        const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
        const rate = (done / ((Date.now() - startTime) / 1000)).toFixed(1);
        const eta = done < podcasts.length ? Math.round(((podcasts.length - done) / parseFloat(rate))).toString() + 's' : '—';

        console.log(
            `[${done}/${podcasts.length} ${pct}%] ` +
            `${batchDuration}ms | ` +
            `✓${batchUpdated - batchSkipped} new, ↩${batchSkipped} skip, ∅${batchEmpty} empty` +
            (batchErrors > 0 ? `, ✗${batchErrors} err` : '') +
            (batchApiFails > 0 ? `, ⚠${batchApiFails} api-fail` : '') +
            ` | ${batchEpisodes} eps | ` +
            `API: ${batchApiMs}ms DB: ${batchDbMs}ms | ` +
            `R:${batchRowsRead} W:${batchRowsWritten} | ` +
            `${rate} pods/s | ETA: ${eta} | elapsed: ${elapsed}s`
        );

        // Polite delay between concurrency groups to avoid hammering
        await new Promise(r => setTimeout(r, 1000));
    }

    const totalDuration = ((Date.now() - startTime) / 1000).toFixed(1);
    const finalRate = (podcasts.length / parseFloat(totalDuration)).toFixed(1);
    console.log('─'.repeat(120));
    console.log(`✅ SYNC COMPLETE | ${totalPodcastsUpdated} updated (${totalSkipped} unchanged, ${totalEmpty} empty) | ${totalEpisodesWritten} episodes written | ${errors} errors | ${totalDuration}s @ ${finalRate} pods/s`);
    console.log(`📊 DB COST: ${totalRowsRead.toLocaleString()} rows read | ${totalRowsWritten.toLocaleString()} rows written`);
}

main().catch(console.error);
