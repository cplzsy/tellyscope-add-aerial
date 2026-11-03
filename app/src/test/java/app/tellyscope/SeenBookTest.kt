// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.SeenBook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SeenBookTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `when History was last opened is kept per TV across restarts`() {
        val file = tmp.root.resolve("history-seen.properties")
        SeenBook(file).set("tv-a", 100L)
        SeenBook(file).set("tv-b", 200L)
        SeenBook(file).set("tv-a", 300L)
        assertEquals(mapOf("tv-a" to 300L, "tv-b" to 200L), SeenBook(file).seen())
    }

    @Test fun `a TV whose History was never opened isn't listed`() {
        assertEquals(emptyMap<String, Long>(), SeenBook(tmp.root.resolve("none.properties")).seen())
    }
}
