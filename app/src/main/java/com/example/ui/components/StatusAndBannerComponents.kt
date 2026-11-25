package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.remote.RemoteConfigState
import com.example.domain.model.ScriptLifecycleState
import com.example.localization.LocaleManager
import com.example.localization.localizedLabel
import com.example.localization.localizedShortLabel
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
    onToggleLanguage: () -> Unit,
) {
    val context = LocalContext.current
    val isBurmese = LocaleManager.isBurmese(context)

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
                        contentDescription = stringResource(R.string.cd_script_safety_notice),
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
            LanguageToggle(
                isBurmese = isBurmese,
                onToggleLanguage = onToggleLanguage,
            )
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
private fun LanguageToggle(
    isBurmese: Boolean,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageDescription = stringResource(R.string.cd_language_toggle)
    Surface(
        onClick = onToggleLanguage,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .padding(end = 6.dp)
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = languageDescription }
            .testTag("language_toggle"),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            LanguageBadge(
                label = stringResource(R.string.language_english),
                selected = !isBurmese,
                testTag = "language_toggle_en",
            )
            LanguageBadge(
                label = stringResource(R.string.language_burmese),
                selected = isBurmese,
                testTag = "language_toggle_mm",
            )
        }
    }
}

@Composable
private fun LanguageBadge(
    label: String,
    selected: Boolean,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(testTag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
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
            stringResource(R.string.shizuku_uid, state.uid),
        )
        ShizukuConnectionState.PermissionGranted -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.Security,
            stringResource(R.string.shizuku_binding),
        )
        is ShizukuConnectionState.PermissionDenied -> Quad(
            semantic.warningContainer,
            semantic.warning,
            Icons.Filled.Warning,
            stringResource(R.string.shizuku_grant),
        )
        ShizukuConnectionState.NotRunning -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.LinkOff,
            stringResource(R.string.shizuku_not_running),
        )
        ShizukuConnectionState.NotInstalled -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.Error,
            stringResource(R.string.shizuku_no_shizuku),
        )
        is ShizukuConnectionState.ServiceDisconnected -> Quad(
            semantic.warningContainer,
            semantic.warning,
            Icons.Filled.LinkOff,
            stringResource(R.string.shizuku_reconnect),
        )
    }

    val actionDescription = stringResource(R.string.cd_shizuku_status_action, shortText)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = modifier
            .padding(end = 4.dp)
            .minimumInteractiveComponentSize()
            .widthIn(max = 132.dp)
            .semantics { contentDescription = actionDescription }
            .testTag("shizuku_status_pill"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.cd_shizuku_status, shortText),
                tint = fgColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = shortText,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                            Text(stringResource(R.string.risk_backup_rules))
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
                        contentDescription = stringResource(R.string.cd_dismiss_safety_banner),
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
                        text = stringResource(
                            if (activeConfig?.maintenanceMode == true) {
                                R.string.remote_maintenance_active
                            } else {
                                R.string.injection_disabled_remotely
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = activeConfig?.maintenanceMessage
                            ?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.maintenance_paused_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_remote_config_banner_button"),
                ) {
                    Text(stringResource(R.string.action_refresh))
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
            text = state.localizedLabel(),
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
                    text = stringResource(R.string.disclaimer_paragraph_1),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.disclaimer_paragraph_2),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.disclaimer_paragraph_3),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_disclaimer_dialog_button"),
            ) {
                Text(stringResource(R.string.action_understood))
            }
        },
    )
}

@Composable
fun NotificationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NotificationDialogContent(title = title, message = message, onDismiss = onDismiss)
    }
}

@Composable
internal fun NotificationDialogContent(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    val cardShape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("remote_notice_dialog_root"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp)
                    .shadow(elevation = 28.dp, shape = cardShape, clip = false)
                    .clip(cardShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFF8F8FA).copy(alpha = 0.95f),
                                Color(0xFFE7E8ED).copy(alpha = 0.90f),
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.78f), cardShape)
                    .padding(start = 26.dp, end = 26.dp, top = 48.dp, bottom = 24.dp)
                    .testTag("remote_notice_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF303138),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notice_dialog_title"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF55575F),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .testTag("notice_dialog_message"),
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoticePillButton(
                        text = stringResource(R.string.action_got_it),
                        color = Color(0xFF00B7C7),
                        testTag = "notice_got_it_button",
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    NoticePillButton(
                        text = stringResource(R.string.action_close),
                        color = Color(0xFFFE1B1B),
                        testTag = "notice_close_button",
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(64.dp)
                    .shadow(elevation = 14.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(Color(0xFFF7F7F9))
                    .border(1.dp, Color.White, CircleShape)
                    .testTag("notice_info_badge"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.cd_notice_info),
                    tint = Color(0xFF3B3C42),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun NoticePillButton(
    text: String,
    color: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .testTag(testTag),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
