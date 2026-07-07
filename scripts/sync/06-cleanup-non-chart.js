#!/usr/bin/env node
'use strict';

/**
 * Stage 6 (20:00 run only): Delete shows that have been absent from ALL
 * country charts for CLEANUP_GRACE_DAYS consecutive days.
 *
 * Grace period prevents delete/re-vectorize thrash: a show dipping out of
 * the top 200 for a day keeps its Turso row and Qdrant vectors; only
 * sustained absence triggers deletion. Last-seen timestamps live in the git
 * state file (shows[id].s) - zero Turso cost.
 *
 * FTS safety: the podcasts_ad delete trigger does a full FTS-content scan
 * PER ROW, so we drop it, bulk-delete, clean FTS orphans in one scan, and
 * recreate it.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const state = require('./lib/state');
const cfg = require('./lib/config');

const QDRANT_CHUNK = 500;
const DELETE_CHUNK = 200;

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    turso.beginStep('cleanup-non-chart');
    await turso.healthCheck();

    log.banner('Stage 6 · Cleanup Non-chart Shows', {
        'Grace period': `${cfg.CLEANUP_GRACE_DAYS} days`,
        'Safety floor': `${log.fmt(cfg.CLEANUP_SAFETY_MIN_CHARTS)} chart shows`,
    });

    // --- Safety: charts must look healthy ---
    const chartCountRes = await turso.execute(
        'SELECT COUNT(DISTINCT itunes_id) FROM charts WHERE itunes_id IS NOT NULL'
    );
    const activeChartCount = parseInt(turso.scalar(chartCountRes), 10) || 0;
    log.info(`Active chart shows: ${log.fmt(activeChartCount)}`);
    if (activeChartCount < cfg.CLEANUP_SAFETY_MIN_CHARTS) {
        log.error(`Safety abort: chart count ${activeChartCount} < ${cfg.CLEANUP_SAFETY_MIN_CHARTS} - charts table may be broken`);
        process.exit(1);
    }

    // --- Compute chart membership + grace clock ---
    const chartSetRes = await turso.execute(
        'SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE itunes_id IS NOT NULL'
    );
    const chartItunesIds = new Set(turso.rows(chartSetRes).map(r => String(r[0])));

    const podsRes = await turso.execute('SELECT id, itunes_id, title FROM podcasts');
    const allPods = turso.rows(podsRes).map(r => ({
        id: String(r[0]),
        itunesId: r[1] !== null ? String(r[1]) : null,
        title: r[2] || 'Unknown',
    }));
    log.info(`Podcasts in DB: ${log.fmt(allPods.length)}`);

    const st = state.load();
    const now = Date.now();
    const graceMs = cfg.CLEANUP_GRACE_DAYS * 24 * 60 * 60 * 1000;
    const toDelete = [];
    let inCharts = 0;
    let inGrace = 0;

    for (const pod of allPods) {
        const rec = st.shows[pod.id] || (st.shows[pod.id] = {});
        if (pod.itunesId && chartItunesIds.has(pod.itunesId)) {
            rec.s = now;
            inCharts++;
        } else if (!rec.s) {
            rec.s = now; // start the grace clock
            inGrace++;
        } else if (now - rec.s > graceMs) {
            toDelete.push(pod);
        } else {
            inGrace++;
        }
    }

    log.group('Cleanup plan');
    log.info(`In charts:                ${log.fmt(inCharts)}`);
    log.info(`In grace period (<${cfg.CLEANUP_GRACE_DAYS}d):   ${log.fmt(inGrace)}`);
    log.info(`Past grace - deleting:    ${log.fmt(toDelete.length)}`);
    log.endGroup();

    if (toDelete.length === 0) {
        state.save(st);
        log.info('Nothing past the grace period - done');
        log.summaryTable('Stage 6: Cleanup', [{
            stage: 'cleanup-non-chart',
            reads: turso.getStats().reads,
            writes: turso.getStats().writes,
            detail: `0 deleted, ${inGrace} in grace`,
        }]);
        return;
    }

    // --- Qdrant deletion first (needs the id list) ---
    log.group('Qdrant vector deletion');
    for (let i = 0; i < toDelete.length; i += QDRANT_CHUNK) {
        const chunk = toDelete.slice(i, i + QDRANT_CHUNK);
        const intIds = chunk.map(p => parseInt(p.id, 10)).filter(n => !isNaN(n));
        const uuids = chunk.map(p => qdrant.stableUUID(p.id));
        try {
            await qdrant.deleteByFilter(cfg.EPISODES_COLLECTION, {
                must: [{ key: 'podcast_id', match: { any: intIds } }],
            });
            await qdrant.deleteByIds(cfg.PODCASTS_COLLECTION, uuids);
            log.info(`Deleted vectors for shows ${i + 1}-${Math.min(i + QDRANT_CHUNK, toDelete.length)} of ${toDelete.length}`);
        } catch (e) {
            log.error(`Qdrant deletion failed for chunk at ${i}: ${e.message}`);
        }
    }
    log.endGroup();

    // --- Turso deletion with trigger workaround ---
    log.group('Turso deletion');
    try {
        log.info('Dropping podcasts_ad trigger (prevents per-row FTS scans)');
        await turso.execute('DROP TRIGGER IF EXISTS podcasts_ad');

        const ids = toDelete.map(p => p.id);
        for (let i = 0; i < ids.length; i += DELETE_CHUNK) {
            const chunk = ids.slice(i, i + DELETE_CHUNK);
            const placeholders = chunk.map(() => '?').join(',');
            await turso.execute(`DELETE FROM podcasts WHERE id IN (${placeholders})`, chunk);
        }
        log.info(`Deleted ${log.fmt(ids.length)} podcast rows`);

        log.info('Cleaning FTS orphans (single scan)');
        await turso.execute('DELETE FROM podcasts_fts WHERE podcast_id NOT IN (SELECT id FROM podcasts)');
    } finally {
        // Always restore the trigger, even if deletion failed midway.
        await turso.execute(`
            CREATE TRIGGER IF NOT EXISTS podcasts_ad AFTER DELETE ON podcasts BEGIN
                DELETE FROM podcasts_fts WHERE podcast_id = old.id;
            END
        `);
        log.info('podcasts_ad trigger restored');
    }
    log.endGroup();

    // --- Prune state for deleted shows ---
    const deletedIds = new Set(toDelete.map(p => p.id));
    const keepIds = allPods.filter(p => !deletedIds.has(p.id)).map(p => p.id);
    const pruned = state.pruneShows(st, keepIds);
    if (st.candidateIds) {
        st.candidateIds = st.candidateIds.filter(id => !deletedIds.has(id));
    }
    state.save(st);
    log.info(`State pruned: ${pruned} dead entries removed`);

    const stats = turso.getStats();
    log.costFooter('Stage 6 · Cleanup', {
        reads: stats.reads,
        writes: stats.writes,
        detail: `${log.fmt(toDelete.length)} shows deleted · ${log.fmt(inGrace)} in grace · ${pruned} state entries pruned`,
    });
    log.summaryTable('Stage 6: Cleanup', [{
        stage: 'cleanup-non-chart',
        reads: stats.reads,
        writes: stats.writes,
        detail: `${toDelete.length} shows deleted, ${inGrace} in grace, ${pruned} state entries pruned`,
    }]);
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`cleanup-non-chart failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
