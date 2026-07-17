package cx.aswin.boxcast.core.model

/**
 * Represents the state of AI auto-transcription for an episode.
 * Used by playback state and the player UI to render transcript/chapter
 * controls with appropriate badges, loaders, and interaction behavior.
 */
enum class AutoTranscriptState {
    /** RSS transcript is available — normal behavior, no badge/indicator. */
    NONE,
    /** Non-blocking server/DB status check in progress. */
    CHECKING,
    /** Eligible for AI transcript — show starry AI badge. */
    NOT_GENERATED,
    /** Actively generating — show wavy circular loader. */
    GENERATING,
    /** Completed — show normal icon with small checkmark badge. */
    COMPLETED,
    /** Failed — show starry AI badge (retryable). */
    FAILED
}
