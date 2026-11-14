package com.guus.photocleaner.source.nas

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Keeps the NAS source on the user's own network (spec AC 6, ADR-0006): a server is only
 * contacted when every address its name resolves to is private, link-local, CGNAT (used by
 * Tailscale and some VPNs) or loopback. The caller connects to the returned address, so the
 * DNS answer cannot change between this check and the connection.
 */
object LocalAddressPolicy {

    /** Resolves [host] and returns a local address (IPv4 preferred); throws [NasException]. */
    fun resolveLocal(
        host: String,
        resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): InetAddress {
        val addresses = try {
            resolve(host)
        } catch (_: UnknownHostException) {
            throw NasException(NasError.UNREACHABLE)
        } catch (_: SecurityException) {
            throw NasException(NasError.UNREACHABLE)
        }
        if (addresses.isEmpty()) throw NasException(NasError.UNREACHABLE)
        if (!addresses.all(::isLocal)) throw NasException(NasError.NOT_LOCAL)
        return addresses.firstOrNull { it is Inet4Address } ?: addresses.first()
    }

    fun isLocal(address: InetAddress): Boolean {
        val b = address.address.map { it.toInt() and 0xFF }
        return when (address) {
            is Inet4Address -> isLocalIpv4(b)
            is Inet6Address -> isLocalIpv6(b)
            else -> false
        }
    }

    private fun isLocalIpv4(b: List<Int>): Boolean = when {
        b[0] == 10 -> true
        b[0] == 172 && b[1] in 16..31 -> true
        b[0] == 192 && b[1] == 168 -> true
        b[0] == 169 && b[1] == 254 -> true
        b[0] == 100 && b[1] in 64..127 -> true // CGNAT: Tailscale, carrier VPNs
        b[0] == 127 -> true
        else -> false
    }

    private fun isLocalIpv6(b: List<Int>): Boolean = when {
        b.subList(0, 15).all { it == 0 } && b[15] == 1 -> true // ::1
        b[0] and 0xFE == 0xFC -> true // fc00::/7 unique local
        b[0] == 0xFE && b[1] and 0xC0 == 0x80 -> true // fe80::/10 link-local
        // IPv4-mapped (::ffff:a.b.c.d): judge the embedded IPv4 address.
        b.subList(0, 10).all { it == 0 } && b[10] == 0xFF && b[11] == 0xFF -> isLocalIpv4(b.subList(12, 16))
        else -> false
    }
}
