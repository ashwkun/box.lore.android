package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem

internal object HomeHeroLogic {
    fun sessionToPodcast(
        session: PlaybackSession,
        subs: List<Podcast>,
    ): Podcast {
        val epImage = session.imageUrl
        val podImage = session.podcastImageUrl
        val ratio =
            if (session.durationMs > 0) {
                (session.positionMs.toFloat() / session.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        val finalPodId = session.podcastId.takeIf { it.isNotBlank() && it != "0" } ?: ""
        val parentPod = subs.find { it.id == finalPodId }
        val parentTitle = parentPod?.title.orEmpty()
        val finalPodTitle =
            session.podcastTitle
                .takeIf { it.isNotBlank() && it != "Unknown Podcast" }
                ?: parentTitle.ifBlank { "Podcast" }
        return Podcast(
            id = finalPodId,
            title = finalPodTitle,
            artist = "",
            imageUrl = if (!epImage.isNullOrEmpty()) epImage else podImage ?: "",
            fallbackImageUrl = podImage,
            description = "",
            genre = "Podcast",
            resumeProgress = ratio,
            latestEpisode =
                Episode(
                    id = session.episodeId,
                    title = session.episodeTitle,
                    description = "",
                    imageUrl = epImage ?: "",
                    audioUrl = session.audioUrl ?: "",
                    duration = (session.durationMs / 1000).toInt(),
                    publishedDate = 0L,
                    podcastTitle = finalPodTitle,
                    podcastId = finalPodId,
                    enclosureType = session.enclosureType,
                ),
        )
    }

    fun appendResumeHeroItems(
        heroList: MutableList<SmartHeroItem>,
        usedPodcastIds: MutableSet<String>,
        resumeList: List<PlaybackSession>,
        subs: List<Podcast>,
    ) {
        if (resumeList.isEmpty()) return

        val first = resumeList[0]
        try {
            val firstPodcast = sessionToPodcast(first, subs)
            val timeLeft = ((first.durationMs - first.positionMs) / 60000).coerceAtLeast(1)
            heroList.add(
                SmartHeroItem(
                    type = HeroType.RESUME,
                    podcast = firstPodcast,
                    label = "RESUME • ${timeLeft}m left",
                    description = first.episodeTitle,
                ),
            )
            usedPodcastIds.add(firstPodcast.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (resumeList.size == 2) {
            val second = resumeList[1]
            try {
                val secondPodcast = sessionToPodcast(second, subs)
                val timeLeft = ((second.durationMs - second.positionMs) / 60000).coerceAtLeast(1)
                heroList.add(
                    SmartHeroItem(
                        type = HeroType.RESUME,
                        podcast = secondPodcast,
                        label = "RESUME • ${timeLeft}m left",
                        description = second.episodeTitle,
                    ),
                )
                usedPodcastIds.add(secondPodcast.id)
            } catch (_: Exception) {
            }
        } else if (resumeList.size > 2) {
            val gridCandidates = resumeList.drop(1).take(4)
            val gridPodcasts =
                gridCandidates.mapNotNull { session ->
                    try {
                        val pod = sessionToPodcast(session, subs)
                        usedPodcastIds.add(pod.id)
                        pod
                    } catch (_: Exception) {
                        null
                    }
                }
            if (gridPodcasts.isNotEmpty()) {
                heroList.add(
                    SmartHeroItem(
                        type = HeroType.RESUME_GRID,
                        podcast = gridPodcasts.first(),
                        label = "JUMP BACK IN",
                        description = null,
                        gridItems = gridPodcasts,
                    ),
                )
            }
        }
    }

    fun unplayedHeroLabel(
        preferredSort: String?,
        unplayedCount: Int,
        isFirst: Boolean,
    ): String {
        val sort = preferredSort ?: "newest"
        return when {
            sort == "oldest" -> "NEXT"
            isFirst && unplayedCount == 1 -> "NEW EPISODE"
            isFirst -> "FRESH DROP"
            else -> "NEW EPISODE"
        }
    }

    fun appendUnplayedHeroItems(
        heroList: MutableList<SmartHeroItem>,
        usedPodcastIds: MutableSet<String>,
        unplayedBucket: List<Podcast>,
    ) {
        if (unplayedBucket.isEmpty()) return

        val first = unplayedBucket.first()
        heroList.add(
            SmartHeroItem(
                type = HeroType.JUMP_BACK_IN,
                podcast = first,
                label = unplayedHeroLabel(first.preferredSort, unplayedBucket.size, isFirst = true),
                description = first.latestEpisode?.title,
            ),
        )
        usedPodcastIds.add(first.id)

        if (unplayedBucket.size == 2) {
            val second = unplayedBucket[1]
            heroList.add(
                SmartHeroItem(
                    type = HeroType.JUMP_BACK_IN,
                    podcast = second,
                    label = unplayedHeroLabel(second.preferredSort, unplayedBucket.size, isFirst = false),
                    description = second.latestEpisode?.title,
                ),
            )
            usedPodcastIds.add(second.id)
        } else if (unplayedBucket.size > 2) {
            val gridDrops = unplayedBucket.drop(1).take(4)
            heroList.add(
                SmartHeroItem(
                    type = HeroType.NEW_EPISODES_GRID,
                    podcast = gridDrops.first(),
                    label = "NEW EPISODES",
                    description = null,
                    gridItems = gridDrops,
                ),
            )
            usedPodcastIds.addAll(gridDrops.map { it.id })
        }
    }

    fun spotlightLabel(
        spotlightAddedCount: Int,
        region: String,
        genre: String,
    ): String =
        when {
            spotlightAddedCount == 0 ->
                when (region.lowercase()) {
                    "in" -> "#1 IN INDIA"
                    "gb", "uk" -> "#1 IN UK"
                    "fr" -> "#1 IN FRANCE"
                    else -> "#1 IN US"
                }
            genre.isNotEmpty() && !genre.equals("Podcast", ignoreCase = true) ->
                "TRENDING IN ${genre.uppercase()}"
            else -> "TRENDING"
        }

    fun displayPodcastForSpotlight(pod: Podcast): Podcast {
        val epUrl = pod.latestEpisode?.imageUrl
        return if (!epUrl.isNullOrEmpty()) {
            pod.copy(
                imageUrl = epUrl,
                fallbackImageUrl = pod.imageUrl,
            )
        } else {
            pod.copy(fallbackImageUrl = pod.imageUrl)
        }
    }

    fun appendSpotlightHeroItems(
        heroList: MutableList<SmartHeroItem>,
        usedPodcastIds: MutableSet<String>,
        trendingList: List<Podcast>,
        region: String,
        targetSize: Int = 8,
    ) {
        var i = 0
        var spotlightAddedCount = 0
        while (heroList.size < targetSize && i < trendingList.size) {
            val pod = trendingList[i]
            if (!usedPodcastIds.contains(pod.id)) {
                val label = spotlightLabel(spotlightAddedCount, region, pod.genre)
                val spotlightDesc = pod.latestEpisode?.title ?: pod.genre
                heroList.add(
                    SmartHeroItem(
                        type = HeroType.SPOTLIGHT,
                        podcast = displayPodcastForSpotlight(pod),
                        label = label,
                        description = spotlightDesc,
                    ),
                )
                usedPodcastIds.add(pod.id)
                spotlightAddedCount++
            }
            i++
        }
    }

    fun buildHeroItems(
        resumeList: List<PlaybackSession>,
        unplayedBucket: List<Podcast>,
        trendingList: List<Podcast>,
        subs: List<Podcast>,
        region: String,
    ): List<SmartHeroItem> {
        val heroList = mutableListOf<SmartHeroItem>()
        val usedPodcastIds = mutableSetOf<String>()
        appendResumeHeroItems(heroList, usedPodcastIds, resumeList, subs)
        appendUnplayedHeroItems(heroList, usedPodcastIds, unplayedBucket)
        appendSpotlightHeroItems(heroList, usedPodcastIds, trendingList, region)
        return heroList
    }
}
