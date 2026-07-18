package cx.aswin.boxlore.core.data.playback

import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture

/**
 * Shared MediaController handle for PlaybackRepository coordinators.
 */
internal class PlaybackMediaControllerHandle {
    var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
}
