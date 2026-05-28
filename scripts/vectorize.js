#!/usr/bin/env node

/**
 * Generate vector embeddings for podcast episodes and sync to Qdrant Cloud.
 * Uses CPU-based transformers.js (bge-large-en-v1.5, 1024-dim)
 * Integrates directly with Qdrant REST APIs.
 */

const crypto = require('crypto');

// Environment Variables
const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const QDRANT_URL = process.env.QDRANT_URL;
const QDRANT_API_KEY = process.env.QDRANT_API_KEY;
const API_KEY = process.env.PODCAST_INDEX_API_KEY;
const API_SECRET = process.env.PODCAST_INDEX_API_SECRET;
const API_BASE = "https://api.podcastindex.org/api/1.0";

if (!TURSO_URL || !TURSO_TOKEN || !QDRANT_URL || !QDRANT_API_KEY || !API_KEY || !API_SECRET) {
    console.error("Missing required environment variables (TURSO_*, QDRANT_*, PODCAST_INDEX_*)");
    process.exit(1);
}

// Configuration
const MODEL_NAME = 'Xenova/bge-large-en-v1.5';
const COLLECTION_NAME = 'episodes';
const BATCH_SIZE = 50;
const EPISODES_LIMIT = 30; // 30 episodes per podcast
const MAX_NEW_PODCASTS_PER_RUN = 100; // Cap to prevent GHA timeouts

// Helper: Generate Podcast Index Auth Headers
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

// Helper: Fetch latest 30 episodes from Podcast Index with retry and backoff on 429
async function fetchEpisodes(feedId, retries = 3, delay = 1000) {
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        try {
            const headers = generateAuthHeaders();
            const res = await fetch(`${API_BASE}/episodes/byfeedid?id=${feedId}&max=${EPISODES_LIMIT}`, { headers });
            
            if (res.status === 429) {
                if (attempt <= retries) {
                    const backoff = delay * Math.pow(2, attempt - 1);
                    console.warn(`  [WARN] Rate limited (429) fetching episodes for pod=${feedId}. Retrying attempt ${attempt}/${retries} after ${backoff}ms...`);
                    await new Promise(resolve => setTimeout(resolve, backoff));
                    continue;
                }
                throw new Error(`API error 429: Too Many Requests`);
            }
            
            if (!res.ok) {
                throw new Error(`API error: ${res.status}`);
            }
            
            const data = await res.json();
            return data.items || [];
        } catch (e) {
            if (attempt > retries) {
                console.error(`[FAIL] fetchEpisodes pod=${feedId} failed after ${attempt} attempts: ${e.message}`);
                throw e; // Rethrow to propagate failure
            }
            const backoff = delay * Math.pow(2, attempt - 1);
            console.warn(`  [WARN] fetchEpisodes pod=${feedId} failed (attempt ${attempt}/${retries}): ${e.message}. Retrying in ${backoff}ms...`);
            await new Promise(resolve => setTimeout(resolve, backoff));
        }
    }
    return [];
}

// Helper: Generate a stable UUID from a string ID (Qdrant requires UUIDs)
function generateUUID(strId) {
    const hash = crypto.createHash('md5').update(String(strId)).digest('hex');
    return `${hash.substring(0,8)}-${hash.substring(8,12)}-${hash.substring(12,16)}-${hash.substring(16,20)}-${hash.substring(20)}`;
}

// Helper: Qdrant Batch Existence Check
async function qdrantCheckExistence(ids) {
    if (ids.length === 0) return new Set();
    try {
        const response = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}/points`, {
            method: "POST",
            headers: {
                "api-key": QDRANT_API_KEY,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                ids,
                with_vector: false,
                with_payload: false
            })
        });
        if (!response.ok) return new Set();
        const data = await response.json();
        const existing = new Set((data.result || []).map(r => String(r.id)));
        return existing;
    } catch (e) {
        console.error(`[QDRANT] Existence check failed: ${e.message}`);
        return new Set();
    }
}

// Helper: Qdrant Batch Upsert
async function qdrantUpsertBatch(points) {
    if (points.length === 0) return;
    const response = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}/points?wait=true`, {
        method: "PUT",
        headers: {
            "api-key": QDRANT_API_KEY,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ points })
    });
    if (!response.ok) {
        const errText = await response.text();
        throw new Error(`Qdrant upsert failed: ${response.status} - ${errText}`);
    }
    const res = await response.json();
    return res;
}

