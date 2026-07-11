package cx.aswin.boxcast.feature.player.v2.sheets

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.feature.player.v2.logic.QueueLabelLogic

/** @see QueueLabelLogic.sourceLabel */
internal fun queueSourceLabel(episode: Episode): String? = QueueLabelLogic.sourceLabel(episode)
