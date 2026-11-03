// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/** Something plugged into or paired with the TV, for the Connected devices card. */
data class Peripheral(
    val name: String,
    val group: Group,
    val kind: Kind,
    /** In use now; false for a paired Bluetooth device that isn't connected. */
    val live: Boolean,
    /** What the TV says about it, already in the card's words: "USB", "Paired", "On". */
    val status: String,
    /** Free bytes on a USB drive when `df` answered; the card shows it in place of [status]. */
    val free: Long? = null,
) {
    enum class Group { Bluetooth, Input, Storage, Hdmi, Audio }
    enum class Kind { Remote, Headphones, Speaker, Keyboard, Mouse, Controller, Bluetooth, Usb, Hdmi, Sound, Camera }
}

/**
 * Reads the TV's Bluetooth, input, USB storage, HDMI-CEC and audio reports. Each source that
 * fails or prints nothing just adds nothing, since output differs from build to build. Built-in
 * inputs (the IR receiver, the TV's own keys) are left out: only external Bluetooth and USB count.
 */
class Peripherals(private val shell: AdbShell) {

    suspend fun read(): List<Peripheral> {
        suspend fun out(cmd: String) = runCatching { shell.shell(cmd).output }.getOrDefault("")
        val inputs = inputs(out("dumpsys input | sed -n '/Event Hub State/,/Input Reader State/p'"))
        val paired = bluetooth(out("dumpsys bluetooth_manager | sed -n '/Bonded devices:/,/^\\s*\$/p'"), inputs)
        // A Bluetooth device that is also an input is listed once, under Bluetooth.
        val btNames = paired.map { it.name }.toSet()
        val storage = volumes(out("sm list-volumes public")).map { (_, uuid) ->
            val label = uuid?.let { labels(out("dumpsys mount | grep -F 'fsUuid=$it'"))[it] }
            val free = uuid?.let { free(out("df -k /storage/$it")) }
            Peripheral(label ?: "USB drive", Peripheral.Group.Storage, Peripheral.Kind.Usb, true, "Connected", free)
        }
        return paired + inputs.filter { it.name !in btNames } + storage + hdmi(out("dumpsys hdmi_control")) + listOfNotNull(audio(out("dumpsys audio")))
    }

