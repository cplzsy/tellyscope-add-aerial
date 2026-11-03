// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.NameBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NameBookTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun survivesARestart() {
        val file = tmp.root.resolve("names.tsv")
        NameBook(file).set("sn:ABC", "Living room")
        assertEquals("Living room", NameBook(file)["sn:ABC"])
    }

    @Test fun blankClearsTheName() {
        val file = tmp.root.resolve("names.tsv")
        val book = NameBook(file)
        book.set("sn:ABC", "Living room")
        book.set("sn:ABC", "   ")
        assertNull(book["sn:ABC"])
        assertNull(NameBook(file)["sn:ABC"])
    }

    @Test fun tabsAndNewlinesCannotBreakTheFile() {
        val file = tmp.root.resolve("names.tsv")
        NameBook(file).set("sn:ABC", "Living\troom\nTV")
        assertEquals("Living room TV", NameBook(file)["sn:ABC"])
    }

    @Test fun longNamesAreCapped() {
        val book = NameBook()
        book.set("sn:ABC", "x".repeat(100))
        assertEquals(NameBook.MAX_LENGTH, book["sn:ABC"]!!.length)
    }

    @Test fun rekeyMovesTheName() {
        val file = tmp.root.resolve("names.tsv")
        NameBook(file).rekey("192.168.1.23", "sn:ABC") // nothing to move
        val book = NameBook(file)
        book.set("192.168.1.23", "Projector")
        book.rekey("192.168.1.23", "sn:ABC")
        assertEquals("Projector", NameBook(file)["sn:ABC"])
        assertNull(book["192.168.1.23"])
    }

    @Test fun rekeyKeepsTheExistingName() {
        // The TV was named under its real key before; a name typed while it read as an address loses.
        val book = NameBook()
        book.set("sn:ABC", "Bedroom")
        book.set("192.168.1.23", "Projector")
        book.rekey("192.168.1.23", "sn:ABC")
        assertEquals("Bedroom", book["sn:ABC"])
    }
}
