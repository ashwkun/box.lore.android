package cx.aswin.boxcast.surveys.internal.theme

import androidx.compose.ui.graphics.Color

/**
 * Parses a survey-appearance color string into a Compose [Color].
 *
 * Supports the web color forms PostHog appearance settings produce: named CSS
 * colors (`"red"`, `"transparent"`), `#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`,
 * and the same forms without the leading `#`. Returns [Color.Transparent] when
 * the input is null, blank, or unrecognized.
 */
internal fun parseSurveyColor(input: String?): Color {
    val raw = input?.trim()?.takeIf { it.isNotEmpty() } ?: return Color.Transparent
    val keyed = cssColorTable[raw.uppercase()]
    val withAlphaPadding: String = keyed?.let { if (it.length == 8) it else it + "ff" } ?: raw
    val noHash = if (withAlphaPadding.startsWith("#")) withAlphaPadding.drop(1) else withAlphaPadding
    val expanded =
        when (noHash.length) {
            3, 4 -> noHash.map { c -> "$c$c" }.joinToString(separator = "")
            else -> noHash
        }
    val full = if (expanded.length <= 7) expanded + "ff" else expanded
    if (full.length != 8 || full.any { !isHexDigit(it) }) return Color.Transparent

    val v = full.toLong(16)
    val r = ((v shr 24) and 0xFF) / 255f
    val g = ((v shr 16) and 0xFF) / 255f
    val b = ((v shr 8) and 0xFF) / 255f
    val a = (v and 0xFF) / 255f
    return Color(r, g, b, a)
}

/**
 * Returns black or white text that meets WCAG-ish contrast against the
 * receiver (luminance < 0.6 → white).
 */
internal fun Color.contrastingTextColor(): Color = if (isLight()) Color.Black else Color.White

/**
 * Whether the color reads as "light" (luminance ≥ 0.6, the threshold used for
 * picking contrasting text).
 */
internal fun Color.isLight(): Boolean {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return luminance >= 0.6f
}

/**
 * Returns true when [foreground] has enough perceptual contrast against [background]
 * to remain legible. Uses a simplified relative-luminance ratio check; the threshold
 * of 1.8 catches obvious clashes like black-on-black or white-on-white while still
 * accepting most intentional PostHog color pairings.
 */
internal fun hasAdequateContrast(foreground: Color, background: Color): Boolean {
    fun luminance(c: Color): Float =
        0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    val fgL = luminance(foreground) + 0.05f
    val bgL = luminance(background) + 0.05f
    val ratio = maxOf(fgL, bgL) / minOf(fgL, bgL)
    return ratio >= 1.8f
}

private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

