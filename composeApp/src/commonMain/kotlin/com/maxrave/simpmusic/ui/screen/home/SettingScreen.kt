package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.eygraber.uri.toKmpUri
import com.maxrave.common.LIMIT_CACHE_SIZE
import com.maxrave.common.QUALITY
import com.maxrave.common.SUPPORTED_LANGUAGE
import com.maxrave.common.SUPPORTED_LOCATION
import com.maxrave.common.SponsorBlockType
import com.maxrave.common.VIDEO_QUALITY
import com.maxrave.domain.extension.now
import com.maxrave.domain.data.model.lyrics.RomanizationDictionaryState
import com.maxrave.domain.data.model.lyrics.RomanizationLanguage
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.manager.DataStoreManager.Values.TRUE
import com.maxrave.domain.utils.LocalResource
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.expect.ui.fileSaverResult
import com.maxrave.simpmusic.expect.ui.isLyricsBlurSupported
import com.maxrave.simpmusic.expect.ui.isWallpaperDynamicColorSupported
import com.maxrave.simpmusic.extension.bytesToMB
import com.maxrave.simpmusic.extension.displayString
import com.maxrave.simpmusic.extension.isTwoLetterCode
import com.maxrave.simpmusic.extension.isValidProxyHost
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.component.ActionButton
import com.maxrave.simpmusic.ui.component.AmbientThemeGlow
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.LoadingDialog
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.component.rememberNowPlayingGlowTint
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Error
import com.maxrave.simpmusic.ui.icon.PeopleAlt
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.login.DiscordLoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.LastfmLoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.LoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.SpotifyLoginDestination
import com.maxrave.simpmusic.ui.theme.md_theme_dark_primary
import com.maxrave.simpmusic.ui.theme.parseThemeColorHex
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SettingAlertState
import com.maxrave.simpmusic.viewModel.SettingBasicAlertState
import com.maxrave.simpmusic.viewModel.SettingsViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.mohamedrejeb.calf.core.ExperimentalCalfApi
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.add_an_account
import simpmusic.composeapp.generated.resources.ai
import simpmusic.composeapp.generated.resources.ai_api_key
import simpmusic.composeapp.generated.resources.ai_provider
import simpmusic.composeapp.generated.resources.anonymous
import simpmusic.composeapp.generated.resources.app_name
import simpmusic.composeapp.generated.resources.audio
import simpmusic.composeapp.generated.resources.audio_delay
import simpmusic.composeapp.generated.resources.audio_delay_description
import simpmusic.composeapp.generated.resources.audio_reverb
import simpmusic.composeapp.generated.resources.audio_reverb_description
import simpmusic.composeapp.generated.resources.auto_backup
import simpmusic.composeapp.generated.resources.auto_backup_description
import simpmusic.composeapp.generated.resources.auto_download_liked_songs
import simpmusic.composeapp.generated.resources.auto_download_liked_songs_description
import simpmusic.composeapp.generated.resources.backup
import simpmusic.composeapp.generated.resources.backup_downloaded
import simpmusic.composeapp.generated.resources.backup_downloaded_description
import simpmusic.composeapp.generated.resources.backup_frequency
import simpmusic.composeapp.generated.resources.balance_media_loudness
import simpmusic.composeapp.generated.resources.better_lyrics
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.animated_artwork_info
import simpmusic.composeapp.generated.resources.canvas_info
import simpmusic.composeapp.generated.resources.categories_sponsor_block
import simpmusic.composeapp.generated.resources.change
import simpmusic.composeapp.generated.resources.change_language_warning
import simpmusic.composeapp.generated.resources.clear
import simpmusic.composeapp.generated.resources.clear_canvas_cache
import simpmusic.composeapp.generated.resources.clear_downloaded_cache
import simpmusic.composeapp.generated.resources.clear_listening_history
import simpmusic.composeapp.generated.resources.clear_listening_history_confirm
import simpmusic.composeapp.generated.resources.clear_listening_history_description
import simpmusic.composeapp.generated.resources.clear_player_cache
import simpmusic.composeapp.generated.resources.clear_thumbnail_cache
import simpmusic.composeapp.generated.resources.content
import simpmusic.composeapp.generated.resources.content_country
import simpmusic.composeapp.generated.resources.contributor_email
import simpmusic.composeapp.generated.resources.contributor_name
import simpmusic.composeapp.generated.resources.crossfade
import simpmusic.composeapp.generated.resources.crossfade_auto
import simpmusic.composeapp.generated.resources.crossfade_description
import simpmusic.composeapp.generated.resources.crossfade_dj_mode
import simpmusic.composeapp.generated.resources.crossfade_dj_mode_description
import simpmusic.composeapp.generated.resources.crossfade_duration
import simpmusic.composeapp.generated.resources.crossfade_skip_album
import simpmusic.composeapp.generated.resources.crossfade_skip_album_description
import simpmusic.composeapp.generated.resources.custom_ai_model_id
import simpmusic.composeapp.generated.resources.custom_color
import simpmusic.composeapp.generated.resources.custom_model_id_messages
import simpmusic.composeapp.generated.resources.daily
import simpmusic.composeapp.generated.resources.database
import simpmusic.composeapp.generated.resources.default_models
import simpmusic.composeapp.generated.resources.discord_integration
import simpmusic.composeapp.generated.resources.download_quality
import simpmusic.composeapp.generated.resources.downloaded_cache
import simpmusic.composeapp.generated.resources.enable_animated_artwork
import simpmusic.composeapp.generated.resources.enable_canvas
import simpmusic.composeapp.generated.resources.enable_liquid_glass_effect
import simpmusic.composeapp.generated.resources.enable_liquid_glass_effect_description
import simpmusic.composeapp.generated.resources.enable_rich_presence
import simpmusic.composeapp.generated.resources.enable_scrobbling
import simpmusic.composeapp.generated.resources.enable_sponsor_block
import simpmusic.composeapp.generated.resources.enable_spotify_lyrics
import simpmusic.composeapp.generated.resources.equalizer
import simpmusic.composeapp.generated.resources.equalizer_description
import simpmusic.composeapp.generated.resources.free_space
import simpmusic.composeapp.generated.resources.gemini
import simpmusic.composeapp.generated.resources.guest
import simpmusic.composeapp.generated.resources.help_build_lyrics_database
import simpmusic.composeapp.generated.resources.help_build_lyrics_database_description
import simpmusic.composeapp.generated.resources.http
import simpmusic.composeapp.generated.resources.intro_login_to_discord
import simpmusic.composeapp.generated.resources.intro_login_to_lastfm
import simpmusic.composeapp.generated.resources.intro_login_to_spotify
import simpmusic.composeapp.generated.resources.invalid
import simpmusic.composeapp.generated.resources.invalid_api_key
import simpmusic.composeapp.generated.resources.invalid_host
import simpmusic.composeapp.generated.resources.invalid_language_code
import simpmusic.composeapp.generated.resources.invalid_port
import simpmusic.composeapp.generated.resources.keep_backups
import simpmusic.composeapp.generated.resources.keep_backups_format
import simpmusic.composeapp.generated.resources.keep_service_alive
import simpmusic.composeapp.generated.resources.keep_service_alive_description
import simpmusic.composeapp.generated.resources.keep_your_youtube_playlist_offline
import simpmusic.composeapp.generated.resources.keep_your_youtube_playlist_offline_description
import simpmusic.composeapp.generated.resources.kill_service_on_exit
import simpmusic.composeapp.generated.resources.kill_service_on_exit_description
import simpmusic.composeapp.generated.resources.language
import simpmusic.composeapp.generated.resources.last_backup
import simpmusic.composeapp.generated.resources.lastfm_integration
import simpmusic.composeapp.generated.resources.limit_player_cache
import simpmusic.composeapp.generated.resources.listening_history
import simpmusic.composeapp.generated.resources.local_tracking_description
import simpmusic.composeapp.generated.resources.local_tracking_title
import simpmusic.composeapp.generated.resources.log_in_to_discord
import simpmusic.composeapp.generated.resources.log_in_to_lastfm
import simpmusic.composeapp.generated.resources.log_in_to_spotify
import simpmusic.composeapp.generated.resources.log_out
import simpmusic.composeapp.generated.resources.log_out_from_discord
import simpmusic.composeapp.generated.resources.log_out_from_lastfm
import simpmusic.composeapp.generated.resources.log_out_from_spotify
import simpmusic.composeapp.generated.resources.log_out_warning
import simpmusic.composeapp.generated.resources.logged_in
import simpmusic.composeapp.generated.resources.logged_in_as
import simpmusic.composeapp.generated.resources.lrclib
import simpmusic.composeapp.generated.resources.lyrics
import simpmusic.composeapp.generated.resources.lyrics_style
import simpmusic.composeapp.generated.resources.lyrics_offset
import simpmusic.composeapp.generated.resources.lyrics_offset_invalid
import simpmusic.composeapp.generated.resources.lyrics_offset_message
import simpmusic.composeapp.generated.resources.lyrics_offset_value
import simpmusic.composeapp.generated.resources.lyrics_romanization
import simpmusic.composeapp.generated.resources.lyrics_romanization_description
import simpmusic.composeapp.generated.resources.romanization_belarusian
import simpmusic.composeapp.generated.resources.romanization_bulgarian
import simpmusic.composeapp.generated.resources.romanization_chinese
import simpmusic.composeapp.generated.resources.romanization_hindi
import simpmusic.composeapp.generated.resources.romanization_japanese
import simpmusic.composeapp.generated.resources.romanization_japanese_dict_downloading
import simpmusic.composeapp.generated.resources.romanization_japanese_dict_failed
import simpmusic.composeapp.generated.resources.romanization_korean
import simpmusic.composeapp.generated.resources.romanization_kyrgyz
import simpmusic.composeapp.generated.resources.romanization_macedonian
import simpmusic.composeapp.generated.resources.romanization_punjabi
import simpmusic.composeapp.generated.resources.romanization_russian
import simpmusic.composeapp.generated.resources.romanization_serbian
import simpmusic.composeapp.generated.resources.romanization_ukrainian
import simpmusic.composeapp.generated.resources.lyrics_style_apple_music
import simpmusic.composeapp.generated.resources.lyrics_style_classic
import simpmusic.composeapp.generated.resources.main_lyrics_provider
import simpmusic.composeapp.generated.resources.manage_your_youtube_accounts
import simpmusic.composeapp.generated.resources.monthly
import simpmusic.composeapp.generated.resources.never
import simpmusic.composeapp.generated.resources.no_account
import simpmusic.composeapp.generated.resources.normalize_volume
import simpmusic.composeapp.generated.resources.not_available_while_casting
import simpmusic.composeapp.generated.resources.now_playing_style
import simpmusic.composeapp.generated.resources.now_playing_style_apple_music
import simpmusic.composeapp.generated.resources.now_playing_style_m3_expressive
import simpmusic.composeapp.generated.resources.now_playing_style_spotify
import simpmusic.composeapp.generated.resources.ok
import simpmusic.composeapp.generated.resources.openai
import simpmusic.composeapp.generated.resources.openai_api_compatible
import simpmusic.composeapp.generated.resources.other_app
import simpmusic.composeapp.generated.resources.play_explicit_content
import simpmusic.composeapp.generated.resources.play_explicit_content_description
import simpmusic.composeapp.generated.resources.play_video_for_video_track_instead_of_audio_only
import simpmusic.composeapp.generated.resources.playback
import simpmusic.composeapp.generated.resources.player_cache
import simpmusic.composeapp.generated.resources.proxy
import simpmusic.composeapp.generated.resources.proxy_description
import simpmusic.composeapp.generated.resources.proxy_host
import simpmusic.composeapp.generated.resources.proxy_host_message
import simpmusic.composeapp.generated.resources.proxy_password
import simpmusic.composeapp.generated.resources.proxy_password_message
import simpmusic.composeapp.generated.resources.proxy_port
import simpmusic.composeapp.generated.resources.proxy_port_message
import simpmusic.composeapp.generated.resources.proxy_type
import simpmusic.composeapp.generated.resources.proxy_username
import simpmusic.composeapp.generated.resources.proxy_username_message
import simpmusic.composeapp.generated.resources.quality
import simpmusic.composeapp.generated.resources.radio_audio_only
import simpmusic.composeapp.generated.resources.radio_audio_only_description
import simpmusic.composeapp.generated.resources.requires_android_12
import simpmusic.composeapp.generated.resources.restore_your_data
import simpmusic.composeapp.generated.resources.restore_your_saved_data
import simpmusic.composeapp.generated.resources.rich_presence_info
import simpmusic.composeapp.generated.resources.save
import simpmusic.composeapp.generated.resources.save_all_your_playlist_data
import simpmusic.composeapp.generated.resources.save_last_played
import simpmusic.composeapp.generated.resources.save_last_played_track_and_queue
import simpmusic.composeapp.generated.resources.save_playback_state
import simpmusic.composeapp.generated.resources.save_shuffle_and_repeat_mode
import simpmusic.composeapp.generated.resources.scrobbling_info
import simpmusic.composeapp.generated.resources.send_back_listening_data_to_google
import simpmusic.composeapp.generated.resources.set
import simpmusic.composeapp.generated.resources.settings
import simpmusic.composeapp.generated.resources.signed_in
import simpmusic.composeapp.generated.resources.simpmusic_lyrics
import simpmusic.composeapp.generated.resources.skip_no_music_part
import simpmusic.composeapp.generated.resources.skip_silent
import simpmusic.composeapp.generated.resources.skip_sponsor_part_of_video
import simpmusic.composeapp.generated.resources.socks
import simpmusic.composeapp.generated.resources.sponsorBlock
import simpmusic.composeapp.generated.resources.sponsor_block_intro
import simpmusic.composeapp.generated.resources.spotify
import simpmusic.composeapp.generated.resources.spotify_canvas_cache
import simpmusic.composeapp.generated.resources.spotify_lyrícs_info
import simpmusic.composeapp.generated.resources.storage
import simpmusic.composeapp.generated.resources.such_as_music_video_lyrics_video_podcasts_and_more
import simpmusic.composeapp.generated.resources.sync_follow_to_youtube
import simpmusic.composeapp.generated.resources.sync_follow_to_youtube_description
import simpmusic.composeapp.generated.resources.theme
import simpmusic.composeapp.generated.resources.theme_color
import simpmusic.composeapp.generated.resources.theme_color_custom
import simpmusic.composeapp.generated.resources.theme_color_default
import simpmusic.composeapp.generated.resources.theme_color_wallpaper
import simpmusic.composeapp.generated.resources.theme_mode_dark
import simpmusic.composeapp.generated.resources.theme_mode_light
import simpmusic.composeapp.generated.resources.theme_mode_system
import simpmusic.composeapp.generated.resources.thumbnail_cache
import simpmusic.composeapp.generated.resources.translation_language
import simpmusic.composeapp.generated.resources.translation_language_message
import simpmusic.composeapp.generated.resources.translucent_bottom_navigation_bar
import simpmusic.composeapp.generated.resources.unknown
import simpmusic.composeapp.generated.resources.upload_your_listening_history_to_youtube_music_server_it_will_make_yt_music_recommendation_system_better_working_only_if_logged_in
import simpmusic.composeapp.generated.resources.use_ai_translation
import simpmusic.composeapp.generated.resources.use_ai_translation_description
import simpmusic.composeapp.generated.resources.user_interface
import simpmusic.composeapp.generated.resources.video_download_quality
import simpmusic.composeapp.generated.resources.video_quality
import simpmusic.composeapp.generated.resources.warning
import simpmusic.composeapp.generated.resources.weekly
import simpmusic.composeapp.generated.resources.what_segments_will_be_skipped
import simpmusic.composeapp.generated.resources.you_can_see_the_content_below_the_bottom_bar
import simpmusic.composeapp.generated.resources.youtube_account
import simpmusic.composeapp.generated.resources.youtube_subtitle_language
import simpmusic.composeapp.generated.resources.youtube_subtitle_language_message
import simpmusic.composeapp.generated.resources.youtube_transcript
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoilApi::class,
    ExperimentalHazeMaterialsApi::class,
    FormatStringsInDatetimeFormats::class,
    ExperimentalCalfApi::class,
)
@Composable
fun SettingScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val platformContext = LocalPlatformContext.current
    val pl = com.mohamedrejeb.calf.core.LocalPlatformContext.current
    val localDensity = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var width by rememberSaveable { mutableIntStateOf(0) }

    val formatter =
        LocalDateTime.Format {
            byUnicodePattern("yyyyMMddHHmmss")
        }
    val appName = stringResource(Res.string.app_name)

    val backupLauncher =
        fileSaverResult(
            "${appName}_${
                now().format(
                    formatter,
                )
            }.backup",
            "application/octet-stream",
        ) { uri ->
            uri?.let {
                viewModel.backup(it.toKmpUri())
            }
        }

    val restoreLauncher =
        rememberFilePickerLauncher(
            type =
                FilePickerFileType.All,
            selectionMode = FilePickerSelectionMode.Single,
        ) { file ->
            file.firstOrNull()?.getPath(pl)?.toKmpUri()?.let {
                viewModel.restore(it)
            }
        }

    val enableTranslucentNavBar by remember { viewModel.translucentBottomBar.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val language by viewModel.language.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val autoDownloadLikedSongs by viewModel.autoDownloadLikedSongs.collectAsStateWithLifecycle()
    val videoDownloadQuality by viewModel.videoDownloadQuality.collectAsStateWithLifecycle()
    val keepYoutubePlaylistOffline by viewModel.keepYouTubePlaylistOffline.collectAsStateWithLifecycle()
    val localTrackingEnabled by viewModel.localTrackingEnabled.collectAsStateWithLifecycle(initialValue = false)
    val blogNotificationEnabled by viewModel.blogNotificationEnabled.collectAsStateWithLifecycle()
    val combineLocalAndYouTubeLiked by viewModel.combineLocalAndYouTubeLiked.collectAsStateWithLifecycle()
    val playVideo by remember { viewModel.playVideoInsteadOfAudio.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val radioAudioOnly by remember { viewModel.radioAudioOnly.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val sendData by remember { viewModel.sendBackToGoogle.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val normalizeVolume by remember { viewModel.normalizeVolume.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val skipSilent by remember { viewModel.skipSilent.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val savePlaybackState by remember { viewModel.savedPlaybackState.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val saveLastPlayed by remember { viewModel.saveRecentSongAndQueue.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val killServiceOnExit by remember { viewModel.killServiceOnExit.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = true)
    val mainLyricsProvider by viewModel.mainLyricsProvider.collectAsStateWithLifecycle()
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsStateWithLifecycle()
    val youtubeSubtitleLanguage by viewModel.youtubeSubtitleLanguage.collectAsStateWithLifecycle()
    val spotifyLoggedIn by viewModel.spotifyLogIn.collectAsStateWithLifecycle()
    val spotifyLyrics by viewModel.spotifyLyrics.collectAsStateWithLifecycle()
    val spotifyCanvas by viewModel.spotifyCanvas.collectAsStateWithLifecycle()
    val amAnimatedArtwork by viewModel.amAnimatedArtwork.collectAsStateWithLifecycle()
    val enableSponsorBlock by remember { viewModel.sponsorBlockEnabled.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val skipSegments by viewModel.sponsorBlockCategories.collectAsStateWithLifecycle()
    val playerCache by viewModel.cacheSize.collectAsStateWithLifecycle()
    val downloadedCache by viewModel.downloadedCacheSize.collectAsStateWithLifecycle()
    val thumbnailCache by viewModel.thumbCacheSize.collectAsStateWithLifecycle()
    val canvasCache by viewModel.canvasCacheSize.collectAsStateWithLifecycle()
    val limitPlayerCache by viewModel.playerCacheLimit.collectAsStateWithLifecycle()
    val fraction by viewModel.fraction.collectAsStateWithLifecycle()
    val lastCheckUpdate by viewModel.lastCheckForUpdate.collectAsStateWithLifecycle()
    val explicitContentEnabled by viewModel.explicitContentEnabled.collectAsStateWithLifecycle()
    val usingProxy by viewModel.usingProxy.collectAsStateWithLifecycle()
    val proxyType by viewModel.proxyType.collectAsStateWithLifecycle()
    val proxyHost by viewModel.proxyHost.collectAsStateWithLifecycle()
    val proxyPort by viewModel.proxyPort.collectAsStateWithLifecycle()
    val proxyUsername by viewModel.proxyUsername.collectAsStateWithLifecycle()
    val proxyPassword by viewModel.proxyPassword.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val isHasApiKey by viewModel.isHasApiKey.collectAsStateWithLifecycle()
    val useAITranslation by viewModel.useAITranslation.collectAsStateWithLifecycle()
    val translationLanguage by viewModel.translationLanguage.collectAsStateWithLifecycle()
    val customModelId by viewModel.customModelId.collectAsStateWithLifecycle()
    val customOpenAIBaseUrl by viewModel.customOpenAIBaseUrl.collectAsStateWithLifecycle()
    val customOpenAIHeaders by viewModel.customOpenAIHeaders.collectAsStateWithLifecycle()
    val helpBuildLyricsDatabase by viewModel.helpBuildLyricsDatabase.collectAsStateWithLifecycle()
    val contributor by viewModel.contributor.collectAsStateWithLifecycle()
    val backupDownloaded by viewModel.backupDownloaded.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsStateWithLifecycle()
    val autoBackupMaxFiles by viewModel.autoBackupMaxFiles.collectAsStateWithLifecycle()
    val autoBackupLastTime by viewModel.autoBackupLastTime.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val enableLiquidGlass by viewModel.enableLiquidGlass.collectAsStateWithLifecycle()
    val themeMode by sharedViewModel.getThemeMode().collectAsStateWithLifecycle(DataStoreManager.THEME_MODE_DARK)
    val themeColorSource by sharedViewModel.getThemeColorSource().collectAsStateWithLifecycle(DataStoreManager.THEME_COLOR_DEFAULT)
    val customThemeColorHex by sharedViewModel.getCustomThemeColor().collectAsStateWithLifecycle(DataStoreManager.DEFAULT_THEME_COLOR_HEX)
    val nowPlayingStyle by sharedViewModel.getNowPlayingStyle().collectAsStateWithLifecycle(DataStoreManager.NOW_PLAYING_STYLE_SPOTIFY)
    val lyricsStyle by sharedViewModel.getLyricsStyle().collectAsStateWithLifecycle(DataStoreManager.LYRICS_STYLE_CLASSIC)
    val romanizationStored by sharedViewModel.getRomanizationLanguages().collectAsStateWithLifecycle("")
    val japaneseDictionaryState by viewModel.japaneseDictionaryState.collectAsStateWithLifecycle()
    var showColorPickerDialog by rememberSaveable { mutableStateOf(false) }
    val discordLoggedIn by viewModel.discordLoggedIn.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val syncFollowToYouTube by viewModel.syncFollowToYouTube.collectAsStateWithLifecycle()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsStateWithLifecycle()
    val delayEnabled by viewModel.delayEnabled.collectAsStateWithLifecycle()
    val reverbEnabled by viewModel.reverbEnabled.collectAsStateWithLifecycle()
    val lastfmLoggedIn by viewModel.lastfmLoggedIn.collectAsStateWithLifecycle()
    val lastfmUsername by viewModel.lastfmUsername.collectAsStateWithLifecycle()
    val lastfmScrobbleEnabled by viewModel.lastfmScrobbleEnabled.collectAsStateWithLifecycle()
    val richPresenceEnabled by viewModel.richPresenceEnabled.collectAsStateWithLifecycle()
    val keepServiceAlive by viewModel.keepServiceAlive.collectAsStateWithLifecycle()

    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
    val crossfadeDjMode by viewModel.crossfadeDjMode.collectAsStateWithLifecycle()
    val crossfadeSkipAlbum by viewModel.crossfadeSkipAlbum.collectAsStateWithLifecycle()
    val castState by viewModel.castState.collectAsStateWithLifecycle()

    val hazeState = rememberHazeState(blurEnabled = true)

    var showYouTubeAccountDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(true) {
        viewModel.getAllGoogleAccount()
    }

    LaunchedEffect(true) {
        viewModel.getData()
        viewModel.getThumbCacheSize(platformContext)
    }

    val settingListState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { settingListState.firstVisibleItemIndex == 0 && settingListState.firstVisibleItemScrollOffset == 0 }
    }
    val glowNowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    AmbientThemeGlow(
        tint = rememberNowPlayingGlowTint(glowNowPlaying?.songEntity?.thumbnails),
        modifier =
            Modifier.graphicsLayer {
                translationY =
                    if (settingListState.firstVisibleItemIndex == 0) {
                        -settingListState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        -size.height
                    }
            },
    )
    LazyColumn(
        state = settingListState,
        contentPadding = innerPadding,
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .hazeSource(hazeState),
    ) {
        item(key = "user_interface") {
            Column {
                Spacer(Modifier.height(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(text = stringResource(Res.string.user_interface), style = typo().labelMedium, color = MaterialTheme.colorScheme.onBackground)
                val themeModeLabels =
                    listOf(
                        DataStoreManager.THEME_MODE_SYSTEM to stringResource(Res.string.theme_mode_system),
                        DataStoreManager.THEME_MODE_DARK to stringResource(Res.string.theme_mode_dark),
                        DataStoreManager.THEME_MODE_LIGHT to stringResource(Res.string.theme_mode_light),
                    )
                SettingItem(
                    title = stringResource(Res.string.theme),
                    subtitle = themeModeLabels.firstOrNull { it.first == themeMode }?.second ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.theme) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect = themeModeLabels.map { (it.first == themeMode) to it.second },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        val selected = state.selectOne?.getSelected()
                                        themeModeLabels.firstOrNull { it.second == selected }?.first?.let {
                                            sharedViewModel.setThemeMode(it)
                                        }
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                val requiresAndroid12 = " (" + stringResource(Res.string.requires_android_12) + ")"
                val nowPlayingStyleLabels =
                    listOf(
                        DataStoreManager.NOW_PLAYING_STYLE_SPOTIFY to stringResource(Res.string.now_playing_style_spotify),
                        DataStoreManager.NOW_PLAYING_STYLE_M3_EXPRESSIVE to stringResource(Res.string.now_playing_style_m3_expressive),
                        DataStoreManager.NOW_PLAYING_STYLE_APPLE_MUSIC to
                            stringResource(Res.string.now_playing_style_apple_music) + requiresAndroid12,
                    )
                SettingItem(
                    title = stringResource(Res.string.now_playing_style),
                    subtitle = nowPlayingStyleLabels.firstOrNull { it.first == nowPlayingStyle }?.second ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.now_playing_style) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect = nowPlayingStyleLabels.map { (it.first == nowPlayingStyle) to it.second },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        val selected = state.selectOne?.getSelected()
                                        nowPlayingStyleLabels.firstOrNull { it.second == selected }?.first?.let {
                                            sharedViewModel.setNowPlayingStyle(it)
                                        }
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                if (isLyricsBlurSupported()) {
                    val lyricsStyleLabels =
                        listOf(
                            DataStoreManager.LYRICS_STYLE_CLASSIC to stringResource(Res.string.lyrics_style_classic),
                            DataStoreManager.LYRICS_STYLE_APPLE_MUSIC to
                                stringResource(Res.string.lyrics_style_apple_music) + requiresAndroid12,
                        )
                    SettingItem(
                        title = stringResource(Res.string.lyrics_style),
                        subtitle = lyricsStyleLabels.firstOrNull { it.first == lyricsStyle }?.second ?: "",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = runBlocking { getString(Res.string.lyrics_style) },
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect = lyricsStyleLabels.map { (it.first == lyricsStyle) to it.second },
                                        ),
                                    confirm =
                                        runBlocking { getString(Res.string.change) } to { state ->
                                            val selected = state.selectOne?.getSelected()
                                            lyricsStyleLabels.firstOrNull { it.second == selected }?.first?.let {
                                                sharedViewModel.setLyricsStyle(it)
                                            }
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                }

                val romanizationLabels =
                    listOf(
                        RomanizationLanguage.JAPANESE to stringResource(Res.string.romanization_japanese),
                        RomanizationLanguage.KOREAN to stringResource(Res.string.romanization_korean),
                        RomanizationLanguage.CHINESE to stringResource(Res.string.romanization_chinese),
                        RomanizationLanguage.HINDI to stringResource(Res.string.romanization_hindi),
                        RomanizationLanguage.PUNJABI to stringResource(Res.string.romanization_punjabi),
                        RomanizationLanguage.RUSSIAN to stringResource(Res.string.romanization_russian),
                        RomanizationLanguage.UKRAINIAN to stringResource(Res.string.romanization_ukrainian),
                        RomanizationLanguage.SERBIAN to stringResource(Res.string.romanization_serbian),
                        RomanizationLanguage.BULGARIAN to stringResource(Res.string.romanization_bulgarian),
                        RomanizationLanguage.BELARUSIAN to stringResource(Res.string.romanization_belarusian),
                        RomanizationLanguage.KYRGYZ to stringResource(Res.string.romanization_kyrgyz),
                        RomanizationLanguage.MACEDONIAN to stringResource(Res.string.romanization_macedonian),
                    )
                val romanizationSelected = RomanizationLanguage.parse(romanizationStored)
                SettingItem(
                    title = stringResource(Res.string.lyrics_romanization),
                    subtitle =
                        if (romanizationSelected.isEmpty()) {
                            stringResource(Res.string.lyrics_romanization_description)
                        } else {
                            val selectedNames =
                                romanizationLabels.filter { it.first in romanizationSelected }.joinToString(", ") { it.second }
                            when {
                                RomanizationLanguage.JAPANESE !in romanizationSelected -> selectedNames
                                japaneseDictionaryState == RomanizationDictionaryState.DOWNLOADING ->
                                    "$selectedNames — ${stringResource(Res.string.romanization_japanese_dict_downloading)}"
                                japaneseDictionaryState == RomanizationDictionaryState.FAILED ->
                                    "$selectedNames — ${stringResource(Res.string.romanization_japanese_dict_failed)}"
                                else -> selectedNames
                            }
                        },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.lyrics_romanization) },
                                multipleSelect =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            romanizationLabels.map { (language, label) ->
                                                (language in romanizationSelected) to label
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.save) } to { state ->
                                        val chosen = state.multipleSelect?.getListSelected().orEmpty()
                                        val languages =
                                            romanizationLabels.filter { it.second in chosen }.map { it.first }.toSet()
                                        sharedViewModel.setRomanizationLanguages(languages)
                                        if (RomanizationLanguage.JAPANESE in languages) {
                                            viewModel.downloadJapaneseDictionaryIfNeeded()
                                        }
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                val colorSourceLabels =
                    buildList {
                        add(DataStoreManager.THEME_COLOR_DEFAULT to stringResource(Res.string.theme_color_default))
                        if (isWallpaperDynamicColorSupported()) {
                            add(DataStoreManager.THEME_COLOR_WALLPAPER to stringResource(Res.string.theme_color_wallpaper))
                        }
                        add(DataStoreManager.THEME_COLOR_CUSTOM to stringResource(Res.string.theme_color_custom))
                    }
                SettingItem(
                    title = stringResource(Res.string.theme_color),
                    subtitle = colorSourceLabels.firstOrNull { it.first == themeColorSource }?.second ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.theme_color) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect = colorSourceLabels.map { (it.first == themeColorSource) to it.second },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        val selected = state.selectOne?.getSelected()
                                        colorSourceLabels.firstOrNull { it.second == selected }?.first?.let {
                                            sharedViewModel.setThemeColorSource(it)
                                            if (it == DataStoreManager.THEME_COLOR_CUSTOM) {
                                                showColorPickerDialog = true
                                            }
                                        }
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                if (themeColorSource == DataStoreManager.THEME_COLOR_CUSTOM) {
                    SettingItem(
                        title = stringResource(Res.string.custom_color),
                        subtitle = "#${customThemeColorHex.takeLast(6)}",
                        smallSubtitle = true,
                        onClick = { showColorPickerDialog = true },
                    )
                }
                SettingItem(
                    title = stringResource(Res.string.translucent_bottom_navigation_bar),
                    subtitle = stringResource(Res.string.you_can_see_the_content_below_the_bottom_bar),
                    smallSubtitle = true,
                    switch = (enableTranslucentNavBar to { viewModel.setTranslucentBottomBar(it) }),
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.enable_liquid_glass_effect),
                        subtitle = stringResource(Res.string.enable_liquid_glass_effect_description),
                        smallSubtitle = true,
                        switch = (enableLiquidGlass to { viewModel.setEnableLiquidGlass(it) }),
                        isEnable = getPlatform() == Platform.Android,
                    )
                }
            }
        }
        item(key = "content") {
            Column {
                Text(
                    text = stringResource(Res.string.content),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.youtube_account),
                    subtitle = stringResource(Res.string.manage_your_youtube_accounts),
                    onClick = {
                        viewModel.getAllGoogleAccount()
                        showYouTubeAccountDialog = true
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.language),
                    subtitle = SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US"),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.language) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            SUPPORTED_LANGUAGE.items.map {
                                                (it.toString() == SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US")) to it.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        val code = SUPPORTED_LANGUAGE.getCodeFromLanguage(state.selectOne?.getSelected() ?: "English")
                                        viewModel.setBasicAlertData(
                                            SettingBasicAlertState(
                                                title = runBlocking { getString(Res.string.warning) },
                                                message = runBlocking { getString(Res.string.change_language_warning) },
                                                confirm =
                                                    runBlocking { getString(Res.string.change) } to {
                                                        sharedViewModel.activityRecreate()
                                                        viewModel.setBasicAlertData(null)
                                                        viewModel.changeLanguage(code)
                                                    },
                                                dismiss = runBlocking { getString(Res.string.cancel) },
                                            ),
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.content_country),
                    subtitle = location ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.content_country) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            SUPPORTED_LOCATION.items.map { item ->
                                                (item.toString() == location) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.changeLocation(
                                            state.selectOne?.getSelected() ?: "US",
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.quality),
                    subtitle = quality ?: "",
                    smallSubtitle = true,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            QUALITY.items.map { item ->
                                                (item.toString() == quality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.changeQuality(state.selectOne?.getSelected())
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.download_quality),
                    subtitle = downloadQuality ?: "",
                    smallSubtitle = true,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.download_quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            QUALITY.items.map { item ->
                                                (item.toString() == downloadQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        state.selectOne?.getSelected()?.let { viewModel.setDownloadQuality(it) }
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.video_quality),
                    subtitle = videoQuality ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.video_quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            VIDEO_QUALITY.items.map { item ->
                                                (item.toString() == videoQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.changeVideoQuality(state.selectOne?.getSelected() ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.video_download_quality),
                    subtitle = videoDownloadQuality ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.video_download_quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            VIDEO_QUALITY.items.map { item ->
                                                (item.toString() == videoDownloadQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setVideoDownloadQuality(state.selectOne?.getSelected() ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.auto_download_liked_songs),
                    subtitle = stringResource(Res.string.auto_download_liked_songs_description),
                    smallSubtitle = true,
                    switch = (autoDownloadLikedSongs to { viewModel.setAutoDownloadLikedSongs(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.play_video_for_video_track_instead_of_audio_only),
                    subtitle = stringResource(Res.string.such_as_music_video_lyrics_video_podcasts_and_more),
                    smallSubtitle = true,
                    switch = (playVideo to { viewModel.setPlayVideoInsteadOfAudio(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.radio_audio_only),
                    subtitle = stringResource(Res.string.radio_audio_only_description),
                    smallSubtitle = true,
                    switch = (radioAudioOnly to { viewModel.setRadioAudioOnly(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.sync_follow_to_youtube),
                    subtitle = stringResource(Res.string.sync_follow_to_youtube_description),
                    smallSubtitle = true,
                    switch = (syncFollowToYouTube to { viewModel.setSyncFollowToYouTube(it) }),
                    isEnable = loggedIn == DataStoreManager.TRUE,
                )
                SettingItem(
                    title = stringResource(Res.string.play_explicit_content),
                    subtitle = stringResource(Res.string.play_explicit_content_description),
                    switch = (explicitContentEnabled to { viewModel.setExplicitContentEnabled(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.keep_your_youtube_playlist_offline),
                    subtitle = stringResource(Res.string.keep_your_youtube_playlist_offline_description),
                    switch = (keepYoutubePlaylistOffline to { viewModel.setKeepYouTubePlaylistOffline(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.proxy),
                    subtitle = stringResource(Res.string.proxy_description),
                    switch = (usingProxy to { viewModel.setUsingProxy(it) }),
                )
            }
        }
        item(key = "proxy") {
            Crossfade(usingProxy) { it ->
                if (it) {
                    Column {
                        SettingItem(
                            title = stringResource(Res.string.proxy_type),
                            subtitle =
                                when (proxyType) {
                                    DataStoreManager.ProxyType.PROXY_TYPE_HTTP -> stringResource(Res.string.http)
                                    DataStoreManager.ProxyType.PROXY_TYPE_SOCKS -> stringResource(Res.string.socks)
                                },
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.proxy_type) },
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(
                                                        (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_HTTP) to
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.http,
                                                                )
                                                            },
                                                        (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_SOCKS) to
                                                            runBlocking { getString(Res.string.socks) },
                                                    ),
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                viewModel.setProxy(
                                                    if (state.selectOne?.getSelected() == runBlocking { getString(Res.string.socks) }) {
                                                        DataStoreManager.ProxyType.PROXY_TYPE_SOCKS
                                                    } else {
                                                        DataStoreManager.ProxyType.PROXY_TYPE_HTTP
                                                    },
                                                    proxyHost,
                                                    proxyPort,
                                                )
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_host),
                            subtitle = proxyHost,
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.proxy_host) },
                                        message = runBlocking { getString(Res.string.proxy_host_message) },
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = runBlocking { getString(Res.string.proxy_host) },
                                                value = proxyHost,
                                                verifyCodeBlock = {
                                                    isValidProxyHost(it) to runBlocking { getString(Res.string.invalid_host) }
                                                },
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                viewModel.setProxy(
                                                    proxyType,
                                                    state.textField?.value ?: "",
                                                    proxyPort,
                                                )
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_port),
                            subtitle = proxyPort.toString(),
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.proxy_port) },
                                        message = runBlocking { getString(Res.string.proxy_port_message) },
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = runBlocking { getString(Res.string.proxy_port) },
                                                value = proxyPort.toString(),
                                                verifyCodeBlock = {
                                                    (it.toIntOrNull() != null) to runBlocking { getString(Res.string.invalid_port) }
                                                },
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                viewModel.setProxy(
                                                    proxyType,
                                                    proxyHost,
                                                    state.textField?.value?.toIntOrNull() ?: 0,
                                                )
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_username),
                            subtitle = proxyUsername,
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.proxy_username) },
                                        message = runBlocking { getString(Res.string.proxy_username_message) },
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = runBlocking { getString(Res.string.proxy_username) },
                                                value = proxyUsername,
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                viewModel.setProxyCredentials(
                                                    state.textField?.value ?: "",
                                                    proxyPassword,
                                                )
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_password),
                            subtitle =
                                if (proxyPassword.isEmpty()) {
                                    ""
                                } else {
                                    "\u2022".repeat(proxyPassword.length)
                                },
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.proxy_password) },
                                        message = runBlocking { getString(Res.string.proxy_password_message) },
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = runBlocking { getString(Res.string.proxy_password) },
                                                value = proxyPassword,
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                viewModel.setProxyCredentials(
                                                    proxyUsername,
                                                    state.textField?.value ?: "",
                                                )
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
        if (getPlatform() == Platform.Android) {
            item(key = "audio") {
                Column {
                    Text(
                        text = stringResource(Res.string.audio),
                        style = typo().labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.normalize_volume),
                        subtitle = stringResource(Res.string.balance_media_loudness),
                        switch = (normalizeVolume to { viewModel.setNormalizeVolume(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.skip_silent),
                        subtitle = stringResource(Res.string.skip_no_music_part),
                        switch = (skipSilent to { viewModel.setSkipSilent(it) }),
                    )
                }
            }
        }
        item(key = "playback") {
            Column {
                Text(
                    text = stringResource(Res.string.playback),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.equalizer),
                    subtitle = stringResource(Res.string.equalizer_description),
                    smallSubtitle = true,
                    switch = (equalizerEnabled to { viewModel.setEqualizerEnabled(it) }),
                )
                AnimatedVisibility(visible = equalizerEnabled) {
                    EqualizerSection()
                }
                SettingItem(
                    title = stringResource(Res.string.audio_delay),
                    subtitle = stringResource(Res.string.audio_delay_description),
                    smallSubtitle = true,
                    switch = (delayEnabled to { viewModel.setDelayEnabled(it) }),
                )
                AnimatedVisibility(visible = delayEnabled) {
                    DelaySection()
                }
                SettingItem(
                    title = stringResource(Res.string.audio_reverb),
                    subtitle = stringResource(Res.string.audio_reverb_description),
                    smallSubtitle = true,
                    switch = (reverbEnabled to { viewModel.setReverbEnabled(it) }),
                )
                AnimatedVisibility(visible = reverbEnabled) {
                    ReverbSection()
                }
                SettingItem(
                    title = stringResource(Res.string.save_playback_state),
                    subtitle = stringResource(Res.string.save_shuffle_and_repeat_mode),
                    switch = (savePlaybackState to { viewModel.setSavedPlaybackState(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.save_last_played),
                    subtitle = stringResource(Res.string.save_last_played_track_and_queue),
                    switch = (saveLastPlayed to { viewModel.setSaveLastPlayed(it) }),
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.kill_service_on_exit),
                        subtitle = stringResource(Res.string.kill_service_on_exit_description),
                        switch = (killServiceOnExit to { viewModel.setKillServiceOnExit(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.keep_service_alive),
                        subtitle = stringResource(Res.string.keep_service_alive_description),
                        switch = (keepServiceAlive to { viewModel.setKeepServiceAlive(it) }),
                    )
                }
            }
        }
        item(key = "crossfade_settings") {
            Column {
                SettingItem(
                    title = stringResource(Res.string.crossfade),
                    subtitle =
                        if (castState.isRemote) {
                            stringResource(Res.string.not_available_while_casting)
                        } else {
                            stringResource(Res.string.crossfade_description)
                        },
                    smallSubtitle = true,
                    switch = (crossfadeEnabled to { viewModel.setCrossfadeEnabled(it) }),
                    isEnable = !castState.isRemote,
                )
                AnimatedVisibility(visible = crossfadeEnabled) {
                    Column {
                        SettingItem(
                            title = stringResource(Res.string.crossfade_duration),
                            subtitle =
                                if (castState.isRemote) {
                                    stringResource(Res.string.not_available_while_casting)
                                } else if (crossfadeDuration == DataStoreManager.CROSSFADE_DURATION_AUTO) {
                                    stringResource(Res.string.crossfade_auto)
                                } else {
                                    "${crossfadeDuration / 1000}s"
                                },
                            isEnable = !castState.isRemote,
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.crossfade_duration) },
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(
                                                        (crossfadeDuration == DataStoreManager.CROSSFADE_DURATION_AUTO) to
                                                            runBlocking { getString(Res.string.crossfade_auto) },
                                                        (crossfadeDuration == 1000) to "1s",
                                                        (crossfadeDuration == 2000) to "2s",
                                                        (crossfadeDuration == 3000) to "3s",
                                                        (crossfadeDuration == 5000) to "5s",
                                                        (crossfadeDuration == 8000) to "8s",
                                                        (crossfadeDuration == 10000) to "10s",
                                                        (crossfadeDuration == 12000) to "12s",
                                                        (crossfadeDuration == 15000) to "15s",
                                                        (crossfadeDuration == 20000) to "20s",
                                                        (crossfadeDuration == 30000) to "30s",
                                                    ),
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                val duration =
                                                    when (state.selectOne?.getSelected()) {
                                                        runBlocking {
                                                            getString(
                                                                Res.string.crossfade_auto,
                                                            )
                                                        },
                                                            -> DataStoreManager.CROSSFADE_DURATION_AUTO
                                                        "1s" -> 1000
                                                        "2s" -> 2000
                                                        "3s" -> 3000
                                                        "5s" -> 5000
                                                        "8s" -> 8000
                                                        "10s" -> 10000
                                                        "12s" -> 12000
                                                        "15s" -> 15000
                                                        "20s" -> 20000
                                                        "30s" -> 30000
                                                        else -> 5000
                                                    }
                                                viewModel.setCrossfadeDuration(duration)
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.crossfade_dj_mode),
                            subtitle =
                                if (castState.isRemote) {
                                    stringResource(Res.string.not_available_while_casting)
                                } else {
                                    stringResource(Res.string.crossfade_dj_mode_description)
                                },
                            smallSubtitle = true,
                            switch = ((crossfadeDjMode) to { viewModel.setCrossfadeDjMode(it) }),
                            isEnable = !castState.isRemote,
                        )
                        SettingItem(
                            title = stringResource(Res.string.crossfade_skip_album),
                            subtitle =
                                if (castState.isRemote) {
                                    stringResource(Res.string.not_available_while_casting)
                                } else {
                                    stringResource(Res.string.crossfade_skip_album_description)
                                },
                            smallSubtitle = true,
                            switch = ((crossfadeSkipAlbum) to { viewModel.setCrossfadeSkipAlbum(it) }),
                            isEnable = !castState.isRemote,
                        )
                    }
                }
            }
        }
        item(key = "listening_history") {
            Column {
                Text(
                    text = stringResource(Res.string.listening_history),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.local_tracking_title),
                    subtitle = stringResource(Res.string.local_tracking_description),
                    switch = (localTrackingEnabled to { viewModel.setLocalTrackingEnabled(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.clear_listening_history),
                    subtitle = stringResource(Res.string.clear_listening_history_description),
                    onClick = {
                        viewModel.setBasicAlertData(
                            SettingBasicAlertState(
                                title = runBlocking { getString(Res.string.clear_listening_history) },
                                message = runBlocking { getString(Res.string.clear_listening_history_confirm) },
                                confirm =
                                    runBlocking { getString(Res.string.clear) } to {
                                        viewModel.clearListeningHistory()
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
            }
        }
        item(key = "lyrics") {
            Column {
                Text(
                    text = stringResource(Res.string.lyrics),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.main_lyrics_provider),
                    subtitle =
                        when (mainLyricsProvider) {
                            DataStoreManager.SIMPMUSIC -> stringResource(Res.string.simpmusic_lyrics)
                            DataStoreManager.YOUTUBE -> stringResource(Res.string.youtube_transcript)
                            DataStoreManager.LRCLIB -> stringResource(Res.string.lrclib)
                            DataStoreManager.BETTER_LYRICS -> stringResource(Res.string.better_lyrics)
                            else -> stringResource(Res.string.unknown)
                        },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.main_lyrics_provider) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listOf(
                                                (mainLyricsProvider == DataStoreManager.SIMPMUSIC) to
                                                    runBlocking { getString(Res.string.simpmusic_lyrics) },
                                                (mainLyricsProvider == DataStoreManager.YOUTUBE) to
                                                    runBlocking { getString(Res.string.youtube_transcript) },
                                                (mainLyricsProvider == DataStoreManager.LRCLIB) to runBlocking { getString(Res.string.lrclib) },
                                                (mainLyricsProvider == DataStoreManager.BETTER_LYRICS) to
                                                    runBlocking { getString(Res.string.better_lyrics) },
                                            ),
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setLyricsProvider(
                                            when (state.selectOne?.getSelected()) {
                                                runBlocking { getString(Res.string.simpmusic_lyrics) } -> DataStoreManager.SIMPMUSIC
                                                runBlocking { getString(Res.string.youtube_transcript) } -> DataStoreManager.YOUTUBE
                                                runBlocking { getString(Res.string.lrclib) } -> DataStoreManager.LRCLIB
                                                runBlocking { getString(Res.string.better_lyrics) } -> DataStoreManager.BETTER_LYRICS
                                                else -> DataStoreManager.SIMPMUSIC
                                            },
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )

                SettingItem(
                    title = stringResource(Res.string.lyrics_offset),
                    subtitle =
                        stringResource(
                            Res.string.lyrics_offset_value,
                            if (lyricsOffsetMs > 0) "+$lyricsOffsetMs" else lyricsOffsetMs.toString(),
                        ),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.lyrics_offset) },
                                message = runBlocking { getString(Res.string.lyrics_offset_message) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.lyrics_offset) },
                                        value = lyricsOffsetMs.toString(),
                                        verifyCodeBlock = {
                                            (it.trim().toIntOrNull() != null) to
                                                runBlocking { getString(Res.string.lyrics_offset_invalid) }
                                        },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        state.textField
                                            ?.value
                                            ?.trim()
                                            ?.toIntOrNull()
                                            ?.let { viewModel.setLyricsOffsetMs(it) }
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )

                SettingItem(
                    title = stringResource(Res.string.translation_language),
                    subtitle = translationLanguage ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.translation_language) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.translation_language) },
                                        value = translationLanguage ?: "",
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                runBlocking { getString(Res.string.invalid_language_code) }
                                        },
                                    ),
                                message = runBlocking { getString(Res.string.translation_language_message) },
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setTranslationLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                    isEnable = true,
                )
                SettingItem(
                    title = stringResource(Res.string.youtube_subtitle_language),
                    subtitle = youtubeSubtitleLanguage,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.youtube_subtitle_language) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.youtube_subtitle_language) },
                                        value = youtubeSubtitleLanguage,
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                runBlocking { getString(Res.string.invalid_language_code) }
                                        },
                                    ),
                                message = runBlocking { getString(Res.string.youtube_subtitle_language_message) },
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setYoutubeSubtitleLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.help_build_lyrics_database),
                    subtitle = stringResource(Res.string.help_build_lyrics_database_description),
                    switch = (helpBuildLyricsDatabase to { viewModel.setHelpBuildLyricsDatabase(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.contributor_name),
                    subtitle = contributor.first.ifEmpty { stringResource(Res.string.anonymous) },
                    isEnable = helpBuildLyricsDatabase,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.contributor_name) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.contributor_name) },
                                        value = "",
                                    ),
                                message = "",
                                confirm =
                                    runBlocking { getString(Res.string.set) } to { state ->
                                        viewModel.setContributorName(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.contributor_email),
                    subtitle = contributor.second.ifEmpty { stringResource(Res.string.anonymous) },
                    isEnable = helpBuildLyricsDatabase,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.contributor_email) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.contributor_email) },
                                        value = "",
                                        verifyCodeBlock = {
                                            if (it.isNotEmpty()) {
                                                (it.contains("@")) to runBlocking { getString(Res.string.invalid) }
                                            } else {
                                                true to ""
                                            }
                                        },
                                    ),
                                message = "",
                                confirm =
                                    runBlocking { getString(Res.string.set) } to { state ->
                                        viewModel.setContributorEmail(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
            }
        }
        item(key = "AI") {
            Column {
                Text(
                    text = stringResource(Res.string.ai),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.ai_provider),
                    subtitle =
                        when (aiProvider) {
                            DataStoreManager.AI_PROVIDER_OPENAI -> stringResource(Res.string.openai)
                            DataStoreManager.AI_PROVIDER_GEMINI -> stringResource(Res.string.gemini)
                            DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI -> stringResource(Res.string.openai_api_compatible)
                            else -> stringResource(Res.string.unknown)
                        },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.ai_provider) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listOf(
                                                (mainLyricsProvider == DataStoreManager.AI_PROVIDER_OPENAI) to
                                                    runBlocking { getString(Res.string.openai) },
                                                (mainLyricsProvider == DataStoreManager.AI_PROVIDER_GEMINI) to
                                                    runBlocking { getString(Res.string.gemini) },
                                                (mainLyricsProvider == DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI) to
                                                    runBlocking { getString(Res.string.openai_api_compatible) },
                                            ),
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setAIProvider(
                                            when (state.selectOne?.getSelected()) {
                                                runBlocking { getString(Res.string.openai) } -> DataStoreManager.AI_PROVIDER_OPENAI
                                                runBlocking { getString(Res.string.gemini) } -> DataStoreManager.AI_PROVIDER_GEMINI
                                                runBlocking {
                                                    getString(
                                                        Res.string.openai_api_compatible,
                                                    )
                                                },
                                                    -> DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI

                                                else -> DataStoreManager.AI_PROVIDER_OPENAI
                                            },
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.ai_api_key),
                    subtitle = if (isHasApiKey) "XXXXXXXXXX" else "N/A",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.ai_api_key) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.ai_api_key) },
                                        value = "",
                                        verifyCodeBlock = {
                                            (it.isNotEmpty()) to runBlocking { getString(Res.string.invalid_api_key) }
                                        },
                                    ),
                                message = "",
                                confirm =
                                    runBlocking { getString(Res.string.set) } to { state ->
                                        viewModel.setAIApiKey(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.custom_ai_model_id),
                    subtitle = customModelId.ifEmpty { stringResource(Res.string.default_models) },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.custom_ai_model_id) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.custom_ai_model_id) },
                                        value = "",
                                        verifyCodeBlock = {
                                            (it.isNotEmpty() && !it.contains(" ")) to runBlocking { getString(Res.string.invalid) }
                                        },
                                    ),
                                message = runBlocking { getString(Res.string.custom_model_id_messages) },
                                confirm =
                                    runBlocking { getString(Res.string.set) } to { state ->
                                        viewModel.setCustomModelId(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                if (aiProvider == DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI) {
                    SettingItem(
                        title = "Custom Base URL",
                        subtitle = customOpenAIBaseUrl.ifEmpty { "https://api.openai.com/v1/" },
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = "Custom Base URL",
                                    textField =
                                        SettingAlertState.TextFieldData(
                                            label = "Base URL",
                                            value = customOpenAIBaseUrl,
                                            verifyCodeBlock = {
                                                (it.isEmpty() || it.startsWith("http")) to "Invalid URL format"
                                            },
                                        ),
                                    message = "Enter OpenAI-compatible API base URL (e.g., https://api.openai.com/v1/)",
                                    confirm =
                                        runBlocking { getString(Res.string.set) } to { state ->
                                            viewModel.setCustomOpenAIBaseUrl(state.textField?.value ?: "")
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = "Custom Headers",
                        subtitle = if (customOpenAIHeaders.isNotEmpty()) "Configured" else "Not set",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = "Custom Headers (JSON)",
                                    textField =
                                        SettingAlertState.TextFieldData(
                                            label = "Headers JSON",
                                            value = customOpenAIHeaders,
                                            verifyCodeBlock = { input ->
                                                if (input.isEmpty()) {
                                                    true to null
                                                } else {
                                                    try {
                                                        val trimmed = input.trim()
                                                        (trimmed.startsWith("{") && trimmed.endsWith("}")) to "Invalid JSON format"
                                                    } catch (e: Exception) {
                                                        false to "Invalid JSON format"
                                                    }
                                                }
                                            },
                                        ),
                                    message = "Enter custom headers in JSON format:\n{\"key1\":\"value1\",\"key2\":\"value2\"}",
                                    confirm =
                                        runBlocking { getString(Res.string.set) } to { state ->
                                            viewModel.setCustomOpenAIHeaders(state.textField?.value ?: "")
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                }
                SettingItem(
                    title = stringResource(Res.string.use_ai_translation),
                    subtitle = stringResource(Res.string.use_ai_translation_description),
                    switch = (useAITranslation to { viewModel.setAITranslation(it) }),
                    isEnable = isHasApiKey,
                )
            }
        }
        item(key = "spotify") {
            Column {
                Text(
                    text = stringResource(Res.string.spotify),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title =
                        if (spotifyLoggedIn) {
                            stringResource(Res.string.log_out_from_spotify)
                        } else {
                            stringResource(Res.string.log_in_to_spotify)
                        },
                    subtitle =
                        if (spotifyLoggedIn) {
                            stringResource(Res.string.logged_in)
                        } else {
                            stringResource(Res.string.intro_login_to_spotify)
                        },
                    onClick = {
                        if (spotifyLoggedIn) {
                            viewModel.confirmLogOut(
                                confirmLabel = runBlocking { getString(Res.string.log_out_from_spotify) },
                            ) { viewModel.setSpotifyLogIn(false) }
                        } else {
                            navController.navigate(SpotifyLoginDestination)
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.enable_spotify_lyrics),
                    subtitle = stringResource(Res.string.spotify_lyrícs_info),
                    switch = (spotifyLyrics to { viewModel.setSpotifyLyrics(it) }),
                    isEnable = spotifyLoggedIn,
                )
                SettingItem(
                    title = stringResource(Res.string.enable_canvas),
                    subtitle = stringResource(Res.string.canvas_info),
                    switch = (spotifyCanvas to { viewModel.setSpotifyCanvas(it) }),
                    isEnable = spotifyLoggedIn,
                )
                SettingItem(
                    title = stringResource(Res.string.enable_animated_artwork),
                    subtitle = stringResource(Res.string.animated_artwork_info),
                    switch = (amAnimatedArtwork to { viewModel.setAMAnimatedArtwork(it) }),
                )
            }
        }
        item(key = "discord") {
            Column {
                Text(
                    text = stringResource(Res.string.discord_integration),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title =
                        if (discordLoggedIn) {
                            stringResource(Res.string.log_out_from_discord)
                        } else {
                            stringResource(Res.string.log_in_to_discord)
                        },
                    subtitle =
                        if (discordLoggedIn) {
                            stringResource(Res.string.logged_in)
                        } else {
                            stringResource(Res.string.intro_login_to_discord)
                        },
                    onClick = {
                        if (discordLoggedIn) {
                            viewModel.confirmLogOut(
                                confirmLabel = runBlocking { getString(Res.string.log_out_from_discord) },
                            ) { viewModel.logOutDiscord() }
                        } else {
                            navController.navigate(DiscordLoginDestination)
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.enable_rich_presence),
                    subtitle = stringResource(Res.string.rich_presence_info),
                    switch = (richPresenceEnabled to { viewModel.setDiscordRichPresenceEnabled(it) }),
                    isEnable = discordLoggedIn,
                )
            }
        }
        if (viewModel.lastfmAvailable) {
            item(key = "lastfm") {
                Column {
                    Text(
                        text = stringResource(Res.string.lastfm_integration),
                        style = typo().labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title =
                            if (lastfmLoggedIn) {
                                stringResource(Res.string.log_out_from_lastfm)
                            } else {
                                stringResource(Res.string.log_in_to_lastfm)
                            },
                        subtitle =
                            if (lastfmLoggedIn) {
                                stringResource(Res.string.logged_in_as, lastfmUsername)
                            } else {
                                stringResource(Res.string.intro_login_to_lastfm)
                            },
                        onClick = {
                            if (lastfmLoggedIn) {
                                viewModel.confirmLogOut(
                                    confirmLabel = runBlocking { getString(Res.string.log_out_from_lastfm) },
                                ) { viewModel.logOutLastfm() }
                            } else {
                                navController.navigate(LastfmLoginDestination)
                            }
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.enable_scrobbling),
                        subtitle = stringResource(Res.string.scrobbling_info),
                        switch = (lastfmScrobbleEnabled to { viewModel.setLastfmScrobbleEnabled(it) }),
                        isEnable = lastfmLoggedIn,
                    )
                }
            }
        }
        item(key = "sponsor_block") {
            Column {
                Text(
                    text = stringResource(Res.string.sponsorBlock),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.enable_sponsor_block),
                    subtitle = stringResource(Res.string.skip_sponsor_part_of_video),
                    switch = (enableSponsorBlock to { viewModel.setSponsorBlockEnabled(it) }),
                )
                val listName =
                    SponsorBlockType.toList().map { it.displayString() }
                SettingItem(
                    title = stringResource(Res.string.categories_sponsor_block),
                    subtitle = stringResource(Res.string.what_segments_will_be_skipped),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.categories_sponsor_block) },
                                multipleSelect =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listName
                                                .mapIndexed { index, item ->
                                                    (
                                                        skipSegments?.contains(
                                                            SponsorBlockType.toList().getOrNull(index)?.value,
                                                        ) == true
                                                        ) to item
                                                }.also {
                                                    Logger.w("SettingScreen", "SettingAlertState: $skipSegments")
                                                    Logger.w("SettingScreen", "SettingAlertState: $it")
                                                },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.save) } to { state ->
                                        viewModel.setSponsorBlockCategories(
                                            state.multipleSelect
                                                ?.getListSelected()
                                                ?.map { selected ->
                                                    listName.indexOf(selected)
                                                }?.mapNotNull { s ->
                                                    SponsorBlockType.toList().getOrNull(s).let {
                                                        it?.value
                                                    }
                                                }?.toCollection(ArrayList()) ?: arrayListOf(),
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                    isEnable = enableSponsorBlock,
                )
                val beforeUrl = stringResource(Res.string.sponsor_block_intro).substringBefore("https://sponsor.ajay.app/")
                val afterUrl = stringResource(Res.string.sponsor_block_intro).substringAfter("https://sponsor.ajay.app/")
                Text(
                    buildAnnotatedString {
                        append(beforeUrl)
                        withLink(
                            LinkAnnotation.Url(
                                "https://sponsor.ajay.app/",
                                TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
                            ),
                        ) {
                            append("https://sponsor.ajay.app/")
                        }
                        append(afterUrl)
                    },
                    style = typo().bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        if (getPlatform() == Platform.Android) {
            item(key = "storage") {
                Column {
                    Text(
                        text = stringResource(Res.string.storage),
                        style = typo().labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SettingItem(
                        title = stringResource(Res.string.player_cache),
                        subtitle = "${playerCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = runBlocking { getString(Res.string.clear_player_cache) },
                                    message = null,
                                    confirm =
                                        runBlocking { getString(Res.string.clear) } to {
                                            viewModel.clearPlayerCache()
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.downloaded_cache),
                        subtitle = "${downloadedCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = runBlocking { getString(Res.string.clear_downloaded_cache) },
                                    message = null,
                                    confirm =
                                        runBlocking { getString(Res.string.clear) } to {
                                            viewModel.clearDownloadedCache()
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.thumbnail_cache),
                        subtitle = "${thumbnailCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = runBlocking { getString(Res.string.clear_thumbnail_cache) },
                                    message = null,
                                    confirm =
                                        runBlocking { getString(Res.string.clear) } to {
                                            viewModel.clearThumbnailCache(platformContext)
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.spotify_canvas_cache),
                        subtitle = "${canvasCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = runBlocking { getString(Res.string.clear_canvas_cache) },
                                    message = null,
                                    confirm =
                                        runBlocking { getString(Res.string.clear) } to {
                                            viewModel.clearCanvasCache()
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.limit_player_cache),
                        subtitle = LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache).toString(),
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = runBlocking { getString(Res.string.limit_player_cache) },
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                LIMIT_CACHE_SIZE.items.map { item ->
                                                    (item == LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache)) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        runBlocking { getString(Res.string.change) } to { state ->
                                            viewModel.setPlayerCacheLimit(
                                                LIMIT_CACHE_SIZE.getDataFromItem(state.selectOne?.getSelected()),
                                            )
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    Box(
                        Modifier.padding(
                            horizontal = 24.dp,
                            vertical = 16.dp,
                        ),
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .onGloballyPositioned { layoutCoordinates ->
                                        with(localDensity) {
                                            width =
                                                layoutCoordinates.size.width
                                                    .toDp()
                                                    .value
                                                    .toInt()
                                        }
                                    },
                        ) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.otherApp * width).dp,
                                            ).background(
                                                md_theme_dark_primary,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.downloadCache * width).dp,
                                            ).background(
                                                Color(0xD540FF17),
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.playerCache * width).dp,
                                            ).background(
                                                Color(0xD5FFFF00),
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.canvasCache * width).dp,
                                            ).background(
                                                Color.Cyan,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.thumbCache * width).dp,
                                            ).background(
                                                Color.Magenta,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.appDatabase * width).dp,
                                            ).background(
                                                Color.White,
                                            ),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.freeSpace * width).dp,
                                            ).background(
                                                Color.DarkGray,
                                            ).fillMaxHeight(),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    md_theme_dark_primary,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.other_app), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Green,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.downloaded_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Yellow,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.player_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Cyan,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.spotify_canvas_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Magenta,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.thumbnail_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.White,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.database), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.LightGray,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.free_space), style = typo().bodySmall)
                    }
                }
            }
        }
        item(key = "backup") {
            Column {
                Text(
                    text = stringResource(Res.string.backup),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SettingItem(
                    title = stringResource(Res.string.backup_downloaded),
                    subtitle = stringResource(Res.string.backup_downloaded_description),
                    switch = (backupDownloaded to { viewModel.setBackupDownloaded(it) }),
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.auto_backup),
                        subtitle = stringResource(Res.string.auto_backup_description),
                        switch = (autoBackupEnabled to { viewModel.setAutoBackupEnabled(it) }),
                    )
                    AnimatedVisibility(visible = autoBackupEnabled) {
                        Column {
                            SettingItem(
                                title = stringResource(Res.string.backup_frequency),
                                subtitle =
                                    when (autoBackupFrequency) {
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY -> stringResource(Res.string.daily)
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY -> stringResource(Res.string.weekly)
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY -> stringResource(Res.string.monthly)
                                        else -> stringResource(Res.string.daily)
                                    },
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = runBlocking { getString(Res.string.backup_frequency) },
                                            selectOne =
                                                SettingAlertState.SelectData(
                                                    listSelect =
                                                        listOf(
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY) to
                                                                runBlocking { getString(Res.string.daily) },
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY) to
                                                                runBlocking { getString(Res.string.weekly) },
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY) to
                                                                runBlocking { getString(Res.string.monthly) },
                                                        ),
                                                ),
                                            confirm =
                                                runBlocking { getString(Res.string.change) } to { state ->
                                                    val frequency =
                                                        when (state.selectOne?.getSelected()) {
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.daily,
                                                                )
                                                            },
                                                                -> DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.weekly,
                                                                )
                                                            },
                                                                -> DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY
                                                            runBlocking {
                                                                getString(
                                                                    Res.string.monthly,
                                                                )
                                                            },
                                                                -> DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY
                                                            else -> DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY
                                                        }
                                                    viewModel.setAutoBackupFrequency(frequency)
                                                },
                                            dismiss = runBlocking { getString(Res.string.cancel) },
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.keep_backups),
                                subtitle = stringResource(Res.string.keep_backups_format, "$autoBackupMaxFiles"),
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = runBlocking { getString(Res.string.keep_backups) },
                                            selectOne =
                                                SettingAlertState.SelectData(
                                                    listSelect =
                                                        listOf(
                                                            (autoBackupMaxFiles == 3) to "3",
                                                            (autoBackupMaxFiles == 5) to "5",
                                                            (autoBackupMaxFiles == 10) to "10",
                                                            (autoBackupMaxFiles == 15) to "15",
                                                        ),
                                                ),
                                            confirm =
                                                runBlocking { getString(Res.string.change) } to { state ->
                                                    val maxFiles = state.selectOne?.getSelected()?.toIntOrNull() ?: 5
                                                    viewModel.setAutoBackupMaxFiles(maxFiles)
                                                },
                                            dismiss = runBlocking { getString(Res.string.cancel) },
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.last_backup),
                                subtitle =
                                    if (autoBackupLastTime == 0L) {
                                        stringResource(Res.string.never)
                                    } else {
                                        DateTimeFormatter
                                            .ofPattern("yyyy-MM-dd HH:mm:ss")
                                            .withZone(ZoneId.systemDefault())
                                            .format(Instant.ofEpochMilli(autoBackupLastTime))
                                    },
                            )
                        }
                    }
                }
                SettingItem(
                    title = stringResource(Res.string.backup),
                    subtitle = stringResource(Res.string.save_all_your_playlist_data),
                    onClick = {
                        coroutineScope.launch {
                            backupLauncher.launch()
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.restore_your_data),
                    subtitle = stringResource(Res.string.restore_your_saved_data),
                    onClick = {
                        coroutineScope.launch {
                            restoreLauncher.launch()
                        }
                    },
                )
            }
        }
        item(key = "end") {
            EndOfPage()
        }
    }

    val showLoadingDialog by viewModel.showLoadingDialog.collectAsStateWithLifecycle()
    if (showLoadingDialog.first) {
        LoadingDialog(
            true,
            showLoadingDialog.second,
        )
    }
    val basisAlertData by viewModel.basicAlertData.collectAsStateWithLifecycle()
    if (basisAlertData != null) {
        val alertBasicState = basisAlertData ?: return
        AlertDialog(
            onDismissRequest = { viewModel.setBasicAlertData(null) },
            title = {
                Text(
                    text = alertBasicState.title,
                    style = typo().titleSmall,
                )
            },
            text = {
                if (alertBasicState.message != null) {
                    Text(text = alertBasicState.message)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertBasicState.confirm.second.invoke()
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.confirm.first)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.dismiss)
                }
            },
        )
    }
    if (showColorPickerDialog) {
        val presetColors =
            listOf(
                "FF8ECAE6",
                "FF4C82EF",
                "FF9B72CF",
                "FFEF6C9B",
                "FFEF5350",
                "FFF4A340",
                "FFFFCA28",
                "FF66BB6A",
                "FF26A69A",
                "FFBDBDBD",
            )
        var pendingHex by rememberSaveable { mutableStateOf(customThemeColorHex.takeLast(6)) }
        val parsedColor = parseThemeColorHex(pendingHex)
        AlertDialog(
            onDismissRequest = { showColorPickerDialog = false },
            title = { Text(text = stringResource(Res.string.custom_color), style = typo().titleSmall) },
            text = {
                Column {
                    presetColors.chunked(5).forEach { rowColors ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            rowColors.forEach { hex ->
                                val color = parseThemeColorHex(hex) ?: Color.Gray
                                val isSelected = pendingHex.equals(hex.takeLast(6), ignoreCase = true)
                                Box(
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape,
                                            ).clickable { pendingHex = hex.takeLast(6) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = pendingHex,
                        onValueChange = { pendingHex = it.removePrefix("#").take(8).uppercase() },
                        label = { Text("HEX") },
                        prefix = { Text("#") },
                        singleLine = true,
                        isError = parsedColor == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parsedColor != null,
                    onClick = {
                        parsedColor?.let {
                            val argb = "FF${pendingHex.takeLast(6).uppercase()}"
                            sharedViewModel.setCustomThemeColor(argb)
                            sharedViewModel.setThemeColorSource(DataStoreManager.THEME_COLOR_CUSTOM)
                        }
                        showColorPickerDialog = false
                    },
                ) { Text(text = stringResource(Res.string.change)) }
            },
            dismissButton = {
                TextButton(onClick = { showColorPickerDialog = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
    if (showYouTubeAccountDialog) {
        BasicAlertDialog(
            onDismissRequest = { },
            modifier = Modifier.wrapContentSize(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                shadowElevation = 1.dp,
            ) {
                val googleAccounts by viewModel.googleAccounts.collectAsStateWithLifecycle(
                    minActiveState = Lifecycle.State.RESUMED,
                )
                LaunchedEffect(googleAccounts) {
                    Logger.w(
                        "SettingScreen",
                        "LaunchedEffect: ${
                            googleAccounts.data?.map {
                                it.name to it.isUsed
                            }
                        }",
                    )
                }
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                        ) {
                            IconButton(
                                onClick = { showYouTubeAccountDialog = false },
                                colors =
                                    IconButtonDefaults.iconButtonColors().copy(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .fillMaxHeight(),
                            ) {
                                Icon(SimpIcons.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                stringResource(Res.string.youtube_account),
                                style = typo().titleMedium,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .wrapContentHeight(align = Alignment.CenterVertically)
                                        .wrapContentWidth(),
                            )
                        }
                    }
                    if (googleAccounts is LocalResource.Success) {
                        val data = googleAccounts.data
                        if (data.isNullOrEmpty()) {
                            item {
                                Text(
                                    stringResource(Res.string.no_account),
                                    style = typo().bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                )
                            }
                        } else {
                            items(data) {
                                Row(
                                    modifier =
                                        Modifier
                                            .padding(vertical = 8.dp)
                                            .clickable {
                                                viewModel.setUsedAccount(it)
                                            },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Spacer(Modifier.width(24.dp))
                                    AsyncImage(
                                        model =
                                            ImageRequest
                                                .Builder(LocalPlatformContext.current)
                                                .data(it.thumbnailUrl)
                                                .crossfade(550)
                                                .build(),
                                        placeholder = rememberVectorPainter(SimpIcons.PeopleAlt),
                                        error = rememberVectorPainter(SimpIcons.PeopleAlt),
                                        contentDescription = it.name,
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(it.name, style = typo().labelMedium, color = MaterialTheme.colorScheme.onBackground)
                                        Text(it.email, style = typo().bodySmall)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    AnimatedVisibility(it.isUsed) {
                                        Text(
                                            stringResource(Res.string.signed_in),
                                            style = typo().bodySmall,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.widthIn(0.dp, 64.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(24.dp))
                                }
                            }
                        }
                    } else {
                        item {
                            CenterLoadingBox(
                                Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                            )
                        }
                    }
                    item {
                        Column {
                            ActionButton(
                                icon = SimpIcons.PeopleAlt,
                                text = Res.string.guest,
                            ) {
                                viewModel.setUsedAccount(null)
                                showYouTubeAccountDialog = false
                            }
                            ActionButton(
                                icon = SimpIcons.Close,
                                text = Res.string.log_out,
                            ) {
                                viewModel.setBasicAlertData(
                                    SettingBasicAlertState(
                                        title = runBlocking { getString(Res.string.warning) },
                                        message = runBlocking { getString(Res.string.log_out_warning) },
                                        confirm =
                                            runBlocking { getString(Res.string.log_out) } to {
                                                viewModel.logOutAllYouTube()
                                                showYouTubeAccountDialog = false
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            }
                            ActionButton(
                                icon = SimpIcons.PlaylistAdd,
                                text = Res.string.add_an_account,
                            ) {
                                showYouTubeAccountDialog = false
                                navController.navigate(LoginDestination)
                            }
                        }
                    }
                }
            }
        }
    }
    val alertData by viewModel.alertData.collectAsStateWithLifecycle()
    if (alertData != null) {
        val alertState = alertData ?: return
        AlertDialog(
            onDismissRequest = { viewModel.setAlertData(null) },
            title = {
                Text(
                    text = alertState.title,
                    style = typo().titleSmall,
                )
            },
            text = {
                if (alertState.message != null) {
                    Column {
                        Text(text = alertState.message)
                        if (alertState.textField != null) {
                            val verify =
                                alertState.textField.verifyCodeBlock?.invoke(
                                    alertState.textField.value,
                                ) ?: (true to null)
                            TextField(
                                value = alertState.textField.value,
                                onValueChange = {
                                    viewModel.setAlertData(
                                        alertState.copy(
                                            textField =
                                                alertState.textField.copy(
                                                    value = it,
                                                ),
                                        ),
                                    )
                                },
                                isError = !verify.first,
                                label = { Text(text = alertState.textField.label) },
                                supportingText = {
                                    if (!verify.first) {
                                        Text(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = verify.second ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (!verify.first) {
                                        SimpIcons.Error
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 6.dp,
                                        ),
                            )
                        }
                    }
                } else if (alertState.selectOne != null) {
                    LazyColumn(
                        Modifier
                            .padding(vertical = 6.dp)
                            .heightIn(0.dp, 500.dp),
                    ) {
                        items(alertState.selectOne.listSelect) { item ->
                            val onSelect = {
                                viewModel.setAlertData(
                                    alertState.copy(
                                        selectOne =
                                            alertState.selectOne.copy(
                                                listSelect =
                                                    alertState.selectOne.listSelect.toMutableList().map {
                                                        if (it == item) {
                                                            true to it.second
                                                        } else {
                                                            false to it.second
                                                        }
                                                    },
                                            ),
                                    ),
                                )
                            }
                            Row(
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onSelect.invoke()
                                    }.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = item.first,
                                    onClick = {
                                        onSelect.invoke()
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.second,
                                    style = typo().bodyMedium,
                                    maxLines = 1,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight(align = Alignment.CenterVertically)
                                            .basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                animationMode = MarqueeAnimationMode.Immediately,
                                            ).focusable(),
                                )
                            }
                        }
                    }
                } else if (alertState.multipleSelect != null) {
                    LazyColumn(
                        Modifier.padding(vertical = 6.dp),
                    ) {
                        items(alertState.multipleSelect.listSelect) { item ->
                            val onCheck = {
                                viewModel.setAlertData(
                                    alertState.copy(
                                        multipleSelect =
                                            alertState.multipleSelect.copy(
                                                listSelect =
                                                    alertState.multipleSelect.listSelect.toMutableList().map {
                                                        if (it == item) {
                                                            !it.first to it.second
                                                        } else {
                                                            it
                                                        }
                                                    },
                                            ),
                                    ),
                                )
                            }
                            Row(
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onCheck.invoke()
                                    }.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = item.first,
                                    onCheckedChange = {
                                        onCheck.invoke()
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = item.second, style = typo().bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertState.confirm.second.invoke(alertState)
                        viewModel.setAlertData(null)
                    },
                    enabled =
                        if (alertState.textField?.verifyCodeBlock != null) {
                            alertState.textField.verifyCodeBlock
                                .invoke(
                                    alertState.textField.value,
                                ).first
                        } else {
                            true
                        },
                ) {
                    Text(text = alertState.confirm.first)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setAlertData(null)
                    },
                ) {
                    Text(text = alertState.dismiss)
                }
            },
        )
    }

    val settingBarTint = MaterialTheme.colorScheme.background
    AnimatedContent(
        targetState = isAtTop,
        transitionSpec = {
            fadeIn(tween(300)).togetherWith(fadeOut(tween(300)))
        },
    ) { atTop ->
        TopAppBar(
            title = {
                Text(
                    text =
                        stringResource(
                            Res.string.settings,
                        ),
                    style = typo().titleMedium,
                )
            },
            navigationIcon = {
                Box(Modifier.padding(horizontal = 5.dp)) {
                    RippleIconButton(
                        SimpIcons.ArrowBackIosNew,
                        Modifier
                            .size(32.dp),
                        true,
                        tint = MaterialTheme.colorScheme.onSurface,
                    ) {
                        navController.navigateUp()
                    }
                }
            },
            modifier =
                Modifier
                    .then(
                        if (atTop) {
                            Modifier
                        } else {
                            Modifier.hazeEffect(hazeState) {
                                blurEnabled = true
                                blurRadius = 24.dp
                                backgroundColor = settingBarTint
                                tints = listOf(HazeTint(settingBarTint.copy(alpha = 0.3f)))
                            }
                        },
                    ),
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
        )
    }
}