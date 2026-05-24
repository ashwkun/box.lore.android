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
                stmt: { sql, args: args.map(a => ({ type: a === null ? "null" : "text", value: a === null ? null : String(a) })) }
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
        stmt: { sql: stmt.sql, args: stmt.args.map(a => ({ type: a === null ? "null" : "text", value: a === null ? null : String(a || "") })) }
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
    if (res.results) {
        for (const result of res.results) {
            if (result.type === "error") {
                throw new Error(`Turso SQL batch error: ${result.error.message}`);
            }
        }
    }
}

async function getPodcasts() {
    // Query top 50 podcasts from the Overall charts
    // Note: Apple's "Overall" category is usually represented as "podcasts" in the category field or handled by the charts logic 
    // We join charts + podcasts to get the PI IDs
    // We select distinct PI IDs from the charts table where rank <= 50 and category = 'all' (or however we stored it)
    // Let's check how populate-charts.js stored it. Usually 'all' or empty string for overall.
    // Assuming 'all' based on typical usage or we check the charts table content.
    // To be safe, we'll order by rank and limit to 50 distinct IDs across all regions/categories if specific one isn't clear,
    // BUT user said "top 50 in overall category".
    // Let's assume category='all' or 'top' in our charts table.

    const sql = `
    SELECT DISTINCT p.id, p.itunes_id 
    FROM charts c
    JOIN podcasts p ON c.itunes_id = p.itunes_id
    -- Removed category filter to ensure we sync episodes for ALL charts (genre-specific too)
    ORDER BY c.rank ASC
  `;

    const res = await executeSQL(sql);
    return res?.results?.[0]?.response?.result?.rows?.map(r => ({
        id: r[0].value,
        itunesId: r[1].value
    })) || [];
}

async function fetchEpisodes(feedId) {
    try {
        const headers = generateAuthHeaders();
        // max=200 episodes per podcast
        const res = await fetch(`${API_BASE}/episodes/byfeedid?id=${feedId}&max=200`, { headers });
        if (!res.ok) throw new Error(`API error: ${res.status}`);
        const data = await res.json();
        return data.items || [];
    } catch (e) {
        console.error(`Failed to fetch episodes for ${feedId}:`, e.message);
        return [];
    }
}

async function fetchFeedInfo(feedId) {
    try {
        const headers = generateAuthHeaders();
        const res = await fetch(`${API_BASE}/podcasts/byfeedid?id=${feedId}`, { headers });
        if (!res.ok) throw new Error(`API error: ${res.status}`);
        const data = await res.json();
        return data.feed || null;
    } catch (e) {
        console.error(`Failed to fetch feed info for ${feedId}:`, e.message);
        return null;
    }
}

async function main() {
    console.log("Starting Episode Sync via API...");

    // 1. Removed old fetch logic


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
        "vector F32(384)",
        "last_ep_sync INTEGER"
    ];

    console.log("Ensuring schema columns exist...");
    for (const colDef of newColumns) {
        try {
            await executeSQL(`ALTER TABLE podcasts ADD COLUMN ${colDef}`);
        } catch (e) { /* Ignore */ }
    }

    // 3. Get podcasts needing sync (Priority: New > News(4h) > Others(24h))
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    const ONE_DAY_MS = 24 * 60 * 60 * 1000;
    const FOUR_HOURS_MS = 4 * 60 * 60 * 1000;

    const CUTOFF_STANDARD = Date.now() - ONE_DAY_MS;
    const CUTOFF_NEWS = Date.now() - FOUR_HOURS_MS;

    let sql = `
        SELECT DISTINCT p.id, p.itunes_id, p.title, p.last_ep_sync, p.medium, c.category
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
        return { id, itunesId, title, lastSync, medium, category };
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

    // 4. Process in batches
    let totalPodcastsUpdated = 0;
    let errors = 0;
    const CONCURRENCY = 5;

    console.log("Starting batch processing...");
    const startTime = Date.now();

    for (let i = 0; i < podcasts.length; i += CONCURRENCY) {
        const batch = podcasts.slice(i, i + CONCURRENCY);
        if (i % 50 === 0 && i > 0) {
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            const rate = (i / elapsed).toFixed(1);
            console.log(`[${new Date().toISOString()}] Progress: ${i}/${podcasts.length} (${Math.round((i / podcasts.length) * 100)}%) | Rate: ${rate} pods/s | Errors: ${errors}`);
        }

        await Promise.all(batch.map(async (pod, idx) => {
            const seqNum = i + idx + 1;
            console.log(`[SYNC] [${seqNum}/${podcasts.length}] Syncing podcast ${pod.id} ("${pod.title}") | Reason: ${podcastReasons[pod.id]}`);
            const [episodes, feedInfo] = await Promise.all([
                fetchEpisodes(pod.id),
                fetchFeedInfo(pod.id)
            ]);
            if (episodes.length === 0) {
                console.log(`[SYNC] [${seqNum}/${podcasts.length}] Podcast ${pod.id} ("${pod.title}") has 0 episodes. Skipping update.`);
                return;
            }

            // Find the latest episode (usually first, but ensure by date)
            const latestEp = episodes[0];

            try {
                // Update podcast with latest episode metadata + P2.0 fields + medium
                const sql = `
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
                        vector = NULL, -- Invalidate vector on new content
                        last_ep_sync = ?
                    WHERE id = ?
                `;

                // P2.0 Fields extraction
                const chaptersUrl = latestEp.chaptersUrl || null;
                const transcriptUrl = latestEp.transcriptUrl || null;
                const personsJson = latestEp.persons ? JSON.stringify(latestEp.persons) : null;
                const transcriptsJson = latestEp.transcripts ? JSON.stringify(latestEp.transcripts) : null;
                const medium = feedInfo?.medium || "podcast";

                await executeSQL(sql, [
                    String(latestEp.id),
                    latestEp.title || "",
                    latestEp.datePublished || 0,
                    latestEp.duration || 0,
                    latestEp.enclosureUrl || "",
                    latestEp.image || latestEp.feedImage || "",
                    latestEp.enclosureType || "audio/mpeg",
                    (latestEp.description || "").substring(0, 1000), // Truncate description
                    chaptersUrl,
                    transcriptUrl,
                    personsJson,
                    transcriptsJson,
                    medium,
                    Date.now(),
                    String(pod.id)
                ]);

                totalPodcastsUpdated++;
                console.log(`[SYNC] [${seqNum}/${podcasts.length}] Successfully updated podcast ${pod.id} ("${pod.title}") | Medium: ${medium} | Latest Ep: "${latestEp.title}"`);
            } catch (err) {
                errors++;
                console.error(`[SYNC] [${seqNum}/${podcasts.length}] Error updating podcast ${pod.id} ("${pod.title}"): ${err.message}`);
            }
        }));

        // Polite delay
        await new Promise(r => setTimeout(r, 100));
    }

    console.log(`\n=== Sync Complete ===`);
    console.log(`Success: ${totalPodcastsUpdated}`);
    console.log(`Failed:  ${errors}`);
    console.log(`Duration: ${((Date.now() - startTime) / 1000).toFixed(1)}s`);
    console.log(`=====================\n`);
}

main().catch(console.error);
