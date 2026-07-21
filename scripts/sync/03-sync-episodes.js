#!/usr/bin/env node
'use strict';

/**
 * Stage 3: Sync latest-episode metadata for chart shows from the PI API.
 *
 * - Candidate list (id, latest_ep_id, categories, medium) is cached in the
 *   git state file and re-queried from Turso only after a charts refresh or
 *   when older than 20h - the other runs cost near-zero Turso reads.
 * - Staleness gating: News shows re-checked after 8h, others after 24h.
 * - Latest-episode-unchanged shows cost 0 Turso writes (state-only update).
 * - New episodes write latest_ep_* + qdrant_vectorized=0 (feeds stage 4).
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const pi = require('./lib/podcast-index');
const state = require('./lib/state');
const text = require('./lib/text');
const cfg = require('./lib/config');

const CANDIDATE_CACHE_MAX_AGE_MS = 20 * 60 * 60 * 1000;
const CONCURRENCY = 5;

/** Refresh candidate cache from Turso and seed state records. */
async function refreshCandidates(st) {
    log.info('[CANDIDATES] Refreshing candidate list from Turso');
    const res = await turso.execute(`
        SELECT p.id, p.latest_ep_id, p.categories, p.medium
        FROM podcasts p
        WHERE p.itunes_id IN (SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE itunes_id IS NOT NULL)
    `);
    const ids = [];
    for (const [id, latestEpId, categories, medium] of turso.rows(res)) {
        const podId = String(id);
        ids.push(podId);
        const rec = st.shows[podId] || {};
        // Seed from DB only when state doesn't know these yet (state is the
        // authority afterwards, since this pipeline is the only writer).
        if (rec.e === undefined && latestEpId) rec.e = String(latestEpId);
        // Compact flag: store n:1 for News shows instead of the category string
        if ((categories || '').includes('News')) rec.n = 1; else delete rec.n;
        if (rec.m === undefined && medium) rec.m = medium;
        st.shows[podId] = rec;
    }
    st.candidateIds = ids;
    st.candidatesRefreshedAt = Date.now();
    log.info(`[CANDIDATES] ${log.fmt(ids.length)} chart shows cached`);
}

