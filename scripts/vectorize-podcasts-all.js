#!/usr/bin/env node

/**
 * One-time / Batch script to generate vector embeddings for all non-vectorized podcast shows
 * in the database and sync to Qdrant Cloud.
 * Uses CPU-based transformers.js (bge-large-en-v1.5, 1024-dim)
 * Integrates directly with Qdrant REST APIs.
 */

const crypto = require('crypto');

// Environment Variables
const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const QDRANT_URL = process.env.QDRANT_URL;
const QDRANT_API_KEY = process.env.QDRANT_API_KEY;

if (!TURSO_URL || !TURSO_TOKEN || !QDRANT_URL || !QDRANT_API_KEY) {
    console.error("Missing required environment variables (TURSO_*, QDRANT_*)");
    process.exit(1);
}

// Configuration
const MODEL_NAME = 'Xenova/bge-large-en-v1.5';
const COLLECTION_NAME = 'podcasts';

// Global State & Counters
let success = 0;
let skipped = 0;
let errors = 0;
let nextPodcastIndex = 0;
let extractor;

// DB Cost Tracking
let totalRowsRead = 0;
let totalRowsWritten = 0;

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

    // Accumulate DB rows cost statistics
    if (res.results) {
        for (const result of res.results) {
            if (result.response?.result) {
                totalRowsRead += result.response.result.rows_read || 0;
                totalRowsWritten += result.response.result.rows_written || 0;
            }
        }
    }

    return res;
}

// Helper: Ensure Qdrant Collection Exists
async function ensureQdrantCollection() {
    console.log(`[QDRANT] Checking if collection '${COLLECTION_NAME}' exists...`);
    try {
        const response = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}`, {
            headers: { "api-key": QDRANT_API_KEY }
        });
        
        if (response.status === 404) {
            console.log(`[QDRANT] Collection '${COLLECTION_NAME}' not found. Creating it...`);
            const createResponse = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}`, {
                method: "PUT",
                headers: {
                    "api-key": QDRANT_API_KEY,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    vectors: {
                        size: 1024,
                        distance: "Cosine"
                    }
                })
            });
            if (!createResponse.ok) {
                const errText = await createResponse.text();
                throw new Error(`Failed to create Qdrant collection: ${createResponse.status} - ${errText}`);
            }
            console.log(`[QDRANT] Collection '${COLLECTION_NAME}' created successfully!`);
        } else if (!response.ok) {
            const errText = await response.text();
            throw new Error(`Failed to check Qdrant collection: ${response.status} - ${errText}`);
        } else {
            console.log(`[QDRANT] Collection '${COLLECTION_NAME}' already exists.`);
        }
    } catch (e) {
        console.error(`[QDRANT] Error during collection initialization: ${e.message}`);
        throw e;
    }
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
    return await response.json();
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
    const uuid = generateUUID(pod.id);

    // Qdrant existence check
    const existingUuids = await qdrantCheckExistence([uuid]);
    if (existingUuids.has(uuid)) {
        console.log(`  [Worker ${workerId}] "${pod.title}" already indexed in Qdrant.`);
        skipped++;
        await executeSQL("UPDATE podcasts SET qdrant_podcast_vectorized = 1 WHERE id = ?", [pod.id]);
        return;
    }

    const cleanedDesc = cleanDescription(pod.description);

    // Construct text representation
    const textParts = [
        `Podcast: ${pod.title}`,
        pod.author ? `Host: ${pod.author}` : null,
        cleanedDesc ? `Description: ${cleanedDesc}` : null,
        pod.categories ? `Genres: ${pod.categories}` : null,
        pod.language ? `Language: ${pod.language}` : null
    ].filter(Boolean);
    const text = textParts.join('. ').replace(/[\n\r]+/g, ' ').substring(0, 1000);

    // Generate embedding vector
    const output = await extractor(text, { pooling: 'mean', normalize: true });
    const vector = Array.from(output.data);

    // Build payload
    const payload = {
        id: parseInt(pod.id) || 0,
        title: pod.title,
        author: pod.author || "",
        description: pod.description || "",
        image_url: pod.image_url || "",
        categories: pod.categories || "",
        language: pod.language || "en",
        feed_url: pod.feed_url || "",
        website_url: pod.website_url || ""
    };

    const points = [{
        id: uuid,
        vector,
        payload
    }];

    // Upload to Qdrant
    await qdrantUpsertBatch(points);
    success++;
    console.log(`  [Worker ${workerId}] Vectorized & uploaded: "${pod.title}"`);

    // Mark as vectorized in Turso
    await executeSQL("UPDATE podcasts SET qdrant_podcast_vectorized = 1 WHERE id = ?", [pod.id]);
}

async function main() {
    console.log("=== Starting Qdrant Complete Podcast Show Vector Sync Pipeline ===");

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

    // Ensure podcasts collection exists
    await ensureQdrantCollection();

    // 1. Setup local CPU embedding generator
    const { env: envESM, pipeline } = await import('@xenova/transformers');
    envESM.cacheDir = './.cache';
    extractor = await pipeline('feature-extraction', MODEL_NAME);
    console.log(`[MODEL] Loaded embedding extractor: ${MODEL_NAME}`);

    // 2. Fetch all podcasts that are not yet vectorized
    const sql = `
        SELECT DISTINCT 
            p.id, 
            p.title, 
            p.categories,
            p.author,
            p.image_url,
            p.language,
            p.description,
            p.feed_url,
            p.website_url
        FROM charts c
        JOIN podcasts p ON c.itunes_id = p.itunes_id
        WHERE (p.qdrant_podcast_vectorized = 0 OR p.qdrant_podcast_vectorized IS NULL)
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
        language: r[5]?.value || "en",
        description: r[6]?.value || "",
        feed_url: r[7]?.value || "",
        website_url: r[8]?.value || ""
    })) || [];

    console.log(`[SYNC] Found ${podcasts.length} non-vectorized chart podcasts to sync.`);
    if (podcasts.length === 0) {
        console.log("No non-vectorized chart podcasts found! Exiting.");
        console.log(`\n=== Qdrant Complete Podcast Show Vector Sync Pipeline Complete ===`);
        console.log(`📊 DB COST: ${totalRowsRead.toLocaleString()} rows read | ${totalRowsWritten.toLocaleString()} rows written`);
        console.log(`====================================================\n`);
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
        // Stagger worker start times slightly to avoid immediate concurrent hits
        await new Promise(r => setTimeout(r, 250));
    }

    // Wait for all workers to complete
    await Promise.all(workers);

    const elapsedTotal = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n=== Qdrant Complete Podcast Show Vector Sync Pipeline Complete ===`);
    console.log(`New Vectors Uploaded: ${success}`);
    console.log(`Existing Skipped:     ${skipped}`);
    console.log(`Errors:               ${errors}`);
    console.log(`Total Duration:       ${elapsedTotal}s`);
    console.log(`📊 DB COST: ${totalRowsRead.toLocaleString()} rows read | ${totalRowsWritten.toLocaleString()} rows written`);
    console.log(`====================================================\n`);
}

main().catch(console.error);
