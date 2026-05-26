#!/usr/bin/env node

/**
 * Generate vector embeddings for episodes
 * Uses CPU-based transformers.js (bge-large-en-v1.5, 1024-dim)
 * Designed to run in GitHub Actions
 */

const { pipeline } = require('@xenova/transformers'); // Use generic import or dynamic import if needed
// const { pipeline } = await import('@xenova/transformers'); // ESM in CJS workaround if needed

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
}

// Configuration
const MODEL_NAME = 'Xenova/bge-large-en-v1.5';
const BATCH_SIZE = 10;
const LIMIT = 2000; // Max items per run to fit within timeout

/**
 * Clean episode description for better vectorization quality.
 * Removes HTML, URLs, sponsor blocks, emails, timestamps, social handles, and boilerplate.
 * (Mirror of the same function in sync-episodes.js)
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

    // 7. Remove sponsor/ad blocks
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

    // 10. Truncate to 1000 chars
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

async function executeVectorBatchUpdate(updates) {
    if (updates.length === 0) return;

    const requests = updates.map(u => {
        const vectorString = '[' + Array.from(u.embedding).join(',') + ']';
        return {
            type: "execute",
            stmt: {
                sql: `UPDATE episodes SET vector = vector(?) WHERE id = ?`,
                args: [
                    { type: "text", value: vectorString },
                    { type: "integer", value: String(u.id) }
                ]
            }
        };
    });

    requests.push({ type: "close" });

    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ requests })
    });

    if (!response.ok) {
        throw new Error(`Batch update failed: ${response.status}`);
    }
    const res = await response.json();
    if (res.results) {
        for (let i = 0; i < res.results.length - 1; i++) {
            if (res.results[i].type === "error") {
                console.error(`Batch item ${i} failed:`, res.results[i].error.message);
            }
        }
    }
    return res;
}

async function executePodcastVectorBatchUpdate(updates) {
    if (updates.length === 0) return;

    const requests = updates.map(u => {
        const vectorString = '[' + Array.from(u.embedding).join(',') + ']';
        return {
            type: "execute",
            stmt: {
                sql: `UPDATE podcasts SET vector = vector(?) WHERE id = ?`,
                args: [
                    { type: "text", value: vectorString },
                    { type: "integer", value: String(u.id) }
                ]
            }
        };
    });

    requests.push({ type: "close" });

    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ requests })
    });

    if (!response.ok) {
        throw new Error(`Podcast batch update failed: ${response.status}`);
    }
    return response.json();
}

async function main() {
    console.log("Starting Vectorization...");

    // Dynamic import for transformers (ESM)
    const { pipeline } = await import('@xenova/transformers');

    // === PHASE 1: Episode Vectorization (1024-dim, bge-large) ===
    console.log(`\n=== Phase 1: Episode Vectorization (Model: ${MODEL_NAME}) ===`);
    const extractor = await pipeline('feature-extraction', MODEL_NAME);

    // 2. Fetch Candidates
    // Prioritize items with latest episodes (richer context)
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    let sql;
    let args = [];

    if (country) {
        sql = `
            SELECT DISTINCT e.id, e.title, e.description, p.title as podcast_title 
            FROM charts c
            JOIN podcasts p ON c.itunes_id = p.itunes_id
            JOIN episodes e ON e.podcast_id = p.id
            WHERE e.vector IS NULL AND c.country = ?
            LIMIT ${LIMIT}
        `;
        args = [country];
        console.log(`Filtering vectorization candidates for country: ${country}`);
    } else {
        sql = `
            SELECT e.id, e.title, e.description, p.title as podcast_title 
            FROM episodes e
            JOIN podcasts p ON e.podcast_id = p.id
            WHERE e.vector IS NULL 
            LIMIT ${LIMIT}
        `;
    }

    console.log("Fetching candidates...");
    const res = await executeSQL(sql, args);
    const rows = res?.results?.[0]?.response?.result?.rows || [];

    if (rows.length === 0) {
        console.log("No episodes need vectorization.");
    } else {
        console.log(`Found ${rows.length} episodes to vectorize.`);

        let success = 0;
        let errors = 0;
        const startTime = Date.now();

        console.log("\n=== Vectorization Plan ===");
        console.log(`Model:      ${MODEL_NAME}`);
        console.log(`Candidates: ${rows.length}`);
        console.log(`==========================\n`);

        console.log("Processing episodes...");

        const batch = [];
        const BATCH_SIZE = 50;

        for (let i = 0; i < rows.length; i++) {
            const row = rows[i];
            const id = row[0].value;
            const title = row[1].value || "";
            const desc = row[2].value || "";
            const podcastTitle = row[3].value || "";

            console.log(`[VECTOR] [${i+1}/${rows.length}] Starting vectorization for episode ${id} ("${title}") | Podcast: "${podcastTitle}"`);

            // Construct Text — clean description for better semantic signal
            const cleanedDesc = cleanDescription(desc);
            const text = `Episode: ${title}. ${cleanedDesc}. Podcast: ${podcastTitle}`
                .replace(/[\n\r]+/g, ' ')
                .substring(0, 1000);

            try {
                const output = await extractor(text, { pooling: 'mean', normalize: true });
                const embedding = output.data;

                batch.push({ id, embedding });
                console.log(`[VECTOR] [${i+1}/${rows.length}] Added to batch for episode ${id} ("${title}")`);

                if (batch.length >= BATCH_SIZE) {
                    console.log(`[VECTOR] Flushing batch of ${batch.length} vector updates to DB...`);
                    await executeVectorBatchUpdate(batch);
                    success += batch.length;
                    batch.length = 0;
                }
            } catch (e) {
                console.error(`[VECTOR] [${i+1}/${rows.length}] Failed to process/vectorize ${id} ("${title}"): ${e.message}`);
                errors++;
            }
        }

        // Flush any remaining items in the batch
        if (batch.length > 0) {
            console.log(`[VECTOR] Flushing final batch of ${batch.length} vector updates to DB...`);
            try {
                await executeVectorBatchUpdate(batch);
                success += batch.length;
            } catch (e) {
                console.error(`[VECTOR] Failed to flush final batch: ${e.message}`);
                errors += batch.length;
            }
        }

        const totalElapsed = ((Date.now() - startTime) / 1000).toFixed(1);
        console.log(`\n=== Episode Vectorization Complete ===`);
        console.log(`Success:  ${success}`);
        console.log(`Errors:   ${errors}`);
        console.log(`Duration: ${totalElapsed}s`);
        console.log(`======================================\n`);
    }

    // === PHASE 2: Curation Podcast Vectorization (384-dim, bge-small) ===
    console.log("\n=== Phase 2: Starting Curation Podcast Vectorization ===");
    console.log("Loading model Xenova/bge-small-en-v1.5...");
    const podcastExtractor = await pipeline('feature-extraction', 'Xenova/bge-small-en-v1.5');

    let podcastsSql;
    let podcastsArgs = [];

    if (country) {
        podcastsSql = `
            SELECT DISTINCT p.id, p.title, p.description, p.latest_ep_title, p.latest_ep_description 
            FROM charts c
            JOIN podcasts p ON c.itunes_id = p.itunes_id
            WHERE p.vector IS NULL AND c.country = ?
            LIMIT 500
        `;
        podcastsArgs = [country];
    } else {
        podcastsSql = `
            SELECT id, title, description, latest_ep_title, latest_ep_description 
            FROM podcasts 
            WHERE vector IS NULL 
            LIMIT 500
        `;
    }

    console.log("Fetching podcasts needing curation vectors...");
    const podcastsRes = await executeSQL(podcastsSql, podcastsArgs);
    const podcastRows = podcastsRes?.results?.[0]?.response?.result?.rows || [];

    if (podcastRows.length === 0) {
        console.log("No podcasts need curation vectorization.");
    } else {
        console.log(`Found ${podcastRows.length} podcasts to vectorize.`);
        let podcastSuccess = 0;
        let podcastErrors = 0;
        const podcastStartTime = Date.now();

        const pBatch = [];
        const BATCH_SIZE = 50;

        for (let i = 0; i < podcastRows.length; i++) {
            const row = podcastRows[i];
            const pId = row[0].value;
            const pTitle = row[1].value || "";
            const pDesc = row[2].value || "";
            const epTitle = row[3].value || "";
            const epDesc = row[4].value || "";

            console.log(`[PODCAST-VECTOR] [${i+1}/${podcastRows.length}] Vectorizing podcast ${pId} ("${pTitle}")`);

            const cleanPDesc = cleanDescription(pDesc);
            const cleanEpDesc = cleanDescription(epDesc);
            const text = `Podcast: ${pTitle}. ${cleanPDesc}. Latest Episode: ${epTitle}. ${cleanEpDesc}`
                .replace(/[\n\r]+/g, ' ')
                .substring(0, 1000);

            try {
                const output = await podcastExtractor(text, { pooling: 'mean', normalize: true });
                const embedding = output.data;

                pBatch.push({ id: pId, embedding });

                if (pBatch.length >= BATCH_SIZE) {
                    console.log(`[PODCAST-VECTOR] Flushing batch of ${pBatch.length} podcast vectors to DB...`);
                    await executePodcastVectorBatchUpdate(pBatch);
                    podcastSuccess += pBatch.length;
                    pBatch.length = 0;
                }
            } catch (e) {
                console.error(`[PODCAST-VECTOR] [${i+1}/${podcastRows.length}] Failed to process/vectorize podcast ${pId}: ${e.message}`);
                podcastErrors++;
            }
        }

        if (pBatch.length > 0) {
            console.log(`[PODCAST-VECTOR] Flushing final batch of ${pBatch.length} podcast vectors to DB...`);
            try {
                await executePodcastVectorBatchUpdate(pBatch);
                podcastSuccess += pBatch.length;
            } catch (e) {
                console.error(`[PODCAST-VECTOR] Failed to flush final podcast batch: ${e.message}`);
                podcastErrors += pBatch.length;
            }
        }

        const podcastElapsed = ((Date.now() - podcastStartTime) / 1000).toFixed(1);
        console.log(`\n=== Podcast Vectorization Complete ===`);
        console.log(`Success:  ${podcastSuccess}`);
        console.log(`Errors:   ${podcastErrors}`);
        console.log(`Duration: ${podcastElapsed}s`);
        console.log(`======================================\n`);
    }
}

main().catch(console.error);
