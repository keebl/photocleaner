package com.guus.photocleaner.source.nas

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.common.SMBRuntimeException
import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Spec AC 4–5 and the edge cases: SMB failures map to one message each. */
class NasErrorsTest {

    private fun api(status: NtStatus) =
        SMBApiException(status.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null)

    @Test
    fun loginFailures_areAuthFailed() {
        assertEquals(NasError.AUTH_FAILED, NasErrors.classify(api(NtStatus.STATUS_LOGON_FAILURE)))
        assertEquals(NasError.AUTH_FAILED, NasErrors.classify(api(NtStatus.STATUS_ACCESS_DENIED)))
    }

    @Test
    fun missingShareOrFolder_isShareNotFound() {
        assertEquals(NasError.SHARE_NOT_FOUND, NasErrors.classify(api(NtStatus.STATUS_BAD_NETWORK_NAME)))
        assertEquals(NasError.SHARE_NOT_FOUND, NasErrors.classify(api(NtStatus.STATUS_OBJECT_PATH_NOT_FOUND)))
    }

    @Test
    fun networkFailures_areUnreachable_alsoWhenWrapped() {
        assertEquals(NasError.UNREACHABLE, NasErrors.classify(ConnectException("refused")))
        assertEquals(
            NasError.UNREACHABLE,
            NasErrors.classify(SMBRuntimeException(RuntimeException(SocketTimeoutException()))),
        )
    }

    @Test
    fun smb1_andUnknown() {
        assertEquals(NasError.SMB1_ONLY, NasErrors.classify(SMBRuntimeException("Server only supports SMBv1")))
        assertEquals(NasError.OTHER, NasErrors.classify(IllegalStateException("?")))
    }

    @Test
    fun wrap_keepsExistingNasException() {
        val e = NasException(NasError.NOT_LOCAL)
        assertSame(e, NasErrors.wrap(e))
        assertEquals(NasError.UNREACHABLE, NasErrors.wrap(ConnectException()).error)
    }
}
