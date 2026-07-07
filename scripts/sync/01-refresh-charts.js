#!/usr/bin/env node
'use strict';

/**
 * Stage 1: Refresh the Turso charts table from iTunes RSS for all configured
 * countries. Runs on the 00:00 UTC schedule (or manual dispatch) only.
 *
 * - One full charts-table read, diffed in memory (no per-category SELECTs).
 * - iTunes requests are spaced and retried; a failed fetch for a
 *   country/category skips BOTH upserts and deletes for that pair (a 403
 *   throttle must never masquerade as an empty chart and wipe rows).
 * - Only changed rows are written, in batched transactions.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const state = require('./lib/state');
const cfg = require('./lib/config');

const ITUNES_SPACING_MS = 350;
const ITUNES_RETRIES = 3;

async function fetchItunesChart(country, category) {
    const genreParam = category === 'all' ? '' : `/genre=${cfg.GENRE_MAP[category]}`;
    const url = `https://itunes.apple.com/${country}/rss/toppodcasts${genreParam}/limit=200/json`;

    for (let attempt = 1; attempt <= ITUNES_RETRIES; attempt++) {
        try {
            const res = await fetch(url);
            if (res.status === 403 || res.status === 429) {
                const backoff = 2000 * Math.pow(2, attempt - 1);
                log.warn(`iTunes ${res.status} for ${country}/${category}; retrying in ${backoff}ms (attempt ${attempt}/${ITUNES_RETRIES})`);
                await new Promise(r => setTimeout(r, backoff));
                continue;
            }
            if (!res.ok) throw new Error(`iTunes HTTP ${res.status}`);
            const data = await res.json();
            const entries = data.feed?.entry || [];
            return entries.map(e => ({
                itunesId: e.id?.attributes?.['im:id'] || '',
                name: e['im:name']?.label || '',
                artist: e['im:artist']?.label || '',
                imageUrl: e['im:image']?.[2]?.label || e['im:image']?.[1]?.label || '',
            })).filter(p => p.itunesId);
        } catch (e) {
            if (attempt === ITUNES_RETRIES) throw e;
            const backoff = 2000 * Math.pow(2, attempt - 1);
            log.warn(`iTunes fetch failed for ${country}/${category}: ${e.message}; retrying in ${backoff}ms`);
            await new Promise(r => setTimeout(r, backoff));
        }
    }
    throw new Error(`iTunes fetch exhausted retries for ${country}/${category}`);
}

function rowKey(country, category, itunesId) {
    return `${country}|${category}|${itunesId}`;
}

async function main() {
    turso.assertEnv();
    turso.beginStep('refresh-charts');
    await turso.healthCheck();

    log.banner('Stage 1 · Refresh Charts', {
        'Countries': cfg.ALL_COUNTRIES.map(c => c.toUpperCase()).join(', '),
        'Categories': String(cfg.CATEGORIES.length),
        'Chart fetches': String(cfg.ALL_COUNTRIES.length * cfg.CATEGORIES.length),
    });

    // --- Read the entire charts table once and index it ---
    log.group('Read existing charts');
    const existingRes = await turso.execute(
        'SELECT country, category, itunes_id, name, artist, image_url, rank FROM charts'
    );
    const existing = new Map();
    for (const [country, category, itunesId, name, artist, imageUrl, rank] of turso.rows(existingRes)) {
        existing.set(rowKey(country, category, String(itunesId)), {
            name: name || '', artist: artist || '', imageUrl: imageUrl || '',
            rank: parseInt(rank, 10),
        });
    }
    log.info(`Existing chart rows: ${log.fmt(existing.size)}`);
    log.endGroup();

    // --- Fetch all charts, spaced ---
    let upserts = 0;
    let deletes = 0;
    let unchanged = 0;
    let failedPairs = 0;
    const totalPairs = cfg.ALL_COUNTRIES.length * cfg.CATEGORIES.length;
    const prog = log.progress(totalPairs, 'charts');

    for (const country of cfg.ALL_COUNTRIES) {
        log.group(`Country: ${country.toUpperCase()}`);
        for (const category of cfg.CATEGORIES) {
            await new Promise(r => setTimeout(r, ITUNES_SPACING_MS));
            let podcasts;
            try {
                podcasts = await fetchItunesChart(country, category);
            } catch (e) {
                log.warn(`SKIPPING ${country}/${category} entirely (fetch failed: ${e.message}) - no upserts, no deletes`);
                failedPairs++;
                prog.tick();
                continue;
            }
            if (podcasts.length === 0) {
                // Empty response is suspicious (charts are never empty); treat like a failure.
                log.warn(`SKIPPING ${country}/${category}: iTunes returned 0 entries`);
                failedPairs++;
                prog.tick();
                continue;
            }

            const statements = [];
            const activeIds = [];
            for (let i = 0; i < podcasts.length; i++) {
                const p = podcasts[i];
                const rank = i + 1;
                activeIds.push(String(p.itunesId));
                const prev = existing.get(rowKey(country, category, String(p.itunesId)));
                if (prev && prev.rank === rank && prev.name === p.name && prev.artist === p.artist && prev.imageUrl === p.imageUrl) {
                    unchanged++;
                    continue;
                }
                statements.push({
                    sql: `INSERT INTO charts (itunes_id, name, artist, image_url, country, category, rank)
                          VALUES (?, ?, ?, ?, ?, ?, ?)
                          ON CONFLICT(itunes_id, country, category) DO UPDATE SET
                              rank = excluded.rank, name = excluded.name,
                              artist = excluded.artist, image_url = excluded.image_url`,
                    args: [p.itunesId, p.name, p.artist, p.imageUrl, country, category, rank],
                });
            }

            // Delete rows that fell off this chart (safe: fetch succeeded above)
            const currentSet = new Set(activeIds);
            let droppedCount = 0;
            for (const key of existing.keys()) {
                const [c, cat, id] = key.split('|');
                if (c === country && cat === category && !currentSet.has(id)) droppedCount++;
            }
            if (droppedCount > 0) {
                const placeholders = activeIds.map(() => '?').join(',');
                statements.push({
                    sql: `DELETE FROM charts WHERE country = ? AND category = ? AND itunes_id NOT IN (${placeholders})`,
                    args: [country, category, ...activeIds],
                });
            }

            if (statements.length > 0) {
                await turso.transaction(statements);
                upserts += statements.length - (droppedCount > 0 ? 1 : 0);
                deletes += droppedCount;
            }
            prog.tick();
        }
        log.endGroup();
    }

    // Record charts refresh time in state so stage 3 refreshes its candidate cache
    const st = state.load();
    st.chartsRefreshedAt = Date.now();
    state.save(st);

    const stats = turso.getStats();
    log.costFooter('Stage 1 · Refresh Charts', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: totalPairs - failedPairs,
        detail: `${log.fmt(upserts)} upserted · ${log.fmt(deletes)} dropped · ${log.fmt(unchanged)} unchanged · ${failedPairs} pairs skipped`,
    });
    log.summaryTable('Stage 1: Refresh Charts', [{
        stage: 'refresh-charts',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: totalPairs - failedPairs,
        detail: `${upserts} upserts, ${deletes} deletes, ${failedPairs} failed pairs`,
    }]);

    if (failedPairs > totalPairs / 2) {
        log.error(`More than half of chart fetches failed (${failedPairs}/${totalPairs}) - failing run`);
        process.exit(1);
    }
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`refresh-charts failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
