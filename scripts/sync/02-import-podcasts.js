#!/usr/bin/env node
'use strict';

/**
 * Stage 2: Ensure every chart show exists in the Turso podcasts table.
 *
 * Modes:
 *   --precheck  Compute missing chart shows (union across all countries),
 *               write missing_itunes_ids.txt for the dump-export step, and
 *               set GHA outputs need_dump / missing_count.
 *   --import    If podcasts_export.csv exists (dump path), bulk-import it;
 *               otherwise fetch missing shows individually from the PI API
 *               (capped). Fixed column whitelist - NO auto-ALTER.
 */

const fs = require('fs');
const log = require('./lib/log');
const turso = require('./lib/turso');
const pi = require('./lib/podcast-index');
const cfg = require('./lib/config');

const MISSING_IDS_FILE = 'missing_itunes_ids.txt';
const CSV_FILE = 'podcasts_export.csv';

const GENRE_PRIORITY = [
    'Technology', 'News', 'Business', 'Science', 'Sports', 'True Crime',
    'History', 'Comedy', 'Arts', 'Fiction', 'Music', 'Religion & Spirituality',
    'Kids & Family', 'Government', 'Health', 'TV & Film', 'Education',
];

function sortCategories(rawCats) {
    if (!rawCats) return rawCats;
    return rawCats.split(',')
        .map(c => c.trim())
        .filter(Boolean)
        .sort((a, b) => {
            const ia = GENRE_PRIORITY.indexOf(a);
            const ib = GENRE_PRIORITY.indexOf(b);
            if (ia !== -1 && ib !== -1) return ia - ib;
            if (ia !== -1) return -1;
            if (ib !== -1) return 1;
            return a.localeCompare(b);
        })
        .join(', ');
}

function parseCSVLine(line) {
    const result = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
        const ch = line[i];
        if (ch === '"') {
            if (inQuotes && line[i + 1] === '"') { current += '"'; i++; }
            else inQuotes = !inQuotes;
        } else if (ch === ',' && !inQuotes) {
            result.push(current);
            current = '';
        } else {
            current += ch;
        }
    }
    result.push(current);
    return result;
}

/** Missing chart itunes ids (string-normalized set difference). */
async function computeMissing() {
    const chartsRes = await turso.execute(
        'SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE itunes_id IS NOT NULL'
    );
    const chartIds = turso.rows(chartsRes).map(r => String(r[0])).filter(Boolean);

    const podsRes = await turso.execute(
        'SELECT DISTINCT itunes_id FROM podcasts WHERE itunes_id IS NOT NULL'
    );
    const existing = new Set(turso.rows(podsRes).map(r => String(r[0])));

    const missing = chartIds.filter(id => !existing.has(id));
    return { chartIds, missing };
}

async function precheck() {
    const { chartIds, missing } = await computeMissing();
    log.info(`Chart shows (union all countries): ${log.fmt(chartIds.length)}`);
    log.info(`Missing from podcasts table:       ${log.fmt(missing.length)}`);

    const needDump = missing.length > cfg.DUMP_THRESHOLD;
    fs.writeFileSync(MISSING_IDS_FILE, missing.join('\n') + (missing.length ? '\n' : ''));
    log.info(`Decision: need_dump=${needDump} (threshold ${cfg.DUMP_THRESHOLD})`);

    if (process.env.GITHUB_OUTPUT) {
        fs.appendFileSync(process.env.GITHUB_OUTPUT, `need_dump=${needDump}\n`);
        fs.appendFileSync(process.env.GITHUB_OUTPUT, `missing_count=${missing.length}\n`);
    }
}

