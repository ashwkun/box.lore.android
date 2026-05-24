#!/usr/bin/env node

/**
 * Generate vector embeddings for podcasts
 * Uses CPU-based transformers.js (all-MiniLM-L6-v2)
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
const MODEL_NAME = 'Xenova/bge-small-en-v1.5';
const BATCH_SIZE = 10;
const LIMIT = 2000; // Max items per run to fit within timeout

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

async function executeVectorUpdate(id, embedding) {
    // embedding is Float32Array or array
    // Format: '[0.1, 0.2, ...]'
    const vectorString = '[' + Array.from(embedding).join(',') + ']';

    // Check if Turso supports vector() function or direct array import
    // Standard libSQL vector support uses `vector32('...')` or just the raw bytes if using client
    // Here we use the vector extension syntax if available: vector('[...]')
    // OR default to raw blob if using official client.

    // Let's assume standard `vector('[...]')` syntax for SQL injection

    const sql = `UPDATE podcasts SET vector = vector(?) WHERE id = ?`;

    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            requests: [{
                type: "execute",
                stmt: {
                    sql: sql,
                    args: [
                        { type: "text", value: vectorString },
                        { type: "text", value: String(id) }
                    ]
                }
            }, { type: "close" }]
        })
    });

    if (!response.ok) {
        console.error(`Update failed: ${response.status}`);
        // console.error(await response.text());
    }
    return response.json();
}

async function main() {
    console.log("Starting Vectorization...");

    // Dynamic import for transformers (ESM)
    const { pipeline } = await import('@xenova/transformers');

    // 1. Load Model
    console.log(`Loading model ${MODEL_NAME}...`);
    const extractor = await pipeline('feature-extraction', MODEL_NAME);

    // 2. Fetch Candidates
    // Prioritize items with latest episodes (richer context)
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    let sql;
    let args = [];

    if (country) {
        sql = `
            SELECT DISTINCT p.id, p.title, p.description, p.latest_ep_title, p.latest_ep_description 
            FROM charts c
            JOIN podcasts p ON c.itunes_id = p.itunes_id
            WHERE p.vector IS NULL AND c.country = ?
            LIMIT ${LIMIT}
        `;
        args = [country];
        console.log(`Filtering vectorization candidates for country: ${country}`);
    } else {
        sql = `
            SELECT id, title, description, latest_ep_title, latest_ep_description 
            FROM podcasts 
            WHERE vector IS NULL 
            LIMIT ${LIMIT}
        `;
    }

    console.log("Fetching candidates...");
    const res = await executeSQL(sql, args);
    const rows = res?.results?.[0]?.response?.result?.rows || [];

    if (rows.length === 0) {
        console.log("No podcasts need vectorization.");
        return;
    }

    console.log(`Found ${rows.length} podcasts to vectorize.`);

    // 3. Process
    let success = 0;
    let errors = 0;
    const startTime = Date.now();

    console.log("\n=== Vectorization Plan ===");
    console.log(`Model:      ${MODEL_NAME}`);
    console.log(`Candidates: ${rows.length}`);
    console.log(`==========================\n`);

    console.log("Processing podcasts...");

    for (let i = 0; i < rows.length; i++) {
        const row = rows[i];
        const id = row[0].value;
        const title = row[1].value || "";
        const desc = row[2].value || "";
        const epTitle = row[3].value || "";
        const epDesc = row[4].value || "";

        console.log(`[VECTOR] [${i+1}/${rows.length}] Starting vectorization for podcast ${id} ("${title}") | Reason: Missing embedding (new or updated feed)`);

        // Construct Text
        const text = `Podcast: ${title}. ${desc}. Latest Episode: ${epTitle}. ${epDesc}`
            .replace(/[\n\r]+/g, ' ')
            .substring(0, 1000);

        try {
            const output = await extractor(text, { pooling: 'mean', normalize: true });
            const embedding = output.data;

            await executeVectorUpdate(id, embedding);
            success++;
            console.log(`[VECTOR] [${i+1}/${rows.length}] Successfully vectorized podcast ${id} ("${title}")`);
        } catch (e) {
            console.error(`[VECTOR] [${i+1}/${rows.length}] Failed to vectorize ${id} ("${title}"): ${e.message}`);
            errors++;
        }
    }

    const totalElapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`\n=== Vectorization Complete ===`);
    console.log(`Success:  ${success}`);
    console.log(`Errors:   ${errors}`);
    console.log(`Duration: ${totalElapsed}s`);
    console.log(`==============================\n`);
}

main().catch(console.error);
