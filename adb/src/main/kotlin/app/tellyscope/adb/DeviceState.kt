// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/**
 * Connection state shown for a device. ADB reports "denied" and "not answered yet" the same way
 * (no CNXN after our key), so a device that never answers the Allow prompt is [NOT_ALLOWED], never
 * "denied".
 */
enum class DeviceState { FOUND, WAITING, ONLINE, OFFLINE, NOT_ALLOWED, NEEDS_PAIRING }

sealed interface DeviceEvent {
    /** User pressed connect, or an offline device answered on its port again. */
    data object Connect : DeviceEvent
    data object Authorized : DeviceEvent
    data object AuthTimedOut : DeviceEvent
    /** Wireless debugging port that doesn't know our key: ask for the 6-digit code. */
    data object NeedsPairing : DeviceEvent
    /** User or app closed the connection on purpose. */
    data object Disconnected : DeviceEvent
    data object Unreachable : DeviceEvent
    data object Lost : DeviceEvent
}

/** Pure transition table; anything not listed leaves the state unchanged. */
fun DeviceState.on(event: DeviceEvent): DeviceState = when (event) {
    DeviceEvent.Connect -> if (this == DeviceState.ONLINE) this else DeviceState.WAITING
    DeviceEvent.Authorized -> DeviceState.ONLINE
    DeviceEvent.AuthTimedOut -> if (this == DeviceState.ONLINE) this else DeviceState.NOT_ALLOWED
    DeviceEvent.Unreachable -> when (this) {
        DeviceState.FOUND, DeviceState.NOT_ALLOWED -> this
        else -> DeviceState.OFFLINE
    }
    DeviceEvent.Lost -> if (this == DeviceState.ONLINE) DeviceState.OFFLINE else this
    DeviceEvent.NeedsPairing -> DeviceState.NEEDS_PAIRING
    DeviceEvent.Disconnected -> DeviceState.FOUND
}