async function importFromCSV() {
    const content = fs.readFileSync(CSV_FILE, 'utf-8');
    const lines = content.split('\n').filter(l => l.trim());
    if (lines.length < 2) {
        log.info('CSV contains no data rows - nothing to import');
        return 0;
    }

    const headers = parseCSVLine(lines[0]);
    // Enforce the fixed whitelist: every CSV header must be a known column.
    const unknown = headers.filter(h => !cfg.PODCAST_IMPORT_COLUMNS.includes(h));
    if (unknown.length > 0) {
        throw new Error(`CSV contains non-whitelisted columns: ${unknown.join(', ')} - refusing to import (no auto-ALTER)`);
    }

    const catIdx = headers.indexOf('categories');
    const dataLines = lines.slice(1);
    log.info(`Importing ${log.fmt(dataLines.length)} rows from ${CSV_FILE}`);

    const BATCH = 50;
    let imported = 0;
    const prog = log.progress(dataLines.length, 'csv-import');

    for (let i = 0; i < dataLines.length; i += BATCH) {
        const statements = dataLines.slice(i, i + BATCH).map(line => {
            const values = parseCSVLine(line);
            if (catIdx !== -1 && values[catIdx]) {
                values[catIdx] = sortCategories(values[catIdx]);
            }
            const placeholders = headers.map(() => '?').join(',');
            return {
                sql: `INSERT OR IGNORE INTO podcasts (${headers.join(',')}) VALUES (${placeholders})`,
                args: values,
            };
        });
        await turso.batch(statements);
        imported += statements.length;
        for (let k = 0; k < statements.length; k++) prog.tick();
    }
    return imported;
}

async function importFromAPI() {
    pi.assertEnv();
    const { missing } = await computeMissing();
    if (missing.length === 0) {
        log.info('All chart shows already present - nothing to import');
        return 0;
    }

    const toImport = missing.slice(0, cfg.API_IMPORT_CAP);
    if (missing.length > cfg.API_IMPORT_CAP) {
        log.warn(`${missing.length} shows missing; importing first ${cfg.API_IMPORT_CAP} via API (rest handled by next dump run)`);
    }
    log.info(`Fetching ${toImport.length} shows from the PI API`);

    const cols = cfg.PODCAST_IMPORT_COLUMNS;
    const placeholders = cols.map(() => '?').join(',');
    let imported = 0;
    let notFound = 0;
    const statements = [];
    const prog = log.progress(toImport.length, 'api-import');

    for (const itunesId of toImport) {
        let feed = null;
        try {
            feed = await pi.podcastByItunesId(itunesId);
        } catch (e) {
            log.warn(`PI lookup failed for itunes_id=${itunesId}: ${e.message}`);
        }
        prog.tick();
        if (!feed) { notFound++; continue; }

        const categoriesStr = feed.categories
            ? sortCategories(Object.values(feed.categories).join(', '))
            : '';
        statements.push({
            sql: `INSERT INTO podcasts (${cols.join(',')}) VALUES (${placeholders})
                  ON CONFLICT(id) DO UPDATE SET itunes_id = excluded.itunes_id
                  WHERE itunes_id IS NULL OR itunes_id != excluded.itunes_id`,
            args: [
                feed.id,
                feed.itunesId || itunesId,
                feed.title || 'Unknown Title',
                feed.author || 'Unknown Author',
                (feed.description || '').substring(0, 1000),
                feed.image || feed.artwork || '',
                feed.url || '',
                feed.link || '',
                categoriesStr,
                feed.language || 'en',
                feed.explicit ? '1' : '0',
                feed.itunesType || 'episodic',
            ],
        });
        imported++;

        if (statements.length >= 50) {
            await turso.batch(statements.splice(0));
        }
    }
    if (statements.length > 0) await turso.batch(statements);

    log.info(`API import done: ${imported} imported, ${notFound} not found on PI`);
    return imported;
}

async function main() {
    turso.assertEnv();
    turso.beginStep('import-podcasts');
    await turso.healthCheck();

    if (process.argv.includes('--precheck')) {
        log.banner('Stage 2a · Pre-check Missing Shows', {
            'Dump threshold': String(cfg.DUMP_THRESHOLD),
        });
        await precheck();
        return;
    }

    let imported = 0;
    let mode;
    if (fs.existsSync(CSV_FILE)) {
        mode = 'dump';
        log.banner('Stage 2 · Import Podcasts', { 'Mode': 'bulk (PI dump CSV)' });
        log.group('Bulk import from PI dump CSV');
        imported = await importFromCSV();
        log.endGroup();
    } else {
        mode = 'api';
        log.banner('Stage 2 · Import Podcasts', {
            'Mode': 'incremental (PI API)',
            'Per-run cap': String(cfg.API_IMPORT_CAP),
        });
        log.group('Incremental import via PI API');
        imported = await importFromAPI();
        log.endGroup();
    }

    const stats = turso.getStats();
    log.costFooter('Stage 2 · Import Podcasts', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${log.fmt(imported)} shows imported (${mode})`,
    });
    log.summaryTable('Stage 2: Import Podcasts', [{
        stage: `import-podcasts (${mode})`,
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${imported} shows imported`,
    }]);
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`import-podcasts failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
