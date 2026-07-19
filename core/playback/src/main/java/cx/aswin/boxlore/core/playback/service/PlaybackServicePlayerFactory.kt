package cx.aswin.boxlore.core.playback.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryCallback
import kotlinx.coroutines.CoroutineScope

/**
 * Builds ExoPlayer (dual-cache), ForwardingPlayer, command buttons, and MediaLibrarySession
 * for [BoxLorePlaybackService.onCreate].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackServicePlayerFactory(
    private val context: Context,
    private val serviceScope: CoroutineScope,
) {
    data class SeekButtons(
        val seekBack: CommandButton,
        val seekForward: CommandButton,
    )

    data class CustomActions(
        val like: CommandButton,
        val addToQueue: CommandButton,
        val markComplete: CommandButton,
    )

    data class BuiltSession(
        val mediaSession: MediaLibrarySession,
        val seekButtons: SeekButtons,
        val customActions: CustomActions,
    )

    fun createExoPlayer(): ExoPlayer {
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

        // Dual-cache architecture:
        // - downloadCache: permanent, user-downloaded episodes (NoOpCacheEvictor)
        // - streamCache: temporary streaming buffer for seeking (250MB LRU cap)
        val downloadCache = DownloadRepository.getDownloadCache(context)
        val streamCache = DownloadRepository.getStreamCache(context)
        val httpDataSourceFactory =
            DefaultHttpDataSource
                .Factory()
                .setUserAgent(Util.getUserAgent(context, "BoxLore"))
                .setAllowCrossProtocolRedirects(true)

        val streamCacheDataSourceFactory =
            CacheDataSource
                .Factory()
                .setCache(streamCache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val cacheDataSourceFactory =
            CacheDataSource
                .Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(streamCacheDataSourceFactory)
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory)

        return ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS)
            .setSeekBackIncrementMs(PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS)
            .build()
    }

    fun createForwardingPlayer(
        player: ExoPlayer,
        seekForwardMs: () -> Long,
        seekBackMs: () -> Long,
        onSeekByConfiguredIncrement: (ExoPlayer, Long, String) -> Unit,
        onSkipNext: () -> Unit,
    ): ForwardingPlayer =
        object : ForwardingPlayer(player) {
            override fun getSeekForwardIncrement(): Long = seekForwardMs()

            override fun getSeekBackIncrement(): Long = seekBackMs()

            override fun seekForward() {
                onSeekByConfiguredIncrement(player, seekForwardMs(), "seek_forward")
            }

            override fun seekBack() {
                onSeekByConfiguredIncrement(player, -seekBackMs(), "seek_backward")
            }

            override fun seekToNext() {
                onSkipNext()
            }

            override fun seekToNextMediaItem() {
                onSkipNext()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (command == Player.COMMAND_SEEK_FORWARD || command == Player.COMMAND_SEEK_BACK) return true
                return super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands =
                super
                    .getAvailableCommands()
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_SEEK_BACK)
                    .build()
        }

    fun buildSeekButtons(
        seekBackwardMs: Long,
        seekForwardMs: Long,
    ): SeekButtons {
        val seekBack =
            CommandButton
                .Builder()
                .setDisplayName(
                    context.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_seek_back,
                        seekBackwardMs / 1_000L,
                    ),
                ).setIconResId(cx.aswin.boxlore.core.data.R.drawable.rounded_replay_24)
                .setSessionCommand(SessionCommand("SEEK_BACK", Bundle.EMPTY))
                .build()
        val seekForward =
            CommandButton
                .Builder()
                .setDisplayName(
                    context.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_seek_forward,
                        seekForwardMs / 1_000L,
                    ),
                ).setIconResId(cx.aswin.boxlore.core.data.R.drawable.rounded_forward_24)
                .setSessionCommand(SessionCommand("SEEK_FORWARD", Bundle.EMPTY))
                .build()
        return SeekButtons(seekBack = seekBack, seekForward = seekForward)
    }

    fun buildCustomActions(): CustomActions {
        val like =
            CommandButton
                .Builder()
                .setDisplayName(context.getString(cx.aswin.boxlore.core.data.R.string.auto_like))
                .setIconResId(cx.aswin.boxlore.core.data.R.drawable.ic_auto_like)
                .setSessionCommand(
                    SessionCommand(AutoBrowseContract.COMMAND_TOGGLE_LIKE, Bundle.EMPTY),
                ).build()
        val addToQueue =
            CommandButton
                .Builder()
                .setDisplayName(context.getString(cx.aswin.boxlore.core.data.R.string.auto_add_queue))
                .setIconResId(cx.aswin.boxlore.core.data.R.drawable.ic_auto_queue_add)
                .setSessionCommand(
                    SessionCommand(AutoBrowseContract.COMMAND_ADD_TO_QUEUE, Bundle.EMPTY),
                ).build()
        val markComplete =
            CommandButton
                .Builder()
                .setDisplayName(context.getString(cx.aswin.boxlore.core.data.R.string.auto_mark_complete))
                .setIconResId(cx.aswin.boxlore.core.data.R.drawable.ic_auto_complete)
                .setSessionCommand(
                    SessionCommand(AutoBrowseContract.COMMAND_MARK_COMPLETE, Bundle.EMPTY),
                ).build()
        return CustomActions(like = like, addToQueue = addToQueue, markComplete = markComplete)
    }

    fun createPlayerSessionActivityIntent(): PendingIntent {
        val intent = Intent()
        intent.component = ComponentName(context.packageName, "cx.aswin.boxlore.MainActivity")
        intent.putExtra("EXTRA_OPEN_PLAYER", true)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun buildMediaLibrarySession(
        service: androidx.media3.session.MediaLibraryService,
        forwardingPlayer: Player,
        callback: AutoBrowseLibraryCallback,
        pendingIntent: PendingIntent,
        seekButtons: SeekButtons,
        customActions: CustomActions,
    ): MediaLibrarySession {
        val coilBitmapLoader = CoilBitmapLoader(context, serviceScope)
        val cacheBitmapLoader = CacheBitmapLoader(coilBitmapLoader)
        return MediaLibrarySession
            .Builder(service, forwardingPlayer, callback)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(listOf(seekButtons.seekBack, seekButtons.seekForward, customActions.markComplete))
            .setCommandButtonsForMediaItems(
                listOf(customActions.like, customActions.addToQueue, customActions.markComplete),
            ).setBitmapLoader(cacheBitmapLoader)
            .build()
    }

    fun assembleSession(
        service: androidx.media3.session.MediaLibraryService,
        player: ExoPlayer,
        seekForwardMs: () -> Long,
        seekBackMs: () -> Long,
        onSeekByConfiguredIncrement: (ExoPlayer, Long, String) -> Unit,
        onSkipNext: () -> Unit,
        callback: AutoBrowseLibraryCallback,
        seekBackwardMs: Long,
        seekForwardMsValue: Long,
    ): BuiltSession {
        val forwardingPlayer =
            createForwardingPlayer(
                player = player,
                seekForwardMs = seekForwardMs,
                seekBackMs = seekBackMs,
                onSeekByConfiguredIncrement = onSeekByConfiguredIncrement,
                onSkipNext = onSkipNext,
            )
        val seekButtons = buildSeekButtons(seekBackwardMs, seekForwardMsValue)
        val customActions = buildCustomActions()
        val pendingIntent = createPlayerSessionActivityIntent()
        val mediaSession =
            buildMediaLibrarySession(
                service = service,
                forwardingPlayer = forwardingPlayer,
                callback = callback,
                pendingIntent = pendingIntent,
                seekButtons = seekButtons,
                customActions = customActions,
            )
        return BuiltSession(
            mediaSession = mediaSession,
            seekButtons = seekButtons,
            customActions = customActions,
        )
    }
}
