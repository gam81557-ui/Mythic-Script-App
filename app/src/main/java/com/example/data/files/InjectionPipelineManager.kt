package com.example.data.files

import android.content.Context
import android.os.ParcelFileDescriptor
import com.example.data.local.AppPreferences
import com.example.data.local.BackupEntryEntity
import com.example.data.local.CompanionDao
import com.example.data.local.SkinScriptEntity
import com.example.domain.model.ScriptLifecycleState
import com.example.shizuku.IFileBridgeService
import com.example.shizuku.ShizukuBridgeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class InjectionPipelineManager(
    private val context: Context,
    private val dao: CompanionDao,
    private val shizukuBridge: ShizukuBridgeManager,
) {

    val backupRootDir: File
        get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun injectScript(
        script: SkinScriptEntity,
        targetPackage: String,
        previouslyActiveScript: SkinScriptEntity?,
        onProgress: (ScriptLifecycleState) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!AppPreferences.isValidPackageName(targetPackage)) {
                throw IllegalArgumentException("Invalid target package name: $targetPackage")
            }

            val relativePaths = parseRelativePaths(script.targetRelativePathsJoined)
            if (relativePaths.isEmpty()) {
                throw IllegalStateException("Script '${script.skinName}' contains no target asset paths.")
            }

            val stagedDir = File(script.stagedDirPath)
            if (!stagedDir.exists() || !stagedDir.isDirectory) {
                throw IllegalStateException("Staged bundle files are missing. Re-import or re-stage the bundle.")
            }

            // Validate every staged file exists and is safe before touching Shizuku or target paths.
            val validatedPairs = relativePaths.map { relPath ->
                val safeRel = ScriptArchiveManager.sanitizeRelativePath(relPath)
                    ?: throw SecurityException("Invalid target relative path: $relPath")
                val stagedFile = File(stagedDir, safeRel)
                if (!stagedFile.exists() || !stagedFile.isFile) {
                    throw IllegalStateException("Staged asset missing: $safeRel")
                }
                safeRel to stagedFile
            }

            // Single-select quick switch: restore the previously active script on the same hero first.
            if (previouslyActiveScript != null && previouslyActiveScript.scriptId != script.scriptId) {
                restoreInternal(
                    script = previouslyActiveScript,
                    targetPackage = targetPackage,
                    onProgress = { state -> onProgress(state) },
                ).getOrThrow()
            }

            val baseTargetDir = buildTargetPackageRoot(targetPackage)
            val scriptBackupDir = File(backupRootDir, "${script.heroId}/${script.scriptId}").apply {
                mkdirs()
            }

            // Step 1 & 2: Back up originals + inject staged files inside a single Shizuku service lock.
            shizukuBridge.withService { service ->
                val existingBackups = dao.getBackupEntriesForScript(script.scriptId)
                val backupManifest = mutableListOf<BackupEntryEntity>()

                // Backup existing target files if no active backup exists yet (preserves true original during re-inject).
                if (existingBackups.isEmpty()) {
                    validatedPairs.forEachIndexed { index, (relPath, _) ->
                        val progress = (index + 1).toFloat() / validatedPairs.size.toFloat()
                        onProgress(ScriptLifecycleState.BackingUp(progress, relPath))

                        val targetPath = "$baseTargetDir/$relPath"
                        val backupFile = File(scriptBackupDir, relPath)
                        if (service.exists(targetPath) && !service.isDirectory(targetPath)) {
                            backupFile.parentFile?.mkdirs()
                            copyFromRemoteToLocal(service, targetPath, backupFile)
                            backupManifest.add(
                                BackupEntryEntity(
                                    scriptId = script.scriptId,
                                    heroId = script.heroId,
                                    relativePath = relPath,
                                    backupFilePath = backupFile.absolutePath,
                                    existedBeforeInjection = true,
                                    byteSize = backupFile.length(),
                                )
                            )
                        } else {
                            backupManifest.add(
                                BackupEntryEntity(
                                    scriptId = script.scriptId,
                                    heroId = script.heroId,
                                    relativePath = relPath,
                                    backupFilePath = backupFile.absolutePath,
                                    existedBeforeInjection = false,
                                    byteSize = 0L,
                                )
                            )
                        }
                    }
                    dao.upsertBackupEntries(backupManifest)
                }

                // Inject staged assets through Shizuku UserService.
                validatedPairs.forEachIndexed { index, (relPath, stagedFile) ->
                    val progress = (index + 1).toFloat() / validatedPairs.size.toFloat()
                    onProgress(ScriptLifecycleState.Injecting(progress, relPath))

                    val targetPath = "$baseTargetDir/$relPath"
                    val targetParent = File(targetPath).parent
                    if (!targetParent.isNullOrBlank()) {
                        service.mkdirs(targetParent)
                    }
                    copyFromLocalToRemote(service, stagedFile, targetPath)
                }
            }.getOrThrow()

            dao.updateScriptStatus(scriptId = script.scriptId, status = "INJECTED", errorMessage = null)
            onProgress(ScriptLifecycleState.Injected)
        }.onFailure { err ->
            val message = err.message ?: "Injection failed due to an unexpected I/O error."
            dao.updateScriptStatus(scriptId = script.scriptId, status = "ERROR", errorMessage = message)
            onProgress(ScriptLifecycleState.Error(message))
        }
    }

    suspend fun restoreScript(
        script: SkinScriptEntity,
        targetPackage: String,
        onProgress: (ScriptLifecycleState) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        restoreInternal(script, targetPackage, onProgress)
    }

    private suspend fun restoreInternal(
        script: SkinScriptEntity,
        targetPackage: String,
        onProgress: (ScriptLifecycleState) -> Unit,
    ): Result<Unit> = runCatching {
        if (!AppPreferences.isValidPackageName(targetPackage)) {
            throw IllegalArgumentException("Invalid target package name: $targetPackage")
        }

        val entries = dao.getBackupEntriesForScript(script.scriptId)
        if (entries.isEmpty()) {
            throw IllegalStateException("No backup manifest found for '${script.skinName}'.")
        }

        val baseTargetDir = buildTargetPackageRoot(targetPackage)
        shizukuBridge.withService { service ->
            entries.forEachIndexed { index, entry ->
                val progress = (index + 1).toFloat() / entries.size.toFloat()
                onProgress(ScriptLifecycleState.Restoring(progress, entry.relativePath))

                val targetPath = "$baseTargetDir/${entry.relativePath}"
                if (entry.existedBeforeInjection) {
                    val backupFile = File(entry.backupFilePath)
                    if (!backupFile.exists()) {
                        throw IllegalStateException("Backup file missing: ${entry.relativePath}")
                    }
                    val targetParent = File(targetPath).parent
                    if (!targetParent.isNullOrBlank()) {
                        service.mkdirs(targetParent)
                    }
                    copyFromLocalToRemote(service, backupFile, targetPath)
                } else {
                    if (service.exists(targetPath)) {
                        service.deleteRecursive(targetPath)
                    }
                }
            }
        }.getOrThrow()

        dao.updateScriptStatus(scriptId = script.scriptId, status = "IDLE", errorMessage = null)
        onProgress(ScriptLifecycleState.Selected)
    }.onFailure { err ->
        val message = err.message ?: "Restore failed."
        dao.updateScriptStatus(scriptId = script.scriptId, status = "ERROR", errorMessage = message)
        onProgress(ScriptLifecycleState.Error(message))
    }

    suspend fun clearBackupForScript(script: SkinScriptEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val backupDir = File(backupRootDir, "${script.heroId}/${script.scriptId}")
                if (backupDir.exists()) {
                    backupDir.deleteRecursively()
                }
                dao.deleteBackupEntriesForScript(script.scriptId)
                if (script.persistedStatus == "ERROR") {
                    dao.updateScriptStatus(script.scriptId, "IDLE", null)
                }
            }
        }

    private fun copyFromRemoteToLocal(
        service: IFileBridgeService,
        remotePath: String,
        localDest: File,
    ) {
        val pfd = service.openRead(remotePath)
            ?: throw IllegalStateException("Shizuku returned null FD for read: $remotePath")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            FileOutputStream(localDest).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }

    private fun copyFromLocalToRemote(
        service: IFileBridgeService,
        localSrc: File,
        remotePath: String,
    ) {
        val pfd = service.openWrite(remotePath, true)
            ?: throw IllegalStateException("Shizuku returned null FD for write: $remotePath")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { output ->
            FileInputStream(localSrc).use { input ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }

    companion object {
        fun buildTargetPackageRoot(targetPackage: String): String {
            return "/storage/emulated/0/Android/data/$targetPackage"
        }

        fun parseRelativePaths(joined: String): List<String> {
            return joined.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
