// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tellyscope.adb.Tweak
import app.tellyscope.data.TvSettings
import app.tellyscope.data.resolve

/**
 * Whole-TV settings and housekeeping. The switches land in History with Undo; clearing
 * caches says up front what it keeps; a screenshot asks where to save it first.
 */
@Composable
fun TweaksBlock(ui: DeviceUi, actions: DeviceActions) {
    val tweaks = ui.tweaks
    if (ui.busy in TWEAK_BUSY) LinearProgressIndicator(Modifier.fillMaxWidth())
    val fast = tweaks?.animationScale == TvSettings.FAST
    SwitchRow(Copy.ANIMATIONS_TITLE.resolve(), Copy.animationsState(tweaks?.animationScale).resolve(), fast, enabled = tweaks != null && ui.busy == null) {
        actions.setFastAnimations(it)
    }
    ButtonRow(Copy.CACHES_TITLE.resolve(), Copy.CACHES_TEXT.resolve(), Copy.CACHES_BUTTON.resolve(), enabled = ui.busy == null, actions.clearCaches)
    ButtonRow(Copy.SCREENSHOT_TITLE.resolve(), Copy.SCREENSHOT_TEXT.resolve(), Copy.SCREENSHOT_BUTTON.resolve(), enabled = ui.busy == null, actions.screenshot)
}

@Composable
private fun SwitchRow(title: String, state: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles, not just the thumb: a bigger target, read as one switch by TalkBack.
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ButtonRow(title: String, text: String, button: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text(button) }
    }
}

/** [DeviceUi.busy] values that belong to this block. */
private val TWEAK_BUSY = Tweak.entries.map { it.name } + listOf(CLEARING, SCREENSHOT)
