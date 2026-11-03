// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeServicesTest {
    private val projectivy = "com.spocky.projengmenu/.services.HomeService"

    @Test fun shortAndFullNamesAreTheSameService() {
        assertTrue(HomeServices.has("com.a/.A:com.spocky.projengmenu/com.spocky.projengmenu.services.HomeService", projectivy))
        assertFalse(HomeServices.has("null", projectivy))
        assertFalse(HomeServices.has("", projectivy))
    }

    @Test fun onAndOffKeepTheOtherServices() {
        assertEquals(listOf("com.a/.A", projectivy), HomeServices.with("com.a/.A\n", projectivy))
        assertEquals(listOf("com.a/.A", projectivy), HomeServices.with("com.a/.A:$projectivy", projectivy))
        assertEquals(listOf("com.a/.A"), HomeServices.without("com.a/.A:$projectivy", projectivy))
        assertEquals(emptyList(), HomeServices.without("null", projectivy))
    }

    private fun tv(enabled: String = "null") = FakeShell().apply {
        var list = enabled
        answer = { cmd ->
            when {
                cmd.startsWith(Commands.A11Y_SERVICES) -> ok("priority=0 preferredOrder=0\n$projectivy\ncom.other.app/.Reader\n")
                cmd.startsWith(Commands.A11Y_ON) -> ok("$list\n")
                cmd.startsWith("settings put secure enabled_accessibility_services") -> ok("").also { list = cmd.substringAfter("'").substringBefore("'") }
                cmd.startsWith("settings delete secure enabled_accessibility_services") -> ok("").also { list = "null" }
                else -> null
            }
        }
    }

    @Test fun theServiceIsReadFromTheTv() = runTest {
        val sh = tv()
        assertEquals(projectivy, Actions(sh).homeService("com.spocky.projengmenu"))
        assertNull(Actions(sh).homeService("me.efesser.flauncher"))
    }

    @Test fun turningItOnAndOffLeavesTheUsersServicesAlone() = runTest {
        val sh = tv("com.talkback/.Talk")
        val a = Actions(sh)
        assertTrue(a.setService(projectivy, on = true))
        assertTrue("settings put secure enabled_accessibility_services 'com.talkback/.Talk:$projectivy' 2>&1" in sh.ran)
        assertTrue("settings put secure accessibility_enabled 1 2>&1" in sh.ran)
        assertTrue(a.serviceOn(projectivy))

        assertTrue(a.setService(projectivy, on = false))
        assertTrue(sh.ran.last { "enabled_accessibility_services" in it && "put" in it }.endsWith("'com.talkback/.Talk' 2>&1"))
        assertFalse(a.serviceOn(projectivy))
    }

    @Test fun theLastServiceOffClearsTheSetting() = runTest {
        val sh = tv(projectivy)
        assertTrue(Actions(sh).setService(projectivy, on = false))
        assertTrue("settings delete secure enabled_accessibility_services 2>&1" in sh.ran)
    }

    @Test fun aTvThatIgnoresTheSettingIsReported() = runTest {
        val sh = FakeShell(mapOf(Commands.A11Y_ON to ok("null\n")))
        assertFalse(Actions(sh).setService(projectivy, on = true))
    }
}
