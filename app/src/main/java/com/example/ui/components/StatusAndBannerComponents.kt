package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.RemoteConfigState
import com.example.domain.model.ScriptLifecycleState
import com.example.shizuku.ShizukuConnectionState
import com.example.ui.theme.LocalSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionTopAppBar(
    showBackButton: Boolean,
    shizukuState: ShizukuConnectionState,
    targetPackage: String,
    onBackClick: () -> Unit,
    onShizukuStatusClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShowDisclaimerClick: () -> Unit,
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        navigationIcon = {
            if (showBackButton) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("back_to_heroes_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_to_heroes),
                    )
                }
            } else {
                IconButton(
                    onClick = onShowDisclaimerClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_disclaimer_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Script safety notice",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = targetPackage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            ShizukuStatusPill(
                state = shizukuState,
                onClick = onShizukuStatusClick,
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_diagnostics_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings_diagnostics),
                )
            }
        },
    )
}

@Composable
fun ShizukuStatusPill(
    state: ShizukuConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    val (bgColor, fgColor, icon, shortText) = when (state) {
        is ShizukuConnectionState.ServiceConnected -> Quad(
            semantic.activeContainer,
            semantic.active,
            Icons.Filled.CheckCircle,
            "Shizuku UID ${state.uid}",
        )
        ShizukuConnectionState.PermissionGranted -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.Security,
            "Binding…",
        )
        is ShizukuConnectionState.PermissionDenied -> Quad(
            semantic.warningContainer,
            semantic.warning,
            Icons.Filled.Warning,
            "Grant Shizuku",
        )
        ShizukuConnectionState.NotRunning -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.LinkOff,
            "Not Running",
        )
        ShizukuConnectionState.NotInstalled -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.Error,
            "No Shizuku",
        )
        is ShizukuConnectionState.ServiceDisconnected -> Quad(
            semantic.warningContainer,
            semantic.warning,
            Icons.Filled.LinkOff,
            "Reconnect",
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = modifier
            .padding(end = 4.dp)
            .testTag("shizuku_status_pill"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Shizuku connection status: $shortText",
                tint = fgColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = shortText,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun DisclaimerWarningBanner(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("disclaimer_warning_banner"),
            shape = RoundedCornerShape(14.dp),
            color = semantic.warningContainer,
            border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.4f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = semantic.warning,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.disclaimer_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.disclaimer_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onLearnMore,
                            modifier = Modifier.testTag("disclaimer_details_button"),
                        ) {
                            Text("Risk & Backup Rules")
                        }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dismiss_disclaimer_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss safety warning banner",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteMaintenanceBanner(
    remoteConfigState: RemoteConfigState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeConfig = when (remoteConfigState) {
        is RemoteConfigState.Ready -> remoteConfigState.config
        is RemoteConfigState.Failed -> remoteConfigState.lastKnownConfig
        RemoteConfigState.Loading -> null
    }
    val showMaintenance = activeConfig != null && !activeConfig.canInjectRemotely

    AnimatedVisibility(visible = showMaintenance, modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("remote_maintenance_banner"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeConfig?.maintenanceMode == true) "Remote Maintenance Active" else "Injection Disabled Remotely",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = activeConfig?.maintenanceMessage?.ifBlank {
                            "New injections are paused via remote config. Restore Original remains available."
                        } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_remote_config_banner_button"),
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
fun ScriptLifecycleChip(
    state: ScriptLifecycleState,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    val (bg, fg) = when (state) {
        ScriptLifecycleState.Idle ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ScriptLifecycleState.Selected ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ScriptLifecycleState.Downloaded ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        is ScriptLifecycleState.Downloading,
        is ScriptLifecycleState.BackingUp,
        is ScriptLifecycleState.Injecting,
        is ScriptLifecycleState.Restoring,
        -> semantic.warningContainer to semantic.warning
        ScriptLifecycleState.Injected ->
            semantic.activeContainer to semantic.active
        is ScriptLifecycleState.Error ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        is ScriptLifecycleState.Unavailable ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier,
    ) {
        Text(
            text = state.displayLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun DisclaimerDetailsDialog(
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
        title = {
            Text(text = stringResource(R.string.disclaimer_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "1. Unofficial Asset Scripts: Skin scripts swap client-side visual/audio bundles under /Android/data/. They are not endorsed by Moonton.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "2. File Integrity & Patch Resets: Official game integrity checks or major patches may overwrite modified assets. Use Re-Inject or Restore Original anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "3. Account Risk: Using third-party file modifications carries potential account penalty risks. Original game files are always backed up automatically before overwriting.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_disclaimer_dialog_button"),
            ) {
                Text("Understood")
            }
        },
    )
}

@Composable
fun MaintenanceAlertDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = "Maintenance Mode",
                modifier = Modifier.testTag("maintenance_dialog_title"),
            )
        },
        text = {
            Text(
                text = message.ifBlank {
                    "Skin injection and remote downloads are temporarily unavailable for maintenance."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("maintenance_dialog_message"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag("maintenance_retry_button"),
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("maintenance_dismiss_button"),
            ) {
                Text("Restore Only")
            }
        },
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
