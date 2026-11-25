package com.example.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppPreferences
import com.example.data.remote.RemoteConfigState
import com.example.domain.model.HeroProfile
import com.example.domain.model.StagedBundlePreview
import com.example.localization.localizedShizukuReason
import com.example.shizuku.ShizukuConnectionState
import com.example.ui.theme.LocalSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportZipConfirmationSheet(
    preview: StagedBundlePreview,
    heroes: List<HeroProfile>,
    onConfirm: (heroId: String, skinName: String, tierTag: String, version: String, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedHeroId by remember(preview.scriptId) {
        mutableStateOf(
            if (heroes.any { it.id == preview.suggestedHeroId }) preview.suggestedHeroId
            else heroes.firstOrNull()?.id ?: preview.suggestedHeroId
        )
    }
    var skinName by remember(preview.scriptId) { mutableStateOf(preview.suggestedSkinName) }
    var tierTag by remember(preview.scriptId) { mutableStateOf(preview.suggestedTierTag) }
    var version by remember(preview.scriptId) { mutableStateOf(preview.suggestedVersion) }
    var notes by remember(preview.scriptId) { mutableStateOf(preview.suggestedNotes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = stringResource(R.string.stage_imported_zip),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(
                            R.string.files_size,
                            preview.relativeTargetPaths.size,
                            Formatter.formatShortFileSize(context, preview.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.assign_target_hero),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                heroes.forEach { hero ->
                    FilterChip(
                        selected = hero.id == selectedHeroId,
                        onClick = { selectedHeroId = hero.id },
                        label = { Text(hero.name) },
                        modifier = Modifier.testTag("import_hero_chip_${hero.id}"),
                    )
                }
            }

            OutlinedTextField(
                value = skinName,
                onValueChange = { skinName = it },
                label = { Text(stringResource(R.string.skin_script_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_skin_name_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = tierTag,
                    onValueChange = { tierTag = it },
                    label = { Text(stringResource(R.string.tier_tag)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text(stringResource(R.string.version)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.compatibility_notes)) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.verified_target_paths),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    preview.relativeTargetPaths.take(5).forEach { rel ->
                        Text(
                            text = "• $rel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (preview.relativeTargetPaths.size > 5) {
                        Text(
                            text = stringResource(R.string.more_files, preview.relativeTargetPaths.size - 5),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onConfirm(selectedHeroId, skinName, tierTag, version, notes) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_import_bundle_button"),
                ) {
                    Text(stringResource(R.string.action_stage_script))
                }
            }
        }
    }
}

@Composable
fun ShizukuGuideDialog(
    state: ShizukuConnectionState,
    onOpenShizukuApp: () -> Unit,
    onWatchTutorial: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when (state) {
        ShizukuConnectionState.NotInstalled ->
            stringResource(R.string.shizuku_guide_not_installed)
        ShizukuConnectionState.NotRunning ->
            stringResource(R.string.shizuku_guide_not_running)
        is ShizukuConnectionState.PermissionDenied ->
            stringResource(R.string.shizuku_guide_permission)
        ShizukuConnectionState.PermissionGranted ->
            stringResource(R.string.shizuku_permission_granted_message)
        is ShizukuConnectionState.ServiceConnected ->
            stringResource(R.string.shizuku_connected_message, state.processLabel)
        is ShizukuConnectionState.ServiceDisconnected ->
            stringResource(R.string.shizuku_guide_disconnected)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.shizuku_guide_title),
                modifier = Modifier.testTag("shizuku_guide_title"),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("shizuku_guide_message"),
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.shizuku_how_to_start),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.shizuku_how_to_start_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onWatchTutorial,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("shizuku_watch_tutorial_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_watch_tutorial))
                }
                Text(
                    text = stringResource(R.string.shizuku_tutorial_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("shizuku_tutorial_credit"),
                )
                if (state == ShizukuConnectionState.NotInstalled) {
                    Text(
                        text = stringResource(R.string.msg_shizuku_not_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("shizuku_not_installed_fallback"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenShizukuApp,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("shizuku_open_app_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_open_shizuku))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("shizuku_guide_refresh"),
                ) {
                    Text(stringResource(R.string.action_refresh))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("shizuku_guide_close"),
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageAndDiagnosticsSheet(
    shizukuState: ShizukuConnectionState,
    remoteConfigState: RemoteConfigState,
    targetPackage: String,
    isTargetPackageInstalled: Boolean,
    totalBackupBytes: Long,
    disclaimerDismissed: Boolean,
    onRefreshShizuku: () -> Unit,
    onRefreshRemoteConfig: () -> Unit,
    onRequestPermission: () -> Unit,
    onBindUserService: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onUpdateTargetPackage: (String) -> Unit,
    onRestoreDisclaimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val semantic = LocalSemanticColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var packageInput by remember(targetPackage) { mutableStateOf(targetPackage) }
    val isPackageValid = AppPreferences.isValidPackageName(packageInput.trim())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.shizuku_target_package),
                style = MaterialTheme.typography.titleLarge,
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = if (shizukuState.isReadyForIo) semantic.active else semantic.warning,
                            )
                            Text(
                                text = stringResource(R.string.shizuku_aidl_service),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        ShizukuStatusPill(
                            state = shizukuState,
                            onClick = onRefreshShizuku,
                        )
                    }

                    Text(
                        text = when (shizukuState) {
                            ShizukuConnectionState.NotInstalled ->
                                stringResource(R.string.shizuku_not_installed_message)
                            ShizukuConnectionState.NotRunning ->
                                stringResource(R.string.shizuku_not_running_message)
                            is ShizukuConnectionState.PermissionDenied ->
                                stringResource(R.string.shizuku_permission_denied_message)
                            ShizukuConnectionState.PermissionGranted ->
                                stringResource(R.string.shizuku_permission_granted_message)
                            is ShizukuConnectionState.ServiceConnected ->
                                stringResource(
                                    R.string.shizuku_connected_message,
                                    shizukuState.processLabel,
                                )
                            is ShizukuConnectionState.ServiceDisconnected -> {
                                val suffix = if (shizukuState.reason != null) {
                                    stringResource(
                                        R.string.shizuku_disconnected_reason,
                                        shizukuState.reason.localizedShizukuReason(context),
                                    )
                                } else {
                                    "."
                                }
                                stringResource(R.string.shizuku_disconnected_message, suffix)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (shizukuState) {
                            is ShizukuConnectionState.PermissionDenied -> {
                                Button(
                                    onClick = onRequestPermission,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("sheet_request_shizuku_permission"),
                                ) {
                                    Text(stringResource(R.string.action_grant_permission))
                                }
                            }
                            ShizukuConnectionState.PermissionGranted,
                            is ShizukuConnectionState.ServiceDisconnected,
                            -> {
                                Button(
                                    onClick = onBindUserService,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("sheet_bind_shizuku_service"),
                                ) {
                                    Text(stringResource(R.string.action_bind_service))
                                }
                            }
                            ShizukuConnectionState.NotRunning -> {
                                Button(
                                    onClick = onOpenShizukuApp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Launch,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_open_shizuku))
                                }
                            }
                            else -> Unit
                        }

                        OutlinedButton(
                            onClick = onRefreshShizuku,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sheet_refresh_shizuku"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_refresh))
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.target_package_override),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.target_package_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = packageInput.trim() == AppPreferences.DEFAULT_TARGET_PACKAGE,
                        onClick = {
                            packageInput = AppPreferences.DEFAULT_TARGET_PACKAGE
                            onUpdateTargetPackage(AppPreferences.DEFAULT_TARGET_PACKAGE)
                        },
                        label = { Text(stringResource(R.string.global_package, AppPreferences.DEFAULT_TARGET_PACKAGE)) },
                        modifier = Modifier.testTag("preset_global_package_chip"),
                    )
                }

                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    label = { Text(stringResource(R.string.custom_target_package)) },
                    isError = !isPackageValid,
                    supportingText = {
                        Text(
                            text = if (!isPackageValid) {
                                stringResource(R.string.invalid_package)
                            } else if (isTargetPackageInstalled) {
                                stringResource(R.string.package_detected)
                            } else {
                                stringResource(R.string.package_not_detected)
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_package_input"),
                )

                Button(
                    onClick = { onUpdateTargetPackage(packageInput.trim()) },
                    enabled = isPackageValid && packageInput.trim() != targetPackage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("apply_package_override_button"),
                ) {
                    Text(stringResource(R.string.action_save_target_package))
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.app_private_backup),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(
                            R.string.stored_size,
                            Formatter.formatShortFileSize(context, totalBackupBytes),
                        ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (disclaimerDismissed) {
                    TextButton(onClick = onRestoreDisclaimer) {
                        Text(stringResource(R.string.action_show_banner))
                    }
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.github_remote_config),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedButton(
                        onClick = onRefreshRemoteConfig,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_remote_config_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_sync_config))
                    }
                }
                Text(
                    text = when (remoteConfigState) {
                        RemoteConfigState.Loading -> stringResource(R.string.fetching_remote)
                        is RemoteConfigState.Ready -> {
                            val cfg = remoteConfigState.config
                            stringResource(
                                R.string.synced_status,
                                cfg.rawRootType,
                                cfg.availableHeroes.size,
                                cfg.skinEntries.size,
                                stringResource(
                                    if (cfg.maintenanceMode) R.string.boolean_true
                                    else R.string.boolean_false
                                ),
                            )
                        }
                        is RemoteConfigState.Failed ->
                            stringResource(R.string.offline_fallback, remoteConfigState.errorMessage)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ConfirmClearBackupDialog(
    skinName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = LocalSemanticColors.current.warning,
            )
        },
        title = { Text(stringResource(R.string.clear_backup_title)) },
        text = {
            Text(
                text = stringResource(R.string.clear_backup_message, skinName),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_clear_backup_dialog_button"),
            ) {
                Text(stringResource(R.string.action_clear_backup))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
