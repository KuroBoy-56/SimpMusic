package com.maxrave.data.di.loader

import com.maxrave.common.Config.SERVICE_SCOPE
import com.maxrave.data.mediaservice.ios.AvPlayerAdapter
import com.maxrave.domain.mediaservice.handler.DownloadHandler.Download
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.domain.repository.CacheRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.newSingleThreadContext
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private val iosPlayerModule =
    module {
        // One thread for every player operation, as on Desktop: the adapter's queue/state machine
        // assumes a single writer, and the handler's runBlocking calls must never land on Main.
        single<CoroutineScope>(qualifier = named(SERVICE_SCOPE)) {
            CoroutineScope(newSingleThreadContext("iOS-Player-Thread") + SupervisorJob())
        }
        single<AvPlayerAdapter> {
            AvPlayerAdapter(
                coroutineScope = get(named(SERVICE_SCOPE)),
                dataStoreManager = get(),
                streamRepository = get(),
            )
        }
        single<MediaPlayerInterface> { get<AvPlayerAdapter>() }
        single<CacheRepository> {
            object : CacheRepository {
                override suspend fun getCacheSize(cacheName: String): Long = 0L

                override fun clearCache(cacheName: String) {}

                override suspend fun getAllCacheKeys(cacheName: String): List<String> = emptyList()
            }
        }
        // Offline downloads need a muxing step (NewPipe + FFmpeg) that has no iOS build.
        single<DownloadHandler> {
            object : DownloadHandler {
                override suspend fun downloadTrack(
                    videoId: String,
                    title: String,
                    thumbnail: String,
                ) {}

                override fun removeDownload(videoId: String) {}

                override fun removeAllDownloads() {}

                override val downloads: StateFlow<Map<String, Pair<Download?, Download?>>> = MutableStateFlow(emptyMap())
                override val downloadTask: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())
            }
        }
    }

actual fun loadMediaService() {
    loadKoinModules(iosPlayerModule)
}

actual fun loadLoginSyncModule() {
}
