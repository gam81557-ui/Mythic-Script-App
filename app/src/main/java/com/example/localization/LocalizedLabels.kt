package com.example.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.domain.model.HeroDetailTab
import com.example.domain.model.HeroRole
import com.example.domain.model.ScriptLifecycleState
import com.example.shizuku.ShizukuConnectionState

fun HeroRole.labelRes(): Int = when (this) {
    HeroRole.ALL -> R.string.role_show_all
    HeroRole.ASSASSIN -> R.string.role_assassin
    HeroRole.FIGHTER -> R.string.role_fighter
    HeroRole.MAGE -> R.string.role_mage
    HeroRole.MARKSMAN -> R.string.role_marksman
    HeroRole.TANK -> R.string.role_tank
    HeroRole.SUPPORT -> R.string.role_support
}

fun HeroRole.localizedLabel(context: Context): String = context.getString(labelRes())

@Composable
fun HeroRole.localizedLabel(): String = stringResource(labelRes())

fun HeroDetailTab.labelRes(): Int = when (this) {
    HeroDetailTab.SCRIPTS -> R.string.tab_skin_scripts
    HeroDetailTab.STATS_COMBOS -> R.string.tab_stats_combos
}

@Composable
fun HeroDetailTab.localizedLabel(): String = stringResource(labelRes())

fun ScriptLifecycleState.localizedLabel(context: Context): String = when (this) {
    ScriptLifecycleState.Idle -> context.getString(R.string.status_idle)
    ScriptLifecycleState.Selected -> context.getString(R.string.status_selected)
    is ScriptLifecycleState.Downloading -> context.getString(R.string.status_downloading)
    ScriptLifecycleState.Downloaded -> context.getString(R.string.status_downloaded)
    is ScriptLifecycleState.BackingUp -> context.getString(R.string.status_backing_up)
    is ScriptLifecycleState.Injecting -> context.getString(R.string.status_injecting)
    ScriptLifecycleState.Injected -> context.getString(R.string.status_injected)
    is ScriptLifecycleState.Restoring -> context.getString(R.string.status_restoring)
    is ScriptLifecycleState.Error -> context.getString(R.string.status_failed)
    is ScriptLifecycleState.Unavailable -> context.getString(R.string.status_unavailable, httpCode)
}

@Composable
fun ScriptLifecycleState.localizedLabel(): String = when (this) {
    ScriptLifecycleState.Idle -> stringResource(R.string.status_idle)
    ScriptLifecycleState.Selected -> stringResource(R.string.status_selected)
    is ScriptLifecycleState.Downloading -> stringResource(R.string.status_downloading)
    ScriptLifecycleState.Downloaded -> stringResource(R.string.status_downloaded)
    is ScriptLifecycleState.BackingUp -> stringResource(R.string.status_backing_up)
    is ScriptLifecycleState.Injecting -> stringResource(R.string.status_injecting)
    ScriptLifecycleState.Injected -> stringResource(R.string.status_injected)
    is ScriptLifecycleState.Restoring -> stringResource(R.string.status_restoring)
    is ScriptLifecycleState.Error -> stringResource(R.string.status_failed)
    is ScriptLifecycleState.Unavailable -> stringResource(R.string.status_unavailable, httpCode)
}

fun ShizukuConnectionState.localizedShortLabel(context: Context): String = when (this) {
    ShizukuConnectionState.NotInstalled -> context.getString(R.string.shizuku_not_installed)
    ShizukuConnectionState.NotRunning -> context.getString(R.string.shizuku_not_running)
    is ShizukuConnectionState.PermissionDenied -> context.getString(R.string.shizuku_permission_denied)
    ShizukuConnectionState.PermissionGranted -> context.getString(R.string.shizuku_permission_granted)
    ShizukuConnectionState.Binding -> context.getString(R.string.shizuku_binding)
    is ShizukuConnectionState.ServiceConnected ->
        context.getString(R.string.shizuku_connected, processLabel)
    is ShizukuConnectionState.ServiceDisconnected ->
        context.getString(R.string.shizuku_service_disconnected)
}

@Composable
fun ShizukuConnectionState.localizedShortLabel(): String = when (this) {
    ShizukuConnectionState.NotInstalled -> stringResource(R.string.shizuku_not_installed)
    ShizukuConnectionState.NotRunning -> stringResource(R.string.shizuku_not_running)
    is ShizukuConnectionState.PermissionDenied -> stringResource(R.string.shizuku_permission_denied)
    ShizukuConnectionState.PermissionGranted -> stringResource(R.string.shizuku_permission_granted)
    ShizukuConnectionState.Binding -> stringResource(R.string.shizuku_binding)
    is ShizukuConnectionState.ServiceConnected ->
        stringResource(R.string.shizuku_connected, processLabel)
    is ShizukuConnectionState.ServiceDisconnected ->
        stringResource(R.string.shizuku_service_disconnected)
}
