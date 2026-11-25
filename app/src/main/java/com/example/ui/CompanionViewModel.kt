package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.catalog.HeroCatalogProvider
import com.example.data.catalog.HeroPortraitProvider
import com.example.data.catalog.HeroRoleMapper
import com.example.data.files.InjectionPipelineManager
import com.example.data.files.RemoteSkinUnavailableException
import com.example.data.files.ScriptArchiveManager
import com.example.data.local.AppPreferences
import com.example.data.local.BackupEntryEntity
import com.example.data.local.CompanionDatabase
import com.example.data.local.SkinScriptEntity
import com.example.data.remote.RemoteConfig
import com.example.data.remote.RemoteConfigManager
import com.example.data.remote.RemoteConfigState
import com.example.data.remote.RemoteSkinEntry
import com.example.data.repository.CompanionRepository
import com.example.localization.LocaleManager
import com.example.localization.localizedLabel
import com.example.localization.localizedMessage
import com.example.localization.localizedShortLabel
import com.example.domain.model.HeroAttributes
import com.example.domain.model.HeroDetailTab
import com.example.domain.model.HeroProfile
import com.example.domain.model.HeroRole
import com.example.domain.model.ScriptLifecycleState
import com.example.domain.model.SkinScriptItem
import com.example.domain.model.StagedBundlePreview
import com.example.shizuku.ShizukuBridgeManager
import com.example.shizuku.ShizukuConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap

data class CompanionUiState(
    val allHeroes: List<HeroProfile> = HeroCatalogProvider.heroes,
    val filteredHeroes: List<HeroProfile> = HeroCatalogProvider.heroes,
    val roleFilter: HeroRole = HeroRole.ALL,
    val searchQuery: String = "",
    val selectedHeroId: String = HeroCatalogProvider.heroes.first().id,
    val selectedHero: HeroProfile = HeroCatalogProvider.heroes.first(),
    val activeDetailTab: HeroDetailTab = HeroDetailTab.SCRIPTS,
    val scriptsForSelectedHero: List<SkinScriptItem> = emptyList(),
    val selectedScriptId: String? = null,
    val selectedScript: SkinScriptItem? = null,
    val activeScriptIdByHero: Map<String, String> = emptyMap(),
    val hideUnavailableSkins: Boolean = false,
    val unavailableCountForHero: Int = 0,
    val shizukuState: ShizukuConnectionState = ShizukuConnectionState.NotRunning,
    val targetPackage: String = AppPreferences.DEFAULT_TARGET_PACKAGE,
    val isTargetPackageInstalled: Boolean = false,
    val disclaimerDismissed: Boolean = false,
    val isImportingZip: Boolean = false,
    val pendingImportPreview: StagedBundlePreview? = null,
    val showDiagnosticsSheet: Boolean = false,
    val showDisclaimerDialog: Boolean = false,
    val totalBackupBytes: Long = 0L,
    val remoteConfigState: RemoteConfigState = RemoteConfigState.Loading,
    val showMaintenanceDialog: Boolean = false,
    val maintenanceMessage: String = "",
    val snackbarMessage: String? = null,
)

private data class FilterAndSelectionState(
    val roleFilter: HeroRole,
    val searchQuery: String,
    val selectedHeroId: String,
    val selectedScriptByHero: Map<String, String>,
    val activeDetailTab: HeroDetailTab,
)

private data class ModalAndTransientState(
    val transientScriptStates: Map<String, ScriptLifecycleState>,
    val isImportingZip: Boolean,
    val pendingImportPreview: StagedBundlePreview?,
    val showDiagnosticsSheet: Boolean,
    val showDisclaimerDialog: Boolean,
    val maintenanceDismissedFetchMillis: Long? = null,
    val snackbarMessage: String? = null,
    val hideUnavailableSkins: Boolean = false,
)

