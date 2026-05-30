#!/usr/bin/env node
/**
 * Populate Turso charts table from iTunes RSS
 * Supports running for a specific country via --country flag
 */

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN environment variables");
    process.exit(1);
}

const GENRE_MAP = {
    "News": "1489",
    "Technology": "1318",
    "Business": "1321",
    "Comedy": "1303",
    "True Crime": "1488",
    "Sports": "1545",
    "Health": "1512",
    "History": "1487",
    "Arts": "1301",
    "Society & Culture": "1324",
    "Education": "1304",
    "Science": "1533",
    "TV & Film": "1309",
    "Fiction": "1483",
    "Music": "1310",
    "Religion & Spirituality": "1314",
    "Kids & Family": "1305",
    "Leisure": "1502",
    "Government": "1511"
};

const DEFAULT_COUNTRIES = ["us", "in", "gb", "fr"];
const CATEGORIES = ["all", "News", "Technology", "Business", "Comedy", "True Crime", "Sports", "Health", "History", "Arts", "Society & Culture", "Education", "Science", "TV & Film", "Fiction", "Music", "Religion & Spirituality", "Kids & Family", "Leisure", "Government"];

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

async function fetchItunesCharts(country, category) {
    const genreParam = category === "all" ? "" : `/genre=${GENRE_MAP[category] || ""}`;
    const url = `https://itunes.apple.com/${country}/rss/toppodcasts${genreParam}/limit=200/json`;

    try {
        const response = await fetch(url);
        if (!response.ok) {
            console.error(`[CHARTS] iTunes fetch failed: ${response.status} for ${country}/${category}`);
            return [];
        }

        const data = await response.json();
        const entries = data.feed?.entry || [];

        return entries.map(entry => ({
            itunesId: entry.id?.attributes?.["im:id"] || "",
            name: entry["im:name"]?.label || "",
            artist: entry["im:artist"]?.label || "",
            imageUrl: entry["im:image"]?.[2]?.label || entry["im:image"]?.[1]?.label || ""
        })).filter(p => p.itunesId);
    } catch (err) {
        console.error(`[CHARTS] Failed to fetch iTunes charts for ${country}/${category}:`, err.message);
        return [];
    }
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
                stmt: { 
                    sql, 
                    args: args.map(mapArgType) 
                }
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
    console.log("[CHARTS] Starting charts population...");

    // === DIAGNOSTIC FAILSAFE LOGGING ===
    console.log("[DIAGNOSTIC] Verifying GHA Environment Configuration...");
    console.log(`  - TURSO_URL:     ${TURSO_URL ? 'PRESENT' : 'MISSING'}`);
    console.log(`  - TURSO_TOKEN:   ${TURSO_TOKEN ? 'PRESENT' : 'MISSING'}`);

    console.log("[DIAGNOSTIC] Testing Turso Database Connection & Health...");
    try {
        const dbRes = await executeSQL("SELECT 1");
        if (dbRes && dbRes.results && dbRes.results[0] && dbRes.results[0].type === "ok") {
            console.log(`[STATUS] Turso database connection SUCCESSFUL!`);
        } else {
            throw new Error("Database query returned an unexpected response.");
        }
    } catch (e) {
        console.error(`[CRITICAL] Turso database connection check FAILED: ${e.message}`);
        console.error("  -> Please verify TURSO_URL and TURSO_AUTH_TOKEN secrets in GitHub Repository Secrets!");
        process.exit(1);
    }
    // ===================================

    const countryIndex = process.argv.indexOf('--country');
    const targetCountry = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    const countriesToProcess = targetCountry ? [targetCountry] : DEFAULT_COUNTRIES;
    console.log(`[CHARTS] Countries to process: ${countriesToProcess.join(', ')}`);

    for (const country of countriesToProcess) {
        for (const category of CATEGORIES) {
            try {
                const podcasts = await fetchItunesCharts(country, category);
                console.log(`[CHARTS] Fetched ${podcasts.length} podcasts for ${country}/${category}`);

                if (podcasts.length === 0) continue;

                // Delete old entries
                await executeSQL(
                    "DELETE FROM charts WHERE country = ? AND category = ?",
                    [country, category]
                );

                // Insert new entries in batch to maximize speed
                const statements = [];
                for (let i = 0; i < podcasts.length; i++) {
                    const p = podcasts[i];
                    statements.push({
                        sql: "INSERT INTO charts (itunes_id, name, artist, image_url, country, category, rank) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        args: [p.itunesId, p.name, p.artist, p.imageUrl, country, category, i + 1]
                    });
                }

                if (statements.length > 0) {
                    // Custom executeBatch inlined here for simplicity
                    const requests = statements.map(stmt => ({
                        type: "execute",
                        stmt: { 
                            sql: stmt.sql, 
                            args: stmt.args.map(mapArgType) 
                        }
                    }));
                    requests.push({ type: "close" });

                    const batchResponse = await fetch(`${TURSO_URL}/v2/pipeline`, {
                        method: "POST",
                        headers: {
                            "Authorization": `Bearer ${TURSO_TOKEN}`,
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({ requests })
                    });
                    
                    if (!batchResponse.ok) {
                        throw new Error(`Turso batch HTTP error: ${batchResponse.status}`);
                    }
                    const batchResult = await batchResponse.json();
                    if (batchResult.results) {
                        for (const result of batchResult.results) {
                            if (result.type === "error") {
                                throw new Error(`Turso SQL batch error: ${result.error.message}`);
                            }
                        }
                    }
                }

                console.log(`[CHARTS] Successfully inserted ${podcasts.length} charts for ${country}/${category}`);
            } catch (e) {
                console.error(`[CHARTS] Error processing ${country}/${category}:`, e.message);
            }
        }
    }

    console.log("[CHARTS] Chart population complete!");
}

main().catch(console.error);
