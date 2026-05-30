#!/usr/bin/env node
/**
 * Import podcasts and episodes from CSV files or Podcast Index API to Turso
 * Automatically adapts DB schema to match CSV columns
 */

const fs = require('fs');
const crypto = require('crypto');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const API_KEY = process.env.PODCAST_INDEX_API_KEY;
const API_SECRET = process.env.PODCAST_INDEX_API_SECRET;
const API_BASE = "https://api.podcastindex.org/api/1.0";

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
}

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

async function fetchPodcastByItunesId(itunesId) {
    try {
        const headers = generateAuthHeaders();
        const res = await fetch(`${API_BASE}/podcasts/byitunesid?id=${itunesId}`, { headers });
        if (!res.ok) throw new Error(`API error: ${res.status}`);
        const data = await res.json();
        return data.feed || null;
    } catch (e) {
        console.error(`Failed to fetch podcast by iTunes ID ${itunesId}:`, e.message);
        return null;
    }
}

function parseCSVLine(line) {
    const result = [];
    let current = '';
    let inQuotes = false;

    for (let i = 0; i < line.length; i++) {
        const char = line[i];
        if (char === '"') {
            if (inQuotes && line[i + 1] === '"') {
                current += '"';
                i++;
            } else {
                inQuotes = !inQuotes;
            }
        } else if (char === ',' && !inQuotes) {
            result.push(current);
            current = '';
        } else {
            current += char;
        }
    }
    result.push(current);
    return result;
}

// Map JavaScript values dynamically to Turso API types to prevent datatype mismatch
function mapArgType(val) {
    if (val === null || val === undefined || val === "") {
        return { type: "null", value: null };
    }
    
    // Check if it represents an integer or is a number
    if (typeof val === 'number') {
        return { type: "integer", value: String(val) };
    }
    if (typeof val === 'string' && /^\d+$/.test(val)) {
        return { type: "integer", value: val };
    }
    
    return { type: "text", value: String(val) };
}

