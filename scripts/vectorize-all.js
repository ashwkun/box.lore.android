#!/usr/bin/env node

/**
 * One-time / Batch script to generate vector embeddings for all non-vectorized podcast episodes
 * in the database and sync to Qdrant Cloud.
 * Uses CPU-based transformers.js (bge-large-en-v1.5, 1024-dim)
 * Integrates directly with Qdrant REST APIs.
 * 
 * Optimized with:
 * - Safe concurrent processing queue
 * - Global rate-limit cooldown with a live logs countdown
 * - Real-time progress indicators showing how many podcasts are remaining
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
const EPISODES_LIMIT = 30; // 30 episodes per podcast

// Global State
let success = 0;
let skipped = 0;
let errors = 0;
let nextPodcastIndex = 0;
let globalRateLimitCooldownUntil = 0;
let isGlobalCooldownRunning = false;
let extractor;

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

// Global Cooldown Handler
async function triggerGlobalCooldown(durationMs) {
    if (isGlobalCooldownRunning) {
        while (Date.now() < globalRateLimitCooldownUntil) {
            await new Promise(r => setTimeout(r, 1000));
        }
        return;
    }
    isGlobalCooldownRunning = true;
    globalRateLimitCooldownUntil = Date.now() + durationMs;
    const durationSeconds = Math.ceil(durationMs / 1000);
    console.warn(`\n⚠️ [RATE LIMIT] 429 Too Many Requests detected. Triggering global cooldown of ${durationSeconds}s...`);
    
    for (let sec = durationSeconds; sec > 0; sec--) {
        if (sec % 5 === 0 || sec <= 5) {
            console.log(`⏱️ Rate-limit cooldown: ${sec}s remaining`);
        }
        await new Promise(r => setTimeout(r, 1000));
    }
    console.log(`✅ Cooldown completed. Resuming pipeline operations.\n`);
    isGlobalCooldownRunning = false;
}

// Helper: Fetch latest episodes from Podcast Index with retry and backoff on 429
async function fetchEpisodes(feedId, retries = 3, delay = 1000) {
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        try {
            // Check global cooldown before making request
            if (Date.now() < globalRateLimitCooldownUntil) {
                const waitTime = globalRateLimitCooldownUntil - Date.now();
                if (waitTime > 0) {
                    await new Promise(r => setTimeout(r, waitTime));
                }
            }

            const headers = generateAuthHeaders();
            const res = await fetch(`${API_BASE}/episodes/byfeedid?id=${feedId}&max=${EPISODES_LIMIT}`, { headers });
            
            if (res.status === 429) {
                await triggerGlobalCooldown(30000); // 30s cooldown
                continue; // Retry
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

// Process a single podcast complete flow
async function processSinglePodcast(pod, workerId, totalPodcasts) {
    // 1. Fetch latest episodes
    let episodes;
    try {
        episodes = await fetchEpisodes(pod.id);
    } catch (e) {
        console.error(`  [Worker ${workerId}] Failed to fetch episodes for "${pod.title}" due to API errors.`);
        errors++;
        return;
    }

    if (episodes.length === 0) {
        console.log(`  [Worker ${workerId}] No episodes found for "${pod.title}". Skipping.`);
        try {
            await executeSQL("UPDATE podcasts SET qdrant_vectorized = 1 WHERE id = ?", [pod.id]);
            console.log(`  [Worker ${workerId}] Marked "${pod.title}" as vectorized (no episodes).`);
        } catch (err) {
            console.error(`  [Worker ${workerId}] Failed to update qdrant_vectorized for "${pod.title}": ${err.message}`);
        }
        return;
    }

    // 2. Map episode IDs to stable UUIDs
    const episodeMap = episodes.map(ep => ({
        raw: ep,
        uuid: generateUUID(ep.id)
    }));
    const uuids = episodeMap.map(m => m.uuid);

    // 3. Qdrant Batch check
    const existingUuids = await qdrantCheckExistence(uuids);
    console.log(`  [Worker ${workerId}] Qdrant status: ${existingUuids.size}/${episodes.length} episodes already indexed for "${pod.title}".`);

    const toVectorize = episodeMap.filter(m => !existingUuids.has(m.uuid));
    skipped += existingUuids.size;

    if (toVectorize.length === 0) {
        console.log(`  [Worker ${workerId}] All episodes up-to-date in Qdrant for "${pod.title}".`);
        try {
            await executeSQL("UPDATE podcasts SET qdrant_vectorized = 1 WHERE id = ?", [pod.id]);
            console.log(`  [Worker ${workerId}] Marked "${pod.title}" as vectorized (up-to-date).`);
        } catch (err) {
            console.error(`  [Worker ${workerId}] Failed to update qdrant_vectorized for "${pod.title}": ${err.message}`);
        }
        return;
    }

    console.log(`  [Worker ${workerId}] ${toVectorize.length} new episodes qualify for vectorization.`);
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
            pod.categories ? `Genres: ${pod.categories}` : null,
            pod.author ? `Host: ${pod.author}` : null
        ].filter(Boolean);
        const text = textParts.join('. ').replace(/[\n\r]+/g, ' ').substring(0, 1000);

        try {
            // Generate embedding vector
            const output = await extractor(text, { pooling: 'mean', normalize: true });
            const vector = Array.from(output.data);

            // Build point metadata payload
            const payload = {
                id: parseInt(ep.id) || 0,
                title: epTitle,
                description: ep.description || "",
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
        } catch (err) {
            console.error(`  [Worker ${workerId}] Failed to vectorize "${epTitle}": ${err.message}`);
            errors++;
        }
    }

    // Batch upload new points to Qdrant
    if (points.length > 0) {
        console.log(`  [Worker ${workerId}] Uploading ${points.length} vectors to Qdrant...`);
        try {
            await qdrantUpsertBatch(points);
            success += points.length;
            console.log(`  [Worker ${workerId}] Successful upload to Qdrant for "${pod.title}"!`);
            
            // Mark as vectorized in Turso DB
            await executeSQL("UPDATE podcasts SET qdrant_vectorized = 1 WHERE id = ?", [pod.id]);
            console.log(`  [Worker ${workerId}] Marked "${pod.title}" as vectorized (uploaded ${points.length} vectors).`);
        } catch (err) {
            console.error(`  [Worker ${workerId}] Failed to upload batch to Qdrant: ${err.message}`);
            errors += points.length;
        }
    }

    // Rolling Window Delete: Keep only the latest 30 episodes in Qdrant for this podcast
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
                console.log(`  [Worker ${workerId}] Pruned older episodes for "${pod.title}" successfully.`);
            }
        } catch (pruneErr) {
            console.warn(`  [Worker ${workerId}] Pruning failed for "${pod.title}":`, pruneErr.message);
        }
    }
}

async function main() {
    console.log("=== Starting Qdrant Complete Vector Sync Pipeline ===");

    // Parse options
    const limitIndex = process.argv.indexOf('--limit');
    const limitVal = limitIndex !== -1 ? parseInt(process.argv[limitIndex + 1]) : 5000;

    const timeLimitIndex = process.argv.indexOf('--max-time');
    const maxTimeMinutes = timeLimitIndex !== -1 ? parseInt(process.argv[timeLimitIndex + 1]) : 330; // Default to 330 mins
    const maxTimeMs = maxTimeMinutes * 60 * 1000;

    const concurrencyIndex = process.argv.indexOf('--concurrency');
    const concurrencyVal = concurrencyIndex !== -1 ? parseInt(process.argv[concurrencyIndex + 1]) : 4; // Default to 4 workers

    console.log(`[CONFIG] Job constraints:`);
    console.log(`  - Podcast Limit: ${limitVal}`);
    console.log(`  - Concurrency:   ${concurrencyVal} workers`);
    console.log(`  - Time Limit:    ${maxTimeMinutes} minutes (${maxTimeMs} ms)`);

    // Verify Qdrant connection
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
        } else {
            const errorText = await clusterRes.text();
            throw new Error(`Cluster returned HTTP ${clusterRes.status} - ${errorText}`);
        }
    } catch (e) {
        console.error(`[CRITICAL] Qdrant connection check FAILED: ${e.message}`);
        process.exit(1);
    }

    // 1. Setup local CPU embedding generator
    const { env: envESM, pipeline } = await import('@xenova/transformers');
    envESM.cacheDir = './.cache';
    extractor = await pipeline('feature-extraction', MODEL_NAME);
    console.log(`[MODEL] Loaded embedding extractor: ${MODEL_NAME}`);

    // 2. Fetch all podcasts that are not yet vectorized and are actively in charts
    const sql = `
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
        LIMIT ?
    `;
    const args = [limitVal];

    console.log("[SYNC] Querying Turso for non-vectorized chart podcasts...");
    const res = await executeSQL(sql, args);
    const podcasts = res?.results?.[0]?.response?.result?.rows?.map(r => ({
        id: String(r[0].value),
        title: r[1].value || "Unknown Show",
        categories: r[2]?.value || "Podcast",
        author: r[3]?.value || "",
        image_url: r[4]?.value || "",
        language: r[5]?.value || "en"
    })) || [];

    console.log(`[SYNC] Found ${podcasts.length} non-vectorized chart podcasts to sync.`);
    if (podcasts.length === 0) {
        console.log("No non-vectorized chart podcasts found! Exiting.");
        return;
    }

    const startTime = Date.now();

    // 3. Worker logic
    async function worker(workerId) {
        while (true) {
            // Check timeout
            const elapsed = Date.now() - startTime;
            if (elapsed > maxTimeMs) {
                console.log(`\n[Worker ${workerId}] Time limit exceeded. Gracefully shutting down worker...`);
                break;
            }

            // Get next podcast index atomically
            const idx = nextPodcastIndex++;
            if (idx >= podcasts.length) {
                break; // No more podcasts
            }

            const pod = podcasts[idx];
            const remaining = podcasts.length - idx;
            console.log(`\n[Worker ${workerId}] [Progress: ${idx + 1}/${podcasts.length}] (${remaining} left) -> Starting: "${pod.title}" (ID: ${pod.id})`);

            // Apply global rate limit cooldown check
            if (Date.now() < globalRateLimitCooldownUntil) {
                const waitTime = globalRateLimitCooldownUntil - Date.now();
                if (waitTime > 0) {
                    await new Promise(r => setTimeout(r, waitTime));
                }
            }

            try {
                await processSinglePodcast(pod, workerId, podcasts.length);
            } catch (err) {
                console.error(`  [Worker ${workerId}] [ERR] Failed processing "${pod.title}": ${err.message}`);
                errors++;
            }

            // Small polite delay between jobs
            await new Promise(r => setTimeout(r, 200));
        }
    }

    // Start workers
    const workers = [];
    for (let i = 0; i < concurrencyVal; i++) {
        workers.push(worker(i + 1));
        // Stagger worker start times slightly to avoid immediate concurrent hit
        await new Promise(r => setTimeout(r, 250));
    }

    // Wait for all workers to complete
    await Promise.all(workers);

    const elapsedTotal = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n=== Qdrant Complete Vector Sync Pipeline Complete ===`);
    console.log(`New Vectors Uploaded: ${success}`);
    console.log(`Existing Skipped:     ${skipped}`);
    console.log(`Errors:               ${errors}`);
    console.log(`Total Duration:       ${elapsedTotal}s`);
    console.log(`====================================================\n`);
}

main().catch(console.error);
