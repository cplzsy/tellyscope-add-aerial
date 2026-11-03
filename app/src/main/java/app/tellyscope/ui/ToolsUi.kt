// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tellyscope.R
import app.tellyscope.data.AdbPrivilegeReport
import app.tellyscope.data.NasShareFile
import app.tellyscope.data.PrivilegeCommandResult
import app.tellyscope.data.PrivilegeCommandState
import app.tellyscope.data.PrivilegeStep
import app.tellyscope.data.PrivilegeStepResult
import app.tellyscope.data.ReleaseAsset
import app.tellyscope.data.UiText
import app.tellyscope.data.resolve

/** 在线下载的落地方式：安装 APK，或仅推送到设备目录。 */
enum class DownloadMode { InstallApk, PushFile }

/** Aerial 安装的来源。 */
enum class AerialSourceKind { Nas, Github }

/** 一个可选的 Aerial 安装包：NAS 定制版（飞牛 / File Browser）或 GitHub 官方版。 */
sealed interface AerialFileOption {
    data class Nas(val file: NasShareFile, val token: String, val cookie: String) : AerialFileOption
    data class FileBrowser(val file: NasShareFile) : AerialFileOption
    data class Github(val asset: ReleaseAsset) : AerialFileOption
    val fileName: String
        get() = when (this) {
            is Nas -> file.fileName
            is FileBrowser -> file.fileName
            is Github -> asset.fileName
        }
}

/** 概览页「实用工具」块的状态：在线下载、ADB 提权、Aerial 安装。 */
data class ToolsUi(
    // 在线下载
    val downloadMode: DownloadMode = DownloadMode.InstallApk,
    val downloadUrl: String = "",
    val downloadError: UiText? = null,
    val downloadDialogVisible: Boolean = false,
    val downloadBusy: Boolean = false,
    val downloadProgress: Float? = null,
    val downloadText: UiText? = null,
    val downloadDone: Boolean = false,
    // 常用任务（GitHub Release 选择）
    val releaseDialogVisible: Boolean = false,
    val releaseLoading: Boolean = false,
    val releaseAssets: List<ReleaseAsset> = emptyList(),
    val releaseError: UiText? = null,
    // ADB 提权
    val privilegeDialogVisible: Boolean = false,
    val privilegeKeepAdb: Boolean = true,
    val privilegeBusy: Boolean = false,
    val privilegeReport: AdbPrivilegeReport? = null,
    // Aerial 安装
    val aerialDialogVisible: Boolean = false,
    val aerialLoading: Boolean = false,
    val aerialError: UiText? = null,
    val aerialConfirm: AerialSourceKind? = null,
    val aerialFilesDialogVisible: Boolean = false,
    val aerialFiles: List<AerialFileOption> = emptyList(),
    val aerialBusy: Boolean = false,
    val aerialText: UiText? = null,
    val aerialDone: Boolean = false,
) {
    val downloadStartEnabled: Boolean get() = downloadUrl.isNotBlank() && !downloadBusy

    companion object {
        const val FILE_BROWSER_SHARE_URL = "http://www.890720.xyz:18907/share/XvVjA_os"
        const val NAS_SHARE_URL = "http://192.168.5.218:5666/s/41412d18807c4e8499"
        const val GITHUB_RELEASE_URL = "https://github.com/pengchujin/aerial-tv/releases/latest"
        const val FAVORITE_TASK_URL = "https://github.com/jackherex/duanjuzhijia-down-tv/releases/latest"
        const val HGV_TASK_URL = "https://github.com/pan8664716/HGTV/releases/latest"
        /** 目标应用包名：签名冲突时先卸载它再重装。 */
        const val AERIAL_PACKAGE = "app.orchard.tv"
    }
}

/** 概览页工具块的事件回调。 */
data class ToolsActions(
    val openDownload: () -> Unit = {},
    val downloadMode: (DownloadMode) -> Unit = {},
    val downloadUrl: (String) -> Unit = {},
    val downloadStart: () -> Unit = {},
    val downloadCancel: () -> Unit = {},
    val downloadDismiss: () -> Unit = {},
    val favoriteTask: () -> Unit = {},
    val hgvTask: () -> Unit = {},
    val releaseDismiss: () -> Unit = {},
    val releaseSelect: (ReleaseAsset) -> Unit = {},
    val privilegeClick: () -> Unit = {},
    val privilegeDismiss: () -> Unit = {},
    val privilegeKeepAdb: (Boolean) -> Unit = {},
    val privilegeConfirm: () -> Unit = {},
    val privilegeResultDismiss: () -> Unit = {},
    val aerialClick: () -> Unit = {},
    val aerialSourceDismiss: () -> Unit = {},
    val aerialNas: () -> Unit = {},
    val aerialGithub: () -> Unit = {},
    val aerialConfirmAccept: () -> Unit = {},
    val aerialConfirmDismiss: () -> Unit = {},
    val aerialFilesDismiss: () -> Unit = {},
    val aerialSelect: (AerialFileOption) -> Unit = {},
    val aerialStatusDismiss: () -> Unit = {},
)

