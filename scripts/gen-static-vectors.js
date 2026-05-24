const { pipeline } = require('@xenova/transformers');

// Define the "Vibes" - Descriptive themes for curation
const VIBES = {
    // Morning / Start of Day
    "morning_news": "Essential daily news briefings, fast-paced updates, global headlines, and market recaps to start the day.",
    "morning_motivation": "Inspirational speeches, productivity hacks, stoicism, and morning routines to get energized.",

    // Work / Focus (Balanced)
    "tech_culture": "Consumer tech news, gadget reviews, smartphone discussions, app recommendations, and internet culture. Like The Vergecast, Waveform, or tech YouTubers chatting.",
    "tech_deep_dive": "Software engineering, coding tutorials, developer interviews, AI research, and technical deep dives for engineers.",
    "business_insider": "Startup stories, founder journeys, company profiles, marketing strategies, and accessible business discussions.",
    "creative_focus": "Design thinking, art history, creative process, and storytelling for makers.",

    // Evening / Relax
    "comedy_gold": "Stand-up specials, improv comedy, hilarious interviews, and lighthearted banter to unwind.",
    "history_buff": "Immersive history storytelling, ancient civilizations, war stories, and biographies of historical figures.",
    "true_crime_sleep": "Calmly narrated true crime mysteries, cold cases, and detective stories with a slower pace for relaxing.",

    // Weekend / Deep Learning
    "science_explainer": "Complex scientific concepts explained simply, space exploration, physics, and biology discoveries.",
    "long_form_interview": "Deep, long conversations with thinkers, authors, and experts about life, philosophy, and society.",
    "mystery_thriller": "Fictional audio dramas, suspenseful storytelling, and immersive soundscapes.",
    "tv_film_buff": "Movie reviews, behind the scenes, celebrity interviews, and film history.",
    "sports_fan": "Sports news, game analysis, athlete interviews, and match highlights.",
};

const MODEL_NAME = 'Xenova/bge-small-en-v1.5';

async function main() {
    console.log("Generating Static Vibe Vectors...");
    console.log(`Model: ${MODEL_NAME}`);

    const extractor = await pipeline('feature-extraction', MODEL_NAME);

    const output = {};

    for (const [key, text] of Object.entries(VIBES)) {
        console.log(`Processing: ${key}...`);
        try {
            const resp = await extractor(text, { pooling: 'mean', normalize: true });
            output[key] = Array.from(resp.data); // Convert Float32Array to standard Array for JSON
        } catch (e) {
            console.error(`Error generating ${key}:`, e);
        }
    }

    const fs = require('fs');
    const path = require('path');

    // Format as TypeScript export
    const fileContent = `export const VIBE_VECTORS: Record<string, number[]> = ${JSON.stringify(output, null, 2)};`;

    const outputPath = path.join(__dirname, '../proxy/src/vibes.ts');
    fs.writeFileSync(outputPath, fileContent);

    console.log(`\nSuccessfully wrote vectors to ${outputPath}`);
}

main().catch(console.error);
