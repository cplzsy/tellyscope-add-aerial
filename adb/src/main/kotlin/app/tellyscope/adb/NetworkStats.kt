// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/** Who the TV is talking to, and how much each uid has sent and received since boot. */
class NetworkStats(private val shell: AdbShell) {
    /** Live connections to somewhere off the TV; listeners and finished ones are left out. */
    suspend fun connections(): List<Parsers.TcpSocket> =
        Parsers.tcpSockets(shell.shell(Commands.SOCKETS).output).filter { it.open }

    /** Bytes per uid; empty when the TV reports neither source. */
    suspend fun traffic(): Map<Int, Parsers.Traffic> {
        val (netstats, qtaguid) = Commands.split(shell.shell(Commands.TRAFFIC).output, 2)
        return Parsers.netstatsTraffic(netstats).ifEmpty { Parsers.qtaguidTraffic(qtaguid) }
    }
}
