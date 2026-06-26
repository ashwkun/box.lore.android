#!/usr/bin/env node

/**
 * One-time script to restore Turso DB from Qdrant and iTunes charts.
 * 1. Scrolls Qdrant 'episodes' to identify all already-vectorized podcast IDs.
 * 2. Finds all chart iTunes IDs that are missing from the Turso 'podcasts' table.
 * 3. Fetches feed details and latest episode details from Podcast Index API.
 * 4. Inserts them into Turso, setting qdrant_vectorized = 1 if the show is already in Qdrant.
 */

const crypto = require('crypto');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const QDRANT_URL = process.env.QDRANT_URL;
const QDRANT_API_KEY = process.env.QDRANT_API_KEY;
const API_KEY = process.env.PODCAST_INDEX_API_KEY;
const API_SECRET = process.env.PODCAST_INDEX_API_SECRET;
const API_BASE = "https://api.podcastindex.org/api/1.0";

if (!TURSO_URL || !TURSO_TOKEN || !QDRANT_URL || !QDRANT_API_KEY || !API_KEY || !API_SECRET) {
    console.error("Missing required environment variables (TURSO_URL, TURSO_AUTH_TOKEN, QDRANT_URL, QDRANT_API_KEY, PODCAST_INDEX_API_KEY, PODCAST_INDEX_API_SECRET)");
    process.exit(1);
}

