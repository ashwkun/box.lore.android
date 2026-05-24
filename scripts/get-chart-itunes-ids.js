#!/usr/bin/env node
/**
 * Get iTunes IDs from charts table in Turso
 * Outputs one ID per line for use with sqlite3 .import
 */

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
}

async function main() {
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    let sql = "SELECT DISTINCT itunes_id FROM charts";
    let args = [];

    if (country) {
        sql = "SELECT DISTINCT itunes_id FROM charts WHERE country = ?";
        args = [{ type: "text", value: country }];
        console.warn(`[CHARTS] Filtering charts for country: ${country}`);
    }

    console.warn(`[CHARTS] Fetching unique iTunes IDs from charts table in Turso...`);
    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            requests: [{
                type: "execute",
                stmt: { sql, args }
            }, { type: "close" }]
        })
    });

    const result = await response.json();
    const rows = result?.results?.[0]?.response?.result?.rows || [];
    console.warn(`[CHARTS] Received ${rows.length} total rows from Turso DB.`);

    // Output one iTunes ID per line
    let validCount = 0;
    for (const row of rows) {
        const itunesId = row[0]?.value;
        if (itunesId) {
            console.log(itunesId);
            validCount++;
        }
    }
    console.warn(`[CHARTS] Successfully wrote ${validCount} valid iTunes IDs to stdout.`);
}

main().catch(err => {
    console.error("Failed:", err);
    process.exit(1);
});
