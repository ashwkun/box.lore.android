#!/usr/bin/env node

/**
 * Generate vector embeddings for podcast shows and sync to Qdrant Cloud.
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
const MAX_NEW_PODCASTS_PER_RUN = 1000; // Cap to prevent GHA timeouts

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
    const response = await fetch(`${QDRANT_URL}/collections/${COLLECTION_NAME}/points?wait=false`, {
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

async function main() {
    console.log("=== Starting Qdrant Podcast Show Vector Sync Pipeline ===");

    // === DIAGNOSTIC FAILSAFE LOGGING ===
    console.log("[DIAGNOSTIC] Verifying Environment Configuration...");
    console.log(`  - TURSO_URL:     ${TURSO_URL ? 'PRESENT' : 'MISSING'}`);
    console.log(`  - TURSO_TOKEN:   ${TURSO_TOKEN ? 'PRESENT' : 'MISSING'}`);
    console.log(`  - QDRANT_URL:    ${QDRANT_URL ? 'PRESENT (' + QDRANT_URL + ')' : 'MISSING'}`);
    console.log(`  - QDRANT_API_KEY:${QDRANT_API_KEY ? 'PRESENT' : 'MISSING'}`);

    // Ensure podcasts collection exists
    await ensureQdrantCollection();

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
            p.language,
            p.description,
            p.feed_url,
            p.website_url
        FROM charts c
        JOIN podcasts p ON c.itunes_id = p.itunes_id
        WHERE (p.qdrant_podcast_vectorized = 0 OR p.qdrant_podcast_vectorized IS NULL)
    `;
    let args = [];
    if (country) {
        sql += ` AND c.country = ?`;
        args.push(country);
        console.log(`[SYNC] Filtering candidates for country: ${country}`);
    }

    sql += ` LIMIT 1000`;

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

    console.log(`[SYNC] Found ${podcasts.length} active podcasts to sync.`);
    if (podcasts.length === 0) {
        console.log(`\n=== Qdrant Podcast Show Vector Sync Pipeline Complete ===`);
        console.log(`📊 DB COST: ${totalRowsRead.toLocaleString()} rows read | ${totalRowsWritten.toLocaleString()} rows written`);
        console.log(`============================================\n`);
        return;
    }

    // Map podcast IDs to stable UUIDs
    const podcastMap = podcasts.map(pod => ({
        raw: pod,
        uuid: generateUUID(pod.id)
    }));
    const uuids = podcastMap.map(m => m.uuid);

    // Qdrant Batch check to filter out already indexed podcasts
    const existingUuids = await qdrantCheckExistence(uuids);
    console.log(`  -> Qdrant status: ${existingUuids.size}/${podcasts.length} podcasts already indexed in collection.`);

    const toVectorize = podcastMap.filter(m => !existingUuids.has(m.uuid));
    let skipped = existingUuids.size;

    // Immediately mark already indexed podcasts as vectorized in Turso to prevent re-querying next run
    const alreadyIndexed = podcastMap.filter(m => existingUuids.has(m.uuid));
    for (const item of alreadyIndexed) {
        try {
            await executeSQL("UPDATE podcasts SET qdrant_podcast_vectorized = 1 WHERE id = ?", [item.raw.id]);
        } catch (err) {
            console.error(`  -> Failed to update qdrant_podcast_vectorized for "${item.raw.title}": ${err.message}`);
        }
    }

    if (toVectorize.length === 0) {
        console.log(`  -> All podcasts are up-to-date in Qdrant.`);
        console.log(`\n=== Qdrant Podcast Show Vector Sync Pipeline Complete ===`);
        console.log(`📊 DB COST: ${totalRowsRead.toLocaleString()} rows read | ${totalRowsWritten.toLocaleString()} rows written`);
        console.log(`============================================\n`);
        return;
    }

    // Limit candidate list to MAX_NEW_PODCASTS_PER_RUN to prevent GHA timeouts
    const candidates = toVectorize.slice(0, MAX_NEW_PODCASTS_PER_RUN);
    console.log(`  -> Vectorizing ${candidates.length} podcasts in this run (Cap: ${MAX_NEW_PODCASTS_PER_RUN}).`);

    let success = 0;
    let errors = 0;
    const startTime = Date.now();
    const points = [];

    for (let idx = 0; idx < candidates.length; idx++) {
        const item = candidates[idx];
        const pod = item.raw;
        const uuid = item.uuid;

        console.log(`[${idx + 1}/${candidates.length}] Vectorizing: "${pod.title}"`);
        
        const cleanedDesc = cleanDescription(pod.description);

        // Construct embedding text representation
        const textParts = [
            `Podcast: ${pod.title}`,
            pod.author ? `Host: ${pod.author}` : null,
            cleanedDesc ? `Description: ${cleanedDesc}` : null,
            pod.categories ? `Genres: ${pod.categories}` : null,
            pod.language ? `Language: ${pod.language}` : null
        ].filter(Boolean);
        const text = textParts.join('. ').replace(/[\n\r]+/g, ' ').substring(0, 1000);

        try {
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

            points.push({
                id: uuid,
                vector,
                payload
            });
            success++;
        } catch (err) {
            console.error(`  [ERR] Failed to vectorize "${pod.title}": ${err.message}`);
            errors++;
        }
    }

    // Batch upload to Qdrant and update Turso in chunks of 100
    if (points.length > 0) {
        console.log(`  -> Uploading ${points.length} vectors to Qdrant in chunks of 100 (wait=false)...`);
        const CHUNK_SIZE = 100;
        for (let i = 0; i < points.length; i += CHUNK_SIZE) {
            const chunk = points.slice(i, i + CHUNK_SIZE);
            try {
                console.log(`  -> [CHUNK] Uploading ${chunk.length} points to Qdrant...`);
                await qdrantUpsertBatch(chunk);
                
                // Mark as vectorized in Turso DB
                const chunkIds = chunk.map(pt => pt.payload.id);
                const placeholders = chunkIds.map(() => '?').join(',');
                console.log(`  -> [CHUNK] Marking ${chunkIds.length} podcasts as vectorized in Turso...`);
                await executeSQL(`UPDATE podcasts SET qdrant_podcast_vectorized = 1 WHERE id IN (${placeholders})`, chunkIds);
            } catch (err) {
                console.error(`  -> [CHUNK ERR] Failed to process chunk starting at index ${i}: ${err.message}`);
                success -= chunk.length;
                errors += chunk.length;
            }
        }
    }

    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n=== Qdrant Podcast Show Vector Sync Pipeline Complete ===`);
    console.log(`New Vectors Uploaded: ${success}`);
    console.log(`Existing Skipped:     ${skipped}`);
    console.log(`Errors:               ${errors}`);
    console.log(`Total Duration:       ${elapsed}s`);
    console.log(`📊 DB COST: ${totalRowsRead.toLocaleString()} rows read | ${totalRowsWritten.toLocaleString()} rows written`);
    console.log(`============================================\n`);
}

main().catch(console.error);
