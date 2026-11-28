package com.example.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Shell-backed compatibility bridge for Android 16 devices where Shizuku 13.6.0 cannot start
 * its UserService. It uses Shizuku's existing privileged process API instead of loading an
 * application class, so it is not affected by the LoadedApk crash in that Shizuku build.
 */
internal class ShellFileBridgeService : IFileBridgeService {

    private val binder = Binder()

    override fun asBinder(): IBinder = binder

    override fun destroy() = Unit

    override fun getUid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    override fun exists(path: String): Boolean {
        val target = validate(path) ?: return false
        return run(shellTest("-e", target)).exitCode == 0
    }

    override fun isDirectory(path: String): Boolean {
        val target = validate(path) ?: return false
        return run(shellTest("-d", target)).exitCode == 0
    }

    override fun getFileSize(path: String): Long {
        val target = validate(path) ?: return -1L
        val result = run(shell("wc -c < ${quote(target)}"))
        if (result.exitCode != 0) return -1L
        return result.stdout.trim().toLongOrNull() ?: -1L
    }

    override fun mkdirs(path: String): Boolean {
        val target = validate(path) ?: return false
        return run(shell("mkdir -p ${quote(target)}")).exitCode == 0
    }

    override fun deleteRecursive(path: String): Boolean {
        val target = validate(path) ?: return false
        return run(shell("rm -rf ${quote(target)}")).exitCode == 0
    }

    override fun listFiles(path: String): Array<String> {
        val target = validate(path) ?: return emptyArray()
        val result = run(shell("ls -1A ${quote(target)}"))
        if (result.exitCode != 0) return emptyArray()
        return result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
            .toList()
            .toTypedArray()
    }

    override fun openRead(path: String): ParcelFileDescriptor? {
        val target = validate(path)
            ?: throw SecurityException("Invalid or restricted read path: $path")
        if (run(shellTest("-f", target)).exitCode != 0) {
            throw IOException("Target file does not exist: $target")
        }

        val process = startProcess(shell("cat ${quote(target)}"))
        val pipe = ParcelFileDescriptor.createPipe()
        thread(name = "shizuku-shell-read", isDaemon = true) {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(process.getInputStream()).use { input ->
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        input.copyTo(output, COPY_BUFFER_SIZE)
                    }
                }
            } catch (_: Throwable) {
                runCatching { pipe[1].close() }
            } finally {
                runCatching { process.waitFor() }
                runCatching { process.destroy() }
            }
        }
        return pipe[0]
    }

    override fun openWrite(path: String, overwrite: Boolean): ParcelFileDescriptor? {
        val target = validate(path)
            ?: throw SecurityException("Invalid or restricted write path: $path")
        if (!overwrite && exists(target)) {
            throw IOException("Target file already exists and overwrite=false: $target")
        }

        val parent = File(target).parentFile
        if (parent != null) {
            val parentPath = validate(parent.path)
                ?: throw SecurityException("Invalid target parent: ${parent.path}")
            val result = run(shell("mkdir -p ${quote(parentPath)}"))
            if (result.exitCode != 0) {
                throw IOException("Failed to create parent directory: $parentPath")
            }
        }

        val process = startProcess(shell("cat > ${quote(target)}"))
        val pipe = ParcelFileDescriptor.createPipe()
        thread(name = "shizuku-shell-write", isDaemon = true) {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
                    ParcelFileDescriptor.AutoCloseOutputStream(process.getOutputStream()).use { output ->
                        input.copyTo(output, COPY_BUFFER_SIZE)
                    }
                }
            } catch (_: Throwable) {
                runCatching { pipe[1].close() }
            } finally {
                runCatching { process.waitFor() }
                runCatching { process.destroy() }
            }
        }
        return pipe[1]
    }

    private data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun run(command: Array<String>): ShellResult {
        val process = startProcess(command)
        return try {
            // Drain stderr concurrently so large diagnostics cannot block stdout reading.
            val stderr = StringBuilder()
            val stderrThread = thread(name = "shizuku-shell-stderr", isDaemon = true) {
                runCatching { process.getErrorStream()?.let(::readFully).orEmpty() }
                    .onSuccess { stderr.append(it) }
            }
            val stdout = process.getInputStream()?.let(::readFully).orEmpty()
            stderrThread.join()
            ShellResult(process.waitFor(), stdout, stderr.toString())
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun startProcess(command: Array<String>): IRemoteProcess {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku service is not available")
        val service = IShizukuService.Stub.asInterface(binder)
        return service.newProcess(command, null, null)
            ?: throw IllegalStateException("Shizuku returned no process binder")
    }

    private fun readFully(descriptor: ParcelFileDescriptor): String =
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }

    private fun shell(command: String): Array<String> = arrayOf("sh", "-c", command)

    private fun shellTest(option: String, path: String): Array<String> =
        shell("[ $option ${quote(path)} ]")

    private fun validate(raw: String): String? {
        if (raw.isBlank() || raw.contains('\u0000') || raw.contains('\n') || raw.contains('\r')) {
            return null
        }
        val canonical = runCatching { File(raw).canonicalPath }.getOrNull() ?: return null
        val allowedPrefix = canonical.startsWith("/storage/emulated/") ||
            canonical.startsWith("/sdcard/Android/data/") ||
            canonical.startsWith("/mnt/user/")
        val isAndroidDataPath = canonical.contains("/Android/data/") ||
            canonical.endsWith("/Android/data")
        return canonical.takeIf { allowedPrefix && isAndroidDataPath }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    companion object {
        private const val COPY_BUFFER_SIZE = 64 * 1024
    }
}
