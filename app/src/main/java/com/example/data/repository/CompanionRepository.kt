package com.example.data.repository

import android.net.Uri
import com.example.data.catalog.HeroCatalogProvider
import com.example.data.files.InjectionPipelineManager
import com.example.data.files.ScriptArchiveManager
import com.example.data.local.AppPreferences
import com.example.data.local.BackupEntryEntity
import com.example.data.local.CompanionDao
import com.example.data.local.SkinScriptEntity
import com.example.domain.model.HeroProfile
import com.example.domain.model.ScriptLifecycleState
import com.example.domain.model.StagedBundlePreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class CompanionRepository(
    private val dao: CompanionDao,
    private val preferences: AppPreferences,
    private val archiveManager: ScriptArchiveManager,
    private val pipelineManager: InjectionPipelineManager,
) {
    val heroes: List<HeroProfile> = HeroCatalogProvider.heroes

    val allScriptsFlow: Flow<List<SkinScriptEntity>> = dao.observeAllScripts()
        .catch { emit(emptyList()) }
    val allBackupEntriesFlow: Flow<List<BackupEntryEntity>> = dao.observeAllBackupEntries()
        .catch { emit(emptyList()) }
    val targetPackageFlow: Flow<String> = preferences.targetPackageFlow
    val disclaimerDismissedFlow: Flow<Boolean> = preferences.disclaimerDismissedFlow
    val selectedHeroIdFlow: Flow<String?> = preferences.selectedHeroIdFlow
    val noticeDismissedKeyFlow: Flow<String?> = preferences.noticeDismissedKeyFlow

    suspend fun initializeCatalogIfNeeded() {
        val templates = HeroCatalogProvider.catalogScripts
        archiveManager.ensureCatalogTemplatesStaged(templates)
        dao.insertScriptsIfAbsent(
            HeroCatalogProvider.toSeedEntities(archiveManager.stagedRootDir.absolutePath)
        )
    }

    suspend fun importZipBundle(uri: Uri, fallbackHeroId: String): Result<StagedBundlePreview> {
        return archiveManager.importZipBundle(uri, fallbackHeroId)
    }

    suspend fun saveImportedScript(
        preview: StagedBundlePreview,
        heroId: String,
        skinName: String,
        tierTag: String,
        version: String,
        notes: String,
    ): SkinScriptEntity {
        val entity = SkinScriptEntity(
            scriptId = preview.scriptId,
            heroId = heroId,
            skinName = skinName.trim().ifBlank { preview.suggestedSkinName },
            tierTag = tierTag.trim().ifBlank { "Custom" },
            version = version.trim().ifBlank { "v1.0" },
            compatibilityNotes = notes.trim().ifBlank { preview.suggestedNotes },
            targetRelativePathsJoined = preview.relativeTargetPaths.joinToString("\n"),
            stagedDirPath = preview.stagedDirPath,
            isUserImported = true,
            persistedStatus = "IDLE",
            errorMessage = null,
        )
        dao.upsertScript(entity)
        return entity
    }

    suspend fun cancelStagedPreview(preview: StagedBundlePreview) {
        archiveManager.deleteStagedFiles(preview.stagedDirPath)
    }

    suspend fun deleteImportedScript(script: SkinScriptEntity): Result<Unit> {
        if (!script.isUserImported) {
            return Result.failure(IllegalStateException("Built-in catalog scripts cannot be deleted."))
        }
        if (script.persistedStatus == "INJECTED") {
            return Result.failure(IllegalStateException("Restore original files before deleting an active script."))
        }
        pipelineManager.clearBackupForScript(script)
        archiveManager.deleteStagedFiles(script.stagedDirPath)
        dao.deleteScriptById(script.scriptId)
        return Result.success(Unit)
    }

    suspend fun ensureRemoteScriptPersisted(script: com.example.domain.model.SkinScriptItem) {
        if (dao.getScriptById(script.scriptId) != null) return
        val fallbackPath = "files/dragon2017/assets/Art/Android/" +
            "${script.scriptId.lowercase().replace(Regex("[^a-z0-9._-]"), "_")}.unity3d"
        val effectivePaths = (script.targetRelativePaths.ifEmpty { listOf(fallbackPath) })
            .mapNotNull { ScriptArchiveManager.sanitizeRelativePath(it) }
            .ifEmpty { listOf(fallbackPath) }
        val stagedDir = java.io.File(
            archiveManager.stagedRootDir,
            ScriptArchiveManager.sanitizeFileSegment(script.scriptId),
        )
        effectivePaths.forEach { safeRel ->
            val outFile = java.io.File(stagedDir, safeRel)
            if (!outFile.exists()) {
                outFile.parentFile?.mkdirs()
                outFile.writeText(
                    "scriptId=${script.scriptId}\nheroId=${script.heroId}\nskinName=${script.skinName}\nversion=${script.version}\n"
                )
            }
        }
        dao.upsertScript(
            SkinScriptEntity(
                scriptId = script.scriptId,
                heroId = script.heroId,
                skinName = script.skinName,
                tierTag = script.tierTag,
                version = script.version,
                compatibilityNotes = script.compatibilityNotes,
                targetRelativePathsJoined = effectivePaths.joinToString("\n"),
                stagedDirPath = stagedDir.absolutePath,
                isUserImported = false,
                persistedStatus = "IDLE",
                errorMessage = null,
            )
        )
    }

    suspend fun downloadRemoteScriptBundle(
        script: com.example.domain.model.SkinScriptItem,
        onProgress: (Float) -> Unit,
    ): Result<com.example.data.files.StagedDownloadResult> {
        val result = archiveManager.downloadRemoteScript(
            scriptId = script.scriptId,
            downloadUrl = script.downloadUrl,
            fallbackRelativePaths = script.targetRelativePaths,
            onProgress = onProgress,
        )
        val outcome = result.getOrNull()
        if (outcome != null) {
            val existing = dao.getScriptById(script.scriptId)
            val nextStatus = if (existing?.persistedStatus == "INJECTED") "INJECTED" else "DOWNLOADED"
            dao.upsertScript(
                SkinScriptEntity(
                    scriptId = script.scriptId,
                    heroId = script.heroId,
                    skinName = script.skinName,
                    tierTag = script.tierTag,
                    version = script.version,
                    compatibilityNotes = script.compatibilityNotes,
                    targetRelativePathsJoined = outcome.relativePaths.joinToString("\n"),
                    stagedDirPath = outcome.stagedDirPath,
                    isUserImported = false,
                    persistedStatus = nextStatus,
                    errorMessage = null,
                )
            )
        }
        return result
    }

    suspend fun markScriptUnavailable(
        script: com.example.domain.model.SkinScriptItem,
        httpCode: Int = 404,
        message: String = "This skin is currently unavailable ($httpCode). Please try another skin.",
    ) {
        val existing = dao.getScriptById(script.scriptId)
        if (existing != null &&
            (existing.persistedStatus == "DOWNLOADED" || existing.persistedStatus == "INJECTED")
        ) {
            return
        }
        val effectivePaths = existing?.targetRelativePathsJoined
            ?.takeIf { it.isNotBlank() }
            ?: script.targetRelativePaths.joinToString("\n")
        val stagedDir = existing?.stagedDirPath?.takeIf { it.isNotBlank() }
            ?: script.stagedDirPath
        dao.upsertScript(
            SkinScriptEntity(
                scriptId = script.scriptId,
                heroId = script.heroId,
                skinName = script.skinName.ifBlank { "Default Skin" },
                tierTag = script.tierTag,
                version = script.version,
                compatibilityNotes = script.compatibilityNotes,
                targetRelativePathsJoined = effectivePaths,
                stagedDirPath = stagedDir,
                isUserImported = script.isUserImported,
                persistedStatus = "UNAVAILABLE",
                errorMessage = message,
            )
        )
    }

    suspend fun checkRemoteUrlStatus(url: String): Int? = archiveManager.checkUrlAvailability(url)

    suspend fun injectScript(
        scriptId: String,
        targetPackage: String,
        onProgress: (ScriptLifecycleState) -> Unit,
    ): Result<Unit> {
        val script = dao.getScriptById(scriptId)
            ?: return Result.failure(IllegalArgumentException("Selected script not found."))
        if (script.persistedStatus == "UNAVAILABLE") {
            return Result.failure(
                com.example.data.files.RemoteSkinUnavailableException(
                    httpCode = 404,
                    message = script.errorMessage ?: "This skin is currently unavailable (404). Please try another skin.",
                )
            )
        }
        val activeForHero = dao.getActiveInjectedForHero(script.heroId)
        return pipelineManager.injectScript(
            script = script,
            targetPackage = targetPackage,
            previouslyActiveScript = activeForHero,
            onProgress = onProgress,
        )
    }

    suspend fun restoreScript(
        scriptId: String,
        targetPackage: String,
        onProgress: (ScriptLifecycleState) -> Unit,
    ): Result<Unit> {
        val script = dao.getScriptById(scriptId)
            ?: return Result.failure(IllegalArgumentException("Selected script not found."))
        return pipelineManager.restoreScript(script, targetPackage, onProgress)
    }

    suspend fun clearBackup(scriptId: String): Result<Unit> {
        val script = dao.getScriptById(scriptId)
            ?: return Result.failure(IllegalArgumentException("Selected script not found."))
        if (script.persistedStatus == "INJECTED") {
            return Result.failure(
                IllegalStateException("Restore original files first before clearing this active backup.")
            )
        }
        return pipelineManager.clearBackupForScript(script)
    }

    suspend fun setTargetPackage(pkg: String): Result<String> = preferences.setTargetPackage(pkg)

    suspend fun setDisclaimerDismissed(dismissed: Boolean) =
        preferences.setDisclaimerDismissed(dismissed)

    suspend fun setSelectedHeroId(heroId: String) = preferences.setSelectedHeroId(heroId)

    suspend fun setNoticeDismissedKey(key: String?) = preferences.setNoticeDismissedKey(key)
}
