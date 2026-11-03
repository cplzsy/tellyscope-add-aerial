// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.lifecycle.viewModelScope
import app.tellyscope.adb.RemoteKey
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceSource
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.Snapshot
import app.tellyscope.ui.Copy
import app.tellyscope.ui.TellyscopeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One TV; screen and key calls are recorded in order, and [screenWorks] is its answer to them. */
private class Screen : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    val calls = mutableListOf<String>()
    var screenWorks = true

    override suspend fun known() = listOf(
        Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), DeviceState.Online, null, null, null, 0L, nickname = "Projector"),
    )

    override fun live(deviceId: String) = emptyFlow<Snapshot>()

    override suspend fun screen(deviceId: String, on: Boolean): Boolean {
        calls += if (on) "on" else "off"
        return screenWorks
    }

    override suspend fun press(deviceId: String, key: RemoteKey): Boolean {
        calls += key.name
        return true
    }

    override fun closeRemote() {
        calls += "close"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenOffTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val tv = Screen()
    private lateinit var vm: TellyscopeViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun off() = with(test) {
        vm = TellyscopeViewModel(tv, clock = { 0L })
        runCurrent()
        vm.setScreenOff(true)
        runCurrent()
    }

    @Test fun theFirstKeyOnlyWakesTheScreenAndTheNextOneIsSent() = with(test) {
        off()
        assertEquals(setOf("tv"), vm.state.value.screenOff)
        launch { vm.press(RemoteKey.DOWN) }
        runCurrent()
        assertTrue(vm.state.value.screenOff.isEmpty())
        launch { vm.press(RemoteKey.DOWN) }
        runCurrent()
        assertEquals(listOf("off", "on", "DOWN"), tv.calls)
    }

    @Test fun closingTheRemoteTurnsThePictureBackOnFirst() = with(test) {
        off()
        vm.closeRemote()
        runCurrent()
        assertEquals(listOf("off", "on", "close"), tv.calls)
        assertTrue(vm.state.value.screenOff.isEmpty())
    }

    @Test fun aTvThatCantTurnItsPictureOffSaysSo() = with(test) {
        tv.screenWorks = false
        off()
        assertFalse("tv" in vm.state.value.screenOff)
        assertEquals(Copy.SCREEN_OFF_REFUSED, vm.notice.value)
    }

    @Test fun aPictureThatWontComeBackStaysOffAndSaysSo() = with(test) {
        off()
        tv.screenWorks = false
        vm.setScreenOff(false)
        runCurrent()
        assertEquals(setOf("tv"), vm.state.value.screenOff)
        assertEquals(Copy.SCREEN_ON_REFUSED, vm.notice.value)
    }
}
