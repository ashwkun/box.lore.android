'use strict';

/**
 * Text utilities: description cleaning for embeddings/storage and
 * embed-text construction. Single copy of what previously lived in
 * sync-episodes.js, vectorize.js, and vectorize-podcasts.js.
 */

const SPONSOR_PATTERNS = [
    /^.*sponsored by.*$/gim,
    /^.*brought to you by.*$/gim,
    /^.*use code\b.*$/gim,
    /^.*promo code\b.*$/gim,
    /^.*discount code\b.*$/gim,
    /^.*sign up at\b.*$/gim,
    /^.*go to\b.*for\b.*$/gim,
    /^.*visit\b.*\.com.*$/gim,
];

const BOILERPLATE_PATTERNS = [
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

/**
 * Strip HTML, URLs, emails, handles, timestamps, sponsor blocks, and
 * boilerplate. Truncates to maxLen (default 1000).
 */
function cleanDescription(raw, maxLen = 1000) {
    if (!raw || typeof raw !== 'string') return '';
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
    for (const p of SPONSOR_PATTERNS) text = text.replace(p, '');
    for (const p of BOILERPLATE_PATTERNS) text = text.replace(p, '');
    return text.replace(/\s+/g, ' ').trim().substring(0, maxLen);
}

/** Text fed to the embedding model for an episode point. */
function episodeEmbedText(episode, podcast) {
    const parts = [
        `Episode: ${episode.title || ''}`,
        episode.cleanedDescription ? `Description: ${episode.cleanedDescription}` : null,
        `Podcast: ${podcast.title}`,
        podcast.categories ? `Genres: ${podcast.categories}` : null,
        podcast.author ? `Host: ${podcast.author}` : null,
    ].filter(Boolean);
    return parts.join('. ').replace(/[\n\r]+/g, ' ').substring(0, 1000);
}

/** Text fed to the embedding model for a show point. */
function podcastEmbedText(podcast) {
    const cleaned = cleanDescription(podcast.description);
    const parts = [
        `Podcast: ${podcast.title}`,
        podcast.author ? `Host: ${podcast.author}` : null,
        cleaned ? `Description: ${cleaned}` : null,
        podcast.categories ? `Genres: ${podcast.categories}` : null,
        podcast.language ? `Language: ${podcast.language}` : null,
    ].filter(Boolean);
    return parts.join('. ').replace(/[\n\r]+/g, ' ').substring(0, 1000);
}

module.exports = { cleanDescription, episodeEmbedText, podcastEmbedText };
