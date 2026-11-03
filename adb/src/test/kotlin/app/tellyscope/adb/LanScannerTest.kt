// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanScannerTest {
    @Test fun findsOnlyOpenPorts() = runBlocking {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            val hits = LanScanner(port = server.localPort).scan(listOf("127.0.0.1", "127.0.0.2")).toList()
            assertEquals(listOf("127.0.0.1"), hits.map { it.host })
            assertEquals(ScanHit.Kind.CLASSIC, hits.single().kind)
        }
    }

    private fun v4(ip: String) = InetAddress.getByName(ip) as Inet4Address

    @Test fun sweepsOnlyWifiAndEthernet() {
        val hosts = LanScanner.lanHosts(listOf(
            Triple("rmnet_data0", v4("100.64.12.7"), 24), // cellular, carrier CGNAT
            Triple("tun0", v4("172.20.0.2"), 24), // VPN
            Triple("wlan0", v4("192.168.1.20"), 24),
        ))
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("192.168.1.") } && "192.168.1.20" !in hosts)
    }

    @Test fun cellularOrVpnAloneSweepsNothing() {
        assertEquals(emptyList(), LanScanner.lanHosts(listOf(
            Triple("rmnet_data0", v4("100.64.12.7"), 24),
            Triple("ccmni0", v4("100.70.1.2"), 22),
            Triple("tun0", v4("172.20.0.2"), 24),
            Triple("wg0", v4("172.21.0.2"), 24),
        )))
    }

    @Test fun subnetCapsAtTheSurroundingSlash24() {
        assertEquals(253, LanScanner.subnetHosts(v4("172.16.5.9"), 16).size)
        assertEquals(listOf("192.168.1.1", "192.168.1.3", "192.168.1.4", "192.168.1.5", "192.168.1.6"), LanScanner.subnetHosts(v4("192.168.1.2"), 29))
    }
}
