package com.example.shizuku

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.annotation.Keep
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

/**
 * Privileged Shizuku UserService running in the Shell process (UID 2000) or Root (UID 0).
 * All file I/O targeting scoped directories under /storage/emulated/0/Android/data/
 * is executed through this AIDL binder rather than from the untrusted app process.
 *
 * Shizuku first looks for a Context constructor and falls back to the no-arg constructor.
 * Both are kept explicitly because the class is loaded by reflection in another process.
 */
@Keep
class FileBridgeUserService : IFileBridgeService.Stub {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused")
    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    override fun exists(path: String): Boolean {
        val file = validateTargetFile(path) ?: return false
        return file.exists()
    }

    override fun isDirectory(path: String): Boolean {
        val file = validateTargetFile(path) ?: return false
        return file.isDirectory
    }

    override fun getFileSize(path: String): Long {
        val file = validateTargetFile(path) ?: return -1L
        return if (file.exists() && file.isFile) file.length() else -1L
    }

    override fun mkdirs(path: String): Boolean {
        val dir = validateTargetFile(path) ?: return false
        if (dir.exists()) return dir.isDirectory
        return dir.mkdirs()
    }

    override fun deleteRecursive(path: String): Boolean {
        val target = validateTargetFile(path) ?: return false
        if (!target.exists()) return true
        return target.deleteRecursively()
    }

    override fun listFiles(path: String): Array<String> {
        val dir = validateTargetFile(path) ?: return emptyArray()
        if (!dir.exists() || !dir.isDirectory) return emptyArray()
        return dir.listFiles()?.map { it.name }?.sorted()?.toTypedArray() ?: emptyArray()
    }

    override fun openRead(path: String): ParcelFileDescriptor? {
        val file = validateTargetFile(path)
            ?: throw SecurityException("Invalid or restricted read path: $path")
        if (!file.exists() || !file.isFile) {
            throw IOException("Target file does not exist: ${file.absolutePath}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openWrite(path: String, overwrite: Boolean): ParcelFileDescriptor? {
        val file = validateTargetFile(path)
            ?: throw SecurityException("Invalid or restricted write path: $path")
        if (file.exists() && !overwrite) {
            throw IOException("Target file already exists and overwrite=false: ${file.absolutePath}")
        }
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create parent directory: ${parent.absolutePath}")
        }
        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_TRUNCATE,
        )
    }

    private fun validateTargetFile(rawPath: String): File? {
        if (rawPath.isBlank() || rawPath.contains('\u0000')) return null
        val file = File(rawPath)
        val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return null
        val allowedPrefixes = listOf(
            "/storage/emulated/",
            "/sdcard/Android/data/",
            "/mnt/user/",
        )
        val isAndroidDataPath = allowedPrefixes.any { prefix ->
            canonical.startsWith(prefix) && canonical.contains("/Android/data/")
        }
        return if (isAndroidDataPath) file else null
    }
}
