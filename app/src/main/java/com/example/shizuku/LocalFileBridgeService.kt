package com.example.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.io.IOException

/**
 * Direct file access for Android versions where /Android/data is readable by the app
 * (Android 10 and older with storage permission). It mirrors the AIDL bridge so the
 * injection pipeline can use one code path.
 */
internal class LocalFileBridgeService : IFileBridgeService {

    private val binder = Binder()

    override fun asBinder(): IBinder = binder

    override fun destroy() = Unit

    override fun getUid(): Int = Process.myUid()

    override fun exists(path: String): Boolean {
        val file = validate(path) ?: return false
        return file.exists()
    }

    override fun isDirectory(path: String): Boolean {
        val file = validate(path) ?: return false
        return file.isDirectory
    }

    override fun getFileSize(path: String): Long {
        val file = validate(path) ?: return -1L
        return if (file.exists() && file.isFile) file.length() else -1L
    }

    override fun mkdirs(path: String): Boolean {
        val dir = validate(path) ?: return false
        if (dir.exists()) return dir.isDirectory
        return dir.mkdirs()
    }

    override fun deleteRecursive(path: String): Boolean {
        val target = validate(path) ?: return false
        if (!target.exists()) return true
        return target.deleteRecursively()
    }

    override fun listFiles(path: String): Array<String> {
        val dir = validate(path) ?: return emptyArray()
        if (!dir.exists() || !dir.isDirectory) return emptyArray()
        return dir.listFiles()?.map { it.name }?.sorted()?.toTypedArray() ?: emptyArray()
    }

    override fun openRead(path: String): ParcelFileDescriptor? {
        val file = validate(path)
            ?: throw SecurityException("Invalid or restricted read path: $path")
        if (!file.exists() || !file.isFile) {
            throw IOException("Target file does not exist: ${file.absolutePath}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openWrite(path: String, overwrite: Boolean): ParcelFileDescriptor? {
        val file = validate(path)
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

    /**
     * A writable directory probe avoids choosing direct access on Android 11+ where
     * File.exists() can be false or write operations fail with EACCES.
     */
    fun canAccess(path: String): Boolean {
        val root = validate(path) ?: return false
        val probeRoot = if (root.exists() && root.isDirectory) {
            root
        } else {
            root.parentFile?.takeIf { it.exists() && it.isDirectory } ?: return false
        }
        val probe = File(probeRoot, ".mythic_access_${System.nanoTime()}")
        return try {
            probe.createNewFile() && probe.delete()
        } catch (_: Throwable) {
            false
        } finally {
            probe.delete()
        }
    }

    private fun validate(rawPath: String): File? {
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
