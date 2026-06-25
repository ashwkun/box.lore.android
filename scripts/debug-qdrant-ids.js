#!/usr/bin/env node

const QDRANT_URL = process.env.QDRANT_URL;
const QDRANT_API_KEY = process.env.QDRANT_API_KEY;

if (!QDRANT_URL || !QDRANT_API_KEY) {
    console.error("Missing QDRANT_* env vars");
    process.exit(1);
}

async function scrollQdrant(collection, fields, limit = 5000) {
    const idsSet = new Set();
    const payloadIdsSet = new Set();
    let offset = null;
    let page = 1;

    while (true) {
        const body = {
            limit,
            with_payload: fields,
            with_vector: false
        };
        if (offset) {
            body.offset = offset;
        }

        const res = await fetch(`${QDRANT_URL}/collections/${collection}/points/scroll`, {
            method: "POST",
            headers: {
                "api-key": QDRANT_API_KEY,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(body)
        });

        if (!res.ok) {
            const txt = await res.text();
            throw new Error(`Scroll failed for page ${page}: ${res.status} - ${txt}`);
        }

        const data = await res.json();
        const points = data.result?.points || [];
        
        for (const p of points) {
            idsSet.add(String(p.id));
            if (p.payload && p.payload.podcast_id !== undefined) {
                payloadIdsSet.add(String(p.payload.podcast_id));
            }
        }

        offset = data.result?.next_page_offset;
        if (!offset || points.length === 0) {
            break;
        }
        page++;
    }

    return { idsSet, payloadIdsSet };
}

async function main() {
    const { payloadIdsSet } = await scrollQdrant('episodes', ['podcast_id']);
    console.log("Unique podcast_ids in Qdrant episodes payload:", Array.from(payloadIdsSet));
}

main().catch(err => {
    console.error(err);
    process.exit(1);
});
