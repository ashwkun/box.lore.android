package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MonitorHeart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiSuggestionIconsTest {
    @Test
    fun `maps keyword categories to their icons case-insensitively`() {
        assertEquals(Icons.Rounded.Fingerprint, getCategoryIcon("True Crime Stories"))
        assertEquals(Icons.Rounded.Computer, getCategoryIcon("Technology Weekly"))
        assertEquals(Icons.Rounded.EmojiEvents, getCategoryIcon("FOOTBALL MATCH"))
        assertEquals(Icons.Rounded.MonitorHeart, getCategoryIcon("Meditation and Sleep"))
    }

    @Test
    fun `falls back to auto awesome for unknown categories`() {
        assertEquals(Icons.Rounded.AutoAwesome, getCategoryIcon("Zzzqqq Wwwvvv"))
    }

    @Test
    fun `returns the first matching rule by declaration order`() {
        // "stories" also lives in the fiction rule, but the crime rule precedes it.
        assertEquals(Icons.Rounded.Fingerprint, getCategoryIcon("Mystery Stories"))
    }
}
