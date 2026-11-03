// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.helper.BannerColors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BannerColorsTest {
    /** A banner of [n] pixels: mostly [bg], with a run of each of [marks]. */
    private fun banner(bg: Int, vararg marks: Pair<Int, Int>, n: Int = 1000): IntArray {
        val px = IntArray(n) { bg }
        var i = 0
        for ((color, count) in marks) repeat(count) { px[i++] = color }
        return px
    }

    @Test fun backgroundIsTheMainColourAndTheLetterTheVividOne() {
        // Spotify: near-black, a green logo, white wordmark.
        val (bg, accent) = BannerColors.pick(banner(0xFF121212.toInt(), 0xFFFFFFFF.toInt() to 80, 0xFF1ED760.toInt() to 40))!!
        assertEquals(0xFF121212.toInt(), bg)
        assertEquals(0xFF1ED760.toInt(), accent)
    }

    @Test fun aSpeckIsNotTheAccent() {
        // Two stray red pixels in a thousand don't make the letter red.
        val (_, accent) = BannerColors.pick(banner(0xFF202020.toInt(), 0xFFFF0000.toInt() to 2, 0xFFF0F0F0.toInt() to 100))!!
        assertEquals(0xFFF0F0F0.toInt(), accent)
    }

    @Test fun anAccentTooCloseToTheBackgroundBecomesWhiteOrBlack() {
        // Mid blue on a slightly darker blue: under 3:1, so white on dark.
        val (bg, accent) = BannerColors.pick(banner(0xFF1A237E.toInt(), 0xFF3949AB.toInt() to 100))!!
        assertEquals(0xFF1A237E.toInt(), bg)
        assertEquals(0xFFFFFFFF.toInt(), accent)
        // And black on a pale background.
        assertEquals(0xFF000000.toInt(), BannerColors.pick(banner(0xFFFFF59D.toInt(), 0xFFFFEE58.toInt() to 100))!![1])
    }

    @Test fun aSingleColourBannerStillGetsAReadableLetter() {
        assertArrayEquals(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), BannerColors.pick(banner(0xFF000000.toInt())))
    }

    @Test fun seeThroughPixelsDontCount() {
        // Transparent padding around an orange logo: the tile is orange, not see-through.
        val px = banner(0x00000000, 0xFFFF6D00.toInt() to 300, 0xFF000000.toInt() to 50)
        assertEquals(0xFFFF6D00.toInt(), BannerColors.pick(px)!![0])
        assertNull(BannerColors.pick(IntArray(10)))
    }
}
