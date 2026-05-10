package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.simpmusic.extension.NonLazyGrid
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.maxrave.simpmusic.ui.screen.library.LibraryDynamicPlaylistType
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.favorite
import simpmusic.composeapp.generated.resources.followed
import simpmusic.composeapp.generated.resources.most_played

@Composable
fun LibraryTilingBox(navController: NavController) {
    val listItem = listOf(
        LibraryTilingState.Favorite,
        LibraryTilingState.Followed,
        LibraryTilingState.MostPlayed,
        LibraryTilingState.Downloaded,
    )
    NonLazyGrid(
        columns = 2,
        itemCount = 4,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, end = 10.dp),
    ) { number ->
        Box(Modifier.padding(start = 10.dp, top = 10.dp)) {
            val state = listItem[number]

            // Colores dinámicos del Material Theme según la categoría
            val containerColor = when(state) {
                LibraryTilingState.Favorite -> MaterialTheme.colorScheme.primaryContainer
                LibraryTilingState.Followed -> MaterialTheme.colorScheme.secondaryContainer
                LibraryTilingState.MostPlayed -> MaterialTheme.colorScheme.tertiaryContainer
                LibraryTilingState.Downloaded -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val iconColor = when(state) {
                LibraryTilingState.Favorite -> MaterialTheme.colorScheme.onPrimaryContainer
                LibraryTilingState.Followed -> MaterialTheme.colorScheme.onSecondaryContainer
                LibraryTilingState.MostPlayed -> MaterialTheme.colorScheme.onTertiaryContainer
                LibraryTilingState.Downloaded -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            LibraryTilingItem(
                state = state,
                containerColor = containerColor,
                iconColor = iconColor,
                onClick = {
                    when (state) {
                        LibraryTilingState.Favorite -> {
                            navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.Favorite.toStringParams()))
                        }
                        LibraryTilingState.Followed -> {
                            navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.Followed.toStringParams()))
                        }
                        LibraryTilingState.MostPlayed -> {
                            navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.MostPlayed.toStringParams()))
                        }
                        LibraryTilingState.Downloaded -> {
                            navController.navigate(LibraryDynamicPlaylistDestination(type = LibraryDynamicPlaylistType.Downloaded.toStringParams()))
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun LibraryTilingItem(
    state: LibraryTilingState,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit = {},
) {
    val title = stringResource(state.title)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick.invoke() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.elevatedCardElevation(),
        colors = CardDefaults.elevatedCardColors().copy(containerColor = containerColor),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                state.icon,
                contentDescription = title,
                modifier = Modifier.size(50.dp).padding(10.dp),
                tint = iconColor,
            )
            Text(
                title,
                style = typo().titleSmall,
                color = iconColor,
            )
        }
    }
}

data class LibraryTilingState(
    val title: StringResource,
    val icon: ImageVector,
) {
    companion object {
        val Favorite = LibraryTilingState(title = Res.string.favorite, icon = Icons.Default.Favorite)
        val Followed = LibraryTilingState(title = Res.string.followed, icon = Icons.Default.Insights)
        val MostPlayed = LibraryTilingState(title = Res.string.most_played, icon = Icons.AutoMirrored.Filled.TrendingUp)
        val Downloaded = LibraryTilingState(title = Res.string.downloaded, icon = Icons.Default.Downloading)
    }
}