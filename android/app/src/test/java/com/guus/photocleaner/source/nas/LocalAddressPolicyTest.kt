package com.guus.photocleaner.source.nas

import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Spec AC 6: only servers on the user's own network are allowed. */
class LocalAddressPolicyTest {

    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    @Test
    fun privateIpv4Ranges_areLocal() {
        for (a in listOf(
            "10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.1", "192.168.1.10",
            "169.254.3.4", "100.64.0.1", "100.127.255.254", "127.0.0.1",
        )) {
            assertTrue(a, LocalAddressPolicy.isLocal(ip(a)))
        }
    }

    @Test
    fun publicIpv4_isNotLocal() {
        for (a in listOf(
            "8.8.8.8", "172.15.255.255", "172.32.0.1", "192.169.0.1", "100.63.255.255",
            "100.128.0.1", "1.1.1.1", "0.0.0.0", "224.0.0.1", "255.255.255.255",
        )) {
            assertFalse(a, LocalAddressPolicy.isLocal(ip(a)))
        }
    }

    @Test
    fun ipv6_uniqueLocalLinkLocalAndLoopback_areLocal_globalIsNot() {
        assertTrue(LocalAddressPolicy.isLocal(ip("fd12:3456::1")))
        assertTrue(LocalAddressPolicy.isLocal(ip("fc00::1")))
        assertTrue(LocalAddressPolicy.isLocal(ip("fe80::1")))
        assertTrue(LocalAddressPolicy.isLocal(ip("::1")))
        assertFalse(LocalAddressPolicy.isLocal(ip("2001:4860:4860::8888")))
        assertFalse(LocalAddressPolicy.isLocal(ip("::ffff:8.8.8.8")))
        assertTrue(LocalAddressPolicy.isLocal(ip("::ffff:192.168.1.2")))
    }

    @Test
    fun resolve_returnsLocalAddress_preferringIpv4() {
        val result = LocalAddressPolicy.resolveLocal("nas.local") {
            listOf(ip("fd00::5"), ip("192.168.1.5"))
        }
        assertEquals(ip("192.168.1.5"), result)
    }

    @Test
    fun resolve_refusesWhenAnyAddressIsPublic() {
        try {
            LocalAddressPolicy.resolveLocal("rebind.example") { listOf(ip("192.168.1.5"), ip("8.8.8.8")) }
            fail("expected NasException")
        } catch (e: NasException) {
            assertEquals(NasError.NOT_LOCAL, e.error)
        }
    }

    @Test
    fun resolve_unknownHost_isUnreachable() {
        try {
            LocalAddressPolicy.resolveLocal("nowhere") { throw UnknownHostException(it) }
            fail("expected NasException")
        } catch (e: NasException) {
            assertEquals(NasError.UNREACHABLE, e.error)
        }
    }
}