// ---------- 概览页功能块与各对话框 ----------

@Composable
fun ToolsCard(ui: ToolsUi, actions: ToolsActions) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            ToolsEntry(Icons.Filled.Send, stringResource(R.string.tools_download_entry), actions.openDownload)
            ToolsEntry(Icons.Filled.Lock, stringResource(R.string.tools_privilege_entry), actions.privilegeClick)
            ToolsEntry(Icons.Filled.Home, stringResource(R.string.tools_aerial_entry), actions.aerialClick)
        }
    }
}

@Composable
private fun ToolsEntry(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 在线下载对话框：模式、链接、常用任务与进度。 */
@Composable
fun OnlineDownloadDialog(ui: ToolsUi, actions: ToolsActions) {
    AlertDialog(
        onDismissRequest = actions.downloadDismiss,
        title = { Text(stringResource(R.string.tools_download_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DownloadMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = ui.downloadMode == mode,
                            onClick = { actions.downloadMode(mode) },
                            enabled = !ui.downloadBusy,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = DownloadMode.entries.size),
                        ) {
                            Text(stringResource(if (mode == DownloadMode.InstallApk) R.string.tools_download_mode_install else R.string.tools_download_mode_push))
                        }
                    }
                }
                OutlinedTextField(
                    value = ui.downloadUrl,
                    onValueChange = actions.downloadUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tools_download_url)) },
                    singleLine = true,
                    isError = ui.downloadError != null,
                    supportingText = ui.downloadError?.let { { Text(it.resolve()) } },
                    enabled = !ui.downloadBusy,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = actions.favoriteTask, enabled = !ui.downloadBusy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tools_download_favorite_task))
                    }
                    OutlinedButton(onClick = actions.hgvTask, enabled = !ui.downloadBusy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tools_download_hgv_task))
                    }
                }
                ui.downloadText?.let { text ->
                    Text(
                        text = text.resolve(),
                        color = when {
                            ui.downloadBusy -> MaterialTheme.colorScheme.onSurfaceVariant
                            ui.downloadDone -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (ui.downloadBusy) {
                    LinearProgressIndicator(progress = { ui.downloadProgress ?: 0f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (ui.downloadBusy) {
                TextButton(onClick = actions.downloadCancel) { Text(stringResource(R.string.tools_download_cancel)) }
            } else {
                TextButton(onClick = actions.downloadStart, enabled = ui.downloadStartEnabled) { Text(stringResource(R.string.tools_download_start)) }
            }
        },
        dismissButton = {
            if (!ui.downloadBusy) {
                TextButton(onClick = actions.downloadDismiss) { Text(stringResource(R.string.tools_dismiss)) }
            }
        },
    )
}

/** GitHub Release 的 APK 选择：常用任务的下载来源。 */
@Composable
fun ReleaseApkDialog(ui: ToolsUi, actions: ToolsActions) {
    AlertDialog(
        onDismissRequest = actions.releaseDismiss,
        title = { Text(stringResource(R.string.tools_release_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    ui.releaseLoading ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(stringResource(R.string.tools_release_loading))
                        }
                    ui.releaseError != null ->
                        Text(ui.releaseError!!.resolve(), color = MaterialTheme.colorScheme.error)
                    ui.releaseAssets.isEmpty() -> Text(stringResource(R.string.tools_release_empty))
                    else -> ui.releaseAssets.forEach { asset ->
                        OutlinedButton(
                            onClick = { actions.releaseSelect(asset) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(releaseAssetLabel(asset.fileName), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.releaseDismiss) { Text(stringResource(R.string.tools_dismiss)) }
        },
    )
}

/** 选中的安装包按 CPU 提示机型，其余原样显示。 */
private fun releaseAssetLabel(fileName: String): String = when {
    fileName.contains("arm64-v8a", ignoreCase = true) -> "64位，客厅电视安装"
    fileName.contains("armeabi-v7a", ignoreCase = true) -> "32位，主卧电视安装"
    else -> fileName
}

/** ADB 提权确认弹窗。 */
@Composable
fun PrivilegeConfirmDialog(ui: ToolsUi, actions: ToolsActions) {
    AlertDialog(
        onDismissRequest = actions.privilegeDismiss,
        title = { Text(stringResource(R.string.tools_privilege_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tools_privilege_message), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tools_privilege_keep_adb), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.tools_privilege_keep_adb_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = ui.privilegeKeepAdb, onCheckedChange = actions.privilegeKeepAdb)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.privilegeConfirm) { Text(stringResource(R.string.tools_privilege_start)) }
        },
        dismissButton = {
            TextButton(onClick = actions.privilegeDismiss) { Text(stringResource(R.string.tools_dismiss)) }
        },
    )
}

/** ADB 提权执行中：命令逐条运行，此时不能关闭，跑完自动出结果。 */
@Composable
fun PrivilegeRunningDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.tools_privilege_title)) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.tools_privilege_running), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {},
    )
}

