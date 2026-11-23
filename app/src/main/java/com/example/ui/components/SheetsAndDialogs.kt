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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppPreferences
import com.example.data.remote.RemoteConfigState
import com.example.domain.model.HeroProfile
import com.example.domain.model.StagedBundlePreview
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
            else heroes.first().id
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
                        text = "Stage Imported .zip Bundle",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${preview.relativeTargetPaths.size} file(s) • ${Formatter.formatShortFileSize(context, preview.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Assign Target Hero",
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
                label = { Text("Skin Script Name") },
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
                    label = { Text("Tier / Tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Version") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Compatibility Notes") },
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
                        text = "Verified Target Relative Paths",
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
                            text = "+${preview.relativeTargetPaths.size - 5} more file(s)",
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
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(selectedHeroId, skinName, tierTag, version, notes) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_import_bundle_button"),
                ) {
                    Text("Stage Script")
                }
            }
        }
    }
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
                text = "Shizuku & Target Package",
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
                                text = "Shizuku AIDL UserService",
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
                                "Shizuku (moe.shizuku.privileged.api) was not found. Install Shizuku to manage /Android/data/ on Android 11–15+."
                            ShizukuConnectionState.NotRunning ->
                                "Shizuku is installed, but its background binder is not running. Start Shizuku via Wireless Debugging or ADB first."
                            is ShizukuConnectionState.PermissionDenied ->
                                "Shizuku is running, but permission for Mythic Script is denied. Grant access to bind the UID 2000 FileBridge service."
                            ShizukuConnectionState.PermissionGranted ->
                                "Permission granted. Binding FileBridgeUserService via Shizuku…"
                            is ShizukuConnectionState.ServiceConnected ->
                                "Privileged FileBridgeUserService is connected (${shizukuState.processLabel}). Restricted /Android/data/ I/O is active."
                            is ShizukuConnectionState.ServiceDisconnected ->
                                "UserService disconnected${shizukuState.reason?.let { ": $it" } ?: "."}"
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
                                    Text("Grant Permission")
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
                                    Text("Bind Service")
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
                                    Text("Open Shizuku")
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
                            Text("Refresh")
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Game Package Override",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Files are backed up from and injected into /storage/emulated/0/Android/data/<package>/",
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
                        label = { Text("Global (${AppPreferences.DEFAULT_TARGET_PACKAGE})") },
                        modifier = Modifier.testTag("preset_global_package_chip"),
                    )
                }

                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    label = { Text("Custom Target Package") },
                    isError = !isPackageValid,
                    supportingText = {
                        Text(
                            text = if (!isPackageValid) {
                                "Enter a valid Android application ID (e.g. com.mobile.legends)"
                            } else if (isTargetPackageInstalled) {
                                "Package detected on device"
                            } else {
                                "Package not detected via PackageManager (custom path override still allowed)"
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
                    Text("Save Target Package")
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
                            text = "App-Private Backup Storage",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = Formatter.formatShortFileSize(context, totalBackupBytes) + " stored",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (disclaimerDismissed) {
                    TextButton(onClick = onRestoreDisclaimer) {
                        Text("Show Banner")
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
                        text = "GitHub Remote Configuration",
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
                        Text("Sync Config")
                    }
                }
                Text(
                    text = when (remoteConfigState) {
                        RemoteConfigState.Loading -> "Fetching latest remote flags from GitHub Raw…"
                        is RemoteConfigState.Ready -> {
                            val cfg = remoteConfigState.config
                            "Synced (${cfg.rawRootType}) • ${cfg.availableHeroes.size} heroes, ${cfg.skinEntries.size} skins • Maintenance=${cfg.maintenanceMode}"
                        }
                        is RemoteConfigState.Failed ->
                            "Offline fallback (${remoteConfigState.errorMessage})"
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
        title = { Text("Clear Saved Backup?") },
        text = {
            Text(
                text = "This removes the app-private backup files for '$skinName'. Make sure your original game assets are already intact before deleting the backup.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_clear_backup_dialog_button"),
            ) {
                Text("Clear Backup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
