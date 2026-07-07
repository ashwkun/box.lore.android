#!/usr/bin/env node
'use strict';

/**
 * Stage 4: Generate episode embeddings for pending full-tier chart shows and
 * upsert to the Qdrant 'episodes' collection.
 *
 * Bounded by MAX_EMBEDDINGS_PER_RUN (a budget of new vectors, NOT a show
 * count): incremental shows (1-2 new eps) always flow through; leftover
 * budget drains cold-start/backlog shows (up to EPISODES_PER_SHOW each),
 * oldest-first. Prune-before-insert keeps every show at strictly the latest
 * EPISODES_PER_SHOW points. Upserts use wait=true so qdrant_vectorized=1 is
 * only set after vectors are durable.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const pi = require('./lib/podcast-index');
const embedder = require('./lib/embedder');
const text = require('./lib/text');
const cfg = require('./lib/config');

const UPSERT_BATCH = 100;

async function ensureIndexes() {
    await turso.execute(`CREATE INDEX IF NOT EXISTS idx_podcasts_pending_ep_vec
                         ON podcasts(qdrant_vectorized) WHERE qdrant_vectorized = 0`);
}

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    pi.assertEnv();
    turso.beginStep('vectorize-episodes');
    await turso.healthCheck();
    await ensureIndexes();
    await qdrant.ensureEpisodesCollection(cfg.VECTOR_DIM, cfg.EPISODES_COLLECTION);

    if (cfg.FULL_TIER_COUNTRIES.length === 0) {
        log.info('No full-tier countries configured - nothing to vectorize');
        return;
    }

    // --- Pending shows (oldest flagged first) ---
    const countryPlaceholders = cfg.FULL_TIER_COUNTRIES.map(() => '?').join(',');
    const res = await turso.execute(`
        SELECT p.id, p.title, p.categories, p.author, p.image_url, p.language
        FROM podcasts p
        WHERE (p.qdrant_vectorized = 0 OR p.qdrant_vectorized IS NULL)
          AND p.itunes_id IN (
              SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE country IN (${countryPlaceholders})
          )
        ORDER BY (p.last_ep_sync IS NULL) DESC, p.last_ep_sync ASC
    `, cfg.FULL_TIER_COUNTRIES);

    const pending = turso.rows(res).map(r => ({
        id: String(r[0]),
        title: r[1] || 'Unknown Show',
        categories: r[2] || 'Podcast',
        author: r[3] || '',
        image_url: r[4] || '',
        language: r[5] || 'en',
    }));
    log.banner('Stage 4 · Vectorize Episodes', {
        'Pending shows': log.fmt(pending.length),
        'Embedding budget': log.fmt(cfg.MAX_EMBEDDINGS_PER_RUN),
        'Episodes per show': String(cfg.EPISODES_PER_SHOW),
        'Collection': cfg.EPISODES_COLLECTION,
    });
    if (pending.length === 0) {
        log.summaryTable('Stage 4: Vectorize Episodes', [{
            stage: 'vectorize-episodes', reads: turso.getStats().reads,
            writes: turso.getStats().writes, detail: 'queue empty',
        }]);
        return;
    }

    let budget = cfg.MAX_EMBEDDINGS_PER_RUN;
    let embedded = 0;
    let showsCompleted = 0;
    let showsSkippedExisting = 0;
    let errors = 0;
    let processedCount = 0;

    // Points buffered for batch upsert; flag updates follow durable writes.
    let pointsQueue = [];
    let flagQueue = [];

    async function flush() {
        if (pointsQueue.length > 0) {
            await qdrant.upsert(cfg.EPISODES_COLLECTION, pointsQueue);
            pointsQueue = [];
        }
        if (flagQueue.length > 0) {
            const placeholders = flagQueue.map(() => '?').join(',');
            await turso.execute(
                `UPDATE podcasts SET qdrant_vectorized = 1 WHERE id IN (${placeholders})`,
                flagQueue
            );
            flagQueue = [];
        }
        turso.flushStats();
    }

    const prog = log.progress(pending.length, 'vectorize', 5);

    for (const pod of pending) {
        if (budget <= 0) {
            log.info(`[BUDGET] Embedding budget exhausted (${embedded}/${cfg.MAX_EMBEDDINGS_PER_RUN}). Remaining backlog: ${pending.length - processedCount} shows`);
            break;
        }
        processedCount++;

        let episodes;
        try {
            episodes = await pi.episodesByFeedId(pod.id, cfg.EPISODES_PER_SHOW);
        } catch (e) {
            errors++;
            log.warn(`Show ${pod.id} (${pod.title.substring(0, 40)}): episode fetch failed: ${e.message}`);
            prog.tick();
            continue;
        }

        if (episodes.length === 0) {
            flagQueue.push(pod.id);
            showsCompleted++;
            prog.tick();
            continue;
        }

        const eps = episodes.slice(0, cfg.EPISODES_PER_SHOW).map(ep => ({
            raw: ep,
            uuid: qdrant.stableUUID(ep.id),
        }));
        const uuids = eps.map(e => e.uuid);

        // Prune BEFORE insert: drop any points for this show outside its latest set.
        try {
            await qdrant.deleteByFilter(cfg.EPISODES_COLLECTION, {
                must: [{ key: 'podcast_id', match: { value: parseInt(pod.id, 10) || 0 } }],
                must_not: [{ has_id: uuids }],
            });
        } catch (e) {
            log.warn(`Prune failed for show ${pod.id}: ${e.message}`);
        }

        const existing = await qdrant.existingIds(cfg.EPISODES_COLLECTION, uuids);
        const toEmbed = eps.filter(e => !existing.has(e.uuid));
        showsSkippedExisting += existing.size;

        if (toEmbed.length === 0) {
            flagQueue.push(pod.id);
            showsCompleted++;
            prog.tick();
            continue;
        }

        let showFailed = false;
        const showPoints = [];
        for (const item of toEmbed) {
            const ep = item.raw;
            const cleaned = text.cleanDescription(ep.description || '');
            const embedText = text.episodeEmbedText({ title: ep.title, cleanedDescription: cleaned }, pod);
            try {
                const vector = await embedder.embed(embedText);
                showPoints.push({
                    id: item.uuid,
                    vector,
                    payload: {
                        id: parseInt(ep.id, 10) || 0,
                        title: ep.title || '',
                        description: (ep.description || '').substring(0, cfg.PAYLOAD_DESCRIPTION_MAX),
                        podcast_id: parseInt(pod.id, 10) || 0,
                        podcast_title: pod.title,
                        podcast_author: pod.author,
                        podcast_image_url: pod.image_url,
                        podcast_categories: pod.categories,
                        language: pod.language,
                        audio_url: ep.enclosureUrl || '',
                        image_url: ep.image || ep.feedImage || pod.image_url || '',
                        published_date: ep.datePublished || 0,
                        duration: ep.duration || 0,
                    },
                });
            } catch (e) {
                errors++;
                showFailed = true;
                log.warn(`Embedding failed for "${(ep.title || '').substring(0, 40)}" (show ${pod.id}): ${e.message}`);
            }
        }

        embedded += showPoints.length;
        budget -= showPoints.length;
        pointsQueue.push(...showPoints);
        if (!showFailed) {
            flagQueue.push(pod.id);
            showsCompleted++;
        }

        if (pointsQueue.length >= UPSERT_BATCH) {
            try {
                await flush();
            } catch (e) {
                errors += pointsQueue.length;
                log.error(`Qdrant upsert batch failed: ${e.message}`);
                pointsQueue = [];
                flagQueue = [];
            }
        }
        prog.tick(`embedded ${embedded}/${cfg.MAX_EMBEDDINGS_PER_RUN}`);
    }

    try {
        await flush();
    } catch (e) {
        errors += pointsQueue.length;
        log.error(`Final Qdrant flush failed: ${e.message}`);
    }

    const backlog = pending.length - processedCount;
    const stats = turso.getStats();
    log.costFooter('Stage 4 · Vectorize Episodes', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${log.fmt(embedded)}/${log.fmt(cfg.MAX_EMBEDDINGS_PER_RUN)} budget used · ${showsCompleted} shows done · ${log.fmt(showsSkippedExisting)} already existed · backlog ${log.fmt(backlog)} · ${errors} errors`,
    });
    log.summaryTable('Stage 4: Vectorize Episodes', [{
        stage: 'vectorize-episodes',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${embedded}/${cfg.MAX_EMBEDDINGS_PER_RUN} budget used, ${showsCompleted} shows done, backlog ${backlog}, ${errors} errors`,
    }]);

    if (processedCount > 20 && errors > processedCount) {
        log.error('Error count exceeds processed shows - failing run for visibility');
        process.exit(1);
    }
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`vectorize-episodes failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