function generateAuthHeaders() {
    const apiHeaderTime = Math.floor(Date.now() / 1000);
    const data4Hash = API_KEY + API_SECRET + apiHeaderTime;
    const hash = crypto.createHash('sha1').update(data4Hash).digest('hex');

    return {
        "User-Agent": "BoxLore/1.0",
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

function cleanDescription(raw) {
    if (!raw || typeof raw !== 'string') return "";
    let text = raw.replace(/<[^>]+>/g, ' ');
    text = text
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&#x27;/g, "'")
        .replace(/&#\d+;/g, ' ')
        .replace(/&\w+;/g, ' ');
    text = text.replace(/https?:\/\/[^\s)"\]]+/gi, '');
    text = text.replace(/www\.[^\s)"\]]+/gi, '');
    text = text.replace(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, '');
    text = text.replace(/[@#]\w+/g, '');
    text = text.replace(/\b\d{1,2}:\d{2}(:\d{2})?\b/g, '');
    
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
    return text.replace(/\s+/g, ' ').trim().substring(0, 1000);
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
            idsSet.add(String(p.id).toLowerCase());
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

let currentBatchDelayMs = 100; // Fast 100ms baseline delay between concurrent batches

async function fetchPodcastAndLatestEpisode(itunesId, retries = 3, delay = 1000) {
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        try {
            const headers = generateAuthHeaders();
            const res = await fetch(`${API_BASE}/podcasts/byitunesid?id=${itunesId}`, { headers });
            
            if (res.status === 429) {
                currentBatchDelayMs = Math.min(3000, currentBatchDelayMs + 500);
                if (attempt <= retries) {
                    const backoff = delay * Math.pow(2, attempt - 1);
                    console.warn(`  [WARN] Rate limited (429) fetching feed for iTunes ID ${itunesId}. Retrying attempt ${attempt}/${retries} after ${backoff}ms...`);
                    await new Promise(resolve => setTimeout(resolve, backoff));
                    continue;
                }
                throw new Error(`API error 429: Too Many Requests`);
            }
            if (!res.ok) {
                throw new Error(`API error: ${res.status}`);
            }
            const data = await res.json();
            const feed = data.feed || null;
            if (!feed || !feed.id) return null;

            // Now fetch the latest episode
            let latestEp = null;
            const epRes = await fetch(`${API_BASE}/episodes/byfeedid?id=${feed.id}&max=1`, { headers });
            if (epRes.ok) {
                const epData = await epRes.json();
                latestEp = epData.items?.[0] || null;
            }

            return { feed, latestEp };
        } catch (e) {
            if (attempt > retries) {
                console.error(`[FAIL] fetchPodcastAndLatestEpisode iTunes ID ${itunesId} failed after ${attempt} attempts: ${e.message}`);
                throw e;
            }
            const backoff = delay * Math.pow(2, attempt - 1);
            console.warn(`  [WARN] fetchPodcastAndLatestEpisode iTunes ID ${itunesId} failed (attempt ${attempt}/${retries}): ${e.message}. Retrying in ${backoff}ms...`);
            await new Promise(resolve => setTimeout(resolve, backoff));
        }
    }
    return null;
}

async function main() {
    console.log("=== Starting Database Restoration from Qdrant and Charts ===");

    // 1. Scroll Qdrant 'episodes' collection to identify already vectorized shows
    const { payloadIdsSet: qdrantEpisodePodcastIds } = await scrollQdrant('episodes', ['podcast_id']);

    // 2. Fetch all missing iTunes IDs from charts table
    console.log("[DATABASE] Querying for chart iTunes IDs missing from podcasts table...");
    const missingRes = await executeSQL(`
        SELECT DISTINCT itunes_id 
        FROM charts 
        WHERE itunes_id NOT IN (
            SELECT DISTINCT itunes_id FROM podcasts WHERE itunes_id IS NOT NULL
        );
    `);
    const rows = missingRes?.results?.[0]?.response?.result?.rows || [];
    const missingItunesIds = rows.map(r => String(r[0].value)).filter(Boolean);
    console.log(`[DATABASE] Found ${missingItunesIds.length} missing podcasts in podcasts table.`);

    if (missingItunesIds.length === 0) {
        console.log("[DATABASE] Database is already fully populated. No missing shows to import.");
        return;
    }

    const CONCURRENCY = 40; // High speed concurrency batching
    console.log(`[RESTORE] Importing ${missingItunesIds.length} podcasts with concurrency of ${CONCURRENCY}...`);

    for (let i = 0; i < missingItunesIds.length; i += CONCURRENCY) {
        const chunk = missingItunesIds.slice(i, i + CONCURRENCY);
        
        console.log(`[RESTORE] Processing batch ${Math.floor(i / CONCURRENCY) + 1}/${Math.ceil(missingItunesIds.length / CONCURRENCY)} (podcasts ${i + 1}-${i + chunk.length}) | Current Delay: ${currentBatchDelayMs}ms...`);

        const promises = chunk.map(async (itunesId) => {
            try {
                const result = await fetchPodcastAndLatestEpisode(itunesId);
                if (!result) return;

                const { feed, latestEp } = result;
                const podIdStr = String(feed.id);
                const isVectorizedInQdrant = qdrantEpisodePodcastIds.has(podIdStr) ? 1 : 0;

                // Prepare categories string
                let categoriesStr = "";
                if (feed.categories) {
                    categoriesStr = Object.values(feed.categories).join(', ');
                }

                // If latest episode details are present
                const columns = [
                    'id', 'itunes_id', 'title', 'author', 'description', 'image_url', 
                    'feed_url', 'website_url', 'categories', 'language', 'explicit', 'type',
                    'latest_ep_id', 'latest_ep_title', 'latest_ep_date', 'latest_ep_duration',
                    'latest_ep_url', 'latest_ep_image', 'latest_ep_type', 'latest_ep_description',
                    'latest_ep_chapters_url', 'latest_ep_transcript_url', 'latest_ep_persons',
                    'latest_ep_transcripts', 'medium', 'last_ep_sync', 'qdrant_vectorized'
                ];
                const placeholders = columns.map(() => '?').join(',');

                const values = [
                    feed.id,
                    feed.itunesId || itunesId,
                    feed.title || "Unknown Title",
                    feed.author || "Unknown Author",
                    (feed.description || "").substring(0, 1000),
                    feed.image || feed.artwork || "",
                    feed.url || "",
                    feed.link || "",
                    categoriesStr,
                    feed.language || "en",
                    feed.explicit ? "1" : "0",
                    feed.itunesType || "episodic",
                    latestEp ? String(latestEp.id) : null,
                    latestEp ? (latestEp.title || "") : null,
                    latestEp ? (latestEp.datePublished || 0) : null,
                    latestEp ? (latestEp.duration || 0) : null,
                    latestEp ? (latestEp.enclosureUrl || "") : null,
                    latestEp ? (latestEp.image || latestEp.feedImage || "") : null,
                    latestEp ? (latestEp.enclosureType || "audio/mpeg") : null,
                    latestEp ? cleanDescription(latestEp.description) : null,
                    latestEp ? (latestEp.chaptersUrl || null) : null,
                    latestEp ? (latestEp.transcriptUrl || null) : null,
                    latestEp ? (latestEp.persons ? JSON.stringify(latestEp.persons) : null) : null,
                    latestEp ? (latestEp.transcripts ? JSON.stringify(latestEp.transcripts) : null) : null,
                    feed.medium || "podcast",
                    Date.now(),
                    isVectorizedInQdrant
                ];

                await executeSQL(`
                    INSERT INTO podcasts (${columns.join(',')}) 
                    VALUES (${placeholders}) 
                    ON CONFLICT(id) DO UPDATE SET 
                        itunes_id = excluded.itunes_id,
                        latest_ep_id = COALESCE(podcasts.latest_ep_id, excluded.latest_ep_id),
                        qdrant_vectorized = COALESCE(podcasts.qdrant_vectorized, excluded.qdrant_vectorized);
                `, values);

                console.log(`  ✓ [IMPORTED & SYNCED] "${feed.title}" (Qdrant Vectorized: ${isVectorizedInQdrant})`);
            } catch (err) {
                console.error(`  ✗ [ERROR] iTunes ID ${itunesId}: ${err.message}`);
            }
        });

        await Promise.all(promises);

        // Slowly decay global batch delay back down if it was increased
        if (currentBatchDelayMs > 100) {
            currentBatchDelayMs = Math.max(100, currentBatchDelayMs - 50);
        }

        if (i + CONCURRENCY < missingItunesIds.length) {
            await new Promise(resolve => setTimeout(resolve, currentBatchDelayMs));
        }
    }

    console.log("=== Database Restoration Completed! ===");
}

main().catch(err => {
    console.error("Restoration failed:", err);
    process.exit(1);
});
