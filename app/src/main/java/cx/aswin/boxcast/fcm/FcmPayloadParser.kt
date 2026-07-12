package cx.aswin.boxcast.fcm

/**
 * Parsed FCM payload data configuration.
 */
data class ParsedFcmNotification(
    val title: String,
    val body: String,
    val type: String,
    val route: String?,
    val imageUrl: String?,
    val sound: String,
    val actionLabel: String,
    val showActionInPush: Boolean,
    val showActionInApp: Boolean
)

/**
 * Pure Kotlin parser to extract notification configurations from raw FCM map data.
 * Built to be cleanly testable under JVM unit tests.
 */
object FcmPayloadParser {
    
    fun parse(data: Map<String, String>): ParsedFcmNotification {
        val title = data["title"] ?: "boxlore Update"
        val body = data["body"] ?: "Check out what's new in boxlore!"
        val type = data["type"] ?: "both"
        val route = data["route"]
        val imageUrl = data["image"]
        val sound = data["sound"] ?: "default"
        val actionLabel = data["action_label"] ?: "View"
        val showActionInPush = data["show_action_in_push"] != "false"
        val showActionInApp = data["show_action_in_app"] != "false"

        return ParsedFcmNotification(
            title = title,
            body = body,
            type = type,
            route = route,
            imageUrl = imageUrl,
            sound = sound,
            actionLabel = actionLabel,
            showActionInPush = showActionInPush,
            showActionInApp = showActionInApp
        )
    }
}
