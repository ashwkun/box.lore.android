'use strict';

/**
 * Pipeline state persisted in git (scripts/data/sync_cache.json).
 *
 * Format v2 (compact keys to keep the committed file small):
 * {
 *   "version": 2,
 *   "candidatesRefreshedAt": <ms epoch of last DB candidate refresh>,
 *   "chartsRefreshedAt": <ms epoch of last charts refresh>,
 *   "candidateIds": ["<podId>", ...],
 *   "shows": {
 *     "<podId>": {
 *       "c": <lastCheckedMs>, "e": "<latestEpId>", "m": "<medium>",
 *       "n": 1 (present only for News shows), "s": <lastSeenInChartsMs>
 *     }
 *   }
 * }
 *
 * Migrates transparently from the legacy v1 flat map { podId: lastCheckedMs }.
 * This file is pipeline bookkeeping ONLY - the app never reads it.
 */

const fs = require('fs');
const path = require('path');
const log = require('./log');
const { STATE_FILE } = require('./config');

function load() {
    let raw = {};
    try {
        if (fs.existsSync(STATE_FILE)) {
            raw = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8') || '{}');
        }
    } catch (e) {
        log.warn(`Failed to parse ${STATE_FILE}, starting fresh: ${e.message}`);
        raw = {};
    }

    if (raw.version === 2 && raw.shows) {
        if (raw.candidateIds) {
            raw.candidateIds = raw.candidateIds.map(String);
        }
        return raw;
    }

    // v1 migration: flat { podId: timestamp }
    const shows = {};
    let migrated = 0;
    for (const [id, val] of Object.entries(raw)) {
        if (typeof val === 'number') {
            shows[id] = { c: val };
            migrated++;
        }
    }
    if (migrated > 0) {
        log.info(`[STATE] Migrated ${migrated} legacy v1 cache entries to v2 format`);
    }
    return { version: 2, candidatesRefreshedAt: 0, shows };
}

/** Save compact (single line) - this file is committed on every run. */
function save(state) {
    const dir = path.dirname(STATE_FILE);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(STATE_FILE, JSON.stringify(state));
}

function getShow(state, podId) {
    return state.shows[String(podId)] || null;
}

function recordCheck(state, podId, { latestEpId } = {}) {
    const id = String(podId);
    const rec = state.shows[id] || {};
    rec.c = Date.now();
    if (latestEpId !== undefined && latestEpId !== null) rec.e = String(latestEpId);
    state.shows[id] = rec;
    return rec;
}

/** Remove entries for shows no longer present. Returns count pruned. */
function pruneShows(state, keepIds) {
    const keep = new Set([...keepIds].map(String));
    let pruned = 0;
    for (const id of Object.keys(state.shows)) {
        if (!keep.has(id)) {
            delete state.shows[id];
            pruned++;
        }
    }
    return pruned;
}

module.exports = { load, save, getShow, recordCheck, pruneShows };
