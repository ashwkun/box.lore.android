package cx.aswin.boxlore.navigation

import androidx.navigation.NavHostController
import cx.aswin.boxlore.core.data.analytics.AnalyticsHelper

/** Bottom-nav tab click handler shared by the Activity shell. */
fun NavHostController.navigateBottomNavTab(route: String, activeTab: String) {
    AnalyticsHelper.trackNavTabClicked(route)
    when {
        activeTab == route -> {
            when (route) {
                "home" -> popBackStack("home", inclusive = false)
                "learn" -> {
                    val popped = popBackStack("learn", inclusive = false)
                    if (!popped) {
                        navigate("learn") {
                            popUpTo("home") { saveState = false }
                            launchSingleTop = true
                        }
                    }
                }
                "explore" -> {
                    val popped = popBackStack(ExploreTabRoutePattern, inclusive = false)
                    if (!popped) {
                        navigate(ExploreBottomNavRoute) {
                            popUpTo("home") { saveState = false }
                            launchSingleTop = true
                        }
                    }
                }
                "library" -> {
                    val popped = popBackStack("library", inclusive = false)
                    if (!popped) {
                        navigate("library") {
                            popUpTo("home") { saveState = false }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
        route == "home" -> {
            navigate("home") {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        route == "learn" -> {
            navigate("learn") {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        route == "explore" -> {
            navigate(ExploreBottomNavRoute) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        route == "library" -> {
            navigate("library") {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        else -> {
            val tabPattern = bottomNavTabRoutePattern(route)
            val popped = tabPattern != null &&
                popBackStack(tabPattern, inclusive = false)
            if (!popped) {
                navigate(
                    if (route == "explore") ExploreBottomNavRoute else route,
                ) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}
