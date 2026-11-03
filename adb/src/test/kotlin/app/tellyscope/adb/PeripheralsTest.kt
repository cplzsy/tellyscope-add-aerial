// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import app.tellyscope.adb.Peripheral.Group
import app.tellyscope.adb.Peripheral.Kind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeripheralsTest {
    private val input11 = """
        Event Hub State:
          BuiltInKeyboardId: -2
          Devices:
            -1: Virtual
              Classes: 0x40000023
              Identifier: bus=0x0000, vendor=0x0000, product=0x0000, version=0x0000
            3: sunxi-ir
              Classes: 0x00000001
              Identifier: bus=0x0019, vendor=0x0001, product=0x0001, version=0x0100
            5: Xbox Wireless Controller
              Classes: 0x80000141
              Identifier: bus=0x0005, vendor=0x045e, product=0x0b13, version=0x0509
            6: Logitech K400
              Classes: 0x80000003
              Identifier: bus=0x0003, vendor=0x046d, product=0x4024, version=0x0111
            7: Logitech K400
              Classes: 0x80000008
              Identifier: bus=0x0003, vendor=0x046d, product=0x4024, version=0x0111
        Input Reader State:
    """.trimIndent()

    @Test fun externalInputsOnlyStrongestKindWins() {
        val got = Peripherals.inputs(input11)
        assertEquals(
            listOf(
                Peripheral("Xbox Wireless Controller", Group.Input, Kind.Controller, true, "Bluetooth"),
                Peripheral("Logitech K400", Group.Input, Kind.Keyboard, true, "USB"),
            ),
            got,
        )
    }

    @Test fun android12ClassNamesWork() {
        val out = """
            Event Hub State:
              Devices:
                9: MX Master
                  Classes: CURSOR | EXTERNAL
                  Identifier: bus=0x0005, vendor=0x046d, product=0xb019, version=0x0002
        """.trimIndent()
        assertEquals(Kind.Mouse, Peripherals.inputs(out).single().kind)
    }

    @Test fun aUsbCameraIsACameraNamedOnce() {
        // The projector's camera, as dumpsys input lists its button.
        val out = """
            Event Hub State:
              Devices:
                4: icspring camera: icspring camer
                  Classes: KEYBOARD | EXTERNAL
                  Identifier: bus=0x0003, vendor=0x1d6b, product=0x0102, version=0x0100
        """.trimIndent()
        assertEquals(listOf(Peripheral("icspring camera", Group.Input, Kind.Camera, true, "USB")), Peripherals.inputs(out))
    }

    @Test fun aNameIsCutToOnceOnlyWhenOneHalfStartsTheOther() {
        assertEquals("icspring camera", Peripherals.once("icspring camera: icspring camer"))
        assertEquals("HD Webcam C270", Peripherals.once("HD Webcam: HD Webcam C270"))
        assertEquals("Mi Remote: Consumer Control", Peripherals.once("Mi Remote: Consumer Control"))
        assertEquals("Logitech K400", Peripherals.once("Logitech K400"))
    }

    private val bluetooth = """
        Bonded devices:
          AA:BB:CC:DD:EE:01 [ DUAL ] Xbox Wireless Controller
          AA:BB:CC:DD:EE:02 [BR/EDR] Sony WH-1000XM4
          XX:XX:XX:XX:EE:03 [  LE  ] Remote Control 2.4G

        Connected profiles:
    """.trimIndent()

    @Test fun bondedDevicesConnectedWhenAlsoAnInput() {
        val got = Peripherals.bluetooth(bluetooth, Peripherals.inputs(input11))
        assertEquals(listOf("Xbox Wireless Controller", "Sony WH-1000XM4", "Remote Control 2.4G"), got.map { it.name })
        assertEquals(listOf(Kind.Controller, Kind.Headphones, Kind.Remote), got.map { it.kind })
        assertEquals(listOf("Connected", "Paired", "Paired"), got.map { it.status })
        assertEquals(listOf(true, false, false), got.map { it.live })
    }

    @Test fun noBluetoothNoRows() {
        assertEquals(emptyList(), Peripherals.bluetooth(""))
        assertEquals(emptyList(), Peripherals.bluetooth("Bluetooth Status\n  enabled: false\n"))
    }

    @Test fun usbVolumesLabelsAndFreeSpace() {
        assertEquals(
            listOf("public:8,1" to "1A2B-3C4D", "public:8,17" to null),
            Peripherals.volumes("public:8,1 mounted 1A2B-3C4D\npublic:8,17 mounted null\npublic:8,33 unmounted 9999-0000\n"),
        )
        val mount = "    VolumeInfo{public:8,1}:\n      type=PUBLIC diskId=disk:8,0 partGuid=null mountFlags=VISIBLE \n      fsType=vfat fsUuid=1A2B-3C4D fsLabel=Movies 2 internalPath=/mnt/media_rw/1A2B-3C4D"
        assertEquals(mapOf("1A2B-3C4D" to "Movies 2"), Peripherals.labels(mount))
        assertEquals(41L * 1024, Peripherals.free("Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/fuse 62000 21000 41 34% /storage/1A2B-3C4D\n"))
        assertNull(Peripherals.free("df: /storage/x: No such file or directory"))
    }

    @Test fun usbDriveFallsBackToPlainWords() = runTest {
        val sh = FakeShell(mapOf("sm list-volumes" to ok("public:8,1 mounted 1A2B-3C4D\n")))
        assertEquals(listOf(Peripheral("USB drive", Group.Storage, Kind.Usb, true, "Connected")), Peripherals(sh).read())
    }

    @Test fun hdmiCecSkipsTheTvItself() {
        val out = """
            mDeviceInfos:
              Device: logical_address: 0x00 device_type: 0 vendor_id: 0 display_name: TV power_status: 0 physical_address: 0x0000 port_id: 0
              Device: logical_address: 0x04 device_type: 4 vendor_id: 0 display_name: Chromecast power_status: 0 physical_address: 0x1000 port_id: 1
              Device: logical_address: 0x05 device_type: 5 vendor_id: 0 display_name: Sonos Beam power_status: 1 physical_address: 0x2000 port_id: 2
        """.trimIndent()
        assertEquals(
            listOf(
                Peripheral("Chromecast", Group.Hdmi, Kind.Hdmi, true, "On"),
                Peripheral("Sonos Beam", Group.Hdmi, Kind.Speaker, false, "Standby"),
            ),
            Peripherals.hdmi(out),
        )
    }

    @Test fun audioOutputFromStreamMusic() {
        val out = "- STREAM_RING:\n   Devices: speaker\n- STREAM_MUSIC:\n   Muted: false\n   Devices: hdmi_arc\n- STREAM_ALARM:\n"
        assertEquals(Peripheral("Sound output", Group.Audio, Kind.Sound, true, "HDMI ARC"), Peripherals.audio(out))
        assertEquals("TV speakers", Peripherals.audio("- STREAM_MUSIC:\n   Devices: speaker\n")?.status)
        assertNull(Peripherals.audio("Can't find service: audio"))
    }

    @Test fun aSourceThatFailsAddsNothing() = runTest {
        val sh = FakeShell(mapOf("dumpsys audio" to ok("- STREAM_MUSIC:\n   Devices: speaker\n")))
        assertEquals(listOf("TV speakers"), Peripherals(sh).read().map { it.status })
    }
}