/** ADB 提权执行结果：每步的命令与成败，滚动查看。 */
@Composable
fun PrivilegeResultDialog(report: AdbPrivilegeReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.tools_privilege_result_title))
                Text(
                    text = stringResource(R.string.tools_privilege_summary, report.succeededCount, report.failedCount, report.skippedCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                report.steps.forEach { step -> PrivilegeStepBlock(step) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tools_privilege_close)) }
        },
    )
}

@Composable
private fun PrivilegeStepBlock(step: PrivilegeStepResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(privilegeStepTitle(step.step)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        step.skippedReason?.let { reason ->
            Text(reason.resolve(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        step.commands.forEach { command -> PrivilegeCommandRow(command) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

@Composable
private fun PrivilegeCommandRow(command: PrivilegeCommandResult) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (command.state) {
                PrivilegeCommandState.SUCCESS -> stringResource(R.string.tools_privilege_ok)
                PrivilegeCommandState.FAILED -> stringResource(R.string.tools_privilege_failed)
                PrivilegeCommandState.SKIPPED -> stringResource(R.string.tools_privilege_skipped)
            },
            color = when (command.state) {
                PrivilegeCommandState.SUCCESS -> MaterialTheme.colorScheme.primary
                PrivilegeCommandState.FAILED -> MaterialTheme.colorScheme.error
                PrivilegeCommandState.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelSmall,
        )
        Column(Modifier.weight(1f)) {
            Text(command.command, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (command.output.isNotBlank()) {
                Text(command.output, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun privilegeStepTitle(step: PrivilegeStep): Int = when (step) {
    PrivilegeStep.GrantPermissions -> R.string.tools_privilege_step_grant
    PrivilegeStep.CompileOptimize -> R.string.tools_privilege_step_compile
    PrivilegeStep.Screensaver -> R.string.tools_privilege_step_screensaver
    PrivilegeStep.KeepAdb -> R.string.tools_privilege_step_keep_adb
    PrivilegeStep.LaunchAerial -> R.string.tools_privilege_step_launch
    PrivilegeStep.DefaultHome -> R.string.tools_privilege_step_home
    PrivilegeStep.HomeGuard -> R.string.tools_privilege_step_guard
}

/** Aerial 来源选择弹窗。 */
@Composable
fun AerialSourceDialog(ui: ToolsUi, actions: ToolsActions) {
    AlertDialog(
        onDismissRequest = actions.aerialSourceDismiss,
        title = { Text(stringResource(R.string.tools_aerial_source_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = actions.aerialNas, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_aerial_source_nas))
                }
                OutlinedButton(onClick = actions.aerialGithub, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_aerial_source_github))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = actions.aerialSourceDismiss) { Text(stringResource(R.string.tools_dismiss)) }
        },
    )
}

/** 选定来源后的签名须知确认弹窗。 */
@Composable
fun AerialSourceConfirmDialog(kind: AerialSourceKind, actions: ToolsActions) {
    AlertDialog(
        onDismissRequest = actions.aerialConfirmDismiss,
        title = { Text(stringResource(R.string.tools_aerial_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    if (kind == AerialSourceKind.Nas) R.string.tools_aerial_confirm_nas else R.string.tools_aerial_confirm_github
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = actions.aerialConfirmAccept) { Text(stringResource(R.string.tools_aerial_confirm_accept)) }
        },
        dismissButton = {
            TextButton(onClick = actions.aerialConfirmDismiss) { Text(stringResource(R.string.tools_dismiss)) }
        },
    )
}

/** 多个候选 APK 时的文件选择弹窗。 */
@Composable
fun AerialFileSelectDialog(ui: ToolsUi, actions: ToolsActions) {
    AlertDialog(
        onDismissRequest = actions.aerialFilesDismiss,
        title = { Text(stringResource(R.string.tools_aerial_files_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ui.aerialFiles.forEach { file ->
                    OutlinedButton(
                        onClick = { actions.aerialSelect(file) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = actions.aerialFilesDismiss) { Text(stringResource(R.string.tools_dismiss)) }
        },
    )
}

/**
 * Aerial 安装的执行状态：加载文件列表、下载、安装，以及成功或失败。
 * 执行中（busy）不可关闭，结果状态通过 [ToolsActions.aerialStatusDismiss] 关闭。
 */
@Composable
fun AerialStatusDialog(ui: ToolsUi, actions: ToolsActions) {
    val busy = ui.aerialLoading || ui.aerialBusy
    val done = ui.aerialDone
    val message = ui.aerialError?.resolve() ?: ui.aerialText?.resolve().orEmpty()
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.tools_aerial_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        if (message.isNotBlank()) {
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (message.isNotBlank()) {
                    Text(
                        message,
                        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (!busy) {
                TextButton(onClick = actions.aerialStatusDismiss) { Text(stringResource(R.string.tools_dismiss)) }
            }
        },
    )
}
