package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueSources
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope

interface AutoBrowseLibraryHost {
    val serviceScope: CoroutineScope
    val database: BoxLoreDatabase
    val podcastRepository: PodcastRepository
    val queueRepository: QueueRepository
    val smartQueueSources: SmartQueueSources
    val adaptiveCandidateScorer: AdaptiveCandidateScorer
    val userPreferencesRepository: UserPreferencesRepository
    val mediaSession: MediaLibrarySession?
    val seekBackAction: CommandButton
    val seekForwardAction: CommandButton
    val markCompleteAction: CommandButton
    val autoCollageUris: Map<String, Uri>
    var isRefilling: Boolean

    fun asContext(): Context

    fun getString(
        @StringRes id: Int,
        vararg formatArgs: Any,
    ): String

    fun observeManualCompletion(episodeId: String)

    fun markCurrentEpisodeCompletedAndSkip(session: MediaSession)

    suspend fun refillQueue(player: ExoPlayer)

    fun toAutoPodcast(entity: PodcastEntity): Podcast

    fun getTimeBasedGenres(hour: Int): List<Pair<String, String>>
}
