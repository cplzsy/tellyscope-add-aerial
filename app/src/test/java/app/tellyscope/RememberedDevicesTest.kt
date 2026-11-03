// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.lifecycle.viewModelScope
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.ui.TellyscopeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RememberedDevicesTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private lateinit var vm: TellyscopeViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun rememberedTvOpensOfflineThenForgetReturnsToScan() = with(test) {
        vm = TellyscopeViewModel(FakeDeviceSource(clock = { 0L }), clock = { 0L })
        runCurrent()
        // The fake remembers one TV that is off: the app opens on its page, not the scan.
        val tv = vm.state.value.selected!!
        assertEquals(DeviceState.Offline, tv.state)

        vm.forget(tv.id)
        runCurrent()
        assertNull(vm.state.value.selectedId)
        assertEquals(emptyList<String>(), vm.state.value.devices.map { it.id })
        // The periodic refresh must not bring it back.
        advanceTimeBy(10_000)
        assertEquals(emptyList<String>(), vm.state.value.devices.map { it.id })
    }

    @Test fun freshInstallStartsOnTheScan() = with(test) {
        vm = TellyscopeViewModel(FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }), clock = { 0L })
        runCurrent()
        assertNull(vm.state.value.selectedId)
    }
}
