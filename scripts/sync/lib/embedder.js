'use strict';

/**
 * Lazy-loaded CPU embedding generator (transformers.js).
 * Model files cache to ./.cache (restored by the workflow's actions/cache).
 */

const log = require('./log');
const { EMBED_MODEL } = require('./config');

let extractorPromise = null;

async function getExtractor() {
    if (!extractorPromise) {
        extractorPromise = (async () => {
            const { env, pipeline } = await import('@xenova/transformers');
            env.cacheDir = './.cache';
            log.info(`[MODEL] Loading embedding model: ${EMBED_MODEL}`);
            const extractor = await pipeline('feature-extraction', EMBED_MODEL);
            log.info('[MODEL] Model ready');
            return extractor;
        })();
    }
    return extractorPromise;
}

/** Embed a single text -> number[] (mean-pooled, normalized). */
async function embed(text) {
    const extractor = await getExtractor();
    const output = await extractor(text, { pooling: 'mean', normalize: true });
    return Array.from(output.data);
}

module.exports = { embed };
