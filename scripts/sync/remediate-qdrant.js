#!/usr/bin/env node
'use strict';

/**
 * One-time (idempotent) Qdrant remediation for the overfill caused by the
 * uncapped vectorization window:
 *
 *   1. Measure current collection state.
 *   2. Delete episode points belonging to non-chart shows.
 *   3. Trim every chart show to its latest EPISODES_PER_SHOW episode points.
 *   4. Cross-check Turso qdrant_vectorized flags against actual point counts;
 *      reset false positives (flag=1 but zero points) so the pipeline heals them.
 *   5. Report before/after point counts.
 *   6. Enable int8 scalar quantization + on-disk originals (skippable).
 *
 * Usage:
 *   node scripts/sync/remediate-qdrant.js [--dry-run] [--skip-quantization]
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const cfg = require('./lib/config');

const DRY_RUN = process.argv.includes('--dry-run');
const SKIP_QUANTIZATION = process.argv.includes('--skip-quantization');

const DELETE_CHUNK = 500;

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    turso.beginStep('remediate-qdrant');
    log.banner('Qdrant Remediation', {
        'Mode': DRY_RUN ? 'DRY RUN (no writes)' : 'LIVE',
        'Episodes per show': String(cfg.EPISODES_PER_SHOW),
        'Quantization': SKIP_QUANTIZATION ? 'skipped' : 'int8 scalar + on-disk originals',
    });

    // ---------------------------------------------------------------
    log.group('Step 1: Measure current state');
    const beforeEpisodes = await qdrant.collectionInfo(cfg.EPISODES_COLLECTION);
    const beforePodcasts = await qdrant.collectionInfo(cfg.PODCASTS_COLLECTION);
    if (!beforeEpisodes) {
        log.error(`Collection '${cfg.EPISODES_COLLECTION}' not found - nothing to remediate`);
        process.exit(1);
    }
    log.info(`episodes collection: ${log.fmt(beforeEpisodes.pointsCount)} points, status=${beforeEpisodes.status}`);
    if (beforePodcasts) {
        log.info(`podcasts collection: ${log.fmt(beforePodcasts.pointsCount)} points, status=${beforePodcasts.status}`);
    }
    log.endGroup();

    // ---------------------------------------------------------------
    log.group('Step 2: Load chart shows from Turso');
    await turso.healthCheck();
    const chartShowRes = await turso.execute(`
        SELECT DISTINCT p.id
        FROM podcasts p
        WHERE p.itunes_id IN (SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE itunes_id IS NOT NULL)
    `);
    const chartShowIds = new Set(turso.rows(chartShowRes).map(r => String(r[0])));
    log.info(`Chart shows in Turso: ${log.fmt(chartShowIds.size)}`);
    if (chartShowIds.size < cfg.CLEANUP_SAFETY_MIN_CHARTS) {
        log.error(`Safety abort: only ${chartShowIds.size} chart shows found (< ${cfg.CLEANUP_SAFETY_MIN_CHARTS}). Charts table may be broken.`);
        process.exit(1);
    }
    log.endGroup();

    // ---------------------------------------------------------------
    log.group('Step 3: Scan episode points (podcast_id + published_date)');
    const scanStart = Date.now();
    const allPoints = await qdrant.scrollAll(
        cfg.EPISODES_COLLECTION,
        null,
        ['podcast_id', 'published_date'],
        1000
    );
    log.info(`Scanned ${log.fmt(allPoints.length)} points in ${log.duration(Date.now() - scanStart)}`);

    const byShow = new Map();
    let unknownPodcastId = 0;
    for (const pt of allPoints) {
        const pid = pt.payload?.podcast_id;
        if (pid === undefined || pid === null) {
            unknownPodcastId++;
            continue;
        }
        const key = String(pid);
        if (!byShow.has(key)) byShow.set(key, []);
        byShow.get(key).push({ id: pt.id, date: pt.payload?.published_date || 0 });
    }
    log.info(`Distinct shows in collection: ${log.fmt(byShow.size)} (${unknownPodcastId} points without podcast_id)`);
    log.endGroup();

    // ---------------------------------------------------------------
    log.group('Step 4: Delete non-chart show points');
    const nonChartShowIds = [...byShow.keys()].filter(id => !chartShowIds.has(id));
    let nonChartPoints = 0;
    for (const id of nonChartShowIds) nonChartPoints += byShow.get(id).length;
    log.info(`Non-chart shows in Qdrant: ${log.fmt(nonChartShowIds.length)} (${log.fmt(nonChartPoints)} points)`);

    if (!DRY_RUN && nonChartShowIds.length > 0) {
        for (let i = 0; i < nonChartShowIds.length; i += DELETE_CHUNK) {
            const chunk = nonChartShowIds.slice(i, i + DELETE_CHUNK).map(id => parseInt(id, 10)).filter(n => !isNaN(n));
            await qdrant.deleteByFilter(cfg.EPISODES_COLLECTION, {
                must: [{ key: 'podcast_id', match: { any: chunk } }],
            });
            log.info(`Deleted points for shows ${i + 1}-${Math.min(i + DELETE_CHUNK, nonChartShowIds.length)} of ${nonChartShowIds.length}`);
        }
    }
    log.endGroup();

    // ---------------------------------------------------------------
    log.group(`Step 5: Trim chart shows to latest ${cfg.EPISODES_PER_SHOW} points`);
    let trimmedShows = 0;
    let trimmedPoints = 0;
    const trimDeleteIds = [];
    const touchedShowIds = [];

    for (const [showId, points] of byShow.entries()) {
        if (!chartShowIds.has(showId)) continue;
        if (points.length <= cfg.EPISODES_PER_SHOW) continue;
        points.sort((a, b) => b.date - a.date);
        const excess = points.slice(cfg.EPISODES_PER_SHOW);
        trimDeleteIds.push(...excess.map(p => p.id));
        trimmedShows++;
        trimmedPoints += excess.length;
        touchedShowIds.push(showId);
    }
    log.info(`Shows over the ${cfg.EPISODES_PER_SHOW}-episode limit: ${log.fmt(trimmedShows)} (${log.fmt(trimmedPoints)} excess points)`);

    if (!DRY_RUN && trimDeleteIds.length > 0) {
        for (let i = 0; i < trimDeleteIds.length; i += DELETE_CHUNK) {
            await qdrant.deleteByIds(cfg.EPISODES_COLLECTION, trimDeleteIds.slice(i, i + DELETE_CHUNK));
        }
        log.info(`Deleted ${log.fmt(trimDeleteIds.length)} excess points`);
    }
    log.endGroup();

    // ---------------------------------------------------------------
    log.group('Step 6: Cross-check qdrant_vectorized flags');
    const flaggedRes = await turso.execute(`
        SELECT p.id FROM podcasts p
        WHERE p.qdrant_vectorized = 1
          AND p.itunes_id IN (SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE itunes_id IS NOT NULL)
    `);
    const flaggedIds = turso.rows(flaggedRes).map(r => String(r[0]));
    const holes = flaggedIds.filter(id => !byShow.has(id) || byShow.get(id).length === 0);
    log.info(`Shows flagged vectorized=1 with ZERO points in Qdrant (holes): ${log.fmt(holes.length)}`);

    const toReset = [...new Set([...holes, ...touchedShowIds])];
    log.info(`Flags to reset (holes + trimmed shows): ${log.fmt(toReset.length)}`);

    if (!DRY_RUN && toReset.length > 0) {
        const CHUNK = 100;
        for (let i = 0; i < toReset.length; i += CHUNK) {
            const chunk = toReset.slice(i, i + CHUNK);
            const placeholders = chunk.map(() => '?').join(',');
            await turso.execute(
                `UPDATE podcasts SET qdrant_vectorized = 0 WHERE id IN (${placeholders})`,
                chunk
            );
        }
        log.info('Flags reset - the pipeline will heal these shows under the embedding budget');
    }
    log.endGroup();

    // ---------------------------------------------------------------
    log.group('Step 7: Verify space reclamation');
    if (!DRY_RUN) {
        await new Promise(r => setTimeout(r, 15000)); // let optimizer start merging
        const after = await qdrant.collectionInfo(cfg.EPISODES_COLLECTION);
        log.info(`episodes points: ${log.fmt(beforeEpisodes.pointsCount)} -> ${log.fmt(after.pointsCount)} (status=${after.status})`);
        const expectedRemoved = nonChartPoints + trimmedPoints;
        log.info(`Expected removal: ~${log.fmt(expectedRemoved)} points. Optimizer merges segments in background; disk frees over the next minutes.`);
        if (after.pointsCount >= beforeEpisodes.pointsCount && expectedRemoved > 0) {
            log.warn('Point count did not decrease - deletes may still be processing. Re-run this script to verify; if it never drops, consider the drop-and-rebuild fallback.');
        }
    }
    log.endGroup();

    // ---------------------------------------------------------------
    log.group('Step 8: Enable quantization');
    if (SKIP_QUANTIZATION) {
        log.info('Skipped (--skip-quantization)');
    } else if (DRY_RUN) {
        log.info('Dry run - would enable int8 scalar quantization + on-disk originals on episodes');
    } else {
        await qdrant.enableQuantization(cfg.EPISODES_COLLECTION);
        log.info('int8 scalar quantization + on-disk originals enabled on episodes collection (background rebuild)');
    }
    log.endGroup();

    // ---------------------------------------------------------------
    const stats = turso.getStats();
    log.summaryTable('Qdrant Remediation', [
        { stage: 'Scan', reads: allPoints.length, detail: `${byShow.size} shows in collection` },
        { stage: 'Non-chart deletion', detail: `${nonChartShowIds.length} shows / ${nonChartPoints} points${DRY_RUN ? ' (dry run)' : ''}` },
        { stage: 'Trim to latest ' + cfg.EPISODES_PER_SHOW, detail: `${trimmedShows} shows / ${trimmedPoints} points${DRY_RUN ? ' (dry run)' : ''}` },
        { stage: 'Flag resets', reads: stats.reads, writes: stats.writes, detail: `${toReset.length} shows queued for healing` },
    ]);
    log.info(`\nDone. Turso cost: ${log.fmt(stats.reads)} reads / ${log.fmt(stats.writes)} writes`);
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`Remediation failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