async function executeBatch(statements) {
    if (statements.length === 0) return;

    const requests = statements.map(stmt => ({
        type: "execute",
        stmt: { 
            sql: stmt.sql, 
            args: stmt.args.map(mapArgType)
        }
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

    if (!response.ok) {
        throw new Error(`Turso HTTP error: ${response.status}`);
    }
    const res = await response.json();
    if (res.results) {
        for (const result of res.results) {
            if (result.type === "error") {
                throw new Error(`Turso SQL batch error: ${result.error.message}`);
            }
        }
    }
    return res;
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

async function getTableColumns(tableName) {
    const res = await executeSQL(`PRAGMA table_info(${tableName})`);
    const rows = res?.results?.[0]?.response?.result?.rows || [];
    return rows.map(r => r[1]?.value);
}

async function ensureColumns(tableName, csvHeaders) {
    const existingColumns = await getTableColumns(tableName);
    console.log(`Current columns in ${tableName}:`, existingColumns.join(', '));

    for (const header of csvHeaders) {
        if (!existingColumns.includes(header)) {
            console.log(`Adding missing column '${header}' to ${tableName}...`);
            try {
                await executeSQL(`ALTER TABLE ${tableName} ADD COLUMN ${header} TEXT`);
            } catch (e) {
                console.error(`Failed to add column ${header}:`, e);
            }
        }
    }
}

async function importTable(filename, tableName, limitPerGroupCol = null, limitCount = 0) {
    if (!fs.existsSync(filename)) {
        console.error(`File ${filename} not found`);
        return 0;
    }

    const content = fs.readFileSync(filename, 'utf-8');
    const lines = content.split('\n').filter(l => l.trim());

    if (lines.length < 2) return 0;

    const headers = parseCSVLine(lines[0]);
    console.log(`Headers for ${tableName}:`, headers.join(', '));

    // Ensure schema matches
    await ensureColumns(tableName, headers);

    const dataLines = lines.slice(1);
    console.log(`[IMPORT] Importing ${dataLines.length} rows into ${tableName}...`);

    const BATCH_SIZE = 50;
    let imported = 0;
    const groupCounts = {};

    for (let i = 0; i < dataLines.length; i += BATCH_SIZE) {
        const batch = dataLines.slice(i, i + BATCH_SIZE);
        const statements = [];

        batch.forEach((line, batchIndex) => {
            const values = parseCSVLine(line);
            const rowSeq = i + batchIndex + 1;

            const idIndex = headers.indexOf('id');
            const titleIndex = headers.indexOf('title');
            const id = idIndex !== -1 ? values[idIndex] : "unknown";
            const title = titleIndex !== -1 ? values[titleIndex] : "Unknown Title";

            let reordered = false;
            let originalCats = "";
            let reorderedCats = "";

            if (tableName === 'podcasts' && headers.includes('categories')) {
                const catIndex = headers.indexOf('categories');
                const rawCats = values[catIndex];
                if (rawCats) {
                    const GENRE_PRIORITY = [
                        "Technology", "News", "Business", "Science", "Sports", "True Crime",
                        "History", "Comedy", "Arts", "Fiction", "Music", "Religion & Spirituality",
                        "Kids & Family", "Government", "Health", "TV & Film", "Education"
                    ];

                    const sortedCats = rawCats.split(',')
                        .map(c => c.trim())
                        .filter(c => c)
                        .sort((a, b) => {
                            const idxA = GENRE_PRIORITY.indexOf(a);
                            const idxB = GENRE_PRIORITY.indexOf(b);
                            if (idxA !== -1 && idxB !== -1) return idxA - idxB;
                            if (idxA !== -1) return -1;
                            if (idxB !== -1) return 1;
                            return a.localeCompare(b);
                        })
                        .join(', ');

                    if (sortedCats !== rawCats) {
                        reordered = true;
                        originalCats = rawCats;
                        reorderedCats = sortedCats;
                    }
                    values[catIndex] = sortedCats;
                }
            }

            // Limit logic (e.g. 200 episodes per podcast)
            if (limitPerGroupCol && limitCount > 0) {
                const groupVal = values[headers.indexOf(limitPerGroupCol)];
                groupCounts[groupVal] = (groupCounts[groupVal] || 0) + 1;
                if (groupCounts[groupVal] > limitCount) return;
            }

            const placeholders = values.map(() => '?').join(',');
            let sql = `INSERT OR IGNORE INTO ${tableName} (${headers.join(',')}) VALUES (${placeholders})`;
            if (tableName === 'podcasts' && headers.includes('id') && headers.includes('medium')) {
                sql = `INSERT INTO ${tableName} (${headers.join(',')}) VALUES (${placeholders}) ON CONFLICT(id) DO UPDATE SET medium = excluded.medium`;
            }

            // Log progress in blocks of 1000 rows instead of every single line
            if (rowSeq % 1000 === 0 || rowSeq === dataLines.length) {
                console.log(`[IMPORT] Staging rows: ${rowSeq}/${dataLines.length} (${Math.round((rowSeq / dataLines.length) * 100)}%) staged...`);
            }

            statements.push({
                sql,
                args: values
            });
        });

        if (statements.length > 0) {
            await executeBatch(statements);
            imported += statements.length;
        }
    }

    console.log(`[IMPORT] Done: Successfully imported ${imported}/${dataLines.length} rows into ${tableName}.`);
    return imported;
}

async function main() {
    console.log("Starting PI data import...");

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
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : null;

    const csvExists = fs.existsSync('podcasts_export.csv');
    if (csvExists) {
        console.log("[IMPORT] Found 'podcasts_export.csv'. Proceeding with Bulk DB Dump Import...");
        await importTable('podcasts_export.csv', 'podcasts');
    } else {
        console.log("[IMPORT] 'podcasts_export.csv' NOT found. Proceeding with Incremental API-based Import...");
        if (!API_KEY || !API_SECRET) {
            console.error("[IMPORT] ERROR: Missing PODCAST_INDEX_API_KEY or PODCAST_INDEX_API_SECRET for API-based import.");
            process.exit(1);
        }

        // 1. Fetch active chart iTunes IDs from Turso (filtering by country matrix if passed)
        console.log(`[IMPORT] Fetching active iTunes IDs from charts table${country ? ` for country: ${country}` : ''}...`);
        let chartsSql = "SELECT DISTINCT itunes_id FROM charts";
        let chartsArgs = [];
        if (country) {
            chartsSql = "SELECT DISTINCT itunes_id FROM charts WHERE country = ?";
            chartsArgs = [country];
        }
        
        const chartsRes = await executeSQL(chartsSql, chartsArgs);
        const chartIds = chartsRes?.results?.[0]?.response?.result?.rows?.map(r => r[0].value).filter(Boolean) || [];
        console.log(`[IMPORT] Found ${chartIds.length} unique iTunes IDs in the charts table.`);

        // 2. Fetch already imported iTunes IDs from podcasts
        const podcastsRes = await executeSQL("SELECT DISTINCT itunes_id FROM podcasts WHERE itunes_id IS NOT NULL;");
        const existingIds = new Set(podcastsRes?.results?.[0]?.response?.result?.rows?.map(r => r[0].value).filter(Boolean).map(String));
        console.log(`[IMPORT] Found ${existingIds.size} unique iTunes IDs already in the podcasts table.`);

        // 3. Find missing iTunes IDs
        const missingIds = chartIds.filter(id => !existingIds.has(String(id)));
        console.log(`[IMPORT] Identified ${missingIds.length} missing chart podcasts.`);

        if (missingIds.length === 0) {
            console.log("[IMPORT] All chart podcasts are already present in the database. Nothing to import!");
            return;
        }

        // Safety limit: if too many are missing, warn the user
        if (missingIds.length > 200) {
            console.warn(`[IMPORT] WARNING: ${missingIds.length} podcasts are missing. Incremental sync is capped at 200 to prevent API rate limits. Please use the Bulk DB Dump path for full bootstrapping.`);
        }

        // 4. Fetch and insert metadata from Podcast Index API
        let importedCount = 0;
        const toImport = missingIds.slice(0, 200); // safety cap
        console.log(`[IMPORT] Fetching metadata for ${toImport.length} shows directly via Podcast Index API...`);

        const batchStatements = [];
        const BATCH_SIZE = 50;

        for (let idx = 0; idx < toImport.length; idx++) {
            const itunesId = toImport[idx];
            
            // Periodically log progress instead of spamming every call details
            if ((idx + 1) % 10 === 0 || (idx + 1) === toImport.length) {
                console.log(`[IMPORT] Fetching: ${idx + 1}/${toImport.length} (${Math.round(((idx + 1) / toImport.length) * 100)}%) completed...`);
            }

            const feed = await fetchPodcastByItunesId(itunesId);
            
            // Robust check: skip empty/null arrays returned by the API when not found
            if (!feed || (Array.isArray(feed) && feed.length === 0) || !feed.id) {
                continue;
            }

            // Extract categories
            let categoriesStr = "";
            if (feed.categories) {
                categoriesStr = Object.values(feed.categories).join(', ');
            }

            // Map feed values to Turso podcasts table structure
            const columns = [
                'id', 'itunes_id', 'title', 'author', 'description', 'image_url', 
                'feed_url', 'website_url', 'categories', 'language', 'explicit', 'type'
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
                feed.itunesType || "episodic"
            ];

            const sql = `INSERT OR IGNORE INTO podcasts (${columns.join(',')}) VALUES (${placeholders})`;
            batchStatements.push({ sql, args: values });
            importedCount++;

            if (batchStatements.length >= BATCH_SIZE) {
                console.log(`[IMPORT] Flushing batch of ${batchStatements.length} podcast inserts to Turso DB...`);
                try {
                    await executeBatch(batchStatements);
                } catch (err) {
                    console.error(`[IMPORT] Error executing podcast inserts batch:`, err.message);
                }
                batchStatements.length = 0;
            }

            // Polite delay (100ms) to stay fully within API rate limits
            await new Promise(r => setTimeout(r, 100));
        }

        // Flush remaining inserts
        if (batchStatements.length > 0) {
            console.log(`[IMPORT] Flushing final batch of ${batchStatements.length} podcast inserts to Turso DB...`);
            try {
                await executeBatch(batchStatements);
            } catch (err) {
                console.error(`[IMPORT] Error executing final podcast inserts batch:`, err.message);
            }
        }

        console.log(`[IMPORT] Incremental sync complete! Successfully imported ${importedCount}/${toImport.length} missing podcasts.`);
    }

    console.log("\nImport complete!");
}

main().catch(err => {
    console.error("Import failed:", err);
    process.exit(1);
});
