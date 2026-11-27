package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.SkinScriptItem
import com.example.ui.components.AdGateLoadingDialog
import com.example.ui.components.CompanionTopAppBar
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.ConfirmClearBackupDialog
import com.example.ui.components.DisclaimerDetailsDialog
import com.example.ui.components.DisclaimerWarningBanner
import com.example.ui.components.HeroDetailPane
import com.example.ui.components.HeroRosterPane
import com.example.ui.components.ImportZipConfirmationSheet
import com.example.ui.components.NotificationDialog
import com.example.ui.components.PackageAndDiagnosticsSheet
import com.example.ui.components.RemoteMaintenanceBanner
import com.example.ui.components.ShizukuGuideDialog

@Composable
fun MythicScriptApp(
    viewModel: CompanionViewModel,
    onToggleLanguage: () -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adGateState by UnityAdManager.gateState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        viewModel.registerShizukuListeners()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShizukuEnvironment()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.unregisterShizukuListeners()
        }
    }

    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke() else viewModel.notifyStoragePermissionRequired()
    }

    fun withStoragePermission(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingStorageAction = action
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    val onDownloadScriptWithPermission: (SkinScriptItem) -> Unit = { script ->
        withStoragePermission { viewModel.downloadSkinScript(script) }
    }
    val onInjectScriptWithPermission: (SkinScriptItem) -> Unit = { script ->
        withStoragePermission { viewModel.injectScript(script) }
    }
    val onInjectSelectedWithPermission: () -> Unit = {
        withStoragePermission { viewModel.injectSelectedScript() }
    }
    val onRestoreWithPermission: () -> Unit = {
        withStoragePermission { viewModel.restoreSelectedScript() }
    }

    // The ViewModel gates downloads, Inject/Re-inject and Restore through this one callback.
    // UnityAdManager owns the ad request, the 15s countdown and the exactly-once continuation.
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

        LiquidGlassBackground {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    CompanionTopAppBar(
                        showBackButton = !isExpandedLayout && isShowingCompactDetail,
                        shizukuState = uiState.shizukuState,
                        targetPackage = uiState.targetPackage,
                        onBackClick = { isShowingCompactDetail = false },
                        onShizukuStatusClick = viewModel::handleShizukuStatusClick,
                        onSettingsClick = { viewModel.setDiagnosticsSheetVisible(true) },
                        onShowDisclaimerClick = { viewModel.setDisclaimerDialogVisible(true) },
                        onToggleLanguage = onToggleLanguage,
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
                            onDownloadScript = onDownloadScriptWithPermission,
                            onInjectScript = onInjectScriptWithPermission,
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
                            onInjectClick = onInjectSelectedWithPermission,
                            onRestoreClick = onRestoreWithPermission,
                            onClearBackupClick = { showClearBackupConfirm = true },
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
                            onDownloadScript = onDownloadScriptWithPermission,
                            onInjectScript = onInjectScriptWithPermission,
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
                            onInjectClick = onInjectSelectedWithPermission,
                            onRestoreClick = onRestoreWithPermission,
                            onClearBackupClick = { showClearBackupConfirm = true },
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

    if (uiState.showShizukuGuideDialog) {
        ShizukuGuideDialog(
            state = uiState.shizukuState,
            onOpenShizukuApp = viewModel::openShizukuApp,
            onWatchTutorial = viewModel::openShizukuTutorial,
            onRefresh = viewModel::refreshShizukuStatus,
            onDismiss = viewModel::dismissShizukuGuide,
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
            skinName = uiState.selectedScript?.skinName ?: stringResource(R.string.msg_selected_script),
            onConfirm = {
                showClearBackupConfirm = false
                viewModel.clearSelectedBackup()
            },
            onDismiss = { showClearBackupConfirm = false },
        )
    }

    if (uiState.showNoticeDialog) {
        NotificationDialog(
            title = uiState.noticeTitle,
            message = uiState.noticeMessage,
            onDismiss = { viewModel.dismissNoticeDialog() },
        )
    }

    AdGateLoadingDialog(state = adGateState)
}
