package cx.aswin.boxcast.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp

/**
 * Content height of the app nav bar (excludes system navigation insets).
 * Slightly taller than stock ShortNavigationBar (64dp) for touch comfort.
 */
val AppNavigationBarHeight = 72.dp

/**
 * Collapsed mini-player height. Keep in sync with
 * `feature.player.v2.MiniPlayerHeight`.
 */
val AppMiniPlayerHeight = 72.dp

/** Gap between collapsed mini-player and the app navbar. */
val AppMiniPlayerNavGap = 2.dp

/** Explore For You / Top segmented control (padding + pill). */
val ExploreTabSelectorFabHeight = 44.dp

private val NavBarTopCornerRadius = 28.dp

data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @DrawableRes val selectedIconRes: Int? = null,
    @DrawableRes val unselectedIconRes: Int? = null,
)

val bottomNavDestinations = listOf(
    NavDestination(
        route = "home",
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    NavDestination(
        route = "learn",
        label = "Lore",
        selectedIcon = Icons.Filled.Psychology,
        unselectedIcon = Icons.Outlined.Psychology,
        selectedIconRes = cx.aswin.boxcast.core.designsystem.R.drawable.ic_neurology_filled,
        unselectedIconRes = cx.aswin.boxcast.core.designsystem.R.drawable.ic_neurology,
    ),
    NavDestination(
        route = "explore",
        label = "Explore",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
    ),
    NavDestination(
        route = "library",
        label = "Library",
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
    ),
)

/**
 * App bottom navigation using Material 3 [ShortNavigationBar], wrapped with a
 * taller min height and rounded top corners.
 */
@Composable
fun BoxLoreNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(
            topStart = NavBarTopCornerRadius,
            topEnd = NavBarTopCornerRadius,
        ),
        shadowElevation = 3.dp,
    ) {
        ShortNavigationBar(
            modifier = Modifier.heightIn(min = AppNavigationBarHeight),
            containerColor = Color.Transparent,
            arrangement = ShortNavigationBarArrangement.EqualWeight,
        ) {
            bottomNavDestinations.forEach { destination ->
                val isSelected =
                    currentRoute == destination.route ||
                        currentRoute.startsWith("${destination.route}?")

                ShortNavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = when {
                                destination.selectedIconRes != null &&
                                    destination.unselectedIconRes != null -> {
                                    ImageVector.vectorResource(
                                        id = if (isSelected) {
                                            destination.selectedIconRes
                                        } else {
                                            destination.unselectedIconRes
                                        },
                                    )
                                }
                                isSelected -> destination.selectedIcon
                                else -> destination.unselectedIcon
                            },
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                    iconPosition = NavigationItemIconPosition.Top,
                )
            }
        }
    }
}
