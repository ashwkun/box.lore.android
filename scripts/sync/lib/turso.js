'use strict';

/**
 * Shared Turso HTTP pipeline client.
 * - Single implementation of retries/backoff, arg typing, and row extraction.
 * - Single source of truth for rows read/written stat counters, flushed
 *   incrementally to RUN_STATS_FILE so partial failures still report cost.
 */

const fs = require('fs');
const log = require('./log');
const { RUN_STATS_FILE } = require('./config');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

const MAX_RETRIES = 5;
const INITIAL_DELAY_MS = 1000;

let stepName = 'unknown';
let reads = 0;
let writes = 0;
let lastFlushedReads = 0;
let lastFlushedWrites = 0;

function assertEnv() {
    if (!TURSO_URL || !TURSO_TOKEN) {
        log.error('Missing TURSO_URL or TURSO_AUTH_TOKEN');
        process.exit(1);
    }
}

/** Set the step name used for stat attribution and reset counters. */
function beginStep(name) {
    stepName = name;
    reads = 0;
    writes = 0;
    lastFlushedReads = 0;
    lastFlushedWrites = 0;
}

function getStats() {
    return { reads, writes };
}

/**
 * Flush current counters into RUN_STATS_FILE (delta-based, safe to call
 * repeatedly; used incrementally and from finally blocks).
 */
function flushStats() {
    try {
        let current = {};
        if (fs.existsSync(RUN_STATS_FILE)) {
            current = JSON.parse(fs.readFileSync(RUN_STATS_FILE, 'utf8') || '{}');
        }
        const prev = current[stepName] || { reads: 0, writes: 0 };
        current[stepName] = {
            reads: prev.reads + (reads - lastFlushedReads),
            writes: prev.writes + (writes - lastFlushedWrites),
        };
        fs.writeFileSync(RUN_STATS_FILE, JSON.stringify(current, null, 2));
        lastFlushedReads = reads;
        lastFlushedWrites = writes;
    } catch (e) {
        log.warn(`Failed to flush run stats: ${e.message}`);
    }
}

/** Map JS values to Turso pipeline arg types. */
function mapArgType(val) {
    if (val === null || val === undefined || val === '') {
        return { type: 'null', value: null };
    }
    if (typeof val === 'number') {
        return { type: Number.isInteger(val) ? 'integer' : 'float', value: String(val) };
    }
    if (typeof val === 'string' && /^\d+$/.test(val)) {
        return { type: 'integer', value: val };
    }
    return { type: 'text', value: String(val) };
}

function isTransient(message) {
    return /fetch failed|socket|UND_ERR|timeout|502|503|504|429/.test(message);
}

async function pipelineRequest(requests) {
    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${TURSO_TOKEN}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ requests }),
            });
            if (!response.ok) {
                throw new Error(`Turso HTTP error ${response.status}: ${response.statusText}`);
            }
            const res = await response.json();
            if (res.results) {
                for (const result of res.results) {
                    if (result.type === 'error') {
                        throw new Error(`Turso SQL error: ${result.error.message}`);
                    }
                    const r = result.response?.result;
                    if (r) {
                        reads += r.rows_read || 0;
                        writes += r.rows_written || 0;
                    }
                }
            }
            return res;
        } catch (e) {
            if (isTransient(e.message) && attempt < MAX_RETRIES) {
                const backoff = INITIAL_DELAY_MS * Math.pow(2, attempt - 1);
                log.warn(`Turso request failed (attempt ${attempt}/${MAX_RETRIES}): ${e.message}. Retrying in ${backoff}ms`);
                await new Promise(r => setTimeout(r, backoff));
                continue;
            }
            throw e;
        }
    }
}

/** Execute a single SQL statement. */
async function execute(sql, args = []) {
    return pipelineRequest([
        { type: 'execute', stmt: { sql, args: args.map(mapArgType) } },
        { type: 'close' },
    ]);
}

/** Execute multiple statements in one pipeline request (single round trip). */
async function batch(statements) {
    if (statements.length === 0) return null;
    const requests = statements.map(s => ({
        type: 'execute',
        stmt: { sql: s.sql, args: (s.args || []).map(mapArgType) },
    }));
    requests.push({ type: 'close' });
    return pipelineRequest(requests);
}

/** Execute statements wrapped in BEGIN/COMMIT. */
async function transaction(statements) {
    if (statements.length === 0) return null;
    const requests = [
        { type: 'execute', stmt: { sql: 'BEGIN' } },
        ...statements.map(s => ({
            type: 'execute',
            stmt: { sql: s.sql, args: (s.args || []).map(mapArgType) },
        })),
        { type: 'execute', stmt: { sql: 'COMMIT' } },
        { type: 'close' },
    ];
    return pipelineRequest(requests);
}

/** Extract row arrays of plain values from an execute() result. */
function rows(res, resultIndex = 0) {
    const raw = res?.results?.[resultIndex]?.response?.result?.rows || [];
    return raw.map(r => r.map(cell => (cell && cell.value !== undefined ? cell.value : null)));
}

/** Extract a single scalar (first row, first column). */
function scalar(res, resultIndex = 0) {
    const r = rows(res, resultIndex);
    return r.length > 0 ? r[0][0] : null;
}

async function healthCheck() {
    const res = await execute('SELECT 1');
    if (!res?.results?.[0] || res.results[0].type !== 'ok') {
        throw new Error('Turso health check returned unexpected response');
    }
}

module.exports = {
    assertEnv, beginStep, getStats, flushStats,
    execute, batch, transaction, rows, scalar, healthCheck,
};
