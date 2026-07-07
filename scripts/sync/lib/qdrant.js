'use strict';

/**
 * Shared Qdrant REST client: retries, stable UUIDs, existence checks,
 * upserts (wait=true so flags are only flipped after durable writes),
 * deletes, scroll, and collection info.
 */

const crypto = require('crypto');
const log = require('./log');

const QDRANT_URL = process.env.QDRANT_URL;
const QDRANT_API_KEY = process.env.QDRANT_API_KEY;

const MAX_RETRIES = 5;
const INITIAL_DELAY_MS = 2000;

function assertEnv() {
    if (!QDRANT_URL || !QDRANT_API_KEY) {
        log.error('Missing QDRANT_URL or QDRANT_API_KEY');
        process.exit(1);
    }
}

/** Stable UUID from any string id (md5-derived, matches existing points). */
function stableUUID(strId) {
    const h = crypto.createHash('md5').update(String(strId)).digest('hex');
    return `${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}`;
}

async function request(path, options = {}, retries = MAX_RETRIES) {
    for (let attempt = 1; attempt <= retries; attempt++) {
        try {
            const response = await fetch(`${QDRANT_URL}${path}`, {
                ...options,
                headers: {
                    'api-key': QDRANT_API_KEY,
                    'Content-Type': 'application/json',
                    ...(options.headers || {}),
                },
            });
            if (response.status === 429 || response.status >= 500) {
                const text = await response.text();
                if (attempt < retries) {
                    const backoff = INITIAL_DELAY_MS * Math.pow(2, attempt - 1);
                    log.warn(`Qdrant ${response.status} on ${path} (attempt ${attempt}/${retries}). Retrying in ${backoff}ms`);
                    await new Promise(r => setTimeout(r, backoff));
                    continue;
                }
                throw new Error(`Qdrant HTTP ${response.status}: ${text}`);
            }
            if (!response.ok) {
                const text = await response.text();
                throw new Error(`Qdrant HTTP ${response.status}: ${text}`);
            }
            return await response.json();
        } catch (e) {
            if (attempt < retries && /fetch failed|socket|timeout|UND_ERR/.test(e.message)) {
                const backoff = INITIAL_DELAY_MS * Math.pow(2, attempt - 1);
                log.warn(`Qdrant request failed (attempt ${attempt}/${retries}): ${e.message}. Retrying in ${backoff}ms`);
                await new Promise(r => setTimeout(r, backoff));
                continue;
            }
            throw e;
        }
    }
}

/** Collection info: { pointsCount, status, vectorSize } or null on 404. */
async function collectionInfo(collection) {
    try {
        const res = await request(`/collections/${collection}`);
        return {
            pointsCount: res.result?.points_count ?? null,
            status: res.result?.status ?? null,
            vectorSize: res.result?.config?.params?.vectors?.size ?? null,
        };
    } catch (e) {
        if (e.message.includes('404')) return null;
        throw e;
    }
}

async function ensureCollection(collection, dim) {
    const info = await collectionInfo(collection);
    if (info) return info;
    log.info(`[QDRANT] Creating collection '${collection}' (dim=${dim}, int8 quantized, on-disk originals)`);
    await request(`/collections/${collection}`, {
        method: 'PUT',
        body: JSON.stringify({
            vectors: { size: dim, distance: 'Cosine', on_disk: true },
            quantization_config: {
                scalar: { type: 'int8', quantile: 0.99, always_ram: true },
            },
        }),
    });
    return collectionInfo(collection);
}

/** Payload schema map from collection info, or null if the collection is missing. */
async function payloadSchema(collection) {
    try {
        const res = await request(`/collections/${collection}`);
        return res.result?.payload_schema ?? {};
    } catch (e) {
        if (e.message.includes('404')) return null;
        throw e;
    }
}

/**
 * Create a payload index when missing. Required before filter deletes on
 * payload fields (e.g. podcast_id match).
 */
