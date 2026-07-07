#!/usr/bin/env node
'use strict';

/**
 * Stage 5: Generate show-level embeddings for pending full-tier chart shows
 * and upsert to the Qdrant 'podcasts' collection (1 vector per show).
 * Shares the embedding budget philosophy of stage 4 (1 embedding per show,
 * so this stage drains fast and cheap). wait=true before flag flips.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const embedder = require('./lib/embedder');
const text = require('./lib/text');
const cfg = require('./lib/config');

const UPSERT_BATCH = 100;

async function ensureIndexes() {
    await turso.execute(`CREATE INDEX IF NOT EXISTS idx_podcasts_pending_show_vec
                         ON podcasts(qdrant_podcast_vectorized) WHERE qdrant_podcast_vectorized = 0`);
}

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    turso.beginStep('vectorize-shows');
    await turso.healthCheck();
    await ensureIndexes();
    await qdrant.ensurePodcastsCollection(cfg.VECTOR_DIM, cfg.PODCASTS_COLLECTION);

    if (cfg.FULL_TIER_COUNTRIES.length === 0) {
        log.info('No full-tier countries configured - nothing to vectorize');
        return;
    }

    const countryPlaceholders = cfg.FULL_TIER_COUNTRIES.map(() => '?').join(',');
    const res = await turso.execute(`
        SELECT p.id, p.title, p.categories, p.author, p.image_url, p.language,
               p.description, p.feed_url, p.website_url
        FROM podcasts p
        WHERE (p.qdrant_podcast_vectorized = 0 OR p.qdrant_podcast_vectorized IS NULL)
          AND p.itunes_id IN (
              SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE country IN (${countryPlaceholders})
          )
    `, cfg.FULL_TIER_COUNTRIES);

    const pending = turso.rows(res).map(r => ({
        id: String(r[0]),
        title: r[1] || 'Unknown Show',
        categories: r[2] || 'Podcast',
        author: r[3] || '',
        image_url: r[4] || '',
        language: r[5] || 'en',
        description: r[6] || '',
        feed_url: r[7] || '',
        website_url: r[8] || '',
    }));
    log.banner('Stage 5 · Vectorize Shows', {
        'Pending shows': log.fmt(pending.length),
        'Embedding budget': log.fmt(cfg.MAX_EMBEDDINGS_PER_RUN),
        'Collection': cfg.PODCASTS_COLLECTION,
    });
    if (pending.length === 0) {
        log.summaryTable('Stage 5: Vectorize Shows', [{
            stage: 'vectorize-shows', reads: turso.getStats().reads,
            writes: turso.getStats().writes, detail: 'queue empty',
        }]);
        return;
    }

    // Existence check: shows already in Qdrant just need their flag set.
    const withUuid = pending.map(pod => ({ pod, uuid: qdrant.stableUUID(pod.id) }));
    const existing = await qdrant.existingIds(cfg.PODCASTS_COLLECTION, withUuid.map(w => w.uuid));
    const alreadyIndexed = withUuid.filter(w => existing.has(w.uuid));
    const toEmbed = withUuid.filter(w => !existing.has(w.uuid));

    if (alreadyIndexed.length > 0) {
        const CHUNK = 100;
        for (let i = 0; i < alreadyIndexed.length; i += CHUNK) {
            const ids = alreadyIndexed.slice(i, i + CHUNK).map(w => w.pod.id);
            const placeholders = ids.map(() => '?').join(',');
            await turso.execute(
                `UPDATE podcasts SET qdrant_podcast_vectorized = 1 WHERE id IN (${placeholders})`,
                ids
            );
        }
        log.info(`${alreadyIndexed.length} shows already indexed - flags set`);
    }

    // Cap embeds by the shared budget (1 embedding per show).
    const budgeted = toEmbed.slice(0, cfg.MAX_EMBEDDINGS_PER_RUN);
    let embedded = 0;
    let errors = 0;
    let pointsQueue = [];
    let flagQueue = [];

    async function flush() {
        if (pointsQueue.length > 0) {
            await qdrant.upsert(cfg.PODCASTS_COLLECTION, pointsQueue);
            pointsQueue = [];
        }
        if (flagQueue.length > 0) {
            const placeholders = flagQueue.map(() => '?').join(',');
            await turso.execute(
                `UPDATE podcasts SET qdrant_podcast_vectorized = 1 WHERE id IN (${placeholders})`,
                flagQueue
            );
            flagQueue = [];
        }
        turso.flushStats();
    }

    const runProg = log.budgetProgress(budgeted.length, 'shows', 50);
    for (const { pod, uuid } of budgeted) {
        try {
            const vector = await embedder.embed(text.podcastEmbedText(pod));
            pointsQueue.push({
                id: uuid,
                vector,
                payload: {
                    id: parseInt(pod.id, 10) || 0,
                    title: pod.title,
                    author: pod.author,
                    description: (pod.description || '').substring(0, cfg.PAYLOAD_DESCRIPTION_MAX),
                    image_url: pod.image_url,
                    categories: pod.categories,
                    language: pod.language,
                    feed_url: pod.feed_url,
                    website_url: pod.website_url,
                },
            });
            flagQueue.push(pod.id);
            embedded++;
            runProg.tick();
        } catch (e) {
            errors++;
            log.warn(`Show embedding failed for "${pod.title.substring(0, 40)}" (${pod.id}): ${e.message}`);
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
    }

    runProg.flush();

    try {
        await flush();
    } catch (e) {
        errors += pointsQueue.length;
        log.error(`Final Qdrant flush failed: ${e.message}`);
    }

    const backlog = toEmbed.length - budgeted.length;
    const stats = turso.getStats();
    log.costFooter('Stage 5 · Vectorize Shows', {
        reads: stats.reads,
        writes: stats.writes,
        detail: `${log.fmt(embedded)} embedded · ${log.fmt(alreadyIndexed.length)} already indexed · backlog ${log.fmt(backlog)} · ${errors} errors`,
    });
    log.summaryTable('Stage 5: Vectorize Shows', [{
        stage: 'vectorize-shows',
        reads: stats.reads,
        writes: stats.writes,
        detail: `${embedded} embedded, ${alreadyIndexed.length} pre-existing, backlog ${backlog}, ${errors} errors`,
    }]);
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`vectorize-shows failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
