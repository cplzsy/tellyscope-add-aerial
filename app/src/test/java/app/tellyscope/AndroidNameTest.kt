// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.ui.androidName
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNameTest {
    @Test fun everyListSdkHasAName() {
        val names = mapOf(
            21 to "Android 5.0", 22 to "Android 5.1", 23 to "Android 6", 24 to "Android 7", 25 to "Android 7.1",
            26 to "Android 8", 27 to "Android 8.1", 28 to "Android 9", 30 to "Android 11", 32 to "Android 12L", 34 to "Android 14",
        )
        names.forEach { (sdk, name) -> assertEquals(name, androidName(sdk)) }
        assertEquals("API 20", androidName(20))
    }
}