async function main() {
    turso.assertEnv();
    pi.assertEnv();
    turso.beginStep('sync-episodes');
    await turso.healthCheck();

    const st = state.load();

    log.banner('Stage 3 · Sync Episodes', {
        'Staleness tiers': 'News 8h · Regular 24h (±10% jitter)',
        'Per-run check cap': log.fmt(cfg.MAX_CHECKS_PER_RUN),
        'Concurrency': String(CONCURRENCY),
    });

    // --- Candidate cache: refresh only when stale or after charts refresh ---
    const cacheAge = Date.now() - (st.candidatesRefreshedAt || 0);
    const chartsNewerThanCache = (st.chartsRefreshedAt || 0) > (st.candidatesRefreshedAt || 0);
    if (!st.candidateIds || chartsNewerThanCache || cacheAge > CANDIDATE_CACHE_MAX_AGE_MS) {
        await refreshCandidates(st);
    } else {
        log.info(`[CANDIDATES] Using cached list (${log.fmt(st.candidateIds.length)} shows, age ${Math.round(cacheAge / 3600000)}h) - 0 Turso reads`);
    }

    // --- Staleness gating ---
    // Deterministic per-show jitter (+/-STALENESS_JITTER) spreads check times
    // so the fleet doesn't re-synchronize into one giant wave per day.
    const jitterFactor = (id) => {
        let h = 0;
        for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
        return 1 + ((h % 1000) / 1000 - 0.5) * 2 * cfg.STALENESS_JITTER;
    };

    const now = Date.now();
    let neverChecked = 0, staleNews = 0, staleRegular = 0;
    const allDue = st.candidateIds.filter(id => {
        const rec = st.shows[id] || {};
        if (!rec.c) { neverChecked++; return true; }
        const isNews = rec.n === 1;
        const threshold = (isNews ? cfg.NEWS_STALE_MS : cfg.REGULAR_STALE_MS) * jitterFactor(id);
        if (now - rec.c >= threshold) {
            if (isNews) staleNews++; else staleRegular++;
            return true;
        }
        return false;
    });
    // Oldest checks first, then cap per run: deferred shows lead the queue
    // next run, so backlogs self-distribute across the 5 daily runs.
    allDue.sort((a, b) => (st.shows[a]?.c || 0) - (st.shows[b]?.c || 0));
    const due = allDue.slice(0, cfg.MAX_CHECKS_PER_RUN);
    const deferred = allDue.length - due.length;

    log.group('Sync plan');
    log.info(`Chart shows:        ${log.fmt(st.candidateIds.length)}`);
    log.info(`Due for check:      ${log.fmt(allDue.length)}`);
    log.info(`Checking this run:  ${log.fmt(due.length)} (cap ${log.fmt(cfg.MAX_CHECKS_PER_RUN)}, ${log.fmt(deferred)} deferred to next run)`);
    log.info(`- never checked:    ${log.fmt(neverChecked)}`);
    log.info(`- stale news (8h):  ${log.fmt(staleNews)}`);
    log.info(`- stale other (24h):${log.fmt(staleRegular)}`);
    if (deferred >= cfg.BACKLOG_WARN_THRESHOLD) {
        log.warn(`Episode sync backlog high: ${log.fmt(deferred)} deferred (threshold ${log.fmt(cfg.BACKLOG_WARN_THRESHOLD)})`);
        console.log(`::warning title=Episode sync backlog::${deferred} shows deferred beyond this run's cap`);
    }
    const oldestCheckAge = allDue.reduce((maxAge, id) => {
        const checkedAt = st.shows[id]?.c || 0;
        if (!checkedAt) return Math.max(maxAge, cfg.MAX_CHECK_AGE_WARN_MS + 1);
        return Math.max(maxAge, now - checkedAt);
    }, 0);
    if (oldestCheckAge > cfg.MAX_CHECK_AGE_WARN_MS) {
        const hours = Math.round(oldestCheckAge / 3600000);
        log.warn(`Oldest due show check age ${hours}h exceeds ${Math.round(cfg.MAX_CHECK_AGE_WARN_MS / 3600000)}h`);
        console.log(`::warning title=Episode sync freshness::Oldest due show is ${hours}h overdue`);
    }
    const chartsAge = now - (st.chartsRefreshedAt || 0);
    if (st.chartsRefreshedAt && chartsAge > cfg.CHART_REFRESH_STALE_WARN_MS) {
        const hours = Math.round(chartsAge / 3600000);
        log.warn(`Charts last refreshed ${hours}h ago`);
        console.log(`::warning title=Chart refresh stale::Charts last refreshed ${hours}h ago`);
    }
    log.endGroup();

    // --- Process ---
    let updated = 0, unchanged = 0, empty = 0, errors = 0;
    const prog = log.progress(due.length, 'episode-sync', 5);

    for (let i = 0; i < due.length; i += CONCURRENCY) {
        const batch = due.slice(i, i + CONCURRENCY);
        await Promise.all(batch.map(async (podId) => {
            const rec = st.shows[podId] || {};
            try {
                const episodes = await pi.episodesByFeedId(podId, 1);
                if (episodes.length === 0) {
                    empty++;
                    state.recordCheck(st, podId);
                    return;
                }
                const latest = episodes[0];

                if (rec.e && String(latest.id) === String(rec.e)) {
                    unchanged++;
                    state.recordCheck(st, podId);
                    return;
                }

                // New/changed episode: resolve medium if unknown, then write.
                let medium = rec.m;
                if (!medium) {
                    try {
                        const feed = await pi.podcastByFeedId(podId);
                        medium = feed?.medium || 'podcast';
                    } catch {
                        medium = 'podcast';
                    }
                }

                await turso.execute(`
                    UPDATE podcasts SET
                        latest_ep_id = ?, latest_ep_title = ?, latest_ep_date = ?,
                        latest_ep_duration = ?, latest_ep_url = ?, latest_ep_image = ?,
                        latest_ep_type = ?, latest_ep_description = ?,
                        latest_ep_chapters_url = ?, latest_ep_transcript_url = ?,
                        latest_ep_persons = ?, latest_ep_transcripts = ?,
                        medium = ?, last_ep_sync = ?, qdrant_vectorized = 0
                    WHERE id = ?
                `, [
                    String(latest.id),
                    latest.title || '',
                    latest.datePublished || 0,
                    latest.duration || 0,
                    latest.enclosureUrl || '',
                    latest.image || latest.feedImage || '',
                    latest.enclosureType || 'audio/mpeg',
                    text.cleanDescription(latest.description),
                    latest.chaptersUrl || null,
                    latest.transcriptUrl || null,
                    latest.persons ? JSON.stringify(latest.persons) : null,
                    latest.transcripts ? JSON.stringify(latest.transcripts) : null,
                    medium,
                    Date.now(),
                    podId,
                ]);
                updated++;
                state.recordCheck(st, podId, { latestEpId: latest.id }).m = medium;
            } catch (e) {
                errors++;
                log.warn(`Show ${podId}: ${e.message}`);
                // No recordCheck on error -> retried next run
            }
        }));
        for (let k = 0; k < batch.length; k++) prog.tick(`new ${updated} / same ${unchanged}`);
        turso.flushStats();
        state.save(st);
    }

    state.save(st);
    const stats = turso.getStats();
    log.costFooter('Stage 3 · Sync Episodes', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${log.fmt(due.length)} checked (${log.fmt(deferred)} deferred) · ${updated} new · ${unchanged} unchanged · ${empty} empty · ${errors} errors`,
    });
    log.summaryTable('Stage 3: Sync Episodes', [{
        stage: 'sync-episodes',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${due.length} checked (${deferred} deferred): ${updated} new, ${unchanged} unchanged, ${empty} empty, ${errors} errors`,
    }]);

    // Fail loudly when the API is systematically failing (not on scattered errors)
    if (due.length > 20 && errors > due.length / 2) {
        log.error(`More than half of episode checks failed (${errors}/${due.length})`);
        process.exit(1);
    }
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`sync-episodes failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
