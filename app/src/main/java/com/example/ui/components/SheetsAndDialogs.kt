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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
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
        containerColor = LiquidGlass.SheetSurface,
        scrimColor = LiquidGlass.Scrim,
        tonalElevation = 0.dp,
        dragHandle = { GlassSheetHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    tint = LiquidGlass.SheetSurface,
                    elevation = 16.dp,
                    opacity = 0.97f,
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderZip,
                    contentDescription = null,
                    tint = LiquidGlass.TealDeep,
                )
                Column {
                    Text(
                        text = stringResource(R.string.stage_imported_zip),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LiquidGlass.Ink,
                    )
                    Text(
                        text = stringResource(
                            R.string.files_size,
                            preview.relativeTargetPaths.size,
                            Formatter.formatShortFileSize(context, preview.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = LiquidGlass.Muted,
                    )
                }
            }

            Text(
                text = stringResource(R.string.assign_target_hero),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = LiquidGlass.Ink,
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
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = LiquidGlass.SurfaceMuted,
                            labelColor = LiquidGlass.Ink,
                            selectedContainerColor = LiquidGlass.Teal,
                            selectedLabelColor = Color.White,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = hero.id == selectedHeroId,
                            borderColor = LiquidGlass.Hairline,
                            selectedBorderColor = Color.White.copy(alpha = 0.72f),
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("import_hero_chip_${hero.id}"),
                    )
                }
            }

            OutlinedTextField(
                value = skinName,
                onValueChange = { skinName = it },
                label = { Text(stringResource(R.string.skin_script_name)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = glassTextFieldColors(),
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
                    shape = RoundedCornerShape(18.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text(stringResource(R.string.version)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.compatibility_notes)) },
                maxLines = 2,
                shape = RoundedCornerShape(18.dp),
                colors = glassTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        tint = LiquidGlass.SurfaceMuted,
                        elevation = 2.dp,
                        opacity = 0.96f,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.verified_target_paths),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiquidGlass.Ink,
                    )
                    preview.relativeTargetPaths.take(5).forEach { rel ->
                        Text(
                            text = "• $rel",
                            style = MaterialTheme.typography.labelSmall,
                            color = LiquidGlass.Muted,
                        )
                    }
                    if (preview.relativeTargetPaths.size > 5) {
                        Text(
                            text = stringResource(R.string.more_files, preview.relativeTargetPaths.size - 5),
                            style = MaterialTheme.typography.labelSmall,
                            color = LiquidGlass.TealDeep,
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
                GlassPillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    style = GlassButtonStyle.Neutral,
                )
                GlassPillButton(
                    text = stringResource(R.string.action_stage_script),
                    onClick = { onConfirm(selectedHeroId, skinName, tierTag, version, notes) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_import_bundle_button"),
                )
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

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Security,
        iconTint = LiquidGlass.TealDeep,
        title = stringResource(R.string.shizuku_guide_title),
        titleTag = "shizuku_guide_title",
        cardTag = "shizuku_guide_dialog",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LiquidGlass.Muted,
                modifier = Modifier.testTag("shizuku_guide_message"),
            )
            HorizontalDivider(color = LiquidGlass.Hairline)
            Text(
                text = stringResource(R.string.shizuku_how_to_start),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LiquidGlass.Ink,
            )
            Text(
                text = stringResource(R.string.shizuku_how_to_start_body),
                style = MaterialTheme.typography.bodySmall,
                color = LiquidGlass.Muted,
            )
            GlassPillButton(
                text = stringResource(R.string.action_watch_tutorial),
                onClick = onWatchTutorial,
                modifier = Modifier.fillMaxWidth(),
                style = GlassButtonStyle.Neutral,
                icon = Icons.Filled.PlayArrow,
                testTag = "shizuku_watch_tutorial_button",
            )
            Text(
                text = stringResource(R.string.shizuku_tutorial_credit),
                style = MaterialTheme.typography.bodySmall,
                color = LiquidGlass.TealDeep,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("shizuku_tutorial_credit"),
            )
            if (state == ShizukuConnectionState.NotInstalled) {
                Text(
                    text = stringResource(R.string.msg_shizuku_not_installed),
                    style = MaterialTheme.typography.bodySmall,
                    color = LiquidGlass.Red,
                    modifier = Modifier.testTag("shizuku_not_installed_fallback"),
                )
            }
        }
        GlassPillButton(
            text = stringResource(R.string.action_open_shizuku),
            onClick = onOpenShizukuApp,
            modifier = Modifier.fillMaxWidth(),
            style = GlassButtonStyle.Primary,
            icon = Icons.AutoMirrored.Filled.Launch,
            testTag = "shizuku_open_app_button",
        )
        GlassDialogActions(modifier = Modifier.widthIn(max = 320.dp)) {
            GlassPillButton(
                text = stringResource(R.string.action_refresh),
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
                style = GlassButtonStyle.Neutral,
                icon = Icons.Filled.Refresh,
                testTag = "shizuku_guide_refresh",
            )
            GlassPillButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = GlassButtonStyle.Neutral,
                testTag = "shizuku_guide_close",
            )
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
        containerColor = LiquidGlass.SheetSurface,
        scrimColor = LiquidGlass.Scrim,
        tonalElevation = 0.dp,
        dragHandle = { GlassSheetHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    tint = LiquidGlass.SheetSurface,
                    elevation = 16.dp,
                    opacity = 0.97f,
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.shizuku_target_package),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LiquidGlass.Ink,
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(20.dp),
                        tint = LiquidGlass.SurfaceMuted,
                        elevation = 2.dp,
                        opacity = 0.96f,
                    ),
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
                                fontWeight = FontWeight.Bold,
                                color = LiquidGlass.Ink,
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
                        color = LiquidGlass.Muted,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (shizukuState) {
                            is ShizukuConnectionState.PermissionDenied -> {
                                GlassPillButton(
                                    text = stringResource(R.string.action_grant_permission),
                                    onClick = onRequestPermission,
                                    modifier = Modifier.weight(1f),
                                    testTag = "sheet_request_shizuku_permission",
                                )
                            }
                            ShizukuConnectionState.PermissionGranted,
                            is ShizukuConnectionState.ServiceDisconnected,
                            -> {
                                GlassPillButton(
                                    text = stringResource(R.string.action_bind_service),
                                    onClick = onBindUserService,
                                    modifier = Modifier.weight(1f),
                                    testTag = "sheet_bind_shizuku_service",
                                )
                            }
                            ShizukuConnectionState.NotRunning -> {
                                GlassPillButton(
                                    text = stringResource(R.string.action_open_shizuku),
                                    onClick = onOpenShizukuApp,
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.AutoMirrored.Filled.Launch,
                                )
                            }
                            else -> Unit
                        }

                        GlassPillButton(
                            text = stringResource(R.string.action_refresh),
                            onClick = onRefreshShizuku,
                            style = GlassButtonStyle.Neutral,
                            icon = Icons.Filled.Refresh,
                            testTag = "sheet_refresh_shizuku",
                        )
                    }
                }
            }

            HorizontalDivider(color = LiquidGlass.Hairline)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.target_package_override),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LiquidGlass.Ink,
                )
                Text(
                    text = stringResource(R.string.target_package_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = LiquidGlass.Muted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = packageInput.trim() == AppPreferences.DEFAULT_TARGET_PACKAGE,
                        onClick = {
                            packageInput = AppPreferences.DEFAULT_TARGET_PACKAGE
                            onUpdateTargetPackage(AppPreferences.DEFAULT_TARGET_PACKAGE)
                        },
                        label = { Text(stringResource(R.string.global_package, AppPreferences.DEFAULT_TARGET_PACKAGE)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = LiquidGlass.SurfaceMuted,
                            labelColor = LiquidGlass.Ink,
                            selectedContainerColor = LiquidGlass.Teal,
                            selectedLabelColor = Color.White,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = packageInput.trim() == AppPreferences.DEFAULT_TARGET_PACKAGE,
                            borderColor = LiquidGlass.Hairline,
                            selectedBorderColor = Color.White.copy(alpha = 0.72f),
                        ),
                        shape = RoundedCornerShape(50),
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
                    shape = RoundedCornerShape(18.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_package_input"),
                )

                GlassPillButton(
                    text = stringResource(R.string.action_save_target_package),
                    onClick = { onUpdateTargetPackage(packageInput.trim()) },
                    enabled = isPackageValid && packageInput.trim() != targetPackage,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "apply_package_override_button",
                )
            }

            HorizontalDivider(color = LiquidGlass.Hairline)

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
                        tint = LiquidGlass.TealDeep,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.app_private_backup),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = LiquidGlass.Ink,
                        )
                        Text(
                            text = stringResource(
                            R.string.stored_size,
                            Formatter.formatShortFileSize(context, totalBackupBytes),
                        ),
                            style = MaterialTheme.typography.bodySmall,
                            color = LiquidGlass.Muted,
                        )
                    }
                }
                if (disclaimerDismissed) {
                    GlassPillButton(
                        text = stringResource(R.string.action_show_banner),
                        onClick = onRestoreDisclaimer,
                        style = GlassButtonStyle.Neutral,
                    )
                }
            }

            HorizontalDivider(color = LiquidGlass.Hairline)

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
                        fontWeight = FontWeight.Bold,
                        color = LiquidGlass.Ink,
                    )
                    GlassPillButton(
                        text = stringResource(R.string.action_sync_config),
                        onClick = onRefreshRemoteConfig,
                        style = GlassButtonStyle.Neutral,
                        icon = Icons.Filled.Refresh,
                        testTag = "sync_remote_config_button",
                    )
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
                    color = LiquidGlass.Muted,
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
    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Warning,
        iconTint = LocalSemanticColors.current.warning,
        title = stringResource(R.string.clear_backup_title),
        cardTag = "confirm_clear_backup_dialog",
    ) {
        Text(
            text = stringResource(R.string.clear_backup_message, skinName),
            style = MaterialTheme.typography.bodyMedium,
            color = LiquidGlass.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        GlassDialogActions(modifier = Modifier.widthIn(max = 340.dp)) {
            GlassPillButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = GlassButtonStyle.Neutral,
            )
            GlassPillButton(
                text = stringResource(R.string.action_clear_backup),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                style = GlassButtonStyle.Danger,
                testTag = "confirm_clear_backup_dialog_button",
            )
        }
    }
}