    companion object {
        private val BONDED = Regex("^\\s*[0-9A-Fa-fX]{2}(?::[0-9A-Fa-fX]{2}){5}\\s+\\[\\s*[\\w/?]+\\s*]\\s+(.+)$")

        /**
         * The "Bonded devices" block of `dumpsys bluetooth_manager`: `<address> [<type>] <name>`.
         * One that is also among [inputs] is connected now, and takes its kind from it.
         */
        fun bluetooth(dumpsys: String, inputs: List<Peripheral> = emptyList()): List<Peripheral> {
            val block = dumpsys.substringAfter("Bonded devices:", "").lineSequence().drop(1).takeWhile { it.isNotBlank() }
            return block.mapNotNull { BONDED.matchEntire(it)?.groupValues?.get(1)?.trim() }.filter { it.isNotEmpty() }.distinct().map { name ->
                val input = inputs.firstOrNull { it.name == name }
                Peripheral(
                    name, Peripheral.Group.Bluetooth, input?.kind ?: guess(name),
                    live = input != null,
                    status = if (input != null) "Connected" else "Paired",
                )
            }.toList()
        }

        /** Names only go so far: these are the usual words in a Bluetooth device's name. */
        private fun guess(name: String): Peripheral.Kind {
            val n = name.lowercase()
            return when {
                Regex("\\b(remote|rc)\\b").containsMatchIn(n) -> Peripheral.Kind.Remote
                listOf("headphone", "buds", "airpods", "wh-", "wf-", "earphone").any { it in n } -> Peripheral.Kind.Headphones
                listOf("speaker", "soundbar", "sound bar", "beam").any { it in n } -> Peripheral.Kind.Speaker
                listOf("controller", "gamepad", "xbox", "dualshock", "dualsense").any { it in n } -> Peripheral.Kind.Controller
                "keyboard" in n -> Peripheral.Kind.Keyboard
                "mouse" in n -> Peripheral.Kind.Mouse
                else -> Peripheral.Kind.Bluetooth
            }
        }

        private val DEVICE = Regex("^\\s*-?\\d+: (.+)$")
        private val BUS = Regex("bus=0x([0-9a-fA-F]+)")

        /**
         * External input devices from `dumpsys input`'s Event Hub: Bluetooth (bus 5) or USB
         * (bus 3). `Classes:` is a hex mask before Android 12 and a list of names after; both work.
         */
        fun inputs(dumpsys: String): List<Peripheral> {
            val found = mutableListOf<Pair<String, Pair<String, Int?>>>()
            var name: String? = null
            var classes = ""
            var bus: Int? = null
            fun flush() { name?.let { found += it to (classes to bus) } }
            for (line in dumpsys.substringAfter("Devices:", "").lines()) {
                val header = DEVICE.matchEntire(line)
                when {
                    header != null && line.indexOf(header.groupValues[1]) <= 12 -> { flush(); name = header.groupValues[1].trim(); classes = ""; bus = null }
                    line.trim().startsWith("Classes:") -> classes = line.substringAfter("Classes:").trim()
                    line.trim().startsWith("Identifier:") -> bus = BUS.find(line)?.groupValues?.get(1)?.toIntOrNull(16)
                }
            }
            flush()
            return found.mapNotNull { (n, info) ->
                val via = when (info.second) { BT_BUS -> "Bluetooth"; USB_BUS -> "USB"; else -> return@mapNotNull null }
                Triple(n, kind(info.first), via)
            }.groupBy { it.first }.map { (n, rows) ->
                // One remote shows up as several devices; the strongest kind wins.
                val best = rows.maxBy { it.second.ordinal }
                val shown = once(n)
                Peripheral(shown, Peripheral.Group.Input, if (CAMERA.containsMatchIn(shown)) Peripheral.Kind.Camera else best.second.kind, true, best.third)
            }.distinctBy { it.name }
        }

        /** A USB camera's button: an input device named after the camera, not a remote. */
        private val CAMERA = Regex("\\b(camera|webcam)\\b", RegexOption.IGNORE_CASE)

        /**
         * Linux names a UVC camera's input device "<product>: <name>", cut short: "icspring
         * camera: icspring camer". When one half starts the other, the name is said once.
         */
        fun once(name: String): String {
            val a = name.substringBefore(": ", "").trim()
            val b = name.substringAfter(": ", "").trim()
            return when {
                a.isEmpty() || b.isEmpty() -> name
                a.startsWith(b) -> a
                b.startsWith(a) -> b
                else -> name
            }
        }

        private const val USB_BUS = 0x03
        private const val BT_BUS = 0x05

        /** Weakest first, so a remote that also says keyboard counts as a keyboard. */
        private enum class InputKind(val kind: Peripheral.Kind) { Remote(Peripheral.Kind.Remote), Mouse(Peripheral.Kind.Mouse), Keyboard(Peripheral.Kind.Keyboard), Controller(Peripheral.Kind.Controller) }

        private fun kind(classes: String): InputKind {
            val mask = Regex("0x([0-9a-fA-F]+)").find(classes)?.groupValues?.get(1)?.toLongOrNull(16)
            fun has(bit: Long, word: String) = (mask != null && mask and bit != 0L) || Regex("\\b$word\\b").containsMatchIn(classes)
            return when {
                has(0x40, "GAMEPAD") || has(0x100, "JOYSTICK") -> InputKind.Controller
                has(0x2, "ALPHAKEY") -> InputKind.Keyboard
                has(0x8, "CURSOR") -> InputKind.Mouse
                else -> InputKind.Remote
            }
        }

        /** `sm list-volumes public`: `public:8,1 mounted 1A2B-3C4D`. The mounted ones, with their filesystem UUID. */
        fun volumes(sm: String): List<Pair<String, String?>> = sm.lines().mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size < 2 || !cols[0].startsWith("public:") || cols[1] != "mounted") return@mapNotNull null
            cols[0] to cols.getOrNull(2)?.takeIf { it != "null" }
        }

        /** Drive labels by UUID from `dumpsys mount`'s VolumeInfo lines (`fsUuid=… fsLabel=…`). */
        fun labels(dumpsys: String): Map<String, String> = dumpsys.lines().mapNotNull { line ->
            val uuid = Regex("fsUuid=(\\S+)").find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            val label = Regex("fsLabel=(.*?)(?:\\s+\\w+=|$)").find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
            uuid to label
        }.toMap()

        /** Free bytes from `df -k <path>`'s one data row; null when it didn't answer. */
        fun free(df: String): Long? = df.lines().drop(1).firstNotNullOfOrNull { line ->
            line.trim().split(Regex("\\s+")).getOrNull(3)?.toLongOrNull()?.times(1024)
        }

        private val CEC = Regex("logical_address: 0x([0-9a-fA-F]+).*?device_type: (\\d+).*?display_name: (.*?) power_status: (\\d+)")

        /**
         * Other devices on HDMI-CEC from `dumpsys hdmi_control`, by their CEC name. The TV itself
         * (logical address 0) is left out. A power status of 0 is on.
         */
        fun hdmi(dumpsys: String): List<Peripheral> = CEC.findAll(dumpsys).mapNotNull { m ->
            val (addr, type, name, power) = m.destructured
            if (addr.toInt(16) == 0 || name.isBlank()) return@mapNotNull null
            Triple(addr, name.trim(), type.toInt() to (power == "0"))
        }.distinctBy { it.first }.map { (_, name, info) ->
            val (type, on) = info
            Peripheral(name, Peripheral.Group.Hdmi, if (type == AUDIO_SYSTEM) Peripheral.Kind.Speaker else Peripheral.Kind.Hdmi, on, if (on) "On" else "Standby")
        }.toList()

        private const val AUDIO_SYSTEM = 5

        /** Where the TV's sound goes now: the `Devices:` line under STREAM_MUSIC in `dumpsys audio`. */
        fun audio(dumpsys: String): Peripheral? {
            val music = dumpsys.substringAfter("- STREAM_MUSIC:", "").takeIf { it.isNotEmpty() } ?: return null
            val devices = Regex("Devices:\\s*(\\S[^\\r\\n]*)").find(music)?.groupValues?.get(1)?.trim() ?: return null
            val first = devices.split(Regex("[\\s,|]+")).firstOrNull { it.isNotBlank() } ?: return null
            val to = when {
                first == "speaker" -> "TV speakers"
                first.startsWith("hdmi_earc") -> "HDMI eARC"
                first.startsWith("hdmi_arc") -> "HDMI ARC"
                first.startsWith("hdmi") -> "HDMI"
                first.startsWith("bt_") || first.startsWith("ble_") -> "Bluetooth"
                first.startsWith("usb") -> "USB audio"
                first.startsWith("wired_") || first == "line" || first.startsWith("headphone") -> "Headphone jack"
                first == "spdif" -> "Optical"
                else -> first
            }
            return Peripheral("Sound output", Peripheral.Group.Audio, Peripheral.Kind.Sound, true, to)
        }
    }
}
