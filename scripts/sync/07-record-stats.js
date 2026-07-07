#!/usr/bin/env node
'use strict';

/**
 * Stage 7: Fold this run's DB cost counters (RUN_STATS_FILE, written
 * incrementally by lib/turso.js) into the 30-day history and regenerate the
 * markdown report.
 *
 * Fixes vs the old recorder:
 * - ACCUMULATES per day across the 5 daily runs (the old code overwrote,
 *   undercounting daily cost by up to 5x).
 * - Tracks runs-per-day so the report shows both totals and run counts.
 */

const fs = require('fs');
const path = require('path');
const log = require('./lib/log');
const cfg = require('./lib/config');

const SCOPE = 'global'; // single-pass pipeline; legacy history had per-country scopes

// Turso free tier: 500M row reads / 10M row writes per month.
const FREE_TIER_DAILY_READS = Math.floor(500_000_000 / 30);
const FREE_TIER_DAILY_WRITES = Math.floor(10_000_000 / 30);

function main() {
    log.banner('Stage 7 · Record DB Cost Stats');
    if (!fs.existsSync(cfg.RUN_STATS_FILE)) {
        log.warn('No run stats file found - nothing to record');
        return;
    }
    let runStats = {};
    try {
        runStats = JSON.parse(fs.readFileSync(cfg.RUN_STATS_FILE, 'utf8') || '{}');
    } catch (e) {
        log.error(`Failed to parse run stats: ${e.message}`);
        return;
    }

    // --- Load + update history (accumulate per day) ---
    let history = {};
    if (fs.existsSync(cfg.HISTORY_FILE)) {
        try {
            history = JSON.parse(fs.readFileSync(cfg.HISTORY_FILE, 'utf8') || '{}');
        } catch (e) {
            log.warn(`History file unreadable, resetting: ${e.message}`);
        }
    }

    const today = new Date().toISOString().split('T')[0];
    if (!history[today]) history[today] = {};
    if (!history[today][SCOPE]) history[today][SCOPE] = {};
    const dayScope = history[today][SCOPE];

    for (const [step, cost] of Object.entries(runStats)) {
        const prev = dayScope[step] || { reads: 0, writes: 0, runs: 0 };
        dayScope[step] = {
            reads: prev.reads + (cost.reads || 0),
            writes: prev.writes + (cost.writes || 0),
            runs: (prev.runs || 0) + 1,
        };
    }

    // 30-day sliding window
    const dates = Object.keys(history).sort();
    for (const d of dates.slice(0, Math.max(0, dates.length - 30))) {
        delete history[d];
    }

    const dir = path.dirname(cfg.HISTORY_FILE);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(cfg.HISTORY_FILE, JSON.stringify(history, null, 1));
    log.info(`History updated: ${cfg.HISTORY_FILE}`);

    // --- Regenerate markdown report ---
    let md = '# Turso Database Cost Report (Last 30 Days)\n\n';
    md += 'Daily totals accumulated across all runs of the sync pipeline.\n\n';

    for (const date of Object.keys(history).sort().reverse()) {
        md += `## ${date}\n\n`;
        md += '| Scope | Step | Runs | DB Reads | DB Writes |\n';
        md += '| :--- | :--- | ---: | ---: | ---: |\n';
        let dayReads = 0;
        let dayWrites = 0;
        for (const scope of Object.keys(history[date]).sort()) {
            let first = true;
            for (const step of Object.keys(history[date][scope]).sort()) {
                const c = history[date][scope][step];
                dayReads += c.reads || 0;
                dayWrites += c.writes || 0;
                md += `| ${first ? `\`${scope}\`` : ''} | \`${step}\` | ${c.runs || '-'} | ${(c.reads || 0).toLocaleString()} | ${(c.writes || 0).toLocaleString()} |\n`;
                first = false;
            }
        }
        md += `| | **Day total** | | **${dayReads.toLocaleString()}** | **${dayWrites.toLocaleString()}** |\n\n`;
    }
    fs.writeFileSync(cfg.REPORT_FILE, md);
    log.info(`Report regenerated: ${cfg.REPORT_FILE}`);

    // --- Totals: this run + accumulated today ---
    let totalR = 0;
    let totalW = 0;
    for (const cost of Object.values(runStats)) {
        totalR += cost.reads || 0;
        totalW += cost.writes || 0;
    }
    let dayR = 0;
    let dayW = 0;
    for (const c of Object.values(dayScope)) {
        dayR += c.reads || 0;
        dayW += c.writes || 0;
    }
    const readPct = ((dayR / FREE_TIER_DAILY_READS) * 100).toFixed(1);
    const writePct = ((dayW / FREE_TIER_DAILY_WRITES) * 100).toFixed(1);

    // --- Aligned console table ---
    console.log('');
    console.log('  Step                        Reads       Writes');
    console.log('  ' + '─'.repeat(48));
    for (const [step, cost] of Object.entries(runStats)) {
        console.log(`  ${step.padEnd(24)} ${(cost.reads || 0).toLocaleString().padStart(10)} ${(cost.writes || 0).toLocaleString().padStart(12)}`);
    }
    console.log('  ' + '─'.repeat(48));
    console.log(`  ${'This run'.padEnd(24)} ${totalR.toLocaleString().padStart(10)} ${totalW.toLocaleString().padStart(12)}`);
    console.log(`  ${'Today (all runs)'.padEnd(24)} ${dayR.toLocaleString().padStart(10)} ${dayW.toLocaleString().padStart(12)}`);
    console.log(`  ${'Free-tier daily budget'.padEnd(24)} ${(readPct + '%').padStart(10)} ${(writePct + '%').padStart(12)}`);
    console.log('');

    // --- GHA step summary ---
    let summary = '\n### 💾 Turso DB Cost\n\n| Step | Reads | Writes |\n| :--- | ---: | ---: |\n';
    for (const [step, cost] of Object.entries(runStats)) {
        summary += `| \`${step}\` | ${(cost.reads || 0).toLocaleString()} | ${(cost.writes || 0).toLocaleString()} |\n`;
    }
    summary += `| **This run** | **${totalR.toLocaleString()}** | **${totalW.toLocaleString()}** |\n`;
    summary += `| **Today (all runs)** | **${dayR.toLocaleString()}** | **${dayW.toLocaleString()}** |\n`;
    summary += `\nToday is at **${readPct}%** of the pro-rated daily read budget and **${writePct}%** of the write budget (free tier: 500M reads / 10M writes per month).\n`;
    if (parseFloat(readPct) > 80 || parseFloat(writePct) > 80) {
        log.warn(`Turso usage today is high: ${readPct}% reads / ${writePct}% writes of the pro-rated daily free-tier budget`);
    }
    log.stepSummary(summary);

    // Clean the temp file so a rerun on the same runner starts fresh
    try {
        fs.unlinkSync(cfg.RUN_STATS_FILE);
    } catch { /* best effort */ }
}

try {
    main();
} catch (err) {
    log.error(`record-stats failed: ${err.message}`);
    process.exit(1);
}
