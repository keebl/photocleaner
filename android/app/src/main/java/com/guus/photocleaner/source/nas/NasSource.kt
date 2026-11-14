package com.guus.photocleaner.source.nas

import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.CachedHasher
import com.guus.photocleaner.service.HashCache
import com.guus.photocleaner.source.Deletion
import com.guus.photocleaner.source.ImageHashing
import com.guus.photocleaner.source.OnThisDay
import com.guus.photocleaner.source.PhotoSource
import com.guus.photocleaner.source.SourceKind
import com.guus.photocleaner.source.greyGrid
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the image loader gets for a NAS item; resolved by [NasImageFetcher]. */
data class NasImage(val asset: PhotoAsset)

/**
 * [PhotoSource] over one folder on a NAS, via SMB (port of iOS `Sources/SMBSource.swift`;
 * spec `specs/android-nas.md`). The chosen folder and all its subfolders are scanned; the
 * listing, previews and hashes are cached on the device. Items are deleted permanently after
 * the in-app trash, because a NAS has no system trash (ADR-0003).
 */
class NasSource(
    context: Context,
    private val configStore: NasConfigStore = NasConfigStore(context),
    private val vault: PasswordVault = PasswordVault(context),
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : PhotoSource, NasFileReader {

    private val appContext = context.applicationContext
    private val listingCache = NasListingCache(File(context.cacheDir, "nas"))
    private val shareDir = File(context.cacheDir, SHARE_DIR)
    private val hashCache = HashCache(File(context.filesDir, "nasHashes.json"))
    val thumbnails = NasThumbnails(File(context.cacheDir, "nasThumbs"), this)

    private val _config = MutableStateFlow(configStore.load())

    /** The linked folder; null when no NAS is linked. */
    val config: StateFlow<NasConfig?> = _config.asStateFlow()

    private val _scanned = MutableStateFlow(0)

    /** Items found so far by a running scan (shown while scanning). */
    val scanned: StateFlow<Int> = _scanned.asStateFlow()

    private val lock = Mutex()
    private var server: NasServer? = null

    @Volatile
    private var cachedAll: List<PhotoAsset>? = null
    private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        // Temporary copies for sharing and playback live until the next start (spec AC 16).
        shareDir.deleteRecursively()
    }

    override val kind: SourceKind get() = SourceKind.NAS
    override val deletion: Deletion get() = Deletion.PERMANENT

    /** Any NAS id: entries of an earlier linked server are reconciled away as missing. */
    override fun owns(id: String): Boolean = NasPaths.isNasId(id)

    // MARK: linking

    /**
     * Checks that the folder can be read with these credentials, then stores the link (spec
     * AC 8). Throws [NasException] and stores nothing on failure (AC 4).
     */
    suspend fun link(config: NasConfig, password: String) {
        val candidate = NasServer(config.host, config.port, config.username, password)
        try {
            candidate.list(config.share, config.normalizedFolder)
        } catch (e: Exception) {
            candidate.close()
            throw NasErrors.wrap(e)
        }
        val old = _config.value
        lock.withLock {
            server?.close()
            server = candidate
        }
        old?.let(listingCache::clear)
        configStore.save(config)
        vault.save(password)
        listingCache.clear(config)
        cachedAll = null
        _config.value = config
        changeEvents.tryEmit(Unit)
    }

    /** Removes the link, password, listing and previews (spec AC 23). */
    suspend fun unlink() {
        lock.withLock {
            server?.close()
            server = null
        }
        _config.value?.let(listingCache::clear)
        configStore.clear()
        vault.clear()
        thumbnails.clear()
        shareDir.deleteRecursively()
        cachedAll = null
        _config.value = null
        changeEvents.tryEmit(Unit)
    }

    /** "NAS-cache legen": listing and previews, not the link (spec AC 22). */
    fun clearCaches() {
        invalidateCache()
        thumbnails.clear()
        changeEvents.tryEmit(Unit)
    }

    /** Forces a fresh scan on the next load ("Opnieuw scannen"). */
    fun rescan() {
        invalidateCache()
        changeEvents.tryEmit(Unit)
    }

    // MARK: PhotoSource

    override fun changes(): Flow<Unit> = changeEvents

    /**
     * All items, newest first: memory, then the disk listing, then a scan. Throws
     * [NasException] when the NAS cannot be reached and nothing is cached.
     */
    override suspend fun fetchAll(): List<PhotoAsset> {
        cachedAll?.let { return it }
        val config = _config.value ?: return emptyList()
        listingCache.load(config)?.let {
            cachedAll = it
            return it
        }
        val scanned = walk(config)
        if (_config.value == config) {
            cachedAll = scanned
            listingCache.save(config, scanned)
        }
        return scanned
    }

    /**
     * Breadth-first: every folder of one level is listed in parallel; [NasServer] caps how
     * many run at once. Unreadable subfolders are skipped; an unreadable root fails the scan.
     */
    private suspend fun walk(config: NasConfig): List<PhotoAsset> = coroutineScope {
        val server = server()
        val assets = ArrayList<PhotoAsset>()
        var level = listOf(config.normalizedFolder)
        var root = true
        _scanned.value = 0
        while (level.isNotEmpty()) {
            ensureActive()
            val results = level.map { dir ->
                async {
                    try {
                        dir to server.list(config.share, dir)
                    } catch (e: NasException) {
                        if (root) throw e
                        dir to emptyList()
                    }
                }
            }.awaitAll()
            root = false
            val next = ArrayList<String>()
            for ((dir, entries) in results) {
                for (entry in entries) {
                    val path = NasPaths.join(dir, entry.name)
                    if (entry.isDirectory) {
                        if (!NasPaths.isSkippableFolder(entry.name)) next += path
                    } else {
                        val kind = NasPaths.kind(entry.name) ?: continue
                        assets += nasAsset(
                            id = NasPaths.id(config, path),
                            video = kind == com.guus.photocleaner.model.MediaKind.VIDEO,
                            modified = entry.modifiedMillis,
                            size = entry.size,
                            name = entry.name,
                            folder = NasPaths.parentFolder(path),
                        )
                    }
                }
            }
            level = next
            _scanned.value = assets.size
        }
        assets.sortedByDescending { it.browseDateMillis ?: Long.MIN_VALUE }
    }

    /**
     * Items for stored ids (trash, keep). Unknown ids are left out only when the NAS says the
     * file is gone; when the NAS cannot be reached a minimal item is returned, so the
     * reconciler never drops entries because of a network problem (spec AC 21).
     */
    override suspend fun assets(ids: Collection<String>): List<PhotoAsset> {
        val config = _config.value ?: return emptyList()
        val known = cachedAll?.associateBy { it.id }.orEmpty()
        val result = ArrayList<PhotoAsset>()
        for (id in ids.distinct()) {
            known[id]?.let { result += it; continue }
            val path = NasPaths.path(config, id) ?: continue
            val kind = NasPaths.kind(path) ?: continue
            val video = kind == com.guus.photocleaner.model.MediaKind.VIDEO
            try {
                val entry = server().info(config.share, path) ?: continue
                result += nasAsset(id, video, entry.modifiedMillis, entry.size, entry.name, NasPaths.parentFolder(path))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                result += nasAsset(id, video, null, 0L, path.substringAfterLast('/'), NasPaths.parentFolder(path))
            }
        }
        return result
    }

    override suspend fun fetchOnThisDay(month: Int, day: Int): List<PhotoAsset> =
        OnThisDay.filter(fetchAll(), month, day, zone())

    override fun invalidateCache() {
        cachedAll = null
        _config.value?.let(listingCache::clear)
    }

    override fun flushCaches() = hashCache.flush()

    override fun imageModel(asset: PhotoAsset): Any = NasImage(asset)

    /** NAS items have no content URI until downloaded; see [localUri]. */
    override fun contentUri(asset: PhotoAsset): Uri? = null

    override suspend fun localUri(asset: PhotoAsset, onProgress: (Float) -> Unit): Uri? {
        val config = _config.value ?: return null
        val path = NasPaths.path(config, asset.id) ?: return null
        val ext = asset.filename?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val name = NasThumbnails.stableHash(asset.id + "|" + (asset.modifiedAtMillis ?: 0)) +
            if (ext.isEmpty()) "" else ".$ext"
        val file = File(shareDir, name)
        if (!file.exists()) {
            try {
                withContext(Dispatchers.IO) {
                    shareDir.mkdirs()
                    val tmp = File(shareDir, "$name.part")
                    tmp.outputStream().use { out ->
                        server().download(config.share, path, out) { done ->
                            if (asset.byteSize > 0) onProgress((done.toFloat() / asset.byteSize).coerceIn(0f, 1f))
                        }
                    }
                    tmp.renameTo(file)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
        }
        onProgress(1f)
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
    }

    private val hasher = CachedHasher(hashCache) { asset ->
        val file = thumbnails.preview(asset, HASH_SOURCE_PX) ?: return@CachedHasher null
        withContext(Dispatchers.Default) {
            BitmapFactory.decodeFile(file.path)?.let { ImageHashing.dHash(greyGrid(it)) }
        }
    }

    override suspend fun perceptualHash(asset: PhotoAsset): Long? =
        if (asset.isVideo) null else hasher.hash(asset)

    /** Not used: NAS items are deleted with [deletePermanently]. */
    override suspend fun createTrashRequest(assets: List<PhotoAsset>): PendingIntent? = null

    /**
     * Deletes the files on the NAS (spec AC 19). Files already gone count as deleted; the
     * first other failure stops and is thrown, so the rest stays in the in-app trash.
     */
    override suspend fun deletePermanently(assets: List<PhotoAsset>) {
        val config = _config.value ?: throw NasException(NasError.NOT_CONFIGURED)
        val server = server()
        try {
            for (asset in assets) {
                val path = NasPaths.path(config, asset.id) ?: continue
                try {
                    server.delete(config.share, path)
                } catch (e: NasException) {
                    if (e.error != NasError.SHARE_NOT_FOUND) throw e // not found = already gone
                }
            }
        } finally {
            invalidateCache()
            changeEvents.tryEmit(Unit)
        }
    }

    // MARK: NasFileReader

    override suspend fun readRange(asset: PhotoAsset, offset: Long, length: Int): ByteArray {
        val config = _config.value ?: throw NasException(NasError.NOT_CONFIGURED)
        val path = NasPaths.path(config, asset.id) ?: throw NasException(NasError.NOT_CONFIGURED)
        return server().read(config.share, path, offset, length)
    }

    override suspend fun readAll(asset: PhotoAsset): ByteArray {
        val config = _config.value ?: throw NasException(NasError.NOT_CONFIGURED)
        val path = NasPaths.path(config, asset.id) ?: throw NasException(NasError.NOT_CONFIGURED)
        val out = java.io.ByteArrayOutputStream(asset.byteSize.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        server().download(config.share, path, out) {}
        return out.toByteArray()
    }

    private suspend fun server(): NasServer = lock.withLock {
        server ?: run {
            val config = _config.value ?: throw NasException(NasError.NOT_CONFIGURED)
            NasServer(config.host, config.port, config.username, vault.load()).also { server = it }
        }
    }

    private companion object {
        const val SHARE_DIR = "nas-share"
        const val HASH_SOURCE_PX = 64
    }
}
