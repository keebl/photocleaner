package com.guus.photocleaner.source.nas

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileAllInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import java.io.Closeable
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What the connect flow needs to browse a server (fakeable in UI tests). */
interface NasBrowser {
    /** Disk shares; also validates the login (spec AC 2–4). */
    suspend fun shareNames(): List<String>

    /** Visible subfolders of [path] in [share]. */
    suspend fun folders(share: String, path: String): List<String>

    fun close()
}

/** One directory entry on the NAS. */
data class NasEntry(val name: String, val isDirectory: Boolean, val size: Long, val modifiedMillis: Long?)

/**
 * One logged-in SMB session to a NAS (smbj), shared by the connect flow and the source.
 * Connects lazily to the address [LocalAddressPolicy] approved, reconnects once after a
 * dropped connection, and runs at most [MAX_PARALLEL] operations at a time so a big scan
 * does not overload the NAS (iOS uses a pool of 6 connections; smbj multiplexes one).
 */
class NasServer(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
) : NasBrowser, Closeable {

    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .withSoTimeout(TIMEOUT_SECONDS * 1000)
            .withSigningEnabled(true)
            .build(),
    )
    private val lock = Mutex()
    private val permits = Semaphore(MAX_PARALLEL)
    private var connection: Connection? = null
    private var session: Session? = null
    private val shares = HashMap<String, DiskShare>()

    /** Disk shares on the server, without admin (`$`) shares (spec AC 2). */
    override suspend fun shareNames(): List<String> = smb(share = null) { session, _ ->
        ServerService(SMBTransportFactories.SRVSVC.getTransport(session)).shares1
            .filter { it.type and STYPE_MASK == STYPE_DISKTREE && !it.netName.endsWith("$") }
            .map { it.netName }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /** Visible subfolders of [path] in [share] (spec AC 7). */
    override suspend fun folders(share: String, path: String): List<String> =
        list(share, path).filter { it.isDirectory && !NasPaths.isSkippableFolder(it.name) }
            .map { it.name }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Entries of one folder (not recursive), without `.` and `..`. */
    suspend fun list(share: String, path: String): List<NasEntry> = smb(share) { _, disk ->
        disk!!.list(NasPaths.smbPath(path)).filter { it.fileName != "." && it.fileName != ".." }.map {
            NasEntry(
                name = it.fileName,
                isDirectory = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L,
                size = it.endOfFile,
                modifiedMillis = it.lastWriteTime?.toEpochMillis()?.takeIf { ms -> ms > 0 },
            )
        }
    }

    /** Size and modification time of one file; null when it does not exist. */
    suspend fun info(share: String, path: String): NasEntry? = smb(share) { _, disk ->
        val smbPath = NasPaths.smbPath(path)
        if (!disk!!.fileExists(smbPath)) return@smb null
        val info: FileAllInformation = disk.getFileInformation(smbPath)
        NasEntry(
            name = path.substringAfterLast('/'),
            isDirectory = info.standardInformation.isDirectory,
            size = info.standardInformation.endOfFile,
            modifiedMillis = info.basicInformation.lastWriteTime?.toEpochMillis()?.takeIf { it > 0 },
        )
    }

    /** Up to [length] bytes from [offset]; fewer at the end of the file. */
    suspend fun read(share: String, path: String, offset: Long, length: Int): ByteArray = smb(share) { _, disk ->
        openRead(disk!!, path).use { file ->
            val buffer = ByteArray(length)
            var filled = 0
            while (filled < length) {
                val n = file.read(buffer, offset + filled, filled, length - filled)
                if (n <= 0) break
                filled += n
            }
            if (filled == length) buffer else buffer.copyOf(filled)
        }
    }

    /** Streams the whole file to [out]; [onProgress] gets the bytes written so far. */
    suspend fun download(share: String, path: String, out: OutputStream, onProgress: (Long) -> Unit) =
        smb(share) { _, disk ->
            openRead(disk!!, path).use { file -> file.read(out) { done, _ -> onProgress(done) } }
        }

    suspend fun delete(share: String, path: String) = smb(share) { _, disk -> disk!!.rm(NasPaths.smbPath(path)) }

    override fun close() {
        synchronized(shares) { shares.clear() }
        try {
            connection?.close()
        } catch (_: Exception) {
            // already closed
        }
        connection = null
        session = null
        client.close()
    }

    private fun openRead(disk: DiskShare, path: String) = disk.openFile(
        NasPaths.smbPath(path),
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        null,
    )

    /**
     * Runs [block] on the IO dispatcher with a live session (and [share], if given). A dropped
     * connection is rebuilt once; every failure surfaces as a [NasException].
     */
    private suspend fun <T> smb(share: String?, block: (Session, DiskShare?) -> T): T = permits.withPermit {
        withContext(Dispatchers.IO) {
            try {
                attempt(share, block)
            } catch (e: NasException) {
                throw e
            } catch (first: Exception) {
                if (connection?.isConnected == true) throw NasErrors.wrap(first)
                reset()
                try {
                    attempt(share, block)
                } catch (second: Exception) {
                    throw NasErrors.wrap(second)
                }
            }
        }
    }

    private suspend fun <T> attempt(share: String?, block: (Session, DiskShare?) -> T): T {
        val session = session()
        val disk = share?.let { name ->
            synchronized(shares) { shares[name] } ?: lock.withLock {
                synchronized(shares) { shares[name] } ?: (session.connectShare(name) as? DiskShare
                    ?: throw NasException(NasError.SHARE_NOT_FOUND))
                    .also { synchronized(shares) { shares[name] = it } }
            }
        }
        return block(session, disk)
    }

    private suspend fun session(): Session = lock.withLock {
        session?.takeIf { connection?.isConnected == true }?.let { return it }
        val address = LocalAddressPolicy.resolveLocal(host)
        val conn = client.connect(address.hostAddress, port)
        val auth = if (username.isEmpty()) {
            AuthenticationContext.guest()
        } else {
            val (domain, user) = splitDomain(username)
            AuthenticationContext(user, password.toCharArray(), domain)
        }
        val s = try {
            conn.authenticate(auth)
        } catch (e: Exception) {
            conn.close()
            throw NasException(NasErrors.classify(e).takeIf { it != NasError.OTHER } ?: NasError.AUTH_FAILED, e)
        }
        connection = conn
        session = s
        s
    }

    private suspend fun reset() = lock.withLock {
        synchronized(shares) { shares.clear() }
        try {
            connection?.close(true)
        } catch (_: Exception) {
            // already gone
        }
        connection = null
        session = null
    }

    private fun splitDomain(name: String): Pair<String?, String> =
        if ('\\' in name) name.substringBefore('\\') to name.substringAfter('\\') else null to name

    private companion object {
        const val MAX_PARALLEL = 6
        const val TIMEOUT_SECONDS = 15
        const val STYPE_DISKTREE = 0
        const val STYPE_MASK = 0x0FFFFFFF
    }
}
