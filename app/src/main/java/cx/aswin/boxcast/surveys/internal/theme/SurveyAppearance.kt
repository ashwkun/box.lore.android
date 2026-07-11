package cx.aswin.boxcast.surveys.internal.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.posthog.surveys.PostHogDisplaySurveyAppearance
import com.posthog.surveys.PostHogDisplaySurveyTextContentType

/** Resolved survey colors and copy, merged from PostHog appearance and Material theme defaults. */
internal data class ResolvedSurveyAppearance(
    val backgroundColor: Color,
    val borderColor: Color,
    val submitButtonColor: Color,
    val submitButtonTextColor: Color,
    val submitButtonText: String,
    val textColor: Color,
    val questionTextColor: Color,
    val descriptionTextColor: Color,
    val placeholder: String,
    val placeholderTextColor: Color,
    val inputBackgroundColor: Color,
    val inputTextColor: Color,
    val ratingButtonColor: Color,
    val ratingButtonActiveColor: Color,
    val ratingButtonActiveTextColor: Color,
    val displayThankYouMessage: Boolean,
    val thankYouMessageHeader: String,
    val thankYouMessageDescription: String?,
    val thankYouMessageDescriptionContentType: PostHogDisplaySurveyTextContentType,
    val thankYouMessageCloseButtonText: String,
)

/** CompositionLocal holding the active survey appearance for child composables. */
internal val LocalSurveyAppearance =
    compositionLocalOf<ResolvedSurveyAppearance> {
        error("LocalSurveyAppearance not provided")
    }

/** Reads the current survey appearance from [LocalSurveyAppearance]. */
@Composable
@ReadOnlyComposable
internal fun localAppearance(): ResolvedSurveyAppearance = LocalSurveyAppearance.current

internal data class MaterialSurveyDefaults(
    val background: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outlineVariant: Color,
)

@Composable
@ReadOnlyComposable
private fun materialSurveyDefaults(): MaterialSurveyDefaults {
    val scheme = MaterialTheme.colorScheme
    return MaterialSurveyDefaults(
        background = scheme.surface,
        onSurface = scheme.onSurface,
        onSurfaceVariant = scheme.onSurfaceVariant,
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        surfaceContainerHigh = scheme.surfaceContainerHigh,
        surfaceContainerHighest = scheme.surfaceContainerHighest,
        outlineVariant = scheme.outlineVariant,
    )
}

private const val DEFAULT_PLACEHOLDER = "Start typing..."
private const val DEFAULT_THANK_YOU_HEADER = "Thank you for your feedback!"
private const val DEFAULT_THANK_YOU_CLOSE = "Close"

/** Maps PostHog survey appearance settings onto Material theme defaults. */
@Composable
@ReadOnlyComposable
internal fun PostHogDisplaySurveyAppearance?.resolveAppearance(): ResolvedSurveyAppearance {
    val defaults = materialSurveyDefaults()
    return resolve(defaults)
}

internal fun PostHogDisplaySurveyAppearance?.resolve(
    defaults: MaterialSurveyDefaults,
): ResolvedSurveyAppearance {
    val backgroundColor = parseSurveyColorOrDefault(this?.backgroundColor, defaults.background)
    val submitButtonColor = parseSurveyColorOrDefault(this?.submitButtonColor, defaults.primary)
    val submitButtonTextColor =
        parseSurveyColorOrDefault(this?.submitButtonTextColor, defaults.onPrimary)
    val textColor = parseSurveyColorOrDefault(this?.textColor, defaults.onSurface)
    val descriptionTextColor =
        parseSurveyColorOrDefault(this?.descriptionTextColor, defaults.onSurfaceVariant)
    val borderColor = parseSurveyColorOrDefault(this?.borderColor, defaults.outlineVariant)
    val ratingButtonColor =
        parseSurveyColorOrDefault(this?.ratingButtonColor, defaults.surfaceContainerHigh)
    val ratingButtonActiveColor =
        parseSurveyColorOrDefault(this?.ratingButtonActiveColor, defaults.primaryContainer)
    val ratingButtonActiveTextColor = defaults.onPrimaryContainer
    val submitButtonText = this?.submitButtonText?.takeIf { it.isNotBlank() } ?: "Submit"
    val questionTextColor = parseSurveyColorOrDefault(this?.textColor, defaults.onSurface)
    val inputBackgroundColor =
        parseSurveyColorOrDefault(this?.inputBackground, defaults.surfaceContainerHighest)
    val inputTextColor =
        parseSurveyColorOrDefault(this?.inputTextColor, defaults.onSurface)
    val placeholderTextColor = defaults.onSurfaceVariant.copy(alpha = 0.7f)
    val placeholder = this?.placeholder?.takeIf { it.isNotBlank() } ?: DEFAULT_PLACEHOLDER
    val displayThankYouMessage = this?.displayThankYouMessage ?: false
    val thankYouMessageHeader =
        this?.thankYouMessageHeader?.takeIf { it.isNotBlank() } ?: DEFAULT_THANK_YOU_HEADER
    val thankYouMessageDescription = this?.thankYouMessageDescription?.takeIf { it.isNotBlank() }
    val thankYouMessageDescriptionContentType =
        this?.thankYouMessageDescriptionContentType ?: PostHogDisplaySurveyTextContentType.TEXT
    val thankYouMessageCloseButtonText =
        this?.thankYouMessageCloseButtonText?.takeIf { it.isNotBlank() } ?: DEFAULT_THANK_YOU_CLOSE

    return ResolvedSurveyAppearance(
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        submitButtonColor = submitButtonColor,
        submitButtonTextColor = submitButtonTextColor,
        submitButtonText = submitButtonText,
        textColor = textColor,
        questionTextColor = questionTextColor,
        descriptionTextColor = descriptionTextColor,
        placeholder = placeholder,
        placeholderTextColor = placeholderTextColor,
        inputBackgroundColor = inputBackgroundColor,
        inputTextColor = inputTextColor,
        ratingButtonColor = ratingButtonColor,
        ratingButtonActiveColor = ratingButtonActiveColor,
        ratingButtonActiveTextColor = ratingButtonActiveTextColor,
        displayThankYouMessage = displayThankYouMessage,
        thankYouMessageHeader = thankYouMessageHeader,
        thankYouMessageDescription = thankYouMessageDescription,
        thankYouMessageDescriptionContentType = thankYouMessageDescriptionContentType,
        thankYouMessageCloseButtonText = thankYouMessageCloseButtonText,
    )
}

private fun parseSurveyColorOrDefault(input: String?, default: Color): Color {
    if (input.isNullOrBlank()) return default
    val parsed = parseSurveyColor(input)
    return if (parsed == Color.Transparent) default else parsed
}
