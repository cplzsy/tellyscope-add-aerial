// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The ticking clock, from [TellyscopeViewModel.now]. It's a [State] so that only the composable
 * that reads it redraws each second, not the whole page. Null in screenshots and tests.
 */
val LocalNow = staticCompositionLocalOf<State<Long>?> { null }

/** Now, for "2 min ago" and countdowns; read it inside the item that shows the time. Falls back to [UiState.now]. */
@Composable
@ReadOnlyComposable
fun UiState.clock(): Long = LocalNow.current?.value ?: now
