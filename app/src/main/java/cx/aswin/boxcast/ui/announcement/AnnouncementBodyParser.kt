package cx.aswin.boxcast.ui.announcement

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

internal data class BodyBlock(
    val text: AnnotatedString,
    val isBullet: Boolean,
    val isSpacer: Boolean = false,
)

/** Parses bold (`**…**`) and italic (`*…*`) markers into an [AnnotatedString]. */
internal fun parseSimpleMarkdownInline(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val regex = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*")
        val matches = regex.findAll(text)
        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            if (match.groups[1] != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[1])
                }
            } else if (match.groups[2] != null) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[2])
                }
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex))
    }
}

/** Splits announcement body into paragraphs, bullets, and blank-line spacers. */
internal fun parseBodyToBlocks(body: String): List<BodyBlock> {
    val normalized = body.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split("\n")
    val blocks = mutableListOf<BodyBlock>()
    var pendingSpacers = 0

    fun flushSpacers() {
        // Collapse runs of blank lines to a single visual spacer between content
        if (pendingSpacers > 0 && blocks.isNotEmpty()) {
            blocks.add(BodyBlock(AnnotatedString(""), isBullet = false, isSpacer = true))
        }
        pendingSpacers = 0
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            pendingSpacers++
            continue
        }
        flushSpacers()

        // Match bullets like "- item", "* item", or "• item"
        val bulletMatch = Regex("^(?:-|\\*|•)\\s+(.*)$").matchEntire(trimmed)
        if (bulletMatch != null) {
            val content = bulletMatch.groupValues[1]
            blocks.add(BodyBlock(parseSimpleMarkdownInline(content), isBullet = true))
        } else {
            blocks.add(BodyBlock(parseSimpleMarkdownInline(line.trimEnd()), isBullet = false))
        }
    }
    return blocks
}
