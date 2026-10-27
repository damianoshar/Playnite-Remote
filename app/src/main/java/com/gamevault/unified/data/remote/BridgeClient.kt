package com.gamevault.unified.data.remote

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.gamevault.unified.data.model.ServerConfig
import kotlinx.coroutines.CancellationException
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Builds authenticated HTTP clients for playnite-bridge.
 * EVERY request (JSON and cover images) needs `Authorization: Bearer <token>`.
 * Covers get a 250 MB disk cache so the library still shows art offline.
 */
object BridgeClient {

    // One client per server: a single Dispatcher caps TOTAL parallel
    // requests (Retrofit + Coil + prefetch share it). The bridge runs on
    // HttpListener inside the Playnite process and falls over when
    // hundreds of cover requests hit it at once (fast scroll / prefetch).
    private val clientCache = mutableMapOf<String, OkHttpClient>()
    private val loaderCache = mutableMapOf<String, ImageLoader>()

    @Synchronized
    fun okHttp(config: ServerConfig): OkHttpClient {
        if (clientCache.size > 6) clientCache.clear()
        val key = "api|" + config.trimmedBase() + "|" + config.token.trim().hashCode()
        return clientCache.getOrPut(key) {
            buildBase(config).dispatcher(
                Dispatcher().apply {
                    maxRequests = 10
                    maxRequestsPerHost = 4
                }
            ).build()
        }
    }

    @Synchronized
    private fun imageHttp(config: ServerConfig): OkHttpClient {
        // Separate slow lane for covers: max 2 at a time so image traffic
        // can never crowd out API calls or flood the PC-side plugin.
        val key = "img|" + config.trimmedBase() + "|" + config.token.trim().hashCode()
        return clientCache.getOrPut(key) {
            buildBase(config).dispatcher(
                Dispatcher().apply {
                    maxRequests = 4
                    maxRequestsPerHost = 2
                }
            ).build()
        }
    }

    private fun buildBase(config: ServerConfig): OkHttpClient.Builder {
        val auth = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", "Bearer ${config.token.trim()}")
                    .build()
            )
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            // No silent retries: a failed request surfaces immediately instead
            // of hitting the PC twice. The user can retry explicitly.
            .retryOnConnectionFailure(false)
    }

    fun api(config: ServerConfig): BridgeApi =
        Retrofit.Builder()
            .baseUrl(config.retrofitBase())
            .client(okHttp(config))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BridgeApi::class.java)

    fun imageLoader(context: Context, config: ServerConfig): ImageLoader {
        // One loader per server, shared by UI + prefetch: two loaders on
        // the same disk-cache folder corrupt each other's journal.
        if (loaderCache.size > 3) loaderCache.clear()
        val key = config.trimmedBase() + "|" + config.token.trim().hashCode()
        return loaderCache.getOrPut(key) { buildLoader(context.applicationContext, config) }
    }

    private fun buildLoader(context: Context, config: ServerConfig): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient { imageHttp(config) }
            .memoryCache(
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("cover_cache"))
                    .maxSizeBytes(1024L * 1024 * 1024)
                    .build()
            }
            // No fade animation: crossfades re-compose every row during fling.
            .crossfade(false)
            .build()

    /** Clears RAM caches of all cached loaders (disk cache is plain files). */
    @Synchronized
    fun clearMemoryCaches() {
        loaderCache.values.forEach { it.memoryCache?.clear() }
    }
    suspend fun downloadCovers(
        context: Context,
        config: ServerConfig,
        urls: List<String>,
        onProgress: suspend (done: Int, total: Int) -> Unit
    ): Int {
        val loader = imageLoader(context, config)
        val distinct = urls.filter { it.isNotBlank() }.distinct()
        var failed = 0
        distinct.forEachIndexed { index, url ->
            try {
                loader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
            if ((index + 1) % 10 == 0 || index + 1 == distinct.size) {
                onProgress(index + 1, distinct.size)
            }
        }
        return failed
    }

    /**
     * Fire-and-forget cover download into the disk cache (used by full
     * offline mode). Memory cache is disabled so prefetching thousands of
     * covers cannot exhaust RAM.
     */
    fun prefetchCovers(context: Context, config: ServerConfig, urls: List<String>) {
        val loader = imageLoader(context, config)
        urls.distinct().forEach { url ->
            if (url.isBlank()) return@forEach
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }
}
