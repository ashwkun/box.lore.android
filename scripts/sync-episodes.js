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
    if (res.results) {
        for (const result of res.results) {
            if (result.type === "error") {
                throw new Error(`Turso SQL batch error: ${result.error.message}`);
            }
        }
    }
}

async function fetchEpisodes(feedId) {
    try {
        const headers = generateAuthHeaders();
        // max=150 episodes per podcast
        const res = await fetch(`${API_BASE}/episodes/byfeedid?id=${feedId}&max=150`, { headers });
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
        "vector F32(1024)",
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

    // 4. Process in batches
    let totalPodcastsUpdated = 0;
    let errors = 0;
    const CONCURRENCY = 15;

    console.log("Starting batch processing...");
    const startTime = Date.now();

    for (let i = 0; i < podcasts.length; i += CONCURRENCY) {
        const batch = podcasts.slice(i, i + CONCURRENCY);
        if (i % 100 === 0 && i > 0) {
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            const rate = (i / elapsed).toFixed(1);
            console.log(`[${new Date().toISOString()}] Progress: ${i}/${podcasts.length} (${Math.round((i / podcasts.length) * 100)}%) | Rate: ${rate} pods/s | Errors: ${errors}`);
        }

        const batchStatements = [];

        await Promise.all(batch.map(async (pod, idx) => {
            const seqNum = i + idx + 1;
            try {
                const episodes = await fetchEpisodes(pod.id);
                if (episodes.length === 0) {
                    return;
                }

                // Find the latest episode (usually first, but ensure by date)
                const latestEp = episodes[0];

                // OPTIMIZATION: If the latest episode matches what is already in DB, 
                // only update the last_ep_sync timestamp and exit (avoids DB write transaction locks)
                if (pod.latestEpId && String(latestEp.id) === String(pod.latestEpId)) {
                    batchStatements.push({
                        sql: `UPDATE podcasts SET last_ep_sync = ? WHERE id = ?`,
                        args: [Date.now(), String(pod.id)]
                    });
                    totalPodcastsUpdated++;
                    return;
                }

                // If mismatch/first-time sync, fetch the full feed metadata to write changes
                const feedInfo = await fetchFeedInfo(pod.id);

                // 1. Statement to update podcasts metadata
                const updatePodcastSql = `
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

                const chaptersUrl = latestEp.chaptersUrl || null;
                const transcriptUrl = latestEp.transcriptUrl || null;
                const personsJson = latestEp.persons ? JSON.stringify(latestEp.persons) : null;
                const transcriptsJson = latestEp.transcripts ? JSON.stringify(latestEp.transcripts) : null;
                const medium = feedInfo?.medium || "podcast";

                batchStatements.push({
                    sql: updatePodcastSql,
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

                // 2. Build a single multi-row insert query for episodes
                const placeholders = [];
                const flatArgs = [];

                episodes.forEach(ep => {
                    placeholders.push('(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
                    const epChaptersUrl = ep.chaptersUrl || null;
                    const epTranscriptUrl = ep.transcriptUrl || null;
                    const epPersonsJson = ep.persons ? JSON.stringify(ep.persons) : null;
                    const epTranscriptsJson = ep.transcripts ? JSON.stringify(ep.transcripts) : null;

                    flatArgs.push(
                        ep.id,
                        pod.id,
                        ep.title || "",
                        cleanDescription(ep.description),
                        ep.datePublished || 0,
                        ep.duration || 0,
                        ep.enclosureUrl || "",
                        ep.image || ep.feedImage || "",
                        ep.explicit ? 1 : 0,
                        epChaptersUrl,
                        epTranscriptUrl,
                        epPersonsJson,
                        epTranscriptsJson,
                        Date.now()
                    );
                });

                const upsertEpisodeSql = `
                    INSERT INTO episodes (
                        id, podcast_id, title, description, published_date, duration, 
                        enclosure_url, image_url, explicit, chapters_url, transcript_url, 
                        persons, transcripts, created_at
                    ) VALUES ${placeholders.join(',')}
                    ON CONFLICT(id) DO UPDATE SET
                        title = excluded.title,
                        description = excluded.description,
                        published_date = excluded.published_date,
                        duration = excluded.duration,
                        enclosure_url = excluded.enclosure_url,
                        image_url = excluded.image_url,
                        explicit = excluded.explicit,
                        chapters_url = excluded.chapters_url,
                        transcript_url = excluded.transcript_url,
                        persons = excluded.persons,
                        transcripts = excluded.transcripts,
                        vector = CASE WHEN (title != excluded.title OR description != excluded.description) THEN NULL ELSE vector END
                `;

                batchStatements.push({
                    sql: upsertEpisodeSql,
                    args: flatArgs
                });

                // 3. Statement to prune older episodes beyond latest 150
                const pruneSql = `
                    DELETE FROM episodes 
                    WHERE podcast_id = ? 
                      AND id NOT IN (
                        SELECT id FROM episodes 
                        WHERE podcast_id = ? 
                        ORDER BY published_date DESC 
                        LIMIT 150
                      )
                `;
                batchStatements.push({
                    sql: pruneSql,
                    args: [pod.id, pod.id]
                });

                totalPodcastsUpdated++;
            } catch (err) {
                errors++;
                console.error(`[SYNC] [${seqNum}/${podcasts.length}] Error processing podcast ${pod.id}: ${err.message}`);
            }
        }));

        if (batchStatements.length > 0) {
            try {
                // Execute all updates/inserts for the concurrency batch in a single HTTP request!
                await executeBatch(batchStatements);
            } catch (err) {
                errors += batch.length;
                console.error(`[SYNC] Error executing batch write of ${batchStatements.length} statements: ${err.message}`);
            }
        }

        // Polite delay
        await new Promise(r => setTimeout(r, 100));
    }

    console.log(`\n=== Sync Complete ===`);
    console.log("Success: " + totalPodcastsUpdated);
    console.log("Failed:  " + errors);
    console.log("Duration: " + ((Date.now() - startTime) / 1000).toFixed(1) + "s");
    console.log("=====================\n");
}

main().catch(console.error);
