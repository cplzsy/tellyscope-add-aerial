// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.lifecycle.viewModelScope
import app.tellyscope.data.AppInfo
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceSource
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.Snapshot
import app.tellyscope.ui.TellyscopeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One TV whose app list takes three seconds, then arrives, comes back empty, or fails. */
private class SlowApps(private val state: DeviceState = DeviceState.Online) :
    DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var reply: () -> List<AppInfo> = { listOf(AppInfo("com.example", "App", null, false, true, null, "")) }

    override suspend fun known() = listOf(Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), state, null, null, null, 0L))
    override fun live(deviceId: String): Flow<Snapshot> = emptyFlow()
    override suspend fun history(deviceId: String) = emptyList<HistoryEntry>()
    override suspend fun apps(deviceId: String): List<AppInfo> {
        delay(3_000)
        return reply()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppsLoadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private lateinit var vm: TellyscopeViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.open(source: SlowApps) {
        vm = TellyscopeViewModel(source, clock = { 0L })
        runCurrent()
        assertEquals("tv", vm.state.value.selectedId)
    }

    @Test fun appsLoadUntilTheListArrives() = with(test) {
        open(SlowApps())
        assertTrue(vm.state.value.device.appsLoading)
        advanceTimeBy(3_100)
        assertFalse(vm.state.value.device.appsLoading)
        assertEquals(listOf("com.example"), vm.state.value.device.apps.map { it.packageName })
    }

    @Test fun anEmptyListEndsTheLoading() = with(test) {
        open(SlowApps().apply { reply = { emptyList() } })
        advanceTimeBy(3_100)
        assertFalse(vm.state.value.device.appsLoading)
    }

    @Test fun aFailedReadEndsTheLoading() = with(test) {
        open(SlowApps().apply { reply = { throw IllegalStateException("the TV dropped") } })
        assertTrue(vm.state.value.device.appsLoading)
        advanceTimeBy(3_100)
        assertFalse(vm.state.value.device.appsLoading)
    }

    @Test fun anOfflineTvIsNotLoading() = with(test) {
        open(SlowApps(DeviceState.Offline))
        assertFalse(vm.state.value.device.appsLoading)
    }
}
