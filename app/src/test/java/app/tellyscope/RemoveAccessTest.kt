// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.lifecycle.viewModelScope
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceSource
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.Snapshot
import app.tellyscope.data.TvAccess
import app.tellyscope.ui.Removal
import app.tellyscope.ui.TellyscopeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A TV whose access, revoke answer and link state the test sets. */
private class AccessTv : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var state = DeviceState.Online
    /** False from a reconnect until the TV allows this phone: the link still shows online, with no session. */
    var readable = true
    var revokes = true
    var revoked = 0
    var reconnects = 0
    var devOptions = 0
    var after = TvAccess()
    var reboots = 0

    override suspend fun known() = listOf(Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), state, null, null, null, 0L))
    override fun live(deviceId: String) = emptyFlow<Snapshot>()
    override suspend fun apps(deviceId: String) = emptyList<app.tellyscope.data.AppInfo>()
    override suspend fun access(deviceId: String) = if (state == DeviceState.Online && readable) after else throw IllegalStateException("offline")
    override suspend fun revokeAll(deviceId: String): Boolean { revoked++; return revokes }
    override suspend fun reconnect(deviceId: String) { reconnects++; readable = false }
    override suspend fun openDeveloperOptions(deviceId: String): Boolean { devOptions++; return true }
    override suspend fun reboot(deviceId: String): Boolean { reboots++; return true }
    override fun phoneFingerprint() = "AA:BB"
}

@OptIn(ExperimentalCoroutinesApi::class)
class RemoveAccessTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val tv = AccessTv()
    private lateinit var vm: TellyscopeViewModel

    private val onlyPhone = TvAccess(listOf(app.tellyscope.data.AccessClient("192.0.2.10", 1, app.tellyscope.data.AccessClient.Who.Phone)), emptyList())

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun start() = with(test) {
        vm = TellyscopeViewModel(tv, clock = { 0L })
        runCurrent()
    }

    private val step get() = vm.state.value.device.removal?.step

    @Test fun confirmThenRevokeThenAllowOnTheTvThenDone() = with(test) {
        start()
        vm.askRemoveAccess()
        assertEquals(Removal.Step.Confirm, step)
        vm.removeAccess()
        runCurrent()
        assertEquals(1, tv.revoked)
        assertEquals(Removal.Step.Allow, step)
        assertEquals("AA:BB", vm.state.value.device.removal?.fingerprint)
        assertEquals(1, tv.reconnects)
        // The link drops while the TV asks; the user allows; it's back.
        tv.state = DeviceState.Waiting
        advanceTimeBy(3_100)
        assertEquals(Removal.Step.Allow, step)
        tv.after = onlyPhone
        tv.state = DeviceState.Online
        tv.readable = true
        advanceTimeBy(3_100)
        assertEquals(Removal.Step.Done, step)
    }

    @Test fun aTvThatRefusesTheCallGetsTheManualGuide() = with(test) {
        tv.revokes = false
        start()
        vm.askRemoveAccess()
        vm.removeAccess()
        runCurrent()
        assertEquals(Removal.Step.Manual, step)
        assertEquals(0, tv.reconnects)
        vm.openDeveloperOptions()
        runCurrent()
        assertEquals(1, tv.devOptions)
        vm.revokedByHand()
        runCurrent()
        assertEquals(Removal.Step.Allow, step)
        assertEquals(1, tv.reconnects)
    }

    @Test fun refusedOnTheTvOffersToAskAgain() = with(test) {
        start()
        vm.askRemoveAccess()
        vm.removeAccess()
        runCurrent()
        tv.state = DeviceState.NotAllowed
        advanceTimeBy(1_100)
        assertTrue(vm.state.value.device.removal!!.refused)
        vm.askTvAgain()
        runCurrent()
        assertEquals(2, tv.reconnects)
        assertEquals(false, vm.state.value.device.removal!!.refused)
    }

    @Test fun onlineIsNotEnoughTheFirstReadOnTheNewLinkDecides() = with(test) {
        tv.after = onlyPhone
        start()
        vm.askRemoveAccess()
        vm.removeAccess()
        // The link stays "online" while the TV holds the new connection at its prompt.
        advanceTimeBy(30_100)
        assertEquals(Removal.Step.Allow, step)
        tv.readable = true
        advanceTimeBy(1_100)
        assertEquals(Removal.Step.Done, step)
    }

    @Test fun aComputerStillConnectedIsNamed() = with(test) {
        tv.after = TvAccess(onlyPhone.connected!! + app.tellyscope.data.AccessClient("192.0.2.47", 2, app.tellyscope.data.AccessClient.Who.Other), emptyList())
        start()
        vm.askRemoveAccess()
        vm.removeAccess()
        runCurrent()
        tv.readable = true
        advanceTimeBy(1_100)
        assertEquals(Removal.Step.StillConnected, step)
        assertTrue(vm.state.value.device.removal!!.open)
        vm.closeRemoval()
        runCurrent()
        assertNull(step)
    }

    private val stillOpen get() = TvAccess(onlyPhone.connected!! + app.tellyscope.data.AccessClient("192.0.2.47", 2, app.tellyscope.data.AccessClient.Who.Other), emptyList())

    @Test fun checkAgainReadsTheTvAndClearsNothing() = with(test) {
        tv.after = stillOpen
        start()
        vm.askRemoveAccess()
        vm.removeAccess()
        runCurrent()
        tv.readable = true
        advanceTimeBy(1_100)
        tv.after = onlyPhone
        vm.checkAgain()
        runCurrent()
        assertEquals(Removal.Step.Done, step)
        assertEquals(1, tv.revoked)
    }

    @Test fun restartingTheTvWaitsForItThenChecksAgain() = with(test) {
        tv.after = stillOpen
        start()
        vm.askRemoveAccess()
        vm.removeAccess()
        runCurrent()
        tv.readable = true
        advanceTimeBy(1_100)
        vm.restartToRemove()
        runCurrent()
        assertEquals(1, tv.reboots)
        assertEquals(Removal.Step.Restarting, step)
        tv.state = DeviceState.Offline
        advanceTimeBy(3_100)
        assertEquals(Removal.Step.Restarting, step)
        tv.after = onlyPhone
        tv.state = DeviceState.Online
        advanceTimeBy(5_000)
        assertEquals(Removal.Step.Done, step)
    }

    @Test fun stopWaitingLeavesTheFlow() = with(test) {
        start()
        vm.askRemoveAccess()
        vm.removeAccess()
        runCurrent()
        tv.state = DeviceState.Waiting
        vm.closeRemoval()
        tv.state = DeviceState.Online
        advanceTimeBy(5_000)
        assertNull(step)
    }
}
