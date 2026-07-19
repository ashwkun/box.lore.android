package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiChatMessageListLogicTest {
    @Test
    fun buildMessages_alwaysStartsWithInitialAssistant() {
        val messages =
            AiChatMessageListLogic.buildMessages(
                history = emptyList(),
                isAiLoading = false,
                isSynthesizing = false,
                onboardingError = null,
            )
        assertEquals(1, messages.size)
        val first = messages.first() as ChatMessage.ModelMessage
        assertEquals(AiChatMessageListLogic.INITIAL_ASSISTANT_MESSAGE, first.text)
    }

    @Test
    fun buildMessages_appendsUserAndModelTurns() {
        val history =
            listOf(
                OnboardingHistoryEntry("user", listOf(OnboardingPart("hello"))),
                OnboardingHistoryEntry("model", listOf(OnboardingPart("hi there"))),
            )
        val messages =
            AiChatMessageListLogic.buildMessages(
                history = history,
                isAiLoading = false,
                isSynthesizing = false,
                onboardingError = null,
            )
        assertEquals(3, messages.size)
        assertTrue(messages[1] is ChatMessage.UserMessage)
        assertTrue(messages[2] is ChatMessage.ModelMessage)
    }

    @Test
    fun buildMessages_addsLoadingAndError() {
        val messages =
            AiChatMessageListLogic.buildMessages(
                history = emptyList(),
                isAiLoading = true,
                isSynthesizing = false,
                onboardingError = "boom",
            )
        assertTrue(messages.any { it is ChatMessage.LoadingMessage })
        assertTrue(messages.any { it is ChatMessage.ErrorMessage })
    }
}
