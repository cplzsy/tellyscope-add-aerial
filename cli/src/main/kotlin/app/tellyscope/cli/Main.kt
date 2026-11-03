// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.cli

import app.tellyscope.adb.Action
import app.tellyscope.adb.ActionResult
import app.tellyscope.adb.Actions
import app.tellyscope.adb.ConnectResult
import app.tellyscope.adb.Connector
import app.tellyscope.adb.DeviceLink
import app.tellyscope.adb.KeyHelper
import app.tellyscope.adb.LanScanner
import app.tellyscope.adb.Remote
import app.tellyscope.adb.RemoteKey
import com.flyfishxu.kadb.mdns.KadbMdnsJvm
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Smoke test for :adb from a desktop on the TV's LAN.
 *   scan                 list ADB devices
 *   stats <host> [port]  connect (press Allow on the TV) and print live stats
 *   pair <host> <port> <code>
 *   remote <host> <port> <helper.jar> <step>...
 *                        run each step and print how long the TV took: a RemoteKey name (sent
 *                        through the key helper), input=<KEY> (through `input keyevent`, to
 *                        compare), text=<text>, open=<package>, or hold=<KEY>:<ms>
 */
fun main(args: Array<String>): Unit = runBlocking {
    Connector.init(File(System.getProperty("user.home"), ".tellyscope/adbkey.pem"))
    when (args.firstOrNull()) {
        "scan" -> LanScanner(KadbMdnsJvm()).scan(LanScanner.lanHosts()).collect { println("${it.host}:${it.port}  ${it.kind}  ${it.name ?: "-"}") }
        "pair" -> Connector.pair(args[1], args[2].toInt(), args[3]).also { println("paired") }
        "stats" -> {
            val link = DeviceLink(args[1], args.getOrNull(2)?.toInt() ?: 5555, this)
            launch { link.state.collect { println("state: $it") } }
            launch { link.stats.filterNotNull().onEach { println(it) }.collect() }
            link.connect()
        }
        "remote" -> {
            val session = (Connector.connect(args[1], args[2].toInt()) as ConnectResult.Online).session
            KeyHelper(session, File(args[3]).readBytes()).use { helper ->
                val remote = Remote(session, helper)
                for (step in args.drop(4)) {
                    val start = System.nanoTime()
                    val (what, ok) = when {
                        step.startsWith("text=") -> "type" to remote.type(step.removePrefix("text="))
                        step.startsWith("open=") -> "open" to (Actions(session).run(Action.OPEN, step.removePrefix("open=")) is ActionResult.Done)
                        step.startsWith("hold=") -> {
                            // hold=DOWN:2000 presses DOWN back to back for 2000 ms, like a held remote key.
                            val (name, ms) = step.removePrefix("hold=").split(':')
                            var n = 0
                            while ((System.nanoTime() - start) / 1_000_000 < ms.toLong() && remote.press(RemoteKey.valueOf(name))) n++
                            "hold x$n" to (n > 0)
                        }
                        // The old path, to compare against.
                        step.startsWith("input=") -> "input" to Remote(session).press(RemoteKey.valueOf(step.removePrefix("input=")))
                        else -> "helper" to remote.press(RemoteKey.valueOf(step))
                    }
                    println("$what  ${(System.nanoTime() - start) / 1_000_000} ms  ${if (ok) "ok" else "FAILED"}  $step")
                }
            }
            session.close()
        }
        else -> println("usage: scan | stats <host> [port] | pair <host> <port> <code> | remote <host> <port> <helper.jar> <key>...")
    }
}
