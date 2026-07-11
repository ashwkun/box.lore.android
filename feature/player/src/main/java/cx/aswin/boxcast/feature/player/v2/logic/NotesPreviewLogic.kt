package cx.aswin.boxcast.feature.player.v2.logic

import android.text.Html

object NotesPreviewLogic {
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun canExpand(
        strippedDescription: String,
        collapsedMaxLines: Int = 3,
        minLengthForExpand: Int = 120,
    ): Boolean {
        if (strippedDescription.isBlank()) return false
        return strippedDescription.length > minLengthForExpand ||
            strippedDescription.count { it == '\n' } >= collapsedMaxLines
    }
}
