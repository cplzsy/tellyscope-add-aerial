// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.test.runTest
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportsTest {
    private val app = AppFacts("0.8.0", "Pixel 7, Android 14")
    private val tv = TvFacts(
        manufacturer = "XGIMI", model = "XK03H", device = "galileo", androidVersion = "11", sdk = 30,
        build = "RTT1.230922.001", stockPackages = listOf("com.google.android.tvlauncher", "com.xgimi.filemanager"),
        userApps = 7, identifiers = setOf("XK03HSN12345678"),
    )

    /** The query of [link] as the form sees it. */
    private fun query(link: String): Map<String, String> = URI(link).rawQuery.split('&').associate {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }

    @Test fun problemGoesToOurFormWithEveryField() {
        val r = Reports.problem(app, tv, Reports.Connection.PORT_5555, Reports.Outcome.MISBEHAVED, "Remote keys lag", log = "line 1")
        assertTrue(r.link.startsWith("https://github.com/${Reports.OURS}/issues/new?"), r.link)
        val q = query(r.link)
        assertEquals("device_report.yml", q["template"])
        assertEquals("[Device] XGIMI XK03H (galileo)", q["title"])
        assertEquals("XGIMI XK03H (galileo)", q["model"])
        assertEquals("Android 11 (API 30), build RTT1.230922.001", q["android-version"])
        assertEquals("Classic network debugging", q["connection"])
        assertEquals("0.8.0", q["app-version"])
        assertEquals("Connected, but something misbehaved", q["outcome"])
        assertEquals("Remote keys lag\n\nTellyscope 0.8.0 on Pixel 7, Android 14\n\nLog:\n```\nline 1\n```", q["details"])
        assertNull(r.rest)
    }

    @Test fun problemBeforeAnyTvConnected() {
        val q = query(Reports.problem(app, null, Reports.Connection.COULD_NOT_CONNECT, Reports.Outcome.COULD_NOT_CONNECT, "", null).link)
        assertEquals("Unknown TV", q["model"])
        assertEquals("Unknown", q["android-version"])
        assertTrue(q.getValue("details").startsWith("(nothing written)"))
    }

    @Test fun newTvGoesToCleanyourtvsMyTvForm() {
        val r = Reports.newTv(app, tv, Reports.Stage.COULD_NOT_CONNECT, "Stuck at Allow")
        assertTrue(r.link.startsWith("https://github.com/Gathin23/cleanyourtv/issues/new?"), r.link)
        val q = query(r.link)
        assertEquals(setOf("template", "title", "model", "android", "stage", "details"), q.keys)
        assertEquals("tv-list.yml", q["template"])
        assertEquals("[TV] XGIMI XK03H (Android 11)", q["title"])
        assertEquals("Android 11 (API 30), build RTT1.230922.001", q["android"])
        assertEquals("Could not connect over ADB", q["stage"])
        val details = q.getValue("details")
        assertTrue(details.startsWith("Stuck at Allow\n\nApps the TV came with (2 maker and Google, 0 Android core):\ncom.google.android.tvlauncher\ncom.xgimi.filemanager\n"), details)
        assertTrue("User-installed apps: 7 (not listed)" in details)
    }

    @Test fun spacesAreEncodedAsPercent20() {
        val link = Reports.newTv(app, tv, Reports.Stage.HERE_IS_MY_LIST, "a b").link
        assertFalse('+' in link.substringAfter('?').replace("%2B", ""), link)
        assertFalse(' ' in link)
    }

    @Test fun longAppListFitsTheLinkAndTheRestWaitsForAComment() {
        val vendor = (1..400).map { "com.vendor.app%03d".format(it) }
        val big = tv.copy(stockPackages = listOf("android", "com.android.printspooler") + vendor)
        val r = Reports.newTv(app, big, Reports.Stage.HERE_IS_MY_LIST, "")
        assertTrue(r.link.length <= Report.MAX_URL, "${r.link.length}")
        val details = query(r.link).getValue("details")
        assertTrue(details.startsWith("Apps the TV came with (400 maker and Google, 2 Android core):\ncom.vendor.app001\n"), details)
        val shown = details.lines().filter { it.startsWith("com.") || it == "android" }
        assertTrue(shown.size in 100..399, "${shown.size}")
        assertTrue("+${402 - shown.size} more, added as a comment" in details, details)
        // Every app is in the issue or in the comment, once, and the Android core is what's cut first.
        assertEquals(vendor + listOf("android", "com.android.printspooler"), shown + r.rest!!.lines().drop(1))
    }

    @Test fun androidCoreComesLastNotOut() {
        val details = Reports.newTv(app, tv.copy(stockPackages = listOf("android", "com.android.printspooler", "com.google.android.katniss")), Reports.Stage.HERE_IS_MY_LIST, "").fields.getValue("details")
        assertTrue("Apps the TV came with (1 maker and Google, 2 Android core):\ncom.google.android.katniss\nandroid\ncom.android.printspooler\n" in details, details)
    }

    @Test fun longLogKeepsItsNewestLines() {
        val log = (1..2000).joinToString("\n") { "10-25 12:00:00.000 line $it from 192.0.2.10" }
        val r = Reports.problem(app, tv, Reports.Connection.PORT_5555, Reports.Outcome.MISBEHAVED, "lag", log)
        assertTrue(r.link.length <= Report.MAX_URL, "${r.link.length}")
        val details = query(r.link).getValue("details")
        assertTrue(details.trimEnd().endsWith("line 2000 from <ip>\n```"), details.takeLast(200))
        val kept = details.lines().count { " line " in it }
        assertTrue("(earlier ${2000 - kept} lines cut, added as a comment)" in details, details)
        val rest = r.rest!!
        assertTrue(rest.startsWith("Earlier log lines:\n```\n10-25 12:00:00.000 line 1 from <ip>\n"), rest.take(200))
        assertTrue("line ${2000 - kept} from" in rest && "line ${2001 - kept} from" !in rest)
        assertFalse("192.0.2" in rest)
    }

    @Test fun overlongWritingIsCutLast() {
        val r = Reports.problem(app, tv, Reports.Connection.PORT_5555, Reports.Outcome.MISBEHAVED, "word ".repeat(3000), log = "only line")
        assertTrue(r.link.length <= Report.MAX_URL, "${r.link.length}")
        assertTrue(query(r.link).getValue("details").endsWith("… (the rest added as a comment)"))
        assertTrue(r.rest!!.contains("only line"))
    }

    @Test fun myIssuesLinkFindsTheNewestOnTheTargetRepo() {
        val r = Reports.newTv(app, tv, Reports.Stage.HERE_IS_MY_LIST, "")
        assertEquals("https://github.com/Gathin23/cleanyourtv/issues?q=is%3Aissue%20author%3A%40me%20sort%3Acreated-desc", r.myIssues)
    }

    @Test fun previewShowsEveryFieldUnderItsLabel() {
        val text = Reports.newTv(app, tv, Reports.Stage.BROKE_AFTER, "").text
        assertTrue(text.startsWith("[TV] XGIMI XK03H (Android 11)\n\n### TV model\n\nXGIMI XK03H (galileo)\n"), text)
        assertTrue("### How far did you get?\n\nSomething broke after the debloat\n" in text)
    }

    @Test fun reportTextIsRedacted() {
        val r = Reports.problem(
            app, tv, Reports.Connection.WIRELESS_DEBUGGING, Reports.Outcome.MISBEHAVED,
            details = "Paired with code 482913 at 192.0.2.10:37155, serial XK03HSN12345678",
            log = "10-25 12:30:45.123 connect adb-XK03HSN12345678-AbCdEf._adb-tls-connect._tcp",
        )
        val details = r.fields.getValue("details")
        assertFalse("482913" in details || "192.0.2" in details || "XK03HSN" in details, details)
        assertTrue("10-25 12:30:45.123" in details, details) // timestamps aren't addresses
    }

    @Test fun redactsAddresses() {
        assertEquals("tv at <ip>:5555", Redact.text("tv at 192.0.2.10:5555"))
        assertEquals("Can't reach <ip>.", Redact.text("Can't reach 192.0.2.10."))
        assertEquals("gw <ip> and <ip>", Redact.text("gw fe80::1c2b:3aff:fe4d%wlan0 and 2001:db8:0:0:0:0:2:1"))
        assertEquals("wlan0 <mac>", Redact.text("wlan0 a4:5e:60:c2:11:9f"))
        assertEquals("signed in as <email>", Redact.text("signed in as alex.smith@gmail.com"))
    }

    @Test fun redactsKeysAndFingerprints() {
        val key = "QAAAA" + "x".repeat(600) + "= alex@laptop"
        assertEquals("key <adb key>", Redact.text("key $key"))
        assertEquals("fp <fingerprint>", Redact.text("fp 1A:2B:3C:4D:5E:6F:70:81:92:A3:B4:C5:D6:E7:F8:09"))
        assertEquals("<key>", Redact.text("-----BEGIN PRIVATE KEY-----\nMIIE\nabc\n-----END PRIVATE KEY-----"))
        assertEquals("sha <fingerprint>", Redact.text("sha " + "ab12".repeat(16)))
    }

    @Test fun redactsSerialsAndCodes() {
        assertEquals("serialno: <serial>", Redact.text("serialno: 0123ABCDEF"))
        assertEquals("pairing code <code>", Redact.text("pairing code 123456"))
        assertEquals("SSID: <wifi> BSSID: <mac>", Redact.text("SSID: \"Smith Family 5G\" BSSID: a4:5e:60:c2:11:9f"))
        assertEquals("found adb-<serial>._adb-tls-pairing._tcp", Redact.text("found adb-0123ABCDEF-Xy7kQz._adb-tls-pairing._tcp"))
        assertEquals("my <serial> box", Redact.text("my Living Room TV box", setOf("Living Room TV")))
    }

    @Test fun leavesWhatAReportNeeds() {
        val s = "com.google.android.tvlauncher disabled on Android 11 build RTT1.230922.001 at 12:30:45, keycode 82, " +
            "versionCode=310100 minSdk=28, java.io.NotSerializableException at AsyncTask\$SerialExecutor, " +
            "_adb-tls-connect._tcp, version 1.2.3.4.5"
        assertEquals(s, Redact.text(s))
    }

    @Test fun readsTheTvAndKeepsSerialsOut() = runTest {
        val sh = FakeShell(
            mapOf(
                "getprop" to ok(
                    "[ro.product.manufacturer]: [XGIMI]\n[ro.product.model]: [XK03H]\n[ro.product.device]: [galileo]\n" +
                        "[ro.build.version.release]: [11]\n[ro.build.version.sdk]: [30]\n[ro.build.display.id]: [RTT1.230922.001]\n" +
                        "[ro.serialno]: [XK03HSN12345678]\n[persist.sys.device_name]: [XGIMI XK03H]\n",
                ),
                Commands.SYSTEM_PACKAGES to ok("package:com.xgimi.filemanager\npackage:android\n"),
                Commands.USER_PACKAGES to ok("package:org.videolan.vlc\npackage:com.spocky.projengmenu\n"),
            ),
        )
        val facts = TvFacts.read(sh)
        assertEquals(tv.copy(stockPackages = listOf("android", "com.xgimi.filemanager"), userApps = 2), facts)
        assertFalse(facts.toString().let { "vlc" in it || "projengmenu" in it })
    }
}
