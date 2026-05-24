#!/usr/bin/env node
/**
 * Import podcasts and episodes from CSV files to Turso
 * Automatically adapts DB schema to match CSV columns
 */

const fs = require('fs');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
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

async function executeBatch(statements) {
    if (statements.length === 0) return;

    const requests = statements.map(stmt => ({
        type: "execute",
        stmt: { sql: stmt.sql, args: stmt.args.map(a => ({ type: a === null ? "null" : "text", value: a === null ? null : String(a || "") })) }
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

async function getTableColumns(tableName) {
    const res = await executeSQL(`PRAGMA table_info(${tableName})`);
    const rows = res?.results?.[0]?.response?.result?.rows || [];
    return rows.map(r => r[1]?.value);
}

async function ensureColumns(tableName, csvHeaders) {
    const existingColumns = await getTableColumns(tableName);
    console.log(`Current columns in ${tableName}:`, existingColumns.join(', '));

    for (const header of csvHeaders) {
        // Map CSV header to DB column name (simple snake_case conversion if needed, 
        // but our workflow exports mostly match DB style or snake_case already)
        // The export query aliases are: id, itunes_id, title, author, description, image_url, feed_url...
        // We assume the CSV header IS the column name we want.

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

    // Clear existing - DISABLED to preserve last_ep_sync history
    // console.log(`Clearing ${tableName}...`);
    // await executeSQL(`DELETE FROM ${tableName}`, []);

    const dataLines = lines.slice(1);
    console.log(`Importing ${dataLines.length} rows into ${tableName}...`);

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
            
            console.log(`[IMPORT] [${rowSeq}/${dataLines.length}] Staging row for ${tableName}: id=${id} ("${title}") | Action: ${sql.includes('ON CONFLICT') ? 'UPSERT (SET medium)' : 'INSERT OR IGNORE'}`);
            if (reordered) {
                console.log(`[IMPORT]   -> Reordered categories for podcast ${id}: "${originalCats}" -> "${reorderedCats}"`);
            }

            statements.push({
                sql,
                args: values
            });
        });

        if (statements.length > 0) {
            await executeBatch(statements);
            const prevImported = imported;
            imported += statements.length;
            console.log(`[IMPORT] Successfully executed batch. Total imported so far: ${imported}/${dataLines.length}`);
        }
    }

    console.log(`Done: ${imported} rows imported into ${tableName}`);
    return imported;
}

async function main() {
    console.log("Starting PI data import...");

    await importTable('podcasts_export.csv', 'podcasts');

    // Import episodes, limiting to 200 per podcast_id
    // Episodes are now synced via API (scripts/sync-episodes.js) because public dump lacks them
    // await importTable('episodes_export.csv', 'episodes', 'podcast_id', 200);

    console.log("\nImport complete!");
}

main().catch(err => {
    console.error("Import failed:", err);
    process.exit(1);
});
