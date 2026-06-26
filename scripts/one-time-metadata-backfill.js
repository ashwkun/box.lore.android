#!/usr/bin/env node

/**
 * One-time backfill script to repair Turso DB podcasts that are missing latest episode metadata.
 * Fetches the latest episode from Podcast Index API and updates:
 * - latest_ep_id, latest_ep_title, latest_ep_date, etc.
 * - medium, last_ep_sync
 * - qdrant_vectorized = 1 (since they are already vectorized in Qdrant)
 */

const crypto = require('crypto');

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;
const API_KEY = process.env.PODCAST_INDEX_API_KEY;
const API_SECRET = process.env.PODCAST_INDEX_API_SECRET;
const API_BASE = "https://api.podcastindex.org/api/1.0";

if (!TURSO_URL || !TURSO_TOKEN || !API_KEY || !API_SECRET) {
    console.error("Missing required environment variables (TURSO_URL, TURSO_AUTH_TOKEN, PODCAST_INDEX_API_KEY, PODCAST_INDEX_API_SECRET)");
    process.exit(1);
}

function generateAuthHeaders() {
    const apiHeaderTime = Math.floor(Date.now() / 1000);
    const data4Hash = API_KEY + API_SECRET + apiHeaderTime;
    const hash = crypto.createHash('sha1').update(data4Hash).digest('hex');

    return {
        "User-Agent": "BoxLore/1.0",
        "X-Auth-Key": API_KEY,
        "X-Auth-Date": "" + apiHeaderTime,
        "Authorization": hash
    };
}

function mapArgType(val) {
    if (val === null || val === undefined || val === "") {
        return { type: "null", value: null };
    }
    if (typeof val === 'number') {
        return { type: "integer", value: String(val) };
    }
    if (typeof val === 'string' && /^\d+$/.test(val)) {
        return { type: "integer", value: val };
    }
    return { type: "text", value: String(val) };
}

async function executeSQL(sql, args = []) {
    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            requests: [{
                type: "execute",
                stmt: { sql, args: args.map(mapArgType) }
            }, { type: "close" }]
        })
    });
    if (!response.ok) {
        throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
    }
    const res = await response.json();
    if (res.results && res.results[0] && res.results[0].type === "error") {
        throw new Error(`SQL execution error: ${res.results[0].error.message}`);
    }
    return res;
}

async function executeBatch(statements) {
    if (statements.length === 0) return;
    const requests = statements.map(stmt => ({
        type: "execute",
        stmt: { sql: stmt.sql, args: stmt.args.map(mapArgType) }
    }));
    requests.push({ type: "close" });

    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ requests })
    });
    if (!response.ok) throw new Error(`Turso HTTP error: ${response.status}`);
    const res = await response.json();
    if (res.results) {
        for (const result of res.results) {
            if (result.type === "error") {
                throw new Error(`Turso SQL batch error: ${result.error.message}`);
            }
        }
    }
}

