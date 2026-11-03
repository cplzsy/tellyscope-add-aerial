// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessTest {
    private fun key(): String {
        val pub = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        return Base64.getEncoder().encodeToString(Connector.adbPublicKey(pub))
    }

    private val laptop = key()
    private val studio = key()
    private val phone = key()

    /** Android 11's `dumpsys adb`, trimmed to what [Access.keys] reads. */
    private fun dumpsys(userKeys: String?, keystore: String = "") = buildString {
        appendLine("ADB MANAGER STATE (dumpsys adb):")
        appendLine("{")
        appendLine("  debugging_manager={")
        appendLine("    connected_to_adb=true")
        appendLine("    last_key_received=$laptop")
        if (userKeys != null) appendLine("    user_keys=$userKeys")
        appendLine("    system_keys=")
        appendLine("    keystore=<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
        appendLine("<keyStore version=\"1\">")
        append(keystore)
        appendLine("</keyStore>")
        appendLine("  }")
        appendLine("}")
    }

    @Test fun readsTheAllowedListWithNamesAndLastSeen() {
        val out = dumpsys(
            userKeys = "$laptop alex@laptop\n$studio studio-pc\n$phone",
            keystore = "<adbKey key=\"$laptop alex@laptop\" lastConnection=\"1793000000000\" />\n" +
                "<adbKey key=\"$studio studio-pc\" lastConnection=\"0\" />\n",
        )
        val keys = Access.keys(out)!!
        assertEquals(listOf("alex@laptop", "studio-pc", null), keys.map { it.name })
        assertEquals(listOf(1793000000000L, null, null), keys.map { it.lastSeen })
        assertEquals(Access.fingerprint(laptop), keys[0].fingerprint)
    }

    @Test fun aKeyOnlyTheKeyStoreKnowsStillCounts() {
        val out = dumpsys(userKeys = laptop, keystore = "<adbKey key=\"$studio studio-pc &amp; co\" lastConnection=\"5\" />\n")
        assertEquals(listOf(null, "studio-pc & co"), Access.keys(out)!!.map { it.name })
    }

    @Test fun afterARevokeNobodyIsAllowed() {
        assertEquals(emptyList(), Access.keys(dumpsys(userKeys = null)))
        assertEquals(emptyList(), Access.keys(dumpsys(userKeys = "")))
    }

    @Test fun deniedOrOldTvsCantSay() {
        assertNull(Access.keys("Permission Denial: can't dump adb from pid=1234"))
        assertNull(Access.keys(""))
    }

    @Test fun fingerprintIsWhatThisPhoneShows() {
        val pub = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        val bytes = Connector.adbPublicKey(pub)
        val expected = MessageDigest.getInstance("MD5").digest(bytes).joinToString(":") { "%02X".format(it) }
        assertEquals(expected, Access.fingerprint(Base64.getEncoder().encodeToString(bytes)))
        assertNull(Access.fingerprint("not base64!"))
    }

    private val header = "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode"

    @Test fun ipv4MappedClientsComeBackAsIpv4() {
        // 192.0.2.47:51234 into :5555, established; one listening socket; one on another port.
        val tcp6 = """
            $header
               0: 00000000000000000000000000000000:15B3 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000  2000        0 1
               1: 0000000000000000FFFF00000A0200C0:15B3 0000000000000000FFFF00002F0200C0:C822 01 00000000:00000000 00:00000000 00000000  2000        0 2
               2: 0000000000000000FFFF00000A0200C0:15B3 0000000000000000FFFF00002F0200C0:C823 01 00000000:00000000 00:00000000 00000000  2000        0 3
               3: 0000000000000000FFFF00000A0200C0:1F90 0000000000000000FFFF0000330200C0:C824 01 00000000:00000000 00:00000000 00000000  1000        0 4
        """.trimIndent()
        assertEquals(
            listOf(AdbClient("192.0.2.47", 0xC822), AdbClient("192.0.2.47", 0xC823)),
            Access.clients(tcp6, setOf(5555)),
        )
    }

    @Test fun plainIpv4AndLoopback() {
        val tcp = """
            $header
               0: 0A0200C0:15B3 0A0200C0:D000 01 00000000:00000000 00:00000000 00000000  2000        0 1
               1: 0100007F:15B3 0100007F:D001 01 00000000:00000000 00:00000000 00000000  2000        0 2
               2: 0A0200C0:15B3 0B0200C0:D002 06 00000000:00000000 00:00000000 00000000  2000        0 3
        """.trimIndent()
        val clients = Access.clients(tcp, setOf(5555))!!
        assertEquals(listOf("192.0.2.10", "127.0.0.1"), clients.map { it.address })
        assertTrue(clients[1].loopback)
        assertFalse(clients[0].loopback)
    }

    @Test fun ipv6LoopbackAndNative() {
        assertEquals("::1", Access.address("00000000000000000000000001000000"))
        assertEquals("2001:db8:0:0:0:0:0:1", Access.address("B80D01200000000000000000" + "01000000"))
        assertNull(Access.address("XYZ"))
    }

    @Test fun noOneConnectedIsEmptyNoTableIsNull() {
        assertEquals(emptyList(), Access.clients(header, setOf(5555)))
        assertNull(Access.clients("cat: /proc/net/tcp6: Permission denied", setOf(5555)))
        assertNull(Access.clients("", setOf(5555)))
    }

    @Test fun clientsAlsoWatchTheWirelessDebuggingPort() = runTest {
        val tcp = "$header\n   0: 0A0200C0:9C40 2F0200C0:D000 01 00000000:00000000 00:00000000 00000000  2000        0 1"
        val sh = FakeShell(mapOf("getprop service.adb.tls.port" to ok("40000\n"), "cat /proc/net" to ok(tcp)))
        assertEquals(listOf(AdbClient("192.0.2.47", 0xD000)), Access(sh).clients(5555))
    }

    private fun tv(sdk: String, call: ShellResult) = FakeShell(mapOf("getprop ro.build.version.sdk" to ok("$sdk\n"), "service call adb 3" to call))

    @Test fun revokeIsTheBinderCallAndChecksItsReply() = runTest {
        assertTrue(Access(tv("30", ok("Result: Parcel(00000000    '....')"))).revokeAll())
        assertFalse(Access(tv("30", ok("Result: Parcel(fffffffc ffffffff '........')"))).revokeAll())
        assertFalse(Access(tv("34", ShellResult("service: not found", 127))).revokeAll())
    }

    @Test fun anUnlistedAndroidIsNeverCalled() = runTest {
        for (sdk in listOf("29", "37", "")) {
            val sh = tv(sdk, ok("Result: Parcel(00000000    '....')"))
            assertFalse(Access(sh).revokeAll())
            assertFalse(sh.ran.any { it.startsWith("service call") }, sdk)
        }
    }
}
