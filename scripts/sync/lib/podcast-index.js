'use strict';

/**
 * Shared Podcast Index API client with a global token-bucket rate limiter
 * (~3.5 req/s) and 429 exponential backoff. All pipeline PI calls go
 * through here so total API pressure is bounded regardless of stage.
 */

const crypto = require('crypto');
const log = require('./log');

const API_KEY = process.env.PODCAST_INDEX_API_KEY;
const API_SECRET = process.env.PODCAST_INDEX_API_SECRET;
const API_BASE = 'https://api.podcastindex.org/api/1.0';

const RATE_LIMIT_RPS = 3.5;
const MAX_RETRIES = 3;

let apiCallCount = 0;
let nextSlotAt = 0;

function assertEnv() {
    if (!API_KEY || !API_SECRET) {
        log.error('Missing PODCAST_INDEX_API_KEY or PODCAST_INDEX_API_SECRET');
        process.exit(1);
    }
}

function getApiCallCount() {
    return apiCallCount;
}

function authHeaders() {
    const t = Math.floor(Date.now() / 1000);
    const hash = crypto.createHash('sha1').update(API_KEY + API_SECRET + t).digest('hex');
    return {
        'User-Agent': 'BoxLore/1.0',
        'X-Auth-Key': API_KEY,
        'X-Auth-Date': String(t),
        'Authorization': hash,
    };
}

/** Global rate limiter: serializes request start times at RATE_LIMIT_RPS. */
async function acquireSlot() {
    const interval = 1000 / RATE_LIMIT_RPS;
    const now = Date.now();
    const slot = Math.max(nextSlotAt, now);
    nextSlotAt = slot + interval;
    if (slot > now) {
        await new Promise(r => setTimeout(r, slot - now));
    }
}

async function apiGet(path, retries = MAX_RETRIES) {
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        await acquireSlot();
        try {
            apiCallCount++;
            const res = await fetch(`${API_BASE}${path}`, { headers: authHeaders() });
            if (res.status === 429) {
                if (attempt <= retries) {
                    const backoff = 1000 * Math.pow(2, attempt - 1);
                    log.warn(`PI API 429 on ${path}; retrying in ${backoff}ms (attempt ${attempt}/${retries})`);
                    await new Promise(r => setTimeout(r, backoff));
                    continue;
                }
                throw new Error('PI API 429: Too Many Requests');
            }
            if (!res.ok) throw new Error(`PI API error: ${res.status}`);
            return await res.json();
        } catch (e) {
            if (attempt <= retries && /fetch failed|socket|timeout|UND_ERR/.test(e.message)) {
                const backoff = 1000 * Math.pow(2, attempt - 1);
                log.warn(`PI API request failed on ${path}: ${e.message}; retrying in ${backoff}ms`);
                await new Promise(r => setTimeout(r, backoff));
                continue;
            }
            throw e;
        }
    }
}

/** Latest episodes for a feed (newest first). Throws on failure. */
async function episodesByFeedId(feedId, max) {
    const data = await apiGet(`/episodes/byfeedid?id=${feedId}&max=${max}`);
    return data.items || [];
}

/** Feed metadata by PI feed id. Returns null when not found. */
async function podcastByFeedId(feedId) {
    const data = await apiGet(`/podcasts/byfeedid?id=${feedId}`);
    const feed = data.feed;
    if (!feed || (Array.isArray(feed) && feed.length === 0) || !feed.id) return null;
    return feed;
}

/** Feed metadata by iTunes id. Returns null when not found. */
async function podcastByItunesId(itunesId) {
    const data = await apiGet(`/podcasts/byitunesid?id=${itunesId}`);
    const feed = data.feed;
    if (!feed || (Array.isArray(feed) && feed.length === 0) || !feed.id) return null;
    return feed;
}

module.exports = {
    assertEnv, getApiCallCount,
    episodesByFeedId, podcastByFeedId, podcastByItunesId,
};
