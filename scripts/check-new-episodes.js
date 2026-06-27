const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// 1. Initialize Firebase Admin SDK using application default credentials (GCP_SA_KEY)
admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    databaseURL: "https://boxcasts-default-rtdb.asia-southeast1.firebasedatabase.app"
});

const db = admin.database();

// Podcast Index Configuration
const apiKey = process.env.PODCAST_INDEX_API_KEY;
const apiSecret = process.env.PODCAST_INDEX_API_SECRET;

if (!apiKey || !apiSecret) {
    console.error("Error: PODCAST_INDEX_API_KEY and PODCAST_INDEX_API_SECRET must be set.");
    process.exit(1);
}

// Generate authentication headers for Podcast Index API
function generateAuthHeaders() {
    const authDate = Math.floor(Date.now() / 1000);
    const data = apiKey + apiSecret + authDate;
    const authHeader = crypto.createHash('sha1').update(data).digest('hex');

    return {
        "X-Auth-Key": apiKey,
        "X-Auth-Date": authDate.toString(),
        "Authorization": authHeader,
        "User-Agent": "BoxLore/1.0"
    };
}

async function run() {
    console.log("Checking for new podcast episodes...");

    // 2. Read tracked podcasts list from Firebase Realtime Database
    let trackedPodcasts = {};
    try {
        const snapshot = await db.ref('tracked_podcasts').once('value');
        trackedPodcasts = snapshot.val() || {};
        console.log(`Retrieved ${Object.keys(trackedPodcasts).length} tracked podcasts from Realtime Database.`);
    } catch (e) {
        console.error("Failed to read tracked podcasts from RTDB:", e);
        process.exit(1);
    }

    // 3. Read state file (local json)
    const statePath = path.join(__dirname, '../data/episode-tracker.json');
    
    // Ensure the data directory exists
    const dataDir = path.dirname(statePath);
    if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir, { recursive: true });
    }

    let state = { lastRun: "", podcasts: {} };
    if (fs.existsSync(statePath)) {
        try {
            state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
        } catch (e) {
            console.warn("Failed to parse state file, initializing fresh state:", e);
        }
    }

    state.lastRun = new Date().toISOString();
    let changeCount = 0;

    // 4. Poll each tracked podcast for new episodes
    for (const [podcastId, podcastData] of Object.entries(trackedPodcasts)) {
        const podcastTitle = podcastData.title || "Podcast";
        const imageUrl = podcastData.imageUrl || "";

        try {
            const url = `https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=${podcastId}&max=1`;
            const response = await fetch(url, { headers: generateAuthHeaders() });
            
            if (!response.ok) {
                console.error(`Podcast Index API returned status ${response.status} for podcast ${podcastId}`);
                continue;
            }

            const result = await response.json();
            const episodes = result.items || [];
            
            if (episodes.length === 0) {
                console.log(`No episodes found in Podcast Index for ${podcastTitle} (${podcastId})`);
                continue;
            }

            const latestEp = episodes[0];
            const latestEpId = String(latestEp.id);
            const latestEpTitle = latestEp.title || "New Episode";
            
            const existingState = state.podcasts[podcastId];

            if (!existingState) {
                // Initialize baseline state without triggering notification
                console.log(`Setting baseline for new tracked podcast: ${podcastTitle} -> Latest: "${latestEpTitle}" (${latestEpId})`);
                state.podcasts[podcastId] = {
                    lastEpisodeId: latestEpId,
                    lastEpisodeTitle: latestEpTitle,
                    lastCheckedAt: Date.now()
                };
                changeCount++;
            } else if (existingState.lastEpisodeId !== latestEpId) {
                // Found a new episode! Trigger push notification
                console.log(`[NEW EPISODE] "${latestEpTitle}" detected for ${podcastTitle}`);
                
                const topic = `new_ep_${podcastId}`;
                const message = {
                    topic: topic,
                    data: {
                        type: 'new_episode',
                        podcastId: String(podcastId),
                        podcastTitle: String(podcastTitle),
                        episodeTitle: String(latestEpTitle),
                        episodeId: String(latestEpId),
                        duration: String(latestEp.duration ? Math.round(Number(latestEp.duration) / 60) : '0'),
                        image: String(latestEp.image || latestEp.feedImage || imageUrl || ''),
                        route: `boxlore://episode/${latestEpId}`
                    }
                };

                try {
                    const messageId = await admin.messaging().send(message);
                    console.log(`Sent notification ${messageId} to topic: ${topic}`);
                } catch (fcmError) {
                    console.error(`Failed to send FCM notification for ${podcastTitle}:`, fcmError);
                }

                // Update state
                state.podcasts[podcastId] = {
                    lastEpisodeId: latestEpId,
                    lastEpisodeTitle: latestEpTitle,
                    lastCheckedAt: Date.now()
                };
                changeCount++;
            }
        } catch (podcastError) {
            console.error(`Error checking podcast ${podcastTitle} (${podcastId}):`, podcastError);
        }
    }

    // 5. Write updated state file back
    try {
        fs.writeFileSync(statePath, JSON.stringify(state, null, 2), 'utf8');
        console.log(`State file updated successfully. ${changeCount} changes recorded.`);
    } catch (writeError) {
        console.error("Failed to write state file:", writeError);
    }

    process.exit(0);
}

run();
