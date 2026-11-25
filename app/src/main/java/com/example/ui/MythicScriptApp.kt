package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CompanionTopAppBar
import com.example.ui.components.ConfirmClearBackupDialog
import com.example.ui.components.DisclaimerDetailsDialog
import com.example.ui.components.DisclaimerWarningBanner
import com.example.ui.components.HeroDetailPane
import com.example.ui.components.HeroRosterPane
import com.example.ui.components.ImportZipConfirmationSheet
import com.example.ui.components.MaintenanceAlertDialog
import com.example.ui.components.PackageAndDiagnosticsSheet
import com.example.ui.components.RemoteMaintenanceBanner

@Composable
fun MythicScriptApp(
    viewModel: CompanionViewModel,
) {
    val appContext = LocalContext.current.applicationContext
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The ViewModel gates every download, including the auto-download behind Inject, so the
    // remote requireAdForDownload flag is enforced in one place. The UI only supplies the ad.
    DisposableEffect(viewModel, appContext) {
        UnityAdManager.onGateAttached()
        val gate: (() -> Unit) -> Boolean = { onProceed ->
            UnityAdManager.onGateRequested()
            UnityAdManager.showRewardedThenDownload(appContext) { onProceed() }
        }
        viewModel.adGate = gate
        onDispose {
            if (viewModel.adGate === gate) viewModel.adGate = null
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var isShowingCompactDetail by rememberSaveable { mutableStateOf(false) }
    var showClearBackupConfirm by remember { mutableStateOf(false) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importZipFromUri(uri)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 680.dp

        if (!isExpandedLayout && isShowingCompactDetail) {
            BackHandler {
                isShowingCompactDetail = false
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CompanionTopAppBar(
                    showBackButton = !isExpandedLayout && isShowingCompactDetail,
                    shizukuState = uiState.shizukuState,
                    targetPackage = uiState.targetPackage,
                    onBackClick = { isShowingCompactDetail = false },
                    onShizukuStatusClick = { viewModel.setDiagnosticsSheetVisible(true) },
                    onSettingsClick = { viewModel.setDiagnosticsSheetVisible(true) },
                    onShowDisclaimerClick = { viewModel.setDisclaimerDialogVisible(true) },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                DisclaimerWarningBanner(
                    visible = !uiState.disclaimerDismissed && (isExpandedLayout || !isShowingCompactDetail),
                    onDismiss = { viewModel.dismissDisclaimerBanner() },
                    onLearnMore = { viewModel.setDisclaimerDialogVisible(true) },
                )

                RemoteMaintenanceBanner(
                    remoteConfigState = uiState.remoteConfigState,
                    onRetry = { viewModel.refreshRemoteConfig(showFeedbackToast = true) },
                )

                if (isExpandedLayout) {
                    Row(modifier = Modifier.weight(1f)) {
                        HeroRosterPane(
                            heroes = uiState.filteredHeroes,
                            roleFilter = uiState.roleFilter,
                            searchQuery = uiState.searchQuery,
                            selectedHeroId = uiState.selectedHeroId,
                            activeScriptIdByHero = uiState.activeScriptIdByHero,
                            onRoleSelected = viewModel::setRoleFilter,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onHeroClick = { hero -> viewModel.selectHero(hero.id) },
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight(),
                        )
                        VerticalDivider()
                        HeroDetailPane(
                            hero = uiState.selectedHero,
                            activeTab = uiState.activeDetailTab,
                            scripts = uiState.scriptsForSelectedHero,
                            selectedScriptId = uiState.selectedScriptId,
                            selectedScript = uiState.selectedScript,
                            hideUnavailableSkins = uiState.hideUnavailableSkins,
                            unavailableCountForHero = uiState.unavailableCountForHero,
                            shizukuState = uiState.shizukuState,
                            remoteConfigState = uiState.remoteConfigState,
                            isImportingZip = uiState.isImportingZip,
                            applyNavigationBarInsets = false,
                            onTabSelected = viewModel::setDetailTab,
                            onScriptSelected = viewModel::selectScript,
                            onDownloadScript = viewModel::downloadSkinScript,
                            onInjectScript = viewModel::injectScript,
                            onToggleHideUnavailable = viewModel::toggleHideUnavailableSkins,
                            onImportZipClick = {
                                zipPickerLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    )
                                )
                            },
                            onDeleteCustomScript = viewModel::deleteCustomScript,
                            onInjectClick = viewModel::injectSelectedScript,
                            onRestoreClick = viewModel::restoreSelectedScript,
                            onClearBackupClick = { showClearBackupConfirm = true },
                            onOpenShizukuDiagnostics = { viewModel.setDiagnosticsSheetVisible(true) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    if (isShowingCompactDetail) {
                        HeroDetailPane(
                            hero = uiState.selectedHero,
                            activeTab = uiState.activeDetailTab,
                            scripts = uiState.scriptsForSelectedHero,
                            selectedScriptId = uiState.selectedScriptId,
                            selectedScript = uiState.selectedScript,
                            hideUnavailableSkins = uiState.hideUnavailableSkins,
                            unavailableCountForHero = uiState.unavailableCountForHero,
                            shizukuState = uiState.shizukuState,
                            remoteConfigState = uiState.remoteConfigState,
                            isImportingZip = uiState.isImportingZip,
                            applyNavigationBarInsets = false,
                            onTabSelected = viewModel::setDetailTab,
                            onScriptSelected = viewModel::selectScript,
                            onDownloadScript = viewModel::downloadSkinScript,
                            onInjectScript = viewModel::injectScript,
                            onToggleHideUnavailable = viewModel::toggleHideUnavailableSkins,
                            onImportZipClick = {
                                zipPickerLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    )
                                )
                            },
                            onDeleteCustomScript = viewModel::deleteCustomScript,
                            onInjectClick = viewModel::injectSelectedScript,
                            onRestoreClick = viewModel::restoreSelectedScript,
                            onClearBackupClick = { showClearBackupConfirm = true },
                            onOpenShizukuDiagnostics = { viewModel.setDiagnosticsSheetVisible(true) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        HeroRosterPane(
                            heroes = uiState.filteredHeroes,
                            roleFilter = uiState.roleFilter,
                            searchQuery = uiState.searchQuery,
                            selectedHeroId = uiState.selectedHeroId,
                            activeScriptIdByHero = uiState.activeScriptIdByHero,
                            onRoleSelected = viewModel::setRoleFilter,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onHeroClick = { hero ->
                                viewModel.selectHero(hero.id)
                                isShowingCompactDetail = true
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    val pendingPreview = uiState.pendingImportPreview
    if (pendingPreview != null) {
        ImportZipConfirmationSheet(
            preview = pendingPreview,
            heroes = uiState.allHeroes,
            onConfirm = { heroId, skinName, tierTag, version, notes ->
                viewModel.confirmImportPreview(heroId, skinName, tierTag, version, notes)
                isShowingCompactDetail = true
            },
            onDismiss = viewModel::cancelImportPreview,
        )
    }

    if (uiState.showDiagnosticsSheet) {
        PackageAndDiagnosticsSheet(
            shizukuState = uiState.shizukuState,
            remoteConfigState = uiState.remoteConfigState,
            targetPackage = uiState.targetPackage,
            isTargetPackageInstalled = uiState.isTargetPackageInstalled,
            totalBackupBytes = uiState.totalBackupBytes,
            disclaimerDismissed = uiState.disclaimerDismissed,
            onRefreshShizuku = viewModel::refreshShizukuStatus,
            onRefreshRemoteConfig = { viewModel.refreshRemoteConfig(showFeedbackToast = true) },
            onRequestPermission = viewModel::requestShizukuPermission,
            onBindUserService = viewModel::bindShizukuService,
            onOpenShizukuApp = viewModel::openShizukuApp,
            onUpdateTargetPackage = viewModel::updateTargetPackage,
            onRestoreDisclaimer = viewModel::restoreDisclaimerBanner,
            onDismiss = { viewModel.setDiagnosticsSheetVisible(false) },
        )
    }

    if (uiState.showDisclaimerDialog) {
        DisclaimerDetailsDialog(
            onDismiss = { viewModel.setDisclaimerDialogVisible(false) },
        )
    }

    if (showClearBackupConfirm) {
        ConfirmClearBackupDialog(
            skinName = uiState.selectedScript?.skinName ?: "Selected Script",
            onConfirm = {
                showClearBackupConfirm = false
                viewModel.clearSelectedBackup()
            },
            onDismiss = { showClearBackupConfirm = false },
        )
    }

    if (uiState.showMaintenanceDialog) {
        MaintenanceAlertDialog(
            message = uiState.maintenanceMessage,
            onRetry = { viewModel.refreshRemoteConfig(showFeedbackToast = true) },
            onDismiss = { viewModel.dismissMaintenanceDialog() },
        )
    }
}
