// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResetTest {
    private fun tv(states: String) = FakeShell(
        mapOf(
            "pm list packages -s -d" to ok("package:com.google.android.tvlauncher\npackage:com.netflix.ninja\npackage:com.maker.factorytest\npackage:com.maker.demo\n"),
            "pm list packages -s -u" to ok("package:com.google.android.tvlauncher\npackage:com.netflix.ninja\npackage:com.google.android.youtube.tvkids\npackage:com.maker.kiosk\npackage:com.android.settings\n"),
            "pm list packages -s" to ok("package:com.google.android.tvlauncher\npackage:com.netflix.ninja\npackage:com.android.settings\n"),
            "for p in" to ok(states),
        ),
    )

    private val states = """
        @@com.google.android.tvlauncher
            User 0: ceDataInode=1 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=3 instant=false
              uninstallReason=0
        @@com.google.android.youtube.tvkids
            User 0: ceDataInode=0 installed=false hidden=false suspended=false stopped=true notLaunched=true enabled=0 instant=false
              uninstallReason=0
        @@com.maker.demo
            User 0: installed=true hidden=false stopped=false notLaunched=false enabled=4
        @@com.maker.factorytest
            User 0: ceDataInode=3 installed=true hidden=false enabled=2 instant=false
              uninstallReason=0
        @@com.maker.kiosk
            User 0: ceDataInode=0 installed=false hidden=false enabled=0 instant=false
              uninstallReason=1
        @@com.netflix.ninja
            User 0: ceDataInode=4 installed=true hidden=false enabled=3 instant=false
              uninstallReason=0
            User 0: ceDataInode=4 installed=true hidden=false enabled=0 instant=false
    """.trimIndent()

    @Test fun findsWhatWasTurnedOffOrUninstalledByHand() = runTest {
        val scan = Reset(tv(states)).scan()
        assertEquals(listOf("com.google.android.tvlauncher", "com.netflix.ninja"), scan.turnOn)
        // Left out for this kind of user by Android itself: it never shipped on.
        assertEquals(listOf("com.google.android.youtube.tvkids"), scan.bringBack)
        assertEquals(listOf("com.maker.demo", "com.maker.factorytest"), scan.shippedOff)
    }

    @Test fun anAppWhoseStateTheTvDidntSayIsLeftAlone() = runTest {
        val scan = Reset(tv("")).scan()
        assertEquals(ResetScan(emptyList(), emptyList(), emptyList()), scan)
    }

    @Test fun readsEveryCandidateInOneCall() = runTest {
        val sh = tv(states)
        Reset(sh).scan()
        assertEquals(1, sh.ran.count { it.startsWith("for p in") })
        assertTrue(sh.ran.none { it.startsWith("dumpsys") })
    }

    @Test fun changesRunPerUserAndKeepData() = runTest {
        val sh = FakeShell(
            mapOf(
                "pm enable" to ok("Package com.netflix.ninja new state: enabled\n"),
                "pm disable-user" to ok("Package com.netflix.ninja new state: disabled-user\n"),
                "pm install-existing" to ok("Package com.google.android.youtube.tvkids installed for user: 0\n"),
                "pm uninstall" to ok("Success\n"),
            ),
        )
        val r = Reset(sh)
        r.turnOn("com.netflix.ninja")
        r.turnOff("com.netflix.ninja")
        r.bringBack("com.google.android.youtube.tvkids")
        r.remove("com.google.android.youtube.tvkids")
        assertEquals(
            listOf(
                "pm enable --user 0 com.netflix.ninja 2>&1",
                "pm disable-user --user 0 com.netflix.ninja 2>&1",
                "pm install-existing --user 0 com.google.android.youtube.tvkids 2>&1",
                "pm uninstall -k --user 0 com.google.android.youtube.tvkids 2>&1",
            ),
            sh.ran,
        )
    }

    @Test fun aRefusalThrowsWithTheTvsAnswer() = runTest {
        val sh = FakeShell(mapOf("pm enable" to ShellResult("Error: java.lang.SecurityException: Shell cannot change component state", 255)))
        val e = assertFailsWith<IllegalStateException> { Reset(sh).turnOn("com.netflix.ninja") }
        assertTrue(e.message!!.contains("Shell cannot change"))
    }

    @Test fun refusesAnythingButAPackageName() = runTest {
        assertFailsWith<IllegalArgumentException> { Reset(FakeShell()).turnOn("com.foo; reboot") }
    }
}
