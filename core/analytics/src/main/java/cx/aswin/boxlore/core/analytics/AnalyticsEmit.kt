package cx.aswin.boxlore.core.analytics

import android.util.Log
import com.posthog.PostHog

/**
 * Single egress for PostHog captures. Drops names outside glossary Phase A∪B
 * (except `$set` person properties). Tests may replace [eventSink] / [personSetSink].
 */
internal object AnalyticsEmit {
    private const val TAG = "AnalyticsEmit"

    @Volatile
    var eventSink: (String, Map<String, Any>) -> Unit = { event, properties ->
        PostHog.capture(event = event, properties = properties)
    }

    @Volatile
    var personSetSink: (Map<String, Any>) -> Unit = { userProperties ->
        PostHog.capture(event = AnalyticsGlossary.PERSON_SET_EVENT, userProperties = userProperties)
    }

    /** Test helper: install a recording sink; returns captured event names + props. */
    fun installRecordingSink(recorder: MutableList<Pair<String, Map<String, Any>>>): () -> Unit {
        val previousEvent = eventSink
        val previousPerson = personSetSink
        eventSink = { event, props -> recorder.add(event to props) }
        personSetSink = { props -> recorder.add(AnalyticsGlossary.PERSON_SET_EVENT to props) }
        return {
            eventSink = previousEvent
            personSetSink = previousPerson
        }
    }

    fun event(
        name: String,
        properties: Map<String, Any> = emptyMap(),
    ) {
        if (!AnalyticsGlossary.isAllowedEvent(name)) {
            Log.w(TAG, "Dropped non-glossary event: $name")
            return
        }
        eventSink(name, properties)
    }

    fun personSet(properties: Map<String, Any>) {
        personSetSink(properties)
    }
}