class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private fun tr(resId: Int, vararg args: Any): String =
        LocaleManager.localizedContext(getApplication()).getString(resId, *args)

    private fun shizukuLabel(state: ShizukuConnectionState): String =
        state.localizedShortLabel(LocaleManager.localizedContext(getApplication()))

    private fun errorMessage(error: Throwable, fallbackRes: Int): String =
        error.localizedMessage(LocaleManager.localizedContext(getApplication()), fallbackRes)

    private val database = CompanionDatabase.getInstance(application)
    private val preferences = AppPreferences(application)
    private val shizukuBridge = ShizukuBridgeManager(application)
    private val archiveManager = ScriptArchiveManager(application)
    private val pipelineManager = InjectionPipelineManager(
        context = application,
        dao = database.companionDao(),
        shizukuBridge = shizukuBridge,
    )
    private val repository = CompanionRepository(
        dao = database.companionDao(),
        preferences = preferences,
        archiveManager = archiveManager,
        pipelineManager = pipelineManager,
    )
    private val remoteConfigManager = RemoteConfigManager()
    private val remoteConfigStateFlow = MutableStateFlow<RemoteConfigState>(RemoteConfigState.Loading)
    private var lastKnownRemoteConfig: RemoteConfig? = null
    private val probedUrlsCache = ConcurrentHashMap<String, Int>()
    private var remoteFetchJob: Job? = null
    private var probeJob: Job? = null
    @Volatile
    private var allowBackgroundRemoteSync: Boolean = true

    /**
     * Installed by the UI layer so every download, whether triggered directly or by injection,
     * can be gated by the rewarded ad. Returns false when another ad flow is already running.
     */
    var adGate: ((onProceed: () -> Unit) -> Boolean)? = null

    private val roleFilterFlow = MutableStateFlow(HeroRole.ALL)
    private val searchQueryFlow = MutableStateFlow("")
    private val selectedHeroIdFlow = MutableStateFlow(HeroCatalogProvider.heroes.first().id)
    private val selectedScriptByHeroFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    private val activeDetailTabFlow = MutableStateFlow(HeroDetailTab.SCRIPTS)
    private val transientScriptStatesFlow = MutableStateFlow<Map<String, ScriptLifecycleState>>(emptyMap())

    private val isImportingZipFlow = MutableStateFlow(false)
    private val pendingImportPreviewFlow = MutableStateFlow<StagedBundlePreview?>(null)
    private val showDiagnosticsSheetFlow = MutableStateFlow(false)
    private val showDisclaimerDialogFlow = MutableStateFlow(false)
    private val maintenanceDismissedFetchMillisFlow = MutableStateFlow<Long?>(null)
    private val snackbarMessageFlow = MutableStateFlow<String?>(null)
    private val hideUnavailableSkinsFlow = MutableStateFlow(false)

    private val filterAndSelectionFlow = combine(
        roleFilterFlow,
        searchQueryFlow,
        selectedHeroIdFlow,
        selectedScriptByHeroFlow,
        activeDetailTabFlow,
    ) { role, query, heroId, scriptMap, tab ->
        FilterAndSelectionState(role, query, heroId, scriptMap, tab)
    }

    private val modalAndTransientFlow = combine(
        transientScriptStatesFlow,
        isImportingZipFlow,
        pendingImportPreviewFlow,
        showDiagnosticsSheetFlow,
        showDisclaimerDialogFlow,
    ) { transientStates, importing, preview, diagnostics, disclaimer ->
        ModalAndTransientState(
            transientScriptStates = transientStates,
            isImportingZip = importing,
            pendingImportPreview = preview,
            showDiagnosticsSheet = diagnostics,
            showDisclaimerDialog = disclaimer,
        )
    }.combine(maintenanceDismissedFetchMillisFlow) { modal, dismissedAt ->
        modal.copy(maintenanceDismissedFetchMillis = dismissedAt)
    }.combine(snackbarMessageFlow) { modal, snack ->
        modal.copy(snackbarMessage = snack)
    }.combine(hideUnavailableSkinsFlow) { modal, hideUnavailable ->
        modal.copy(hideUnavailableSkins = hideUnavailable)
    }

    private val persistedDataFlow = combine(
        repository.allScriptsFlow,
        repository.allBackupEntriesFlow,
        repository.targetPackageFlow,
        repository.disclaimerDismissedFlow,
        shizukuBridge.connectionState,
    ) { scripts, backups, targetPkg, disclaimerDismissed, shizukuState ->
        PersistedSnapshot(scripts, backups, targetPkg, disclaimerDismissed, shizukuState)
    }

    val uiState: StateFlow<CompanionUiState> = combine(
        filterAndSelectionFlow,
        modalAndTransientFlow,
        persistedDataFlow,
        remoteConfigStateFlow,
    ) { filterState, modalState, persisted, remoteConfigState ->
        val activeRemoteConfig = when (remoteConfigState) {
            is RemoteConfigState.Ready -> remoteConfigState.config
            is RemoteConfigState.Failed -> remoteConfigState.lastKnownConfig
            RemoteConfigState.Loading -> null
        }

        val mergedHeroes = buildMergedHeroList(
            localHeroes = repository.heroes,
            remoteConfig = activeRemoteConfig,
        )

        val filteredHeroes = mergedHeroes.filter { hero ->
            hero.matchesRole(filterState.roleFilter) && hero.matchesQuery(filterState.searchQuery)
        }

        val activeHero = mergedHeroes.find { it.id == filterState.selectedHeroId }
            ?: mergedHeroes.first()

        val backupsByScript = persisted.backups.groupBy { it.scriptId }
        val heroScriptsRaw = persisted.scripts.filter {
            it.heroId.equals(activeHero.id, ignoreCase = true) ||
                it.heroId.equals(activeHero.name, ignoreCase = true)
        }

        val remoteSkinsForHeroAll = findRemoteSkinsForHero(activeRemoteConfig, activeHero.name)
        val remoteBySafeId = remoteSkinsForHeroAll.associateBy { it.scriptIdSafe() }

        val activeScriptIdByHero = persisted.scripts
            .filter { it.persistedStatus == "INJECTED" }
            .associate { it.heroId to it.scriptId }

        val existingIds = heroScriptsRaw.map { it.scriptId }.toSet()
        val remoteSkinsForHero = remoteSkinsForHeroAll.filter { it.scriptIdSafe() !in existingIds }

        val explicitSelectedForHero = filterState.selectedScriptByHero[activeHero.id]
        val explicitExistsInHero = explicitSelectedForHero != null && (
            heroScriptsRaw.any { it.scriptId == explicitSelectedForHero } ||
                remoteSkinsForHero.any { it.scriptIdSafe() == explicitSelectedForHero }
            )
        val effectiveSelectedScriptId = if (explicitExistsInHero) {
            explicitSelectedForHero
        } else {
            heroScriptsRaw.firstOrNull { it.persistedStatus == "INJECTED" }?.scriptId
                ?: heroScriptsRaw.firstOrNull { it.persistedStatus == "DOWNLOADED" }?.scriptId
                ?: heroScriptsRaw.firstOrNull { it.persistedStatus != "UNAVAILABLE" }?.scriptId
                ?: remoteSkinsForHero.firstOrNull()?.scriptIdSafe()
                ?: heroScriptsRaw.firstOrNull()?.scriptId
        }

        val persistedMappedScripts = heroScriptsRaw.map { entity ->
            val scriptBackups = backupsByScript[entity.scriptId].orEmpty()
            val isSelected = entity.scriptId == effectiveSelectedScriptId
            val transient = modalState.transientScriptStates[entity.scriptId]
            val lifecycle = resolveLifecycleState(entity, isSelected, transient)
            val remoteMatch = remoteBySafeId[entity.scriptId]

            val safeSkinName = if (!entity.isUserImported && remoteMatch != null) {
                remoteMatch.name
            } else {
                entity.skinName.ifBlank { tr(R.string.default_skin) }
            }
            val rawTier = if (!entity.isUserImported && remoteMatch != null) {
                remoteMatch.tierTag
            } else {
                entity.tierTag
            }
            val safeTierTag = if (rawTier.startsWith("http://", true) || rawTier.startsWith("https://", true)) {
                ""
            } else {
                rawTier
            }

            SkinScriptItem(
                scriptId = entity.scriptId,
                heroId = entity.heroId,
                skinName = safeSkinName,
                tierTag = safeTierTag,
                version = entity.version,
                compatibilityNotes = entity.compatibilityNotes,
                targetRelativePaths = InjectionPipelineManager.parseRelativePaths(
                    entity.targetRelativePathsJoined
                ),
                stagedDirPath = entity.stagedDirPath,
                isUserImported = entity.isUserImported,
                isDownloaded = entity.isUserImported ||
                    entity.persistedStatus == "DOWNLOADED" ||
                    entity.persistedStatus == "INJECTED" ||
                    (entity.persistedStatus != "UNAVAILABLE" && remoteMatch == null),
                hasBackup = scriptBackups.isNotEmpty(),
                backupFileCount = scriptBackups.count { it.existedBeforeInjection },
                backupByteSize = scriptBackups.sumOf { it.byteSize },
                lastUpdatedAt = entity.lastUpdatedAt,
                lifecycleState = lifecycle,
                imageUrl = remoteMatch?.imageUrl.orEmpty(),
                fallbackImageUrl = remoteMatch?.fallbackImageUrl.orEmpty(),
                tagBadgeUrl = remoteMatch?.tagBadgeUrl.orEmpty(),
                downloadUrl = remoteMatch?.downloadUrl.orEmpty(),
                dateUpload = remoteMatch?.dateUpload.orEmpty(),
                sortOrder = remoteMatch?.sortOrder ?: Int.MAX_VALUE,
            )
        }

        val remoteMappedScripts = remoteSkinsForHero.map { entry ->
            val safeId = entry.scriptIdSafe()
            val isSelected = safeId == effectiveSelectedScriptId
            val transient = modalState.transientScriptStates[safeId]
            val lifecycle = transient ?: if (isSelected) {
                ScriptLifecycleState.Selected
            } else {
                ScriptLifecycleState.Idle
            }
            val relPaths = entry.targetRelativePaths.ifEmpty {
                listOf("files/dragon2017/assets/Art/Android/${safeId.lowercase().replace(Regex("[^a-z0-9._-]"), "_")}.unity3d")
            }
            SkinScriptItem(
                scriptId = safeId,
                heroId = activeHero.id,
                skinName = entry.name.ifBlank { tr(R.string.default_skin) },
                tierTag = entry.tierTag,
                version = entry.version,
                compatibilityNotes = entry.notes,
                targetRelativePaths = relPaths,
                stagedDirPath = "${archiveManager.stagedRootDir.absolutePath}/$safeId",
                isUserImported = false,
                hasBackup = false,
                backupFileCount = 0,
                backupByteSize = 0L,
                lastUpdatedAt = activeRemoteConfig?.fetchedAtMillis ?: 0L,
                lifecycleState = lifecycle,
                imageUrl = entry.imageUrl,
                fallbackImageUrl = entry.fallbackImageUrl,
                tagBadgeUrl = entry.tagBadgeUrl,
                downloadUrl = entry.downloadUrl,
                dateUpload = entry.dateUpload,
                sortOrder = entry.sortOrder,
            )
        }

        val allMappedScripts = (persistedMappedScripts + remoteMappedScripts)
            .sortedWith(
                compareBy<SkinScriptItem> { it.skinName.contains("default", true) || it.skinName.contains("backup", true) }.thenBy { script ->
                    when {
                        script.lifecycleState is ScriptLifecycleState.Injected -> 0
                        script.lifecycleState is ScriptLifecycleState.Downloaded || script.isDownloaded -> 1
                        script.lifecycleState is ScriptLifecycleState.Unavailable -> 3
                        else -> 2
                    }
                }.thenBy { it.sortOrder }.thenBy { it.skinName.lowercase() }
            )
        val unavailableCountForHero = allMappedScripts.count { it.isUnavailable }
        val visibleScripts = if (modalState.hideUnavailableSkins) {
            allMappedScripts.filterNot { it.isUnavailable }
        } else {
            allMappedScripts
        }
        val selectedScript = visibleScripts.find { it.scriptId == effectiveSelectedScriptId }
        val totalBackupBytes = persisted.backups.sumOf { it.byteSize }
        val targetInstalled = isPackageInstalled(persisted.targetPackage)
        val showMaintenanceDialog = activeRemoteConfig != null &&
            activeRemoteConfig.maintenanceMode &&
            modalState.maintenanceDismissedFetchMillis != activeRemoteConfig.fetchedAtMillis

        CompanionUiState(
            allHeroes = mergedHeroes,
            filteredHeroes = filteredHeroes,
            roleFilter = filterState.roleFilter,
            searchQuery = filterState.searchQuery,
            selectedHeroId = activeHero.id,
            selectedHero = activeHero,
            activeDetailTab = filterState.activeDetailTab,
            scriptsForSelectedHero = visibleScripts,
            selectedScriptId = selectedScript?.scriptId ?: effectiveSelectedScriptId,
            selectedScript = selectedScript,
            activeScriptIdByHero = activeScriptIdByHero,
            hideUnavailableSkins = modalState.hideUnavailableSkins,
            unavailableCountForHero = unavailableCountForHero,
            shizukuState = persisted.shizukuState,
            targetPackage = persisted.targetPackage,
            isTargetPackageInstalled = targetInstalled,
            disclaimerDismissed = persisted.disclaimerDismissed,
            isImportingZip = modalState.isImportingZip,
            pendingImportPreview = modalState.pendingImportPreview,
            showDiagnosticsSheet = modalState.showDiagnosticsSheet,
            showDisclaimerDialog = modalState.showDisclaimerDialog,
            totalBackupBytes = totalBackupBytes,
            remoteConfigState = remoteConfigState,
            showMaintenanceDialog = showMaintenanceDialog,
            maintenanceMessage = activeRemoteConfig?.maintenanceMessage.orEmpty(),
            snackbarMessage = modalState.snackbarMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CompanionUiState(),
    )

    init {
        shizukuBridge.startListening()
        viewModelScope.launch {
            repository.initializeCatalogIfNeeded()
        }
        viewModelScope.launch {
            repository.selectedHeroIdFlow.collect { savedHeroId ->
                if (!savedHeroId.isNullOrBlank()) {
                    selectedHeroIdFlow.value = savedHeroId
                }
            }
        }
        refreshRemoteConfig()
    }

    fun refreshRemoteConfig(showFeedbackToast: Boolean = false) {
        if (showFeedbackToast) {
            allowBackgroundRemoteSync = true
        }
        remoteFetchJob?.cancel()
        remoteConfigStateFlow.value = RemoteConfigState.Loading
        remoteFetchJob = viewModelScope.launch {
            remoteConfigManager.fetchConfig()
                .onSuccess { config ->
                    if (!allowBackgroundRemoteSync) return@onSuccess
                    lastKnownRemoteConfig = config
                    remoteConfigStateFlow.value = RemoteConfigState.Ready(config)
                    probeSelectedHeroLinks(heroId = selectedHeroIdFlow.value, remoteConfig = config)
                    if (showFeedbackToast) {
                        val status = tr(
                            if (config.maintenanceMode) R.string.msg_maintenance_active
                            else R.string.msg_config_synced
                        )
                        snackbarMessageFlow.value = tr(
                            R.string.msg_config_summary,
                            status,
                            config.availableHeroes.size,
                            config.skinEntries.size,
                        )
                    }
                }
                .onFailure { err ->
                    if (!allowBackgroundRemoteSync) return@onFailure
                    val msg = errorMessage(err, R.string.msg_unable_remote_config)
                    remoteConfigStateFlow.value = RemoteConfigState.Failed(
                        errorMessage = msg,
                        lastKnownConfig = lastKnownRemoteConfig,
                    )
                    if (showFeedbackToast) {
                        snackbarMessageFlow.value = tr(R.string.msg_remote_config_offline, msg)
                    }
                }
        }
    }

    internal fun applyRemoteConfigForTest(config: RemoteConfig) {
        allowBackgroundRemoteSync = false
        remoteFetchJob?.cancel()
        probeJob?.cancel()
        lastKnownRemoteConfig = config
        remoteConfigStateFlow.value = RemoteConfigState.Ready(config)
    }

    internal fun markScriptUnavailableForTest(
        script: SkinScriptItem,
        httpCode: Int = HttpURLConnection.HTTP_NOT_FOUND,
    ) {
        viewModelScope.launch {
            markScriptUnavailableNowForTest(script, httpCode)
        }
    }

    internal suspend fun markScriptUnavailableNowForTest(
        script: SkinScriptItem,
        httpCode: Int = HttpURLConnection.HTTP_NOT_FOUND,
    ) {
        repository.markScriptUnavailable(
            script = script,
            httpCode = httpCode,
            message = tr(R.string.msg_skin_unavailable_code, httpCode),
        )
    }

    fun toggleHideUnavailableSkins() {
        hideUnavailableSkinsFlow.value = !hideUnavailableSkinsFlow.value
    }

    fun setHideUnavailableSkins(hide: Boolean) {
        hideUnavailableSkinsFlow.value = hide
    }

    fun dismissMaintenanceDialog() {
        val currentConfig = when (val rc = remoteConfigStateFlow.value) {
            is RemoteConfigState.Ready -> rc.config
            is RemoteConfigState.Failed -> rc.lastKnownConfig
            RemoteConfigState.Loading -> null
        }
        maintenanceDismissedFetchMillisFlow.value = currentConfig?.fetchedAtMillis
    }

    fun downloadSkinScript(script: SkinScriptItem) {
        selectScript(script.scriptId)
        if (script.isUnavailable) {
            snackbarMessageFlow.value = tr(
                R.string.msg_skin_unavailable_named,
                script.skinName,
            )
            return
        }
        val url = script.downloadUrl.trim()
        if (url.isEmpty()) {
            snackbarMessageFlow.value = tr(R.string.msg_no_download_link, script.skinName)
            return
        }
        if (script.lifecycleState.isBusy) return

        maybeGateDownload { startDownload(script) }
    }

    private fun maybeGateDownload(onProceed: () -> Unit) {
        val gate = adGate
        val requireAd = requireAdForDownload()
        Log.d(
            "CompanionViewModel",
            "Download gate: requireAdForDownload=$requireAd, gateAttached=${gate != null}",
        )
        if (requireAd && gate != null) {
            if (!gate(onProceed)) {
                snackbarMessageFlow.value = tr(R.string.msg_wait_for_ad)
            }
        } else {
            if (requireAd) {
                Log.w("CompanionViewModel", "Ad gate bypassed: UI gate is not attached")
            } else {
                Log.d("CompanionViewModel", "Ad gate bypassed: requireAdForDownload=false")
            }
            onProceed()
        }
    }

    private fun requireAdForDownload(): Boolean =
        currentRemoteConfig()?.requireAdForDownload ?: true

    private fun startDownload(script: SkinScriptItem) {
        viewModelScope.launch {
            transientScriptStatesFlow.value =
                transientScriptStatesFlow.value + (script.scriptId to ScriptLifecycleState.Downloading(0.10f))
            val result = repository.downloadRemoteScriptBundle(script) { progress ->
                transientScriptStatesFlow.value =
                    transientScriptStatesFlow.value + (script.scriptId to ScriptLifecycleState.Downloading(progress))
            }

            result.onSuccess { staged ->
                // Clear downloading state; Room now holds persistedStatus = "DOWNLOADED"
                transientScriptStatesFlow.value = transientScriptStatesFlow.value - script.scriptId

                val remoteConfig = currentRemoteConfig()
                if (remoteConfig != null && !remoteConfig.canInjectRemotely) {
                    snackbarMessageFlow.value = tr(
                        R.string.msg_downloaded_paused,
                        script.skinName,
                        staged.relativePaths.size,
                    )
                    return@onSuccess
                }

                val currentShizuku = uiState.value.shizukuState
                if (!currentShizuku.isReadyForIo) {
                    snackbarMessageFlow.value = tr(
                        R.string.msg_downloaded_connect_shizuku,
                        script.skinName,
                        staged.relativePaths.size,
                        shizukuLabel(currentShizuku),
                    )
                    showDiagnosticsSheetFlow.value = true
                    return@onSuccess
                }

                performInjectionForScript(script = script, fromAutoDownload = true)
            }.onFailure { err ->
                if (err is RemoteSkinUnavailableException) {
                    transientScriptStatesFlow.value = transientScriptStatesFlow.value - script.scriptId
                    val unavailableMsg = tr(R.string.msg_skin_unavailable_code, err.httpCode)
                    repository.markScriptUnavailable(
                        script = script,
                        httpCode = err.httpCode,
                        message = unavailableMsg,
                    )
                    snackbarMessageFlow.value = tr(
                        R.string.msg_skin_unavailable_named_code,
                        script.skinName,
                        err.httpCode,
                    )
                } else {
                    val reason = errorMessage(err, R.string.msg_download_failed_generic)
                    transientScriptStatesFlow.value =
                        transientScriptStatesFlow.value + (script.scriptId to ScriptLifecycleState.Error(reason))
                    snackbarMessageFlow.value = tr(R.string.msg_download_failed, script.skinName, reason)
                }
            }
        }
    }

    private fun currentRemoteConfig(): RemoteConfig? = when (val rc = uiState.value.remoteConfigState) {
        is RemoteConfigState.Ready -> rc.config
        is RemoteConfigState.Failed -> rc.lastKnownConfig
        RemoteConfigState.Loading -> null
    }

    override fun onCleared() {
        super.onCleared()
        adGate = null
        shizukuBridge.stopListening()
    }

    fun refreshShizukuStatus() {
        shizukuBridge.refreshStatus()
    }

    fun requestShizukuPermission() {
        shizukuBridge.requestPermission()
    }

    fun bindShizukuService() {
        shizukuBridge.bindUserService()
    }

    fun openShizukuApp() {
        if (!shizukuBridge.openShizukuApp()) {
            snackbarMessageFlow.value = tr(R.string.msg_shizuku_not_installed)
        }
    }

    fun setRoleFilter(role: HeroRole) {
        roleFilterFlow.value = role
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun selectHero(heroId: String) {
        selectedHeroIdFlow.value = heroId
        viewModelScope.launch {
            repository.setSelectedHeroId(heroId)
        }
        probeSelectedHeroLinks(heroId = heroId, remoteConfig = lastKnownRemoteConfig)
    }

    private fun probeSelectedHeroLinks(heroId: String, remoteConfig: RemoteConfig?) {
        if (remoteConfig == null || !allowBackgroundRemoteSync) return
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            val heroName = uiState.value.allHeroes.find { it.id == heroId }?.name
                ?: remoteConfig.availableHeroes.find {
                    it.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_') == heroId
                }
                ?: heroId
            val remoteEntries = findRemoteSkinsForHero(remoteConfig, heroName)
                .filter { it.downloadUrl.startsWith("http://", true) || it.downloadUrl.startsWith("https://", true) }
            if (remoteEntries.isEmpty()) return@launch

            val existingById = repository.allScriptsFlow.first().associateBy { it.scriptId }
            val semaphore = Semaphore(4)
            coroutineScope {
                remoteEntries.map { entry ->
                    async {
                        val safeId = entry.scriptIdSafe()
                        val existing = existingById[safeId]
                        if (existing != null && existing.persistedStatus in setOf("DOWNLOADED", "INJECTED", "UNAVAILABLE")) {
                            return@async
                        }
                        val status = probedUrlsCache[entry.downloadUrl]
                            ?: semaphore.withPermit {
                                repository.checkRemoteUrlStatus(entry.downloadUrl)
                            }?.also { code ->
                                probedUrlsCache[entry.downloadUrl] = code
                            }

                        if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
                            val stub = SkinScriptItem(
                                scriptId = safeId,
                                heroId = heroId,
                                skinName = entry.name.ifBlank { tr(R.string.default_skin) },
                                tierTag = entry.tierTag,
                                version = entry.version,
                                compatibilityNotes = entry.notes,
                                targetRelativePaths = entry.targetRelativePaths,
                                stagedDirPath = "${archiveManager.stagedRootDir.absolutePath}/$safeId",
                                isUserImported = false,
                                hasBackup = false,
                                backupFileCount = 0,
                                backupByteSize = 0L,
                                lastUpdatedAt = remoteConfig.fetchedAtMillis,
                                lifecycleState = ScriptLifecycleState.Idle,
                                imageUrl = entry.imageUrl,
                                fallbackImageUrl = entry.fallbackImageUrl,
                                tagBadgeUrl = entry.tagBadgeUrl,
                                downloadUrl = entry.downloadUrl,
                                dateUpload = entry.dateUpload,
                                sortOrder = entry.sortOrder,
                            )
                            repository.markScriptUnavailable(
                                script = stub,
                                httpCode = status,
                                message = tr(R.string.msg_skin_unavailable_code, status),
                            )
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun setDetailTab(tab: HeroDetailTab) {
        activeDetailTabFlow.value = tab
    }

    fun selectScript(scriptId: String) {
        val heroId = uiState.value.selectedHeroId
        selectedScriptByHeroFlow.value = selectedScriptByHeroFlow.value + (heroId to scriptId)
        // Clear stale error transient state when user selects the card again.
        val currentTransient = transientScriptStatesFlow.value[scriptId]
        if (currentTransient is ScriptLifecycleState.Error) {
            transientScriptStatesFlow.value = transientScriptStatesFlow.value - scriptId
        }
    }

    fun injectSelectedScript() {
        val script = uiState.value.selectedScript ?: return
        injectScript(script)
    }

    fun injectScript(script: SkinScriptItem) {
        selectScript(script.scriptId)
        if (script.lifecycleState.isBusy) return
        if (script.isUnavailable) {
            snackbarMessageFlow.value = tr(
                R.string.msg_skin_unavailable_named,
                script.skinName,
            )
            return
        }

        // If the skin has a remote download URL and hasn't been downloaded yet, download & stage in-app first.
        if (script.downloadUrl.isNotBlank() && !script.isDownloaded && !script.isUserImported) {
            downloadSkinScript(script)
            return
        }

        val remoteConfig = currentRemoteConfig()
        if (remoteConfig != null && !remoteConfig.canInjectRemotely) {
            val reason = remoteConfig.maintenanceMessage.ifBlank {
                tr(R.string.msg_injection_disabled)
            }
            snackbarMessageFlow.value = reason
            return
        }

        if (!uiState.value.shizukuState.isReadyForIo) {
            val stateLabel = shizukuLabel(uiState.value.shizukuState)
            snackbarMessageFlow.value = if (script.isDownloaded) {
                tr(R.string.msg_downloaded_grant_shizuku, stateLabel)
            } else {
                tr(R.string.msg_grant_shizuku, stateLabel)
            }
            showDiagnosticsSheetFlow.value = true
            return
        }

        viewModelScope.launch {
            performInjectionForScript(script = script, fromAutoDownload = false)
        }
    }

    private suspend fun performInjectionForScript(
        script: SkinScriptItem,
        fromAutoDownload: Boolean,
    ) {
        val targetPkg = uiState.value.targetPackage
        repository.ensureRemoteScriptPersisted(script)
        val result = repository.injectScript(
            scriptId = script.scriptId,
            targetPackage = targetPkg,
            onProgress = { state ->
                transientScriptStatesFlow.value =
                    transientScriptStatesFlow.value + (script.scriptId to state)
            },
        )
        result.onSuccess {
            transientScriptStatesFlow.value = transientScriptStatesFlow.value - script.scriptId
            snackbarMessageFlow.value = if (fromAutoDownload) {
                tr(R.string.msg_downloaded_injected, script.skinName)
            } else {
                tr(R.string.msg_injected, script.skinName)
            }
        }.onFailure { err ->
            snackbarMessageFlow.value = errorMessage(err, R.string.msg_injection_failed)
        }
    }

    fun restoreSelectedScript() {
        val script = uiState.value.selectedScript ?: return
        if (script.lifecycleState.isBusy) return
        val targetPkg = uiState.value.targetPackage

        if (!uiState.value.shizukuState.isReadyForIo) {
            snackbarMessageFlow.value = tr(R.string.msg_restore_requires_shizuku)
            showDiagnosticsSheetFlow.value = true
            return
        }

        viewModelScope.launch {
            val result = repository.restoreScript(
                scriptId = script.scriptId,
                targetPackage = targetPkg,
                onProgress = { state ->
                    transientScriptStatesFlow.value =
                        transientScriptStatesFlow.value + (script.scriptId to state)
                },
            )
            result.onSuccess {
                transientScriptStatesFlow.value = transientScriptStatesFlow.value - script.scriptId
                snackbarMessageFlow.value = tr(R.string.msg_restored, uiState.value.selectedHero.name)
            }.onFailure { err ->
                snackbarMessageFlow.value = errorMessage(err, R.string.msg_restore_failed)
            }
        }
    }

    fun clearSelectedBackup() {
        val script = uiState.value.selectedScript ?: return
        viewModelScope.launch {
            repository.clearBackup(script.scriptId)
                .onSuccess {
                    transientScriptStatesFlow.value = transientScriptStatesFlow.value - script.scriptId
                    snackbarMessageFlow.value = tr(R.string.msg_backup_cleared, script.skinName)
                }
                .onFailure { err ->
                    snackbarMessageFlow.value = errorMessage(err, R.string.msg_clear_backup_failed)
                }
        }
    }

    fun importZipFromUri(uri: Uri) {
        val fallbackHeroId = uiState.value.selectedHeroId
        isImportingZipFlow.value = true
        viewModelScope.launch {
            repository.importZipBundle(uri, fallbackHeroId)
                .onSuccess { preview ->
                    pendingImportPreviewFlow.value = preview
                }
                .onFailure { err ->
                    snackbarMessageFlow.value = errorMessage(err, R.string.msg_zip_inspect_failed)
                }
            isImportingZipFlow.value = false
        }
    }

    fun confirmImportPreview(
        heroId: String,
        skinName: String,
        tierTag: String,
        version: String,
        notes: String,
    ) {
        val preview = pendingImportPreviewFlow.value ?: return
        viewModelScope.launch {
            val saved = repository.saveImportedScript(
                preview = preview,
                heroId = heroId,
                skinName = skinName,
                tierTag = tierTag,
                version = version,
                notes = notes,
            )
            pendingImportPreviewFlow.value = null
            selectedHeroIdFlow.value = saved.heroId
            selectedScriptByHeroFlow.value = selectedScriptByHeroFlow.value + (saved.heroId to saved.scriptId)
            activeDetailTabFlow.value = HeroDetailTab.SCRIPTS
            snackbarMessageFlow.value = tr(
                R.string.msg_staged,
                saved.skinName,
                preview.relativeTargetPaths.size,
            )
        }
    }

    fun cancelImportPreview() {
        val preview = pendingImportPreviewFlow.value ?: return
        pendingImportPreviewFlow.value = null
        viewModelScope.launch {
            repository.cancelStagedPreview(preview)
        }
    }

    fun deleteCustomScript(script: SkinScriptItem) {
        viewModelScope.launch {
            val entity = SkinScriptEntity(
                scriptId = script.scriptId,
                heroId = script.heroId,
                skinName = script.skinName,
                tierTag = script.tierTag,
                version = script.version,
                compatibilityNotes = script.compatibilityNotes,
                targetRelativePathsJoined = script.targetRelativePaths.joinToString("\n"),
                stagedDirPath = script.stagedDirPath,
                isUserImported = script.isUserImported,
                persistedStatus = if (script.lifecycleState is ScriptLifecycleState.Injected) "INJECTED" else "IDLE",
            )
            repository.deleteImportedScript(entity)
                .onSuccess {
                    snackbarMessageFlow.value = tr(R.string.msg_removed, script.skinName)
                }
                .onFailure { err ->
                    snackbarMessageFlow.value = errorMessage(err, R.string.msg_delete_failed)
                }
        }
    }

    fun updateTargetPackage(newPackage: String) {
        viewModelScope.launch {
            repository.setTargetPackage(newPackage)
                .onSuccess { validPkg ->
                    snackbarMessageFlow.value = tr(R.string.msg_target_package_set, validPkg)
                }
                .onFailure { err ->
                    snackbarMessageFlow.value = errorMessage(err, R.string.msg_invalid_package)
                }
        }
    }

    fun setDiagnosticsSheetVisible(visible: Boolean) {
        showDiagnosticsSheetFlow.value = visible
        if (visible) {
            shizukuBridge.refreshStatus()
        }
    }

    fun setDisclaimerDialogVisible(visible: Boolean) {
        showDisclaimerDialogFlow.value = visible
    }

    fun dismissDisclaimerBanner() {
        viewModelScope.launch {
            repository.setDisclaimerDismissed(true)
        }
    }

    fun restoreDisclaimerBanner() {
        viewModelScope.launch {
            repository.setDisclaimerDismissed(false)
        }
    }

    fun clearSnackbar() {
        snackbarMessageFlow.value = null
    }

    private fun resolveLifecycleState(
        entity: SkinScriptEntity,
        isSelected: Boolean,
        transient: ScriptLifecycleState?,
    ): ScriptLifecycleState {
        if (transient != null) return transient
        return when (entity.persistedStatus) {
            "INJECTED" -> ScriptLifecycleState.Injected
            "DOWNLOADED" -> ScriptLifecycleState.Downloaded
            "UNAVAILABLE" -> ScriptLifecycleState.Unavailable(
                httpCode = if (entity.errorMessage?.contains("410") == true) 410 else 404,
                reason = entity.errorMessage ?: tr(R.string.unavailable_skin_message),
            )
            "ERROR" -> ScriptLifecycleState.Error(entity.errorMessage ?: tr(R.string.msg_status_failed))
            else -> if (isSelected) ScriptLifecycleState.Selected else ScriptLifecycleState.Idle
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return runCatching {
            getApplication<Application>().packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }

    private fun findRemoteSkinsForHero(
        remoteConfig: RemoteConfig?,
        heroName: String,
    ): List<RemoteSkinEntry> {
        if (remoteConfig == null) return emptyList()
        val normalizedTarget = heroName.trim().lowercase()
        val compactTarget = normalizedTarget.replace(" ", "")
        return remoteConfig.skinsByHero.entries
            .firstOrNull { (key, _) ->
                val lower = key.trim().lowercase()
                lower == normalizedTarget || lower.replace(" ", "") == compactTarget
            }
            ?.value
            .orEmpty()
    }

    private fun buildMergedHeroList(
        localHeroes: List<HeroProfile>,
        remoteConfig: RemoteConfig?,
    ): List<HeroProfile> {
        if (remoteConfig == null || remoteConfig.availableHeroes.isEmpty()) {
            return localHeroes.map { local ->
                val portrait = HeroPortraitProvider.resolveHeroPortrait(local.name, local.previewImageUrl)
                val fallback = HeroPortraitProvider.resolveHeroFallback(local.name, local.fallbackImageUrl)
                local.copy(
                    previewImageUrl = portrait,
                    fallbackImageUrl = fallback,
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        val mergedByKey = linkedMapOf<String, HeroProfile>()

        // Enrich local companion heroes with matching remote skins & portrait URLs
        localHeroes.forEach { local ->
            val key = local.name.trim().lowercase()
            val remoteSkins = findRemoteSkinsForHero(remoteConfig, local.name)
            val remotePortrait = remoteConfig.heroPortraits[key]
                ?: remoteConfig.heroPortraits[local.name]
                ?: ""
            val remoteFallback = remoteConfig.heroFallbackPortraits[key]
                ?: remoteConfig.heroFallbackPortraits[local.name]
                ?: remotePortrait
            val portraitUrl = HeroPortraitProvider.resolveHeroPortrait(
                local.name,
                remotePortrait.ifBlank { local.previewImageUrl },
            )
            val fallbackImg = HeroPortraitProvider.resolveHeroFallback(
                local.name,
                remoteFallback.ifBlank { local.fallbackImageUrl },
            )
            mergedByKey[key] = local.copy(
                previewImageUrl = portraitUrl,
                fallbackImageUrl = fallbackImg,
                skinCount = remoteSkins.size,
            )
        }

        // Add every hero discovered across the full remote JSON with HeroRoleMapper lookup
        remoteConfig.availableHeroes.forEach { heroName ->
            val key = heroName.trim().lowercase()
            if (!mergedByKey.containsKey(key)) {
                val skins = remoteConfig.skinsByHero[heroName].orEmpty()
                val explicitHint = skins.firstOrNull { it.roleHint.isNotBlank() }?.roleHint.orEmpty()
                val resolvedRole = HeroRoleMapper.resolveRole(heroName, explicitHint)
                val remotePortrait = remoteConfig.heroPortraits[key]
                    ?: remoteConfig.heroPortraits[heroName]
                    ?: ""
                val remoteFallback = remoteConfig.heroFallbackPortraits[key]
                    ?: remoteConfig.heroFallbackPortraits[heroName]
                    ?: remotePortrait
                val portraitUrl = HeroPortraitProvider.resolveHeroPortrait(heroName, remotePortrait)
                val fallbackImg = HeroPortraitProvider.resolveHeroFallback(heroName, remoteFallback)
                val slugId = heroName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    .ifBlank { "hero_${key.hashCode()}" }

                mergedByKey[key] = HeroProfile(
                    id = slugId,
                    name = heroName,
                    aliases = emptyList(),
                    title = tr(R.string.dynamic_hero_title, skins.size),
                    role = resolvedRole,
                    specialty = if (resolvedRole == HeroRole.ALL) {
                        tr(R.string.msg_dynamic_role)
                    } else {
                        resolvedRole.localizedLabel(LocaleManager.localizedContext(getApplication()))
                    },
                    lane = tr(R.string.msg_all_lanes),
                    previewImageUrl = portraitUrl,
                    fallbackImageUrl = fallbackImg,
                    skinCount = skins.size,
                    attributes = HeroAttributes(durability = 55, offense = 72, controlEffect = 55, difficulty = 55),
                    skills = emptyList(),
                    combos = emptyList(),
                    companionNotes = tr(R.string.msg_synced_notes, skins.size),
                )
            }
        }

        return mergedByKey.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun RemoteSkinEntry.scriptIdSafe(): String {
        return id.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private data class PersistedSnapshot(
        val scripts: List<SkinScriptEntity>,
        val backups: List<BackupEntryEntity>,
        val targetPackage: String,
        val disclaimerDismissed: Boolean,
        val shizukuState: ShizukuConnectionState,
    )
}
