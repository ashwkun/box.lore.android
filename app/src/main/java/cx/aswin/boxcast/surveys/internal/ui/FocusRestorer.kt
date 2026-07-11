package cx.aswin.boxcast.surveys.internal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Restores text-field focus after configuration changes or dialog re-entry.
 * Used by [OpenText] so keyboard focus survives activity recreation.
 */
@Composable
internal fun Modifier.restoreFocusOnReentry(): Modifier {
    val focusRequester = remember { FocusRequester() }
    var wasFocused by rememberSaveable { mutableStateOf(false) }
    val restoreFocus = remember { wasFocused }
    LaunchedEffect(Unit) {
        if (restoreFocus) {
            focusRequester.requestFocus()
        }
    }
    return this
        .focusRequester(focusRequester)
        .onFocusChanged { wasFocused = it.isFocused }
}
