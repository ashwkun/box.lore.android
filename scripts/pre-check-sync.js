#!/usr/bin/env node
/**
 * Pre-check sync candidates to decide whether to skip the large 7 GB PI dump download
 * Outputs result to GitHub Actions step outputs
 */

const fs = require('fs');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
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

async function main() {
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    let sql = "SELECT DISTINCT itunes_id FROM charts";
    let args = [];
    if (country) {
        sql = "SELECT DISTINCT itunes_id FROM charts WHERE country = ?";
        args = [country];
    }

    // 1. Fetch active chart iTunes IDs
    const chartsRes = await executeSQL(sql, args);
    const chartIds = chartsRes?.results?.[0]?.response?.result?.rows?.map(r => r[0].value).filter(Boolean) || [];

    // 2. Fetch existing iTunes IDs
    const podcastsRes = await executeSQL("SELECT DISTINCT itunes_id FROM podcasts WHERE itunes_id IS NOT NULL;");
    const existingIds = new Set(podcastsRes?.results?.[0]?.response?.result?.rows?.map(r => r[0].value).filter(Boolean).map(String));

    // 3. Calculate missing count
    const missingIds = chartIds.filter(id => !existingIds.has(String(id)));

    console.log(`[PRE-CHECK] Country: ${country || 'all'}`);
    console.log(`[PRE-CHECK] Total Unique Chart Podcasts: ${chartIds.length}`);
    console.log(`[PRE-CHECK] Missing Podcasts in Turso:    ${missingIds.length}`);

    const skipDownload = missingIds.length <= 100;
    console.log(`[PRE-CHECK] Decision: skip_download = ${skipDownload}`);

    // Set GitHub Actions step output if running in a runner
    if (process.env.GITHUB_OUTPUT) {
        fs.appendFileSync(process.env.GITHUB_OUTPUT, `skip_download=${skipDownload}\n`);
        fs.appendFileSync(process.env.GITHUB_OUTPUT, `missing_count=${missingIds.length}\n`);
    }
}

main().catch(err => {
    console.error("Pre-check failed:", err);
    process.exit(1);
});
