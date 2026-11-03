// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import app.tellyscope.adb.DeviceEvent.*
import app.tellyscope.adb.DeviceState.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceStateTest {
    @Test fun happyPath() = assertEquals(ONLINE, FOUND.on(Connect).on(Authorized))
    @Test fun noAnswerIsNotAllowed() = assertEquals(NOT_ALLOWED, FOUND.on(Connect).on(AuthTimedOut))
    @Test fun dropAndComeBack() = assertEquals(ONLINE, ONLINE.on(Lost).also { assertEquals(OFFLINE, it) }.on(Authorized))
    @Test fun unreachableWhileWaitingIsOffline() = assertEquals(OFFLINE, WAITING.on(Unreachable))
    @Test fun foundStaysFoundWhenUnreachable() = assertEquals(FOUND, FOUND.on(Unreachable))
    @Test fun connectWhileOnlineIsNoop() = assertEquals(ONLINE, ONLINE.on(Connect))
    @Test fun pairingThenDisconnect() = assertEquals(FOUND, WAITING.on(NeedsPairing).also { assertEquals(NEEDS_PAIRING, it) }.on(Disconnected))
    @Test fun retryAfterNotAllowedWaitsAgain() = assertEquals(WAITING, NOT_ALLOWED.on(Connect))
}