async function ensurePayloadIndex(collection, fieldName, fieldSchema) {
    const schema = await payloadSchema(collection);
    if (!schema) return;
    if (schema[fieldName]) return;
    log.info(`[QDRANT] Creating payload index '${fieldName}' on '${collection}'`);
    await request(`/collections/${collection}/index?wait=true`, {
        method: 'PUT',
        body: JSON.stringify({ field_name: fieldName, field_schema: fieldSchema }),
    });
}

/** Episodes collection plus indexes required by proxy query filters (strict mode). */
async function ensureEpisodesCollection(dim, collection = 'episodes') {
    await ensureCollection(collection, dim);
    await ensurePayloadIndex(collection, 'podcast_id', 'integer');
    await ensurePayloadIndex(collection, 'language', 'keyword');
}

/** Podcasts collection plus indexes required by because-you-like / onboarding filters. */
async function ensurePodcastsCollection(dim, collection = 'podcasts') {
    await ensureCollection(collection, dim);
    await ensurePayloadIndex(collection, 'id', 'integer');
    await ensurePayloadIndex(collection, 'language', 'keyword');
}

/** Drop a collection entirely (no-op if it doesn't exist). */
async function dropCollection(collection) {
    await request(`/collections/${collection}`, { method: 'DELETE' });
}

/** Which of these point ids exist? Returns Set of string ids. */
async function existingIds(collection, ids) {
    if (ids.length === 0) return new Set();
    const res = await request(`/collections/${collection}/points`, {
        method: 'POST',
        body: JSON.stringify({ ids, with_vector: false, with_payload: false }),
    });
    return new Set((res.result || []).map(p => String(p.id)));
}

/**
 * Upsert points with wait=true: the call returns only after the write is
 * durable, so callers can safely mark work as complete afterwards.
 */
async function upsert(collection, points) {
    if (points.length === 0) return;
    await request(`/collections/${collection}/points?wait=true`, {
        method: 'PUT',
        body: JSON.stringify({ points }),
    });
}

/** Delete points by id list. */
async function deleteByIds(collection, ids, wait = false) {
    if (ids.length === 0) return;
    await request(`/collections/${collection}/points/delete?wait=${wait}`, {
        method: 'POST',
        body: JSON.stringify({ points: ids }),
    });
}

/** Delete points matching a filter. */
async function deleteByFilter(collection, filter, wait = false) {
    await request(`/collections/${collection}/points/delete?wait=${wait}`, {
        method: 'POST',
        body: JSON.stringify({ filter }),
    });
}

/**
 * Scroll all points matching a filter. Returns array of {id, payload}.
 * Payload selector defaults to false (ids only) to keep responses small.
 */
async function scrollAll(collection, filter, withPayload = false, pageLimit = 500) {
    const out = [];
    let offset = null;
    for (;;) {
        const body = { limit: pageLimit, with_vector: false, with_payload: withPayload };
        if (filter) body.filter = filter;
        if (offset !== null) body.offset = offset;
        const res = await request(`/collections/${collection}/points/scroll`, {
            method: 'POST',
            body: JSON.stringify(body),
        });
        const points = res.result?.points || [];
        out.push(...points.map(p => ({ id: String(p.id), payload: p.payload || null })));
        offset = res.result?.next_page_offset ?? null;
        if (offset === null || points.length === 0) break;
    }
    return out;
}

/** Enable int8 scalar quantization + on-disk originals on a collection. */
async function enableQuantization(collection) {
    await request(`/collections/${collection}`, {
        method: 'PATCH',
        body: JSON.stringify({
            vectors: { on_disk: true },
            quantization_config: {
                scalar: { type: 'int8', quantile: 0.99, always_ram: true },
            },
        }),
    });
}

module.exports = {
    assertEnv, stableUUID, request, collectionInfo, ensureCollection, dropCollection,
    payloadSchema, ensurePayloadIndex, ensureEpisodesCollection, ensurePodcastsCollection,
    existingIds, upsert, deleteByIds, deleteByFilter, scrollAll, enableQuantization,
};