function cleanDescription(raw) {
    if (!raw || typeof raw !== 'string') return "";
    let text = raw.replace(/<[^>]+>/g, ' ');
    text = text
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&#x27;/g, "'")
        .replace(/&#\d+;/g, ' ')
        .replace(/&\w+;/g, ' ');
    text = text.replace(/https?:\/\/[^\s)"\]]+/gi, '');
    text = text.replace(/www\.[^\s)"\]]+/gi, '');
    text = text.replace(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, '');
    text = text.replace(/[@#]\w+/g, '');
    text = text.replace(/\b\d{1,2}:\d{2}(:\d{2})?\b/g, '');
    
    const sponsorPatterns = [
        /^.*sponsored by.*$/gim,
        /^.*brought to you by.*$/gim,
        /^.*use code\b.*$/gim,
        /^.*promo code\b.*$/gim,
        /^.*discount code\b.*$/gim,
        /^.*sign up at\b.*$/gim,
        /^.*go to\b.*for\b.*$/gim,
        /^.*visit\b.*\.com.*$/gim,
    ];
    for (const pattern of sponsorPatterns) {
        text = text.replace(pattern, '');
    }

    const boilerplate = [
        /learn more at\b.*/gi,
        /for more info(rmation)?\b.*/gi,
        /subscribe (to|on|at|in)\b.*/gi,
        /follow us (on|at)\b.*/gi,
        /rate (and|&) review\b.*/gi,
        /leave a review\b.*/gi,
        /support (the|this) (show|podcast)\b.*/gi,
        /available on\b.*/gi,
        /listen on\b.*/gi,
        /download the app\b.*/gi,
        /all rights reserved\.?/gi,
        /copyright ©?\s*\d{4}.*/gi,
        /see privacy policy at\b.*/gi,
        /see omnystudio\.com.*/gi,
        /advertising inquiries\b.*/gi,
    ];
    for (const pattern of boilerplate) {
        text = text.replace(pattern, '');
    }
    return text.replace(/\s+/g, ' ').trim().substring(0, 1000);
}

let currentBatchDelayMs = 100; // Start with a fast 100ms delay between batches

// Fetch latest episode details from Podcast Index with retry and backoff on 429
async function fetchLatestEpisode(feedId, retries = 3, delay = 1000) {
    for (let attempt = 1; attempt <= retries + 1; attempt++) {
        try {
            const headers = generateAuthHeaders();
            const res = await fetch(`${API_BASE}/episodes/byfeedid?id=${feedId}&max=1`, { headers });
            
            if (res.status === 429) {
                // Dynamically increase global batch delay to slow down execution
                currentBatchDelayMs = Math.min(3000, currentBatchDelayMs + 500);
                if (attempt <= retries) {
                    const backoff = delay * Math.pow(2, attempt - 1);
                    console.warn(`  [WARN] Rate limited (429) fetching episodes for pod=${feedId}. Increased global delay to ${currentBatchDelayMs}ms. Retrying attempt ${attempt}/${retries} after ${backoff}ms...`);
                    await new Promise(resolve => setTimeout(resolve, backoff));
                    continue;
                }
                throw new Error(`API error 429: Too Many Requests`);
            }
            if (!res.ok) {
                throw new Error(`API error: ${res.status}`);
            }
            const data = await res.json();
            return data.items?.[0] || null;
        } catch (e) {
            if (attempt > retries) {
                console.error(`[FAIL] fetchLatestEpisode pod=${feedId} failed after ${attempt} attempts: ${e.message}`);
                throw e;
            }
            const backoff = delay * Math.pow(2, attempt - 1);
            console.warn(`  [WARN] fetchLatestEpisode pod=${feedId} failed (attempt ${attempt}/${retries}): ${e.message}. Retrying in ${backoff}ms...`);
            await new Promise(resolve => setTimeout(resolve, backoff));
        }
    }
    return null;
}

async function main() {
    console.log("=== Starting One-Time Episode Metadata Backfill ===");

    // 1. Fetch all podcasts where latest_ep_id is missing (NULL or empty string)
    console.log("[DATABASE] Fetching podcasts missing latest episode metadata...");
    const res = await executeSQL("SELECT id, title, medium FROM podcasts WHERE latest_ep_id IS NULL OR latest_ep_id = '';");
    const rows = res?.results?.[0]?.response?.result?.rows || [];
    console.log(`[DATABASE] Found ${rows.length} podcasts needing episode metadata.`);

    if (rows.length === 0) {
        console.log("[DATABASE] No podcasts missing episode metadata. Exiting.");
        return;
    }

    const CONCURRENCY = 20; // Safe worker pool concurrency
    console.log(`[BACKFILL] Processing ${rows.length} podcasts with concurrency of ${CONCURRENCY}...`);

    let index = 0;
    const promises = [];

    async function worker() {
        while (index < rows.length) {
            const currentIndex = index++;
            const row = rows[currentIndex];

            // Stagger start times slightly to prevent burst 429s
            await new Promise(r => setTimeout(r, currentIndex * 25));

            const podId = String(row[0].value);
            const title = row[1].value || "Unknown Show";
            const currentMedium = row[2].value || "podcast";

            try {
                const latestEp = await fetchLatestEpisode(podId);
                if (!latestEp) {
                    // No episodes returned from PI API — mark last_ep_sync and preserve vectorized status
                    await executeSQL(`
                        UPDATE podcasts 
                        SET last_ep_sync = ?
                        WHERE id = ?;
                    `, [Date.now(), parseInt(podId)]);
                    console.log(`  - [NO EPISODES] "${title}" (ID: ${podId}) marked as synced.`);
                } else {
                    // Process details
                    const chaptersUrl = latestEp.chaptersUrl || null;
                    const transcriptUrl = latestEp.transcriptUrl || null;
                    const personsJson = latestEp.persons ? JSON.stringify(latestEp.persons) : null;
                    const transcriptsJson = latestEp.transcripts ? JSON.stringify(latestEp.transcripts) : null;
                    
                    await executeSQL(`
                        UPDATE podcasts SET 
                            latest_ep_id = ?,
                            latest_ep_title = ?,
                            latest_ep_date = ?,
                            latest_ep_duration = ?,
                            latest_ep_url = ?,
                            latest_ep_image = ?,
                            latest_ep_type = ?,
                            latest_ep_description = ?,
                            latest_ep_chapters_url = ?,
                            latest_ep_transcript_url = ?,
                            latest_ep_persons = ?,
                            latest_ep_transcripts = ?,
                            medium = ?,
                            vector = NULL,
                            last_ep_sync = ?
                        WHERE id = ?;
                    `, [
                        String(latestEp.id),
                        latestEp.title || "",
                        latestEp.datePublished || 0,
                        latestEp.duration || 0,
                        latestEp.enclosureUrl || "",
                        latestEp.image || latestEp.feedImage || "",
                        latestEp.enclosureType || "audio/mpeg",
                        cleanDescription(latestEp.description),
                        chaptersUrl,
                        transcriptUrl,
                        personsJson,
                        transcriptsJson,
                        currentMedium,
                        Date.now(),
                        parseInt(podId)
                    ]);

                    console.log(`  ✓ [UPDATED] "${title}" (Latest: "${latestEp.title}") | Delay: ${currentBatchDelayMs}ms`);
                }

                // Decay the delay if operations are succeeding
                if (currentBatchDelayMs > 100) {
                    currentBatchDelayMs = Math.max(100, currentBatchDelayMs - 10);
                }
            } catch (err) {
                console.error(`  ✗ [ERROR] "${title}" (ID: ${podId}): ${err.message}`);
            }

            // Cooldown delay before starting next item
            await new Promise(resolve => setTimeout(resolve, currentBatchDelayMs));
        }
    }

    // Start concurrent worker promises
    for (let w = 0; w < CONCURRENCY; w++) {
        promises.push(worker());
    }

    await Promise.all(promises);

    console.log("=== Backfill Completed! ===");
}

main().catch(err => {
    console.error("Backfill script execution failed:", err);
    process.exit(1);
});
