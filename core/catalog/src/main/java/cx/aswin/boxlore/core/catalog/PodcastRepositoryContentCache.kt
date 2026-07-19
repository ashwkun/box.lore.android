package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot
import cx.aswin.boxlore.core.catalog.content.ContentSectionsDaypartResolver
import cx.aswin.boxlore.core.catalog.content.GroupedContentSections
import cx.aswin.boxlore.core.catalog.content.contentSectionsStaleCachePrefix
import cx.aswin.boxlore.core.catalog.content.toGroupedContentSections
import com.google.gson.Gson

import cx.aswin.boxlore.core.network.model.ContentSectionsV1Response
import cx.aswin.boxlore.core.catalog.BuildConfig

internal const val CONTENT_CATALOG_JSON = "catalog_v3_json"
internal const val CONTENT_CATALOG_ETAG = "catalog_v3_etag"
internal const val CONTENT_CATALOG_FETCHED_AT = "catalog_v3_fetched_at"
internal const val MAX_CONTENT_SECTION_SEEDS = 8
internal const val MAX_CONTENT_SECTION_INTERESTS = 12
internal const val MAX_CONTENT_SECTION_INTEREST_LENGTH = 80
internal const val MAX_CONTENT_SECTION_EXCLUSIONS = 250
internal const val CONTENT_SECTION_CANDIDATE_BUDGET = 120
internal const val MAX_RECENT_CONTENT_SECTION_IDS = 24
internal const val MAX_CONTENT_SECTION_ID_LENGTH = 128
internal const val PERSONALIZED_CONTENT_SECTIONS_ALGORITHM = "personalized-recipe-mmr-v1.1"
internal const val CONTENT_SECTIONS_PROFILE_FINGERPRINT_HEX_LENGTH = 24
internal const val MIN_TIMEZONE_OFFSET_MINUTES = -840
internal const val MAX_TIMEZONE_OFFSET_MINUTES = 840

internal fun PodcastRepository.readCachedContentCatalog(
    now: Long,
): cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot? {
    val json = contentCatalogPreferences.getString(CONTENT_CATALOG_JSON, null) ?: return null
    val fetchedAt = contentCatalogPreferences.getLong(CONTENT_CATALOG_FETCHED_AT, 0L)
    return runCatching {
        Gson().fromJson(
            json,
            cx.aswin.boxlore.core.network.model.ContentCatalogResponse::class.java,
        ).toContentCatalogSnapshot(fetchedAt.takeIf { it > 0L } ?: now)
    }.getOrNull()
}

internal fun PodcastRepository.readCachedContentSections(
    cacheKey: String,
    catalog: ContentCatalogSnapshot,
    seenPodcastIds: Set<String>,
    subscribedPodcastIds: Set<String>,
): GroupedContentSections? {
    val json = contentSectionsPreferences.getString(cacheKey, null) ?: return null
    return runCatching {
        val response = Gson().fromJson(json, ContentSectionsV1Response::class.java)
        if (response.algorithmVersion != PERSONALIZED_CONTENT_SECTIONS_ALGORITHM) {
            contentSectionsPreferences.edit().remove(cacheKey).apply()
            null
        } else {
            response.toGroupedContentSections(catalog, seenPodcastIds, subscribedPodcastIds)
        }
    }.getOrNull()
}

internal fun PodcastRepository.readStaleCachedContentSections(
    slot: ContentSectionsSlotKey,
    catalog: ContentCatalogSnapshot,
    seenPodcastIds: Set<String>,
    subscribedPodcastIds: Set<String>,
): GroupedContentSections? {
    val prefix = contentSectionsStaleCachePrefix(
        catalogVersion = slot.catalogVersion,
        country = slot.country,
        surface = slot.surface,
        resolvedDaypart = ContentSectionsDaypartResolver.resolve(slot.localMinuteOfDay),
        localDate = slot.localDate,
    )
    val pointerKey = contentSectionsLatestPointerKey(prefix)
    val staleKey = contentSectionsPreferences.getString(pointerKey, null)
        ?: contentSectionsPreferences.all.keys
            .asSequence()
            .filterIsInstance<String>()
            .filter { it.startsWith(prefix) && it != pointerKey }
            .maxOrNull()
        ?: return null
    return readCachedContentSections(
        cacheKey = staleKey,
        catalog = catalog,
        seenPodcastIds = seenPodcastIds,
        subscribedPodcastIds = subscribedPodcastIds,
    )
}

/**
 * Keeps a single active payload per daypart slot. Older fingerprint keys under the same
 * prefix are removed so stale-while-revalidate cannot resurrect a superseded profile.
 */
internal fun PodcastRepository.persistContentSectionsCache(
    activeKey: String,
    aliasKey: String?,
    payload: String,
) {
    val slotPrefixes = buildSet {
        add(contentSectionsSlotPrefix(activeKey))
        if (aliasKey != null) add(contentSectionsSlotPrefix(aliasKey))
    }
    val editor = contentSectionsPreferences.edit()
    contentSectionsPreferences.all.keys
        .asSequence()
        .filterIsInstance<String>()
        .filter { key -> slotPrefixes.any { prefix -> key.startsWith(prefix) } }
        .forEach(editor::remove)
    editor.putString(activeKey, payload)
    if (aliasKey != null) {
        editor.putString(aliasKey, payload)
    }
    slotPrefixes.forEach { prefix ->
        editor.putString(contentSectionsLatestPointerKey(prefix), activeKey)
    }
    editor.apply()
}

internal fun PodcastRepository.contentSectionsSlotPrefix(cacheKey: String): String {
    return cacheKey.dropLast(CONTENT_SECTIONS_PROFILE_FINGERPRINT_HEX_LENGTH)
}

internal fun PodcastRepository.contentSectionsLatestPointerKey(slotPrefix: String): String {
    return "${slotPrefix}__latest"
}

internal data class ContentSectionsSlotKey(
    val catalogVersion: Int,
    val country: String,
    val surface: String,
    val localMinuteOfDay: Int,
    val localDate: String,
)
