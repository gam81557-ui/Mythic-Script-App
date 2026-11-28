package com.example.data.files

import android.content.Context
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import com.example.data.local.AppPreferences
import com.example.data.local.BackupEntryEntity
import com.example.data.local.CompanionDao
import com.example.data.local.SkinScriptEntity
import com.example.domain.model.ScriptLifecycleState
import com.example.shizuku.IFileBridgeService
import com.example.shizuku.LocalFileBridgeService
import com.example.shizuku.ShizukuBridgeManager
import com.example.shizuku.ShizukuServiceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ShizukuRequiredException(
    message: String = "",
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class InjectionPipelineManager(
    private val context: Context,
    private val dao: CompanionDao,
    private val shizukuBridge: ShizukuBridgeManager,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val batchYieldMs: Long = DEFAULT_BATCH_YIELD_MS,
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

            if (script.stagedDirPath.isBlank()) {
                throw IllegalStateException("Staged bundle path is missing. Re-import or re-stage the bundle.")
            }
            val stagedRoot = File(context.filesDir, "staged_scripts").canonicalFile
            val stagedDir = File(script.stagedDirPath).canonicalFile
            if (!stagedDir.exists() || !stagedDir.isDirectory) {
                throw IllegalStateException("Staged bundle files are missing. Re-import or re-stage the bundle.")
            }
            if (!stagedDir.path.startsWith(stagedRoot.path + File.separator)) {
                throw SecurityException("Staged bundle path is outside the app staging area.")
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
            val scriptBackupDir = File(
                backupRootDir,
                "${safeSegment(script.heroId)}/${safeSegment(script.scriptId)}",
            ).apply {
                mkdirs()
            }

            Log.i(
                TAG,
                "Starting inject script=${script.scriptId} pkg=$targetPackage files=${validatedPairs.size} " +
                    "batchSize=$batchSize binder=${shizukuBridge.isShizukuBinderAlive()}",
            )

            // Check the binder/permission before any backup or target write. This avoids
            // starting a 53-file operation against a dead service and failing halfway through.
            Log.d(TAG, "Preflight target access for $targetPackage")
            ensureTargetAccess(targetPackage)
            Log.d(TAG, "Preflight passed; binder=${shizukuBridge.isShizukuBinderAlive()}")

            // Step 1 & 2: Back up originals + inject staged files in small batches. Re-checking
            // the service for each batch lets a dropped UserService rebind cleanly.
            val existingBackups = dao.getBackupEntriesForScript(script.scriptId)
            if (existingBackups.isEmpty()) {
                val backupManifest = linkedMapOf<String, BackupEntryEntity>()
                forEachBatch(validatedPairs) { batch, offset, batchNumber ->
                    withTargetServiceWithRetry(
                        targetPackage = targetPackage,
                        operation = "backup",
                        batchNumber = batchNumber,
                    ) { service ->
                        batch.forEachIndexed { batchIndex, (relPath, _) ->
                            val index = offset + batchIndex
                            val progress = (index + 1).toFloat() / validatedPairs.size.toFloat()
                            onProgress(ScriptLifecycleState.BackingUp(progress, relPath))

                            val targetPath = "$baseTargetDir/$relPath"
                            val backupFile = File(scriptBackupDir, relPath)
                            val exists = ioStep("Backup exists check for '$targetPath'", relPath) {
                                service.exists(targetPath)
                            }
                            val isDirectory = if (exists) {
                                ioStep("Backup directory check for '$targetPath'", relPath) {
                                    service.isDirectory(targetPath)
                                }
                            } else {
                                false
                            }
                            if (exists && !isDirectory) {
                                backupFile.parentFile?.mkdirs()
                                ioStep("Backup read '$targetPath'", relPath) {
                                    copyFromRemoteToLocal(service, targetPath, backupFile)
                                }
                                backupManifest[relPath] = BackupEntryEntity(
                                    scriptId = script.scriptId,
                                    heroId = script.heroId,
                                    relativePath = relPath,
                                    backupFilePath = backupFile.absolutePath,
                                    existedBeforeInjection = true,
                                    byteSize = backupFile.length(),
                                )
                            } else {
                                backupManifest[relPath] = BackupEntryEntity(
                                    scriptId = script.scriptId,
                                    heroId = script.heroId,
                                    relativePath = relPath,
                                    backupFilePath = backupFile.absolutePath,
                                    existedBeforeInjection = false,
                                    byteSize = 0L,
                                )
                            }
                        }
                    }.getOrThrow()
                }
                if (backupManifest.isNotEmpty()) {
                    dao.upsertBackupEntries(backupManifest.values.toList())
                }
            }

            // Inject staged assets in batches so a large skin does not monopolize the binder.
            forEachBatch(validatedPairs) { batch, offset, batchNumber ->
                withTargetServiceWithRetry(
                    targetPackage = targetPackage,
                    operation = "inject",
                    batchNumber = batchNumber,
                ) { service ->
                    batch.forEachIndexed { batchIndex, (relPath, stagedFile) ->
                        val index = offset + batchIndex
                        val progress = (index + 1).toFloat() / validatedPairs.size.toFloat()
                        onProgress(ScriptLifecycleState.Injecting(progress, relPath))

                        val targetPath = "$baseTargetDir/$relPath"
                        val targetParent = File(targetPath).parent
                        if (!targetParent.isNullOrBlank()) {
                            ioStep("Inject mkdir '$targetParent'", relPath) {
                                service.mkdirs(targetParent)
                            }
                        }
                        ioStep("Inject write '$targetPath'", relPath) {
                            copyFromLocalToRemote(service, stagedFile, targetPath)
                        }
                    }
                }.getOrThrow()
            }
            Log.i(TAG, "Inject completed for ${script.scriptId}")

            dao.updateScriptStatus(scriptId = script.scriptId, status = "INJECTED", errorMessage = null)
            onProgress(ScriptLifecycleState.Injected)
        }.onFailure { err ->
            val message = err.message ?: "Injection failed due to an unexpected I/O error."
            runCatching {
                dao.updateScriptStatus(scriptId = script.scriptId, status = "ERROR", errorMessage = message)
            }
            runCatching { onProgress(ScriptLifecycleState.Error(message)) }
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

        ensureTargetAccess(targetPackage)
        val baseTargetDir = buildTargetPackageRoot(targetPackage)
        forEachBatch(entries) { batch, offset, batchNumber ->
            withTargetServiceWithRetry(
                targetPackage = targetPackage,
                operation = "restore",
                batchNumber = batchNumber,
            ) { service ->
                batch.forEachIndexed { batchIndex, entry ->
                    val index = offset + batchIndex
                    val progress = (index + 1).toFloat() / entries.size.toFloat()
                    onProgress(ScriptLifecycleState.Restoring(progress, entry.relativePath))

                    val safeRelativePath = ScriptArchiveManager.sanitizeRelativePath(entry.relativePath)
                        ?: throw SecurityException("Invalid backup relative path: ${entry.relativePath}")
                    val targetPath = "$baseTargetDir/$safeRelativePath"
                    if (entry.existedBeforeInjection) {
                        val backupRoot = backupRootDir.canonicalFile
                        val backupFile = File(entry.backupFilePath).canonicalFile
                        if (!backupFile.path.startsWith(backupRoot.path + File.separator)) {
                            throw SecurityException("Backup file is outside the app backup area.")
                        }
                        if (!backupFile.exists()) {
                            throw IllegalStateException("Backup file missing: $safeRelativePath")
                        }
                        val targetParent = File(targetPath).parent
                        if (!targetParent.isNullOrBlank()) {
                            ioStep("Restore mkdir '$targetParent'", safeRelativePath) {
                                service.mkdirs(targetParent)
                            }
                        }
                        ioStep("Restore write '$targetPath'", safeRelativePath) {
                            copyFromLocalToRemote(service, backupFile, targetPath)
                        }
                    } else {
                        val exists = ioStep("Restore exists check for '$targetPath'", safeRelativePath) {
                            service.exists(targetPath)
                        }
                        if (exists) {
                            ioStep("Restore delete '$targetPath'", safeRelativePath) {
                                service.deleteRecursive(targetPath)
                            }
                        }
                    }
                }
            }.getOrThrow()
        }

        dao.updateScriptStatus(scriptId = script.scriptId, status = "IDLE", errorMessage = null)
        onProgress(ScriptLifecycleState.Selected)
    }.onFailure { err ->
        val message = err.message ?: "Restore failed."
        runCatching {
            dao.updateScriptStatus(scriptId = script.scriptId, status = "ERROR", errorMessage = message)
        }
        runCatching { onProgress(ScriptLifecycleState.Error(message)) }
    }

    suspend fun clearBackupForScript(script: SkinScriptEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val backupRoot = backupRootDir.canonicalFile
                val backupDir = File(
                    backupRoot,
                    "${safeSegment(script.heroId)}/${safeSegment(script.scriptId)}",
                ).canonicalFile
                if (backupDir.path.startsWith(backupRoot.path + File.separator) && backupDir.exists()) {
                    backupDir.deleteRecursively()
                }
                dao.deleteBackupEntriesForScript(script.scriptId)
                if (script.persistedStatus == "ERROR") {
                    dao.updateScriptStatus(script.scriptId, "IDLE", null)
                }
            }
        }

    private suspend fun ensureTargetAccess(targetPackage: String) {
        if (LocalFileBridgeService().canAccess(buildTargetPackageRoot(targetPackage))) return
        shizukuBridge.prepareServiceForIo().getOrElse { error ->
            throw error.asShizukuRequired()
        }
    }

    private suspend fun <T> withTargetService(
        targetPackage: String,
        block: suspend (IFileBridgeService) -> T,
    ): Result<T> {
        val direct = LocalFileBridgeService()
        if (direct.canAccess(buildTargetPackageRoot(targetPackage))) {
            return try {
                Result.success(block(direct))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                Result.failure(t)
            }
        }
        shizukuBridge.prepareServiceForIo().onFailure { error ->
            return Result.failure(error.asShizukuRequired())
        }
        return shizukuBridge.withService(block).fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    if (error is ShizukuServiceUnavailableException) error.asShizukuRequired() else error
                )
            },
        )
    }

    private suspend fun <T> withTargetServiceWithRetry(
        targetPackage: String,
        operation: String,
        batchNumber: Int,
        block: suspend (IFileBridgeService) -> T,
    ): Result<T> {
        val usesShizuku = !LocalFileBridgeService().canAccess(buildTargetPackageRoot(targetPackage))
        var lastError: Throwable = ShizukuServiceUnavailableException(
            "Shizuku service is disconnected. Reconnect and retry."
        )
        repeat(MAX_SERVICE_ATTEMPTS) { attempt ->
            if (usesShizuku && !shizukuBridge.isShizukuBinderAlive()) {
                lastError = ShizukuServiceUnavailableException(
                    "Shizuku binder disconnected before $operation batch #$batchNumber."
                )
            } else {
                Log.d(
                    TAG,
                    "$operation batch #$batchNumber start (attempt=${attempt + 1}, " +
                        "binder=${shizukuBridge.isShizukuBinderAlive()}, direct=${!usesShizuku})",
                )
                val result = withTargetService(targetPackage) { service ->
                    if (!service.asBinder().isBinderAlive) {
                        throw DeadObjectException("Shizuku file service is no longer alive")
                    }
                    block(service)
                }
                if (result.isSuccess) {
                    if (!usesShizuku || shizukuBridge.isShizukuBinderAlive()) {
                        Log.d(TAG, "$operation batch #$batchNumber completed")
                        return result
                    }
                    lastError = ShizukuServiceUnavailableException(
                        "Shizuku binder disconnected after $operation batch #$batchNumber."
                    )
                } else {
                    val error = result.exceptionOrNull() ?: return result
                    if (error is CancellationException) throw error
                    lastError = error
                }
            }

            val error = lastError
            if (!error.isTransientBinderFailure()) return Result.failure(error)
            if (attempt < MAX_SERVICE_ATTEMPTS - 1) {
                Log.w(TAG, "$operation batch #$batchNumber lost the binder; retrying once", error)
                delay(RETRY_DELAY_MS)
            }
        }
        val error = lastError
        return Result.failure(
            if (error.isTransientBinderFailure()) {
                val detail = error.message?.takeIf { it.isNotBlank() }
                ShizukuRequiredException(
                    "Shizuku disconnected during $operation batch #$batchNumber. " +
                        "Open Shizuku, reconnect it, then tap Inject again." +
                        if (detail != null) " ($detail)" else "",
                    error,
                )
            } else {
                error
            }
        )
    }

    private fun Throwable.asShizukuRequired(): ShizukuRequiredException =
        if (this is ShizukuRequiredException) {
            this
        } else {
            ShizukuRequiredException(
                message?.takeIf { it.isNotBlank() }
                    ?: "Shizuku service is disconnected. Reconnect and retry.",
                this,
            )
        }

    private fun Throwable.isTransientBinderFailure(): Boolean {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = this
        while (current != null && seen.add(current)) {
            if (current is DeadObjectException || current is RemoteException) return true
            val message = current.message.orEmpty()
            if (current is ShizukuServiceUnavailableException ||
                current is ShizukuRequiredException
            ) {
                return !message.contains("permission", ignoreCase = true) &&
                    !message.contains("not running", ignoreCase = true) &&
                    !message.contains("not installed", ignoreCase = true)
            }
            if (message.contains("binder", ignoreCase = true) ||
                message.contains("service disconnected", ignoreCase = true) ||
                message.contains("UserService", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun fileFailure(action: String, relativePath: String, cause: Throwable): Throwable {
        Log.e(TAG, "$action failed for '$relativePath'", cause)
        return IllegalStateException(
            "$action failed for '$relativePath': ${cause.message ?: cause.javaClass.simpleName}",
            cause,
        )
    }

    private inline fun <T> ioStep(
        action: String,
        relativePath: String,
        block: () -> T,
    ): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        throw fileFailure(action, relativePath, t)
    }

    private suspend fun <T> forEachBatch(
        items: List<T>,
        block: suspend (batch: List<T>, offset: Int, batchNumber: Int) -> Unit,
    ) {
        if (items.isEmpty()) return
        val size = batchSize.coerceAtLeast(1)
        var offset = 0
        var batchNumber = 1
        while (offset < items.size) {
            val end = (offset + size).coerceAtMost(items.size)
            block(items.subList(offset, end), offset, batchNumber)
            offset = end
            batchNumber += 1
            if (offset < items.size) {
                yield()
                if (batchYieldMs > 0L) delay(batchYieldMs)
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

    private fun safeSegment(value: String): String =
        ScriptArchiveManager.sanitizeFileSegment(value)

    companion object {
        const val DEFAULT_BATCH_SIZE = 8
        const val DEFAULT_BATCH_YIELD_MS = 15L
        private const val TAG = "InjectionPipeline"
        private const val MAX_SERVICE_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 250L

        fun buildTargetPackageRoot(targetPackage: String): String {
            return "/storage/emulated/0/Android/data/$targetPackage"
        }

        fun parseRelativePaths(joined: String): List<String> {
            return joined.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
