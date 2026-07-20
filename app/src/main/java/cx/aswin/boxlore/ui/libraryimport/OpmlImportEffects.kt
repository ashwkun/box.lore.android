package cx.aswin.boxlore.ui.libraryimport

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs OPML / JSON library import work when [importTriggerKey] changes. */
@Composable
fun OpmlImportEffects(
    importTriggerKey: Long,
    opmlImportState: OpmlImportState,
    onStateChange: (OpmlImportState) -> Unit,
    context: Context,
    subscriptionRepository: SubscriptionRepository,
    playbackRepository: PlaybackRepository,
    podcastRepository: PodcastRepository,
) {
    LaunchedEffect(importTriggerKey) {
        if (importTriggerKey == 0L) return@LaunchedEffect
        val state = opmlImportState
        if (state is OpmlImportState.Parsing) {
            val uri = state.uri
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        AnalyticsHelper.trackOnboardingImportFailed(
                            "opml",
                            "Failed to open the selected file.",
                        )
                        onStateChange(OpmlImportState.Error("Failed to open the selected file."))
                        return@withContext
                    }
                    val backupManager =
                        LibraryBackupManager(
                            subscriptionRepository,
                            playbackRepository,
                            podcastRepository,
                            context = context,
                        )
                    val feeds = backupManager.parseOpmlFeeds(inputStream)
                    inputStream.close()

                    if (feeds.isEmpty()) {
                        AnalyticsHelper.trackOnboardingImportFailed(
                            "opml",
                            "No podcast feeds found in the OPML file.",
                        )
                        onStateChange(
                            OpmlImportState.Error("No podcast feeds found in the OPML file."),
                        )
                        return@withContext
                    }

                    onStateChange(
                        OpmlImportState.Importing(
                            currentFeedTitle = feeds.first().title,
                            progress = 0f,
                            currentCount = 0,
                            totalCount = feeds.size,
                            importedPodcasts = emptyList(),
                        ),
                    )

                    val importedList = mutableListOf<Podcast>()
                    for (index in feeds.indices) {
                        val feed = feeds[index]
                        onStateChange(
                            OpmlImportState.Importing(
                                currentFeedTitle = feed.title,
                                progress = index.toFloat() / feeds.size,
                                currentCount = index,
                                totalCount = feeds.size,
                                importedPodcasts = importedList.toList(),
                            ),
                        )
                        val imported = backupManager.importSingleOpmlFeed(feed)
                        if (imported != null) importedList.add(imported)
                    }

                    if (importedList.isEmpty()) {
                        AnalyticsHelper.trackOnboardingImportFailed(
                            "opml",
                            "Could not resolve or subscribe to any podcasts from this OPML file.",
                        )
                        AnalyticsHelper.trackBackupRestoreResult(
                            action = "import",
                            success = false,
                            format = "opml",
                            errorMessage = "Could not resolve or subscribe to any podcasts from this OPML file.",
                        )
                        onStateChange(
                            OpmlImportState.Error(
                                "Could not resolve or subscribe to any podcasts from this OPML file.",
                            ),
                        )
                    } else {
                        AnalyticsHelper.trackBackupRestoreResult(
                            action = "import",
                            success = true,
                            itemCount = importedList.size,
                            format = "opml",
                        )
                        onStateChange(
                            OpmlImportState.AskCompleted(
                                importedPodcasts = importedList.toList(),
                                selectedIds = importedList.map { it.id }.toSet(),
                            ),
                        )
                    }
                } catch (e: Exception) {
                    Log.e("OPML_IMPORT", "Error during parsing/importing", e)
                    AnalyticsHelper.trackOnboardingImportFailed("opml", e.message)
                    AnalyticsHelper.trackBackupRestoreResult(
                        action = "import",
                        success = false,
                        format = "opml",
                        errorMessage = e.message,
                    )
                    onStateChange(OpmlImportState.Error("OPML Import failed: ${e.message}"))
                }
            }
        } else if (state is OpmlImportState.Completing) {
            withContext(Dispatchers.IO) {
                try {
                    val backupManager =
                        LibraryBackupManager(
                            subscriptionRepository,
                            playbackRepository,
                            podcastRepository,
                            context = context,
                        )
                    val total = state.podcastsToMark.size
                    for (index in state.podcastsToMark.indices) {
                        val podcast = state.podcastsToMark[index]
                        onStateChange(
                            OpmlImportState.Completing(
                                progress = index.toFloat() / total,
                                currentShowTitle = podcast.title,
                                podcastsToMark = state.podcastsToMark,
                                totalImportedCount = state.totalImportedCount,
                                importedPodcasts = state.importedPodcasts,
                            ),
                        )
                        backupManager.markAllEpisodesCompleted(podcast)
                    }
                    onStateChange(
                        OpmlImportState.Success(
                            importedCount = state.totalImportedCount,
                            completedCount = total,
                            isJson = false,
                            importedPodcasts = state.importedPodcasts,
                        ),
                    )
                } catch (e: Exception) {
                    Log.e("OPML_IMPORT", "Error marking completed", e)
                    AnalyticsHelper.trackOnboardingImportFailed("opml", e.message)
                    onStateChange(
                        OpmlImportState.Error(
                            "Failed to mark episodes as completed: ${e.message}",
                        ),
                    )
                }
            }
        }
    }
}

/** Import a JSON library backup on a background dispatcher. */
suspend fun performJsonLibraryImport(
    uri: Uri,
    context: Context,
    subscriptionRepository: SubscriptionRepository,
    playbackRepository: PlaybackRepository,
    podcastRepository: PodcastRepository,
    userPrefs: UserPreferencesRepository,
    onStateChange: (OpmlImportState) -> Unit,
) {
    onStateChange(OpmlImportState.ImportingJson)
    withContext(Dispatchers.IO) {
        try {
            val jsonStr =
                context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }
            if (jsonStr == null) {
                AnalyticsHelper.trackOnboardingImportFailed("json", "Failed to read the JSON file.")
                withContext(Dispatchers.Main) {
                    onStateChange(OpmlImportState.Error("Failed to read the JSON file."))
                }
                return@withContext
            }
            val (count, hasNotificationsEnabled) =
                LibraryBackupManager(
                    subscriptionRepository,
                    playbackRepository,
                    podcastRepository,
                    userPrefs,
                    context,
                ).importLibraryFromJson(jsonStr)
            AnalyticsHelper.trackBackupRestoreResult(
                action = "import",
                success = true,
                itemCount = count,
                format = "json",
            )
            withContext(Dispatchers.Main) {
                onStateChange(
                    OpmlImportState.Success(
                        importedCount = count,
                        completedCount = 0,
                        isJson = true,
                        hasNotificationsEnabled = hasNotificationsEnabled,
                    ),
                )
            }
        } catch (e: Exception) {
            AnalyticsHelper.trackOnboardingImportFailed("json", e.message)
            AnalyticsHelper.trackBackupRestoreResult(
                action = "import",
                success = false,
                format = "json",
                errorMessage = e.message,
            )
            withContext(Dispatchers.Main) {
                onStateChange(OpmlImportState.Error("JSON Import failed: ${e.message}"))
            }
        }
    }
}
