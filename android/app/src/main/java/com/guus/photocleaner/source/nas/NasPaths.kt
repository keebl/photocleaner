package com.guus.photocleaner.source.nas

import com.guus.photocleaner.model.MediaKind
import kotlinx.serialization.Serializable

/**
 * One linked NAS folder (port of iOS `Models/SMBCredentials.swift`, plus a port). The
 * password is not part of it: it lives in [PasswordVault].
 */
@Serializable
data class NasConfig(
    /** IP address or hostname, e.g. "192.168.1.10" or "nas.local". */
    val host: String,
    val port: Int = DEFAULT_PORT,
    /** Share name, e.g. "photo". */
    val share: String,
    /** Folder inside the share, "" = the whole share; "/" separated. */
    val folder: String = "",
    /** Empty = guest. */
    val username: String = "",
) {
    val normalizedFolder: String get() = NasPaths.normalize(folder)

    /** Short description for the UI: "photo op 192.168.1.10/2022". */
    val displayName: String
        get() {
            val server = if (port == DEFAULT_PORT) host else "$host:$port"
            val base = "$share op $server"
            return if (normalizedFolder.isEmpty()) base else "$base/$normalizedFolder"
        }

    /**
     * Prefix of every asset id from this NAS. Ids include server and share, so they never
     * collide with MediaStore's numeric ids or with a previously linked server.
     */
    val idPrefix: String get() = "${NasPaths.SCHEME}$host:$port/$share/"

    companion object {
        const val DEFAULT_PORT = 445
    }
}

/** Pure helpers for NAS paths, ids and file types (port of the helpers in `SMBSource.swift`). */
object NasPaths {
    const val SCHEME = "nas://"

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "heic", "heif", "tiff", "tif", "gif", "bmp", "webp", "avif",
        // RAW formats of common cameras
        "dng", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "pef", "raw",
    )
    private val videoExtensions = setOf("mp4", "mov", "m4v", "avi", "hevc", "3gp", "mkv", "m2ts", "mts")

    /** Hidden folders and NAS system folders (Synology thumbnails, recycle bin, snapshots). */
    private val skippedFolders = setOf("@eaDir", "#recycle", "#snapshot", "@tmp", "@sharesnap", "lost+found")

    fun isNasId(id: String): Boolean = id.startsWith(SCHEME)

    fun isSkippableFolder(name: String): Boolean = name.startsWith(".") || name in skippedFolders

    fun kind(filename: String): MediaKind? {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in imageExtensions -> MediaKind.PHOTO
            in videoExtensions -> MediaKind.VIDEO
            else -> null
        }
    }

    /** Trims slashes and spaces at both ends; backslashes become slashes. */
    fun normalize(path: String): String = path.replace('\\', '/').trim('/', ' ')

    fun join(dir: String, name: String): String = if (dir.isEmpty()) name else "$dir/$name"

    fun id(config: NasConfig, path: String): String = config.idPrefix + normalize(path)

    /** The path inside the share for an id of [config]; null for ids of other sources. */
    fun path(config: NasConfig, id: String): String? =
        if (id.startsWith(config.idPrefix)) id.removePrefix(config.idPrefix) else null

    /** Folder that holds the file, relative to the share; null at the share root. */
    fun parentFolder(path: String): String? =
        normalize(path).substringBeforeLast('/', "").ifEmpty { null }

    /** smbj wants backslash-separated paths. */
    fun smbPath(path: String): String = normalize(path).replace('/', '\\')

    /**
     * Parses the server field: "host", "host:port", "[v6]" or "[v6]:port". Null for an empty
     * field, an invalid port or characters that cannot be part of a hostname or address.
     */
    fun parseServer(input: String): Pair<String, Int>? {
        val text = input.trim().removePrefix("smb://").trimEnd('/')
        if (text.isEmpty()) return null
        val host: String
        val portText: String?
        if (text.startsWith("[")) {
            val end = text.indexOf(']')
            if (end < 0) return null
            host = text.substring(1, end)
            val rest = text.substring(end + 1)
            portText = when {
                rest.isEmpty() -> null
                rest.startsWith(":") -> rest.substring(1)
                else -> return null
            }
        } else if (text.count { it == ':' } == 1) {
            host = text.substringBefore(':')
            portText = text.substringAfter(':')
        } else if (text.count { it == ':' } > 1) {
            host = text // bare IPv6
            portText = null
        } else {
            host = text
            portText = null
        }
        if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it in ".-_:%" }) return null
        val port = if (portText == null) NasConfig.DEFAULT_PORT else portText.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return host to port
    }
}
