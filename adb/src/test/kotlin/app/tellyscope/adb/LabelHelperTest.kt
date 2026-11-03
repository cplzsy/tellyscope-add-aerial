// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelHelperTest {
    private val dex = byteArrayOf(1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    private val b64 = Base64.getEncoder().encodeToString(png)

    @Test fun parsesHelperLines() {
        val out = """
            {"p":"com.netflix.ninja","l":"Netflix","v":62174,"i":"$b64"}
            {"p":"com.example.cafe","l":"Café \"TV\" \\ guide","v":3}
        """.trimIndent()
        val apps = LabelHelper.parse(out)
        assertEquals(listOf("com.netflix.ninja", "com.example.cafe"), apps.map { it.packageName })
        assertEquals("Netflix", apps[0].label)
        assertEquals(62174L, apps[0].versionCode)
        assertContentEquals(png, apps[0].iconPng)
        assertEquals("Café \"TV\" \\ guide", apps[1].label)
        assertNull(apps[1].iconPng)
    }

    @Test fun aBannerAppSendsTileColoursInsteadOfAnIcon() {
        val out = """
            {"p":"com.spotify.tv.android","l":"Spotify","v":1,"b":4279308561,"a":4280211296}
            {"p":"com.example.half","l":"Half","v":1,"b":4279308561}
        """.trimIndent()
        val (spotify, half) = LabelHelper.parse(out)
        assertNull(spotify.iconPng)
        assertEquals(0xFF111111, spotify.tileColor)
        assertEquals(0xFF1ED760, spotify.tileAccent)
        // A colour without its letter colour isn't a tile.
        assertNull(half.tileColor)
        assertNull(half.tileAccent)
    }

        @Test fun skipsLinesThatDontParse() {
        val out = """
            Exception in thread "main" java.lang.SecurityException
            {"p":"com.a","l":"A","v":1}
            {"p":"com.b","l":"","v":1}
            {"p":"com.c","l":"C"}
            {"p":"com.d","l":"D","v":1,"i":"not base64!"}
            {"p":"com.e","l":"E","v":1
        """.trimIndent()
        val apps = LabelHelper.parse(out)
        assertEquals(listOf("com.a", "com.d"), apps.map { it.packageName })
        assertNull(apps[1].iconPng)
    }

    @Test fun aHalfPrintedLineCostsTheNextAppToo() {
        // What the helper printed when loadIcon threw after the label: no closing brace, no newline,
        // so the next app's object landed on the same line. The helper now prints whole lines only.
        val out = """
            {"p":"com.apple.atve.androidtv.appletv","l":"Apple TV","v":7{"p":"org.jellyfin.androidtv","l":"Jellyfin","v":2,"i":"$b64"}
            {"p":"com.netflix.ninja","l":"Netflix","v":1,"i":"$b64"}
        """.trimIndent()
        assertEquals(listOf("com.netflix.ninja"), LabelHelper.parse(out).map { it.packageName })
    }

    @Test fun pushesRunsAndDeletes() = runTest {
        val sh = FakeShell(mapOf("CLASSPATH=" to ok("""{"p":"com.a","l":"A","v":7}""" + "\n")))
        val apps = LabelHelper(sh, dex).describe(listOf("com.a", "com.b"))
        assertEquals(listOf("com.a"), apps?.map { it.packageName })
        assertContentEquals(dex, sh.pushed[LabelHelper.PATH])
        val run = sh.ran.single { it.startsWith("CLASSPATH=") }
        assertTrue(run.contains("app_process / ${LabelHelper.MAIN} com.a com.b "))
        assertTrue(run.endsWith("rm -f ${LabelHelper.PATH}"))
        assertEquals("rm -f ${LabelHelper.PATH}", sh.ran.last())
    }

    @Test fun manyAppsAskForAll() = runTest {
        val sh = FakeShell(mapOf("CLASSPATH=" to ok("""{"p":"com.a","l":"A","v":7}""")))
        LabelHelper(sh, dex).describe((1..41).map { "com.app$it" })
        assertTrue(sh.ran.single { it.startsWith("CLASSPATH=") }.contains("${LabelHelper.MAIN} --all 2>"))
    }

    @Test fun refusedPushIsNullAndStillCleansUp() = runTest {
        val sh = FakeShell().apply { failPush = IOException("read-only file system") }
        assertNull(LabelHelper(sh, dex).describe(listOf("com.a")))
        assertEquals(listOf("rm -f ${LabelHelper.PATH}"), sh.ran)
    }

    @Test fun noOutputIsNull() = runTest {
        // An OEM that blocks app_process for the shell user: the run "succeeds" and prints nothing.
        val sh = FakeShell(mapOf("CLASSPATH=" to ShellResult("", 1)))
        assertNull(LabelHelper(sh, dex).describe(listOf("com.a")))
        assertEquals("rm -f ${LabelHelper.PATH}", sh.ran.last())
    }

    @Test fun hungHelperTimesOutToNull() = runTest {
        val sh = object : AdbSession by FakeShell() {
            val ran = mutableListOf<String>()
            override suspend fun shell(command: String): ShellResult {
                ran += command
                if (command.startsWith("CLASSPATH=")) awaitCancellation()
                return ok("")
            }
        }
        assertNull(LabelHelper(sh, dex).describe(listOf("com.a")))
        assertEquals("rm -f ${LabelHelper.PATH}", sh.ran.last())
    }

    @Test fun packageNamesNeverReachTheShellUnchecked() = runTest {
        val sh = FakeShell(mapOf("CLASSPATH=" to ok("""{"p":"com.a","l":"A","v":7}""")))
        LabelHelper(sh, dex).describe(listOf("com.a", "com.b; reboot"))
        assertTrue(sh.ran.none { "reboot" in it })
    }

    @Test fun versionCodeFromPmList() {
        val pkgs = Parsers.packages("package:com.a versionCode:42 uid:10023\npackage:com.b uid:10024\n")
        assertEquals(listOf(42L, null), pkgs.map { it.versionCode })
        assertEquals(listOf(10023, 10024), pkgs.map { it.uid })
    }
}
