package com.guus.photocleaner.source.nas

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/** What went wrong talking to the NAS, mapped to one user-facing message each (spec AC 4–6). */
enum class NasError {
    NOT_LOCAL,
    BAD_SERVER,
    AUTH_FAILED,
    UNREACHABLE,
    SHARE_NOT_FOUND,
    SMB1_ONLY,
    NOT_CONFIGURED,
    OTHER,
}

class NasException(val error: NasError, cause: Throwable? = null) : IOException(error.name, cause)

object NasErrors {

    /** Classifies any exception from the SMB stack; walks the cause chain. */
    fun classify(t: Throwable): NasError {
        var current: Throwable? = t
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current)) {
            classifyOne(current)?.let { return it }
            current = current.cause
        }
        return NasError.OTHER
    }

    /** Wraps [t] in a [NasException] unless it already is one. */
    fun wrap(t: Throwable): NasException = t as? NasException ?: NasException(classify(t), t)

    private fun classifyOne(t: Throwable): NasError? = when (t) {
        is NasException -> t.error
        is SMBApiException -> when (t.status) {
            NtStatus.STATUS_LOGON_FAILURE,
            NtStatus.STATUS_ACCESS_DENIED,
            NtStatus.STATUS_ACCOUNT_DISABLED,
            NtStatus.STATUS_PASSWORD_EXPIRED,
            NtStatus.STATUS_USER_SESSION_DELETED,
            -> NasError.AUTH_FAILED
            NtStatus.STATUS_BAD_NETWORK_NAME,
            NtStatus.STATUS_OBJECT_NAME_NOT_FOUND,
            NtStatus.STATUS_OBJECT_PATH_NOT_FOUND,
            NtStatus.STATUS_NOT_FOUND,
            -> NasError.SHARE_NOT_FOUND
            else -> null
        }
        is UnknownHostException, is ConnectException, is NoRouteToHostException,
        is SocketTimeoutException, is TimeoutException,
        -> NasError.UNREACHABLE
        else -> {
            val message = t.message.orEmpty()
            when {
                // smbj only speaks SMB 2/3; an SMB1-only server fails the dialect negotiation.
                message.contains("SMBv1", ignoreCase = true) ||
                    message.contains("SMB1", ignoreCase = true) -> NasError.SMB1_ONLY
                message.contains("Authentication failed", ignoreCase = true) -> NasError.AUTH_FAILED
                else -> null
            }
        }
    }
}
