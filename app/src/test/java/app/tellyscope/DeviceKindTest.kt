// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.Device
import app.tellyscope.data.DeviceKind
import app.tellyscope.data.DeviceState
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.ListEntry
import app.tellyscope.data.deviceKind
import app.tellyscope.data.listKind
import app.tellyscope.data.listMakers
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceKindTest {
    private fun kind(manufacturer: String? = null, model: String? = null, maker: String? = null, hostname: String? = null) =
        deviceKind(Device("tv", ScanHit("tv", 5555, ScanKind.Classic, hostname, maker), DeviceState.Online, model, manufacturer, "11", null))

    @Test fun projectorMakers() {
        assertEquals(DeviceKind.Projector, kind(manufacturer = "XGIMI", model = "Horizon Pro"))
        assertEquals(DeviceKind.Projector, kind(manufacturer = "Anker Nebula", model = "Capsule 3"))
        assertEquals(DeviceKind.Projector, kind(maker = "Dangbei"))
    }

    @Test fun projectorInAName() {
        assertEquals(DeviceKind.Projector, kind(manufacturer = "Amlogic", hostname = "living-room-projector"))
    }

    @Test fun fireTv() {
        assertEquals(DeviceKind.FireTv, kind(manufacturer = "Amazon", model = "AFTMM"))
        assertEquals(DeviceKind.FireTv, kind(model = "AFTKA"))
        assertEquals(DeviceKind.FireTv, kind(maker = "Amazon"))
        assertEquals(DeviceKind.FireTv, kind(hostname = "bedroom-firetv"))
    }

    @Test fun sticksAndBoxes() {
        assertEquals(DeviceKind.Stick, kind(manufacturer = "Google", model = "Chromecast"))
        assertEquals(DeviceKind.Stick, kind(manufacturer = "Xiaomi", model = "Mi TV Stick"))
        assertEquals(DeviceKind.Stick, kind(manufacturer = "NVIDIA", model = "SHIELD Android TV"))
        assertEquals(DeviceKind.Stick, kind(maker = "Google", hostname = "chromecast"))
    }

    @Test fun everythingElseIsATv() {
        assertEquals(DeviceKind.Tv, kind(manufacturer = "Sony", model = "BRAVIA 4K VH2"))
        assertEquals(DeviceKind.Tv, kind(manufacturer = "TCL", model = "Smart TV Pro"))
        assertEquals(DeviceKind.Tv, kind())
    }

    /** "aft" only counts at the start of a model, so a name that merely contains it stays a TV. */
    @Test fun aftOnlyAsAPrefix() = assertEquals(DeviceKind.Tv, kind(manufacturer = "Philips", model = "Craft 55"))

    private fun entry(id: String, name: String) = ListEntry(id, name, null, null, "devices/$id.json", "0".repeat(64))

    /** Lists carry no maker: it comes from the name, or the id when the name starts with a product. */
    @Test fun listMakersFromTheName() {
        val lists = listOf(
            entry("xgimi-horizon-s-pro", "XGIMI Horizon S Pro projector"),
            entry("skyworth-q72", "Skyworth Q72"),
            entry("coocaa-k3-pro", "Skyworth Coocaa K3 Pro"),
            entry("google-chromecast-gtv-4k", "Chromecast with Google TV 4K"),
            entry("ok-ai-pont", "OK. \"AI PONT\""),
        )
        assertEquals(
            mapOf(
                "xgimi-horizon-s-pro" to "XGIMI", "skyworth-q72" to "Skyworth", "coocaa-k3-pro" to "Skyworth",
                "google-chromecast-gtv-4k" to "Google", "ok-ai-pont" to "OK.",
            ),
            listMakers(lists),
        )
    }

    /** The picker's icons use the same guess as the TV's own. */
    @Test fun listKinds() {
        assertEquals(DeviceKind.Projector, listKind(entry("xgimi-mogo-3", "XGIMI MoGo 3 Pro"), "XGIMI"))
        assertEquals(DeviceKind.Projector, listKind(entry("epson-ls800", "Epson LS800 laser projector"), "Epson"))
        assertEquals(DeviceKind.FireTv, listKind(entry("amazon-fire-tv-stick-4k", "Amazon Fire TV Stick 4K"), "Amazon"))
        assertEquals(DeviceKind.Stick, listKind(entry("google-chromecast-gtv-4k", "Chromecast with Google TV 4K"), "Google"))
        assertEquals(DeviceKind.Tv, listKind(entry("sony-bravia-x90j", "Sony Bravia X90J"), "Sony"))
    }
}
