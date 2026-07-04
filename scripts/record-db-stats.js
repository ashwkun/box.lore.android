#!/usr/bin/env node
/**
 * Record database read/write counts for GHA runs
 * Maintains a 30-day sliding window of history in data/db_cost_history.json
 * Auto-generates a human-readable Markdown table in data/db_cost_report.md
 */

const fs = require('fs');
const path = require('path');

const STATS_FILE = '/tmp/db_run_stats.json';
const HISTORY_FILE = 'data/db_cost_history.json';
const REPORT_FILE = 'data/db_cost_report.md';

function ensureDirectoryExists(filePath) {
    const dir = path.dirname(filePath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
}

async function main() {
    const countryIndex = process.argv.indexOf('--country');
    const country = countryIndex !== -1 ? process.argv[countryIndex + 1] : 'global';

    console.log(`[STATS] Recording DB costs for country: ${country}...`);

    // 1. Read current run stats from temp file
    if (!fs.existsSync(STATS_FILE)) {
        console.warn("[STATS] No temp stats file found at /tmp/db_run_stats.json. Skipping stats recording.");
        return;
    }

    let runStats = {};
    try {
        runStats = JSON.parse(fs.readFileSync(STATS_FILE, 'utf8') || '{}');
    } catch (e) {
        console.error(`[STATS] Failed to parse temp run stats: ${e.message}`);
        return;
    }

    // 2. Load existing history
    ensureDirectoryExists(HISTORY_FILE);
    let history = {};
    if (fs.existsSync(HISTORY_FILE)) {
        try {
            history = JSON.parse(fs.readFileSync(HISTORY_FILE, 'utf8') || '{}');
        } catch (e) {
            console.warn("[STATS] Failed to parse history file, resetting history:", e.message);
        }
    }

    // 3. Update history for today
    const today = new Date().toISOString().split('T')[0];
    if (!history[today]) {
        history[today] = {};
    }
    if (!history[today][country]) {
        history[today][country] = {};
    }

    // Merge run stats
    for (const [step, cost] of Object.entries(runStats)) {
        history[today][country][step] = {
            reads: cost.reads || 0,
            writes: cost.writes || 0
        };
    }

    // 4. Prune to last 30 days (sliding window)
    const dates = Object.keys(history).sort();
    if (dates.length > 30) {
        const toRemove = dates.slice(0, dates.length - 30);
        for (const date of toRemove) {
            delete history[date];
            console.log(`[STATS] Pruned stale history entry for date: ${date}`);
        }
    }

    // Write updated history to disk
    fs.writeFileSync(HISTORY_FILE, JSON.stringify(history, null, 2));
    console.log(`[STATS] Updated historical logs in ${HISTORY_FILE}`);

    // 5. Generate Markdown Report
    let md = `# 📊 Turso Database Cost Report (Last 30 Days)\n\n`;
    md += `This report tracks the Turso SQLite read and write metrics for the background synchronization pipeline, bifurcated by country and step.\n\n`;

    // Sort dates in descending order for the report (newest first)
    const sortedDates = Object.keys(history).sort().reverse();

    for (const date of sortedDates) {
        md += `## 📅 Date: ${date}\n\n`;
        md += `| Country | Sync Step | DB Reads | DB Writes |\n`;
        md += `| :--- | :--- | :---: | :---: |\n`;

        const countries = Object.keys(history[date]).sort();
        for (const c of countries) {
            const steps = Object.keys(history[date][c]).sort();
            let countryReads = 0;
            let countryWrites = 0;
            let firstRow = true;

            for (const step of steps) {
                const cost = history[date][c][step];
                countryReads += cost.reads;
                countryWrites += cost.writes;

                // For clean formatting, only show the country code on the first row of its steps block
                const cDisplay = firstRow ? `**\`${c.toUpperCase()}\`**` : '';
                md += `| ${cDisplay} | \`${step}\` | ${cost.reads.toLocaleString()} | ${cost.writes.toLocaleString()} |\n`;
                firstRow = false;
            }
            
            // Add a subtotal summary row for the country
            md += `| | *Subtotal (${c.toUpperCase()})* | *${countryReads.toLocaleString()}* | *${countryWrites.toLocaleString()}* |\n`;
        }
        md += `\n---\n\n`;
    }

    // Save Markdown report to disk
    ensureDirectoryExists(REPORT_FILE);
    fs.writeFileSync(REPORT_FILE, md);
    console.log(`[STATS] Generated cost report table in ${REPORT_FILE}`);

    // 6. Write to GitHub Actions summary if running in GHA
    if (process.env.GITHUB_STEP_SUMMARY) {
        try {
            fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n### 📊 Turso DB Cost Summary for Matrix Run (${country.toUpperCase()})\n\n`);
            let summaryTable = `| Sync Step | DB Reads | DB Writes |\n| :--- | :---: | :---: |\n`;
            let totalR = 0, totalW = 0;
            for (const [step, cost] of Object.entries(runStats)) {
                summaryTable += `| \`${step}\` | ${cost.reads.toLocaleString()} | ${cost.writes.toLocaleString()} |\n`;
                totalR += cost.reads;
                totalW += cost.writes;
            }
            summaryTable += `| **Total** | **${totalR.toLocaleString()}** | **${totalW.toLocaleString()}** |\n`;
            fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, summaryTable);
            console.log("[STATS] Successfully appended summary table to $GITHUB_STEP_SUMMARY");
        } catch (e) {
            console.warn(`[STATS] Failed to write to GHA Step Summary: ${e.message}`);
        }
    }

    // 7. Print current run stats to console
    console.log(`\n=== CURRENT RUN COST SUMMARY (${country.toUpperCase()}) ===`);
    console.log(`Step Name           | Reads        | Writes`);
    console.log(`-----------------------------------------------`);
    let totR = 0, totW = 0;
    for (const [step, cost] of Object.entries(runStats)) {
        console.log(`${step.padEnd(19)} | ${cost.reads.toString().padEnd(12)} | ${cost.writes.toString().padEnd(10)}`);
        totR += cost.reads;
        totW += cost.writes;
    }
    console.log(`-----------------------------------------------`);
    console.log(`TOTAL               | ${totR.toString().padEnd(12)} | ${totW.toString().padEnd(10)}`);
    console.log(`===============================================\n`);

    // 8. Delete temp run stats file to prepare for next run/avoid pollution
    try {
        fs.unlinkSync(STATS_FILE);
        console.log("[STATS] Cleaned up temporary stats file.");
    } catch (e) {
        console.warn(`[STATS] Failed to delete temp stats file: ${e.message}`);
    }
}

main().catch(err => {
    console.error("[STATS] Cost recording failed:", err);
    process.exit(1);
});