// Clean descriptions
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
    for (const pattern of sponsorPatterns) text = text.replace(pattern, '');

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
    for (const pattern of boilerplate) text = text.replace(pattern, '');

    return text.replace(/\s+/g, ' ').trim().substring(0, 1000);
}

async function main() {
    console.log("=== Starting Qdrant Vector Sync Pipeline ===");

    // === DIAGNOSTIC FAILSAFE LOGGING ===
    console.log("[DIAGNOSTIC] Verifying GHA Environment Configuration...");
    console.log(`  - TURSO_URL:     ${TURSO_URL ? 'PRESENT' : 'MISSING'}`);
    console.log(`  - TURSO_TOKEN:   ${TURSO_TOKEN ? 'PRESENT (Masked: ' + TURSO_TOKEN.substring(0, 8) + '...)' : 'MISSING'}`);
    console.log(`  - QDRANT_URL:    ${QDRANT_URL ? 'PRESENT (' + QDRANT_URL + ')' : 'MISSING'}`);
    console.log(`  - QDRANT_API_KEY:${QDRANT_API_KEY ? 'PRESENT (Masked: ' + QDRANT_API_KEY.substring(0, 8) + '...)' : 'MISSING'}`);
    console.log(`  - API_KEY:       ${API_KEY ? 'PRESENT' : 'MISSING'}`);

    console.log("[DIAGNOSTIC] Testing Qdrant Cluster Connection & Health...");
    try {
        const clusterRes = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}`, {
            headers: { "api-key": QDRANT_API_KEY }
        });
        if (clusterRes.ok) {
            const clusterData = await clusterRes.json();
            console.log(`[STATUS] Qdrant connection SUCCESSFUL!`);
            console.log(`  - Collection:   '${COLLECTION_NAME}'`);
            console.log(`  - Status:       ${clusterData.result?.status}`);
            console.log(`  - Active Points: ${clusterData.result?.points_count}`);
            console.log(`  - Vectors Size: ${clusterData.result?.config?.params?.vectors?.size}`);
        } else {
            const errorText = await clusterRes.text();
            throw new Error(`Cluster returned HTTP ${clusterRes.status} - ${errorText}`);
        }
    } catch (e) {
        console.error(`[CRITICAL] Qdrant connection check FAILED: ${e.message}`);
        console.error("  -> Please verify QDRANT_URL and QDRANT_API_KEY secrets in GitHub Repository Secrets!");
        process.exit(1);
    }
    // ===================================

    // 1. Setup local CPU embedding generator
    const { env: envESM, pipeline } = await import('@xenova/transformers');
    envESM.cacheDir = './.cache';
    const extractor = await pipeline('feature-extraction', MODEL_NAME);
    console.log(`[MODEL] Loaded embedding extractor: ${MODEL_NAME}`);

    // 2. Fetch active trending podcasts from charts matching country matrix
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    let sql = `
        SELECT DISTINCT 
            p.id, 
            p.title, 
            p.categories,
            p.author,
            p.image_url,
            p.language
        FROM charts c
        JOIN podcasts p ON c.itunes_id = p.itunes_id
        WHERE (p.qdrant_vectorized = 0 OR p.qdrant_vectorized IS NULL)
    `;
    let args = [];
    if (country) {
        sql += ` AND c.country = ?`;
        args.push(country);
        console.log(`[SYNC] Filtering candidates for country: ${country}`);
    }

    console.log("[SYNC] Querying Turso for chart podcasts...");
    const res = await executeSQL(sql, args);
    const podcasts = res?.results?.[0]?.response?.result?.rows?.map(r => ({
        id: String(r[0].value),
        title: r[1].value || "Unknown Show",
        categories: r[2]?.value || "Podcast",
        author: r[3]?.value || "",
        image_url: r[4]?.value || "",
        language: r[5]?.value || "en"
    })) || [];

    console.log(`[SYNC] Found ${podcasts.length} active podcasts to sync.`);
    if (podcasts.length === 0) return;

    let success = 0;
    let skipped = 0;
    let errors = 0;
    let vectorizedPodcastsCount = 0;
    const startTime = Date.now();

    // 3. Process podcasts sequentially to be polite to APIs and prevent locks
    for (let idx = 0; idx < podcasts.length; idx++) {
        const pod = podcasts[idx];
        console.log(`\n[${idx+1}/${podcasts.length}] Processing: "${pod.title}" (ID: ${pod.id})`);

        // Fetch latest 30 episodes
        let episodes;
        try {
            episodes = await fetchEpisodes(pod.id);
        } catch (e) {
            console.error(`  -> Failed to fetch episodes for "${pod.title}" due to API errors. Skipping updates for this run.`);
            errors++;
            await new Promise(r => setTimeout(r, 400));
            continue;
        }

        if (episodes.length === 0) {
            console.log(`  -> No episodes found for "${pod.title}". Skipping.`);
            try {
                await executeSQL("UPDATE podcasts SET qdrant_vectorized = 1 WHERE id = ?", [pod.id]);
                console.log(`  -> Marked "${pod.title}" as vectorized (no episodes).`);
            } catch (err) {
                console.error(`  -> Failed to update qdrant_vectorized for "${pod.title}": ${err.message}`);
            }
            await new Promise(r => setTimeout(r, 400));
            continue;
        }

        // Map episode IDs to stable UUIDs
        const episodeMap = episodes.map(ep => ({
            raw: ep,
            uuid: generateUUID(ep.id)
        }));
        const uuids = episodeMap.map(m => m.uuid);

        // Qdrant Batch check
        const existingUuids = await qdrantCheckExistence(uuids);
        console.log(`  -> Qdrant status: ${existingUuids.size}/${episodes.length} episodes already indexed.`);

        const toVectorize = episodeMap.filter(m => !existingUuids.has(m.uuid));
        skipped += existingUuids.size;

        if (toVectorize.length === 0) {
            console.log(`  -> All episodes are up-to-date in Qdrant.`);
            try {
                await executeSQL("UPDATE podcasts SET qdrant_vectorized = 1 WHERE id = ?", [pod.id]);
                console.log(`  -> Marked "${pod.title}" as vectorized (up-to-date).`);
            } catch (err) {
                console.error(`  -> Failed to update qdrant_vectorized for "${pod.title}": ${err.message}`);
            }
            await new Promise(r => setTimeout(r, 400));
            continue;
        }

        // Check if we hit our run cap of newly vectorized podcasts
        if (vectorizedPodcastsCount >= MAX_NEW_PODCASTS_PER_RUN) {
            console.log(`\n[CAP REACHED] Already vectorized ${MAX_NEW_PODCASTS_PER_RUN} podcasts in this run. Skipping further vectorizations to prevent timeout.`);
            break;
        }

        console.log(`  -> ${toVectorize.length} new episodes qualify for vectorization.`);
        vectorizedPodcastsCount++;
        const points = [];

        for (const item of toVectorize) {
            const ep = item.raw;
            const uuid = item.uuid;
            const epTitle = ep.title || "";
            const rawDesc = ep.description || "";
            const cleanedDesc = cleanDescription(rawDesc);

            // Construct text string
            const textParts = [
                `Episode: ${epTitle}`,
                cleanedDesc ? `Description: ${cleanedDesc}` : null,
                `Podcast: ${pod.title}`,
                pod.categories ? `Genres: ${pod.categories}` : null
            ].filter(Boolean);
            const text = textParts.join('. ').replace(/[\n\r]+/g, ' ').substring(0, 1000);

            try {
                // Generate embedding vector
                const output = await extractor(text, { pooling: 'mean', normalize: true });
                const vector = Array.from(output.data);

                // Build point metadata payload
                const payload = {
                    id: parseInt(ep.id) || 0, // Store original numeric episode ID for mobile app compatibility
                    title: epTitle,
                    description: ep.description || "", // Store original description for the UI
                    podcast_id: parseInt(pod.id) || 0,
                    podcast_title: pod.title,
                    podcast_author: pod.author || "",
                    podcast_image_url: pod.image_url || "",
                    podcast_categories: pod.categories || "",
                    language: pod.language || "en",
                    audio_url: ep.enclosureUrl || "",
                    image_url: ep.image || ep.feedImage || pod.image_url || "",
                    published_date: ep.datePublished || 0,
                    duration: ep.duration || 0
                };

                points.push({
                    id: uuid,
                    vector,
                    payload
                });
                console.log(`  [VEC] Vectorized: "${epTitle}"`);
            } catch (err) {
                console.error(`  [ERR] Failed to vectorize "${epTitle}": ${err.message}`);
                errors++;
            }
        }

        // Batch upload new points to Qdrant
        let uploadSuccess = true;
        if (points.length > 0) {
            console.log(`  -> Uploading ${points.length} vectors to Qdrant...`);
            try {
                await qdrantUpsertBatch(points);
                success += points.length;
                console.log(`  -> Successful upload to Qdrant!`);
                
                // Mark as vectorized in Turso DB
                await executeSQL("UPDATE podcasts SET qdrant_vectorized = 1 WHERE id = ?", [pod.id]);
                console.log(`  -> Marked "${pod.title}" as vectorized (uploaded ${points.length} vectors).`);
            } catch (err) {
                console.error(`  -> Failed to upload batch to Qdrant: ${err.message}`);
                errors += points.length;
                uploadSuccess = false;
            }
        } else if (toVectorize.length > 0) {
            console.warn(`  -> ${toVectorize.length} episodes were qualified but none were successfully vectorized.`);
        }

        // Rolling Window Delete: Keep only the latest 30 episodes in Qdrant for this podcast
        // (Any point belonging to this podcast whose UUID is NOT in the current active top 30 list)
        if (uuids.length > 0) {
            try {
                const deleteResponse = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}/points/delete`, {
                    method: "POST",
                    headers: {
                        "api-key": QDRANT_API_KEY,
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        filter: {
                            must: [
                                { key: "podcast_id", match: { value: parseInt(pod.id) || 0 } }
                            ],
                            must_not: [
                                { has_id: uuids }
                            ]
                        }
                    })
                });
                if (deleteResponse.ok) {
                    console.log(`  -> Pruned older episodes for "${pod.title}" successfully.`);
                }
            } catch (pruneErr) {
                console.warn(`  -> Pruning failed for "${pod.title}":`, pruneErr.message);
            }
        }
        
        // Polite API rate limiting delay
        await new Promise(r => setTimeout(r, 400));
    }

    // 4. Global Rolling Window Cleanup (Backup prune: Deletes any point older than 60 days)
    console.log("\n[CLEANUP] Running global 60-day rolling window cleanup...");
    const sixtyDaysAgo = Math.floor(Date.now() / 1000) - (60 * 24 * 60 * 60);
    try {
        const cleanupResponse = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}/points/delete`, {
            method: "POST",
            headers: {
                "api-key": QDRANT_API_KEY,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                filter: {
                    must: [
                        { key: "published_date", range: { lt: sixtyDaysAgo } }
                    ]
                }
            })
        });
        if (cleanupResponse.ok) {
            console.log("[CLEANUP] Global 60-day rolling window deletion completed!");
        }
    } catch (cleanupErr) {
        console.error("[CLEANUP] Global cleanup failed:", cleanupErr.message);
    }

    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n=== Qdrant Vector Sync Pipeline Complete ===`);
    console.log(`New Vectors Uploaded: ${success}`);
    console.log(`Existing Skipped:     ${skipped}`);
    console.log(`Errors:               ${errors}`);
    console.log(`Total Duration:       ${elapsed}s`);
    console.log(`============================================\n`);
}

main().catch(console.error);
