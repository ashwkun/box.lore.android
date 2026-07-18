package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.SectionHeaderFontFamily

enum class HomeChildHeaderTone {
    PRIMARY,
    TERTIARY,
}

@Composable
fun HomeTopLevelSectionHeader(
    title: String,
    icon: ImageVector,
    seeAllIcon: ImageVector,
    seeAllContentDescription: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = SectionHeaderFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FilledTonalIconButton(
            onClick = onSeeAllClick,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = seeAllIcon,
                contentDescription = seeAllContentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun HomeChildSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: HomeChildHeaderTone = HomeChildHeaderTone.PRIMARY,
) {
    val containerColor =
        when (tone) {
            HomeChildHeaderTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
            HomeChildHeaderTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
        }
    val contentColor =
        when (tone) {
            HomeChildHeaderTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
            HomeChildHeaderTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