// CSS named-color table (140 entries + transparency aliases). Uppercased keys.
// Values are 6- or 8-char hex without the leading "#". 8-char entries already
// include an alpha byte; 6-char entries get "ff" appended at lookup time.
private val cssColorTable: Map<String, String> =
    mapOf(
        "" to "00000000",
        "CLEAR" to "00000000",
        "TRANSPARENT" to "00000000",
        "ALICEBLUE" to "F0F8FF",
        "ANTIQUEWHITE" to "FAEBD7",
        "AQUA" to "00FFFF",
        "AQUAMARINE" to "7FFFD4",
        "AZURE" to "F0FFFF",
        "BEIGE" to "F5F5DC",
        "BISQUE" to "FFE4C4",
        "BLACK" to "000000",
        "BLANCHEDALMOND" to "FFEBCD",
        "BLUE" to "0000FF",
        "BLUEVIOLET" to "8A2BE2",
        "BROWN" to "A52A2A",
        "BURLYWOOD" to "DEB887",
        "CADETBLUE" to "5F9EA0",
        "CHARTREUSE" to "7FFF00",
        "CHOCOLATE" to "D2691E",
        "CORAL" to "FF7F50",
        "CORNFLOWERBLUE" to "6495ED",
        "CORNSILK" to "FFF8DC",
        "CRIMSON" to "DC143C",
        "CYAN" to "00FFFF",
        "DARKBLUE" to "00008B",
        "DARKCYAN" to "008B8B",
        "DARKGOLDENROD" to "B8860B",
        "DARKGRAY" to "A9A9A9",
        "DARKGREY" to "A9A9A9",
        "DARKGREEN" to "006400",
        "DARKKHAKI" to "BDB76B",
        "DARKMAGENTA" to "8B008B",
        "DARKOLIVEGREEN" to "556B2F",
        "DARKORANGE" to "FF8C00",
        "DARKORCHID" to "9932CC",
        "DARKRED" to "8B0000",
        "DARKSALMON" to "E9967A",
        "DARKSEAGREEN" to "8FBC8F",
        "DARKSLATEBLUE" to "483D8B",
        "DARKSLATEGRAY" to "2F4F4F",
        "DARKSLATEGREY" to "2F4F4F",
        "DARKTURQUOISE" to "00CED1",
        "DARKVIOLET" to "9400D3",
        "DEEPPINK" to "FF1493",
        "DEEPSKYBLUE" to "00BFFF",
        "DIMGRAY" to "696969",
        "DIMGREY" to "696969",
        "DODGERBLUE" to "1E90FF",
        "FIREBRICK" to "B22222",
        "FLORALWHITE" to "FFFAF0",
        "FORESTGREEN" to "228B22",
        "FUCHSIA" to "FF00FF",
        "GAINSBORO" to "DCDCDC",
        "GHOSTWHITE" to "F8F8FF",
        "GOLD" to "FFD700",
        "GOLDENROD" to "DAA520",
        "GRAY" to "808080",
        "GREY" to "808080",
        "GREEN" to "008000",
        "GREENYELLOW" to "ADFF2F",
        "HONEYDEW" to "F0FFF0",
        "HOTPINK" to "FF69B4",
        "INDIANRED" to "CD5C5C",
        "INDIGO" to "4B0082",
        "IVORY" to "FFFFF0",
        "KHAKI" to "F0E68C",
        "LAVENDER" to "E6E6FA",
        "LAVENDERBLUSH" to "FFF0F5",
        "LAWNGREEN" to "7CFC00",
        "LEMONCHIFFON" to "FFFACD",
        "LIGHTBLUE" to "ADD8E6",
        "LIGHTCORAL" to "F08080",
        "LIGHTCYAN" to "E0FFFF",
        "LIGHTGOLDENRODYELLOW" to "FAFAD2",
        "LIGHTGRAY" to "D3D3D3",
        "LIGHTGREY" to "D3D3D3",
        "LIGHTGREEN" to "90EE90",
        "LIGHTPINK" to "FFB6C1",
        "LIGHTSALMON" to "FFA07A",
        "LIGHTSEAGREEN" to "20B2AA",
        "LIGHTSKYBLUE" to "87CEFA",
        "LIGHTSLATEGRAY" to "778899",
        "LIGHTSLATEGREY" to "778899",
        "LIGHTSTEELBLUE" to "B0C4DE",
        "LIGHTYELLOW" to "FFFFE0",
        "LIME" to "00FF00",
        "LIMEGREEN" to "32CD32",
        "LINEN" to "FAF0E6",
        "MAGENTA" to "FF00FF",
        "MAROON" to "800000",
        "MEDIUMAQUAMARINE" to "66CDAA",
        "MEDIUMBLUE" to "0000CD",
        "MEDIUMORCHID" to "BA55D3",
        "MEDIUMPURPLE" to "9370DB",
        "MEDIUMSEAGREEN" to "3CB371",
        "MEDIUMSLATEBLUE" to "7B68EE",
        "MEDIUMSPRINGGREEN" to "00FA9A",
        "MEDIUMTURQUOISE" to "48D1CC",
        "MEDIUMVIOLETRED" to "C71585",
        "MIDNIGHTBLUE" to "191970",
        "MINTCREAM" to "F5FFFA",
        "MISTYROSE" to "FFE4E1",
        "MOCCASIN" to "FFE4B5",
        "NAVAJOWHITE" to "FFDEAD",
        "NAVY" to "000080",
        "OLDLACE" to "FDF5E6",
        "OLIVE" to "808000",
        "OLIVEDRAB" to "6B8E23",
        "ORANGE" to "FFA500",
        "ORANGERED" to "FF4500",
        "ORCHID" to "DA70D6",
        "PALEGOLDENROD" to "EEE8AA",
        "PALEGREEN" to "98FB98",
        "PALETURQUOISE" to "AFEEEE",
        "PALEVIOLETRED" to "DB7093",
        "PAPAYAWHIP" to "FFEFD5",
        "PEACHPUFF" to "FFDAB9",
        "PERU" to "CD853F",
        "PINK" to "FFC0CB",
        "PLUM" to "DDA0DD",
        "POWDERBLUE" to "B0E0E6",
        "PURPLE" to "800080",
        "REBECCAPURPLE" to "663399",
        "RED" to "FF0000",
        "ROSYBROWN" to "BC8F8F",
        "ROYALBLUE" to "4169E1",
        "SADDLEBROWN" to "8B4513",
        "SALMON" to "FA8072",
        "SANDYBROWN" to "F4A460",
        "SEAGREEN" to "2E8B57",
        "SEASHELL" to "FFF5EE",
        "SIENNA" to "A0522D",
        "SILVER" to "C0C0C0",
        "SKYBLUE" to "87CEEB",
        "SLATEBLUE" to "6A5ACD",
        "SLATEGRAY" to "708090",
        "SLATEGREY" to "708090",
        "SNOW" to "FFFAFA",
        "SPRINGGREEN" to "00FF7F",
        "STEELBLUE" to "4682B4",
        "TAN" to "D2B48C",
        "TEAL" to "008080",
        "THISTLE" to "D8BFD8",
        "TOMATO" to "FF6347",
        "TURQUOISE" to "40E0D0",
        "VIOLET" to "EE82EE",
        "WHEAT" to "F5DEB3",
        "WHITE" to "FFFFFF",
        "WHITESMOKE" to "F5F5F5",
        "YELLOW" to "FFFF00",
        "YELLOWGREEN" to "9ACD32",
    )
