package com.example.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.catalog.CatalogScriptTemplate
import com.example.domain.model.StagedBundlePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream

data class StagedDownloadResult(
    val relativePaths: List<String>,
    val stagedDirPath: String,
    val totalBytes: Long,
)

class RemoteSkinUnavailableException(
    val httpCode: Int,
    message: String,
) : IOException(message)

class ScriptArchiveManager(private val context: Context) {

    val stagedRootDir: File
        get() = File(context.filesDir, "staged_scripts").apply { mkdirs() }

    /**
     * Stages lightweight descriptor/asset payloads for built-in catalog entries if not already present,
     * ensuring every catalog item has verifiable staged files before validation or injection.
     */
    suspend fun ensureCatalogTemplatesStaged(templates: List<CatalogScriptTemplate>) =
        withContext(Dispatchers.IO) {
            templates.forEach { tpl ->
                val scriptDir = File(stagedRootDir, sanitizeFileSegment(tpl.scriptId))
                tpl.relativeTargetPaths.forEach { relPath ->
                    val safeRel = sanitizeRelativePath(relPath) ?: return@forEach
                    val outFile = File(scriptDir, safeRel)
                    if (!outFile.exists()) {
                        outFile.parentFile?.mkdirs()
                        val payload = buildString {
                            appendLine("# MythicScript Local Bundle Payload")
                            appendLine("scriptId=${tpl.scriptId}")
                            appendLine("heroId=${tpl.heroId}")
                            appendLine("skinName=${tpl.skinName}")
                            appendLine("version=${tpl.version}")
                            appendLine("target=$safeRel")
                        }
                        outFile.writeText(payload)
                    }
                }
            }
        }

    /**
     * Imports a user-selected .zip bundle via SAF Uri on Dispatchers.IO, verifies all zip entries
     * against Zip-Slip path traversal, extracts optional manifest.json metadata, and stages files.
     */
    suspend fun importZipBundle(
        uri: Uri,
        fallbackHeroId: String,
    ): Result<StagedBundlePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveDisplayName(uri) ?: "imported_bundle.zip"
            val scriptId = "imp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val stagingDir = File(stagedRootDir, scriptId).apply { mkdirs() }

            var manifestJson: JSONObject? = null
            val extractedPaths = mutableListOf<String>()
            var totalBytes = 0L

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected archive stream.")

            inputStream.use { rawStream ->
                extractZipStreamSafely(
                    inputStream = rawStream,
                    stagingDir = stagingDir,
                    onManifestFound = { jsonText ->
                        manifestJson = runCatching { JSONObject(jsonText) }.getOrNull()
                    },
                    onFileExtracted = { relPath, bytes ->
                        extractedPaths.add(relPath)
                        totalBytes += bytes
                    },
                )
            }

            if (extractedPaths.isEmpty()) {
                stagingDir.deleteRecursively()
                throw IllegalArgumentException(
                    "Archive contains no valid script asset files."
                )
            }

            val baseSkinName = fileName.removeSuffix(".zip").removeSuffix(".ZIP")
                .replace('_', ' ')
                .ifBlank { "Custom Imported Skin" }

            val heroId = manifestJson?.optString("heroId")?.takeIf { it.isNotBlank() } ?: fallbackHeroId
            val skinName = manifestJson?.optString("skinName")?.takeIf { it.isNotBlank() } ?: baseSkinName
            val tierTag = manifestJson?.optString("tierTag")?.takeIf { it.isNotBlank() } ?: "Custom"
            val version = manifestJson?.optString("version")?.takeIf { it.isNotBlank() } ?: "v1.0"
            val notes = manifestJson?.optString("notes")?.takeIf { it.isNotBlank() }
                ?: "Imported from $fileName (${extractedPaths.size} asset file${if (extractedPaths.size == 1) "" else "s"})"

            StagedBundlePreview(
                scriptId = scriptId,
                suggestedHeroId = heroId,
                suggestedSkinName = skinName,
                suggestedTierTag = tierTag,
                suggestedVersion = version,
                suggestedNotes = notes,
                relativeTargetPaths = extractedPaths.sorted(),
                stagedDirPath = stagingDir.absolutePath,
                totalBytes = totalBytes,
            )
        }
    }

    /**
     * Exposed as internal/public helper so unit tests can directly verify Zip-Slip and extraction logic.
     */
    fun extractZipStreamSafely(
        inputStream: InputStream,
        stagingDir: File,
        onManifestFound: (String) -> Unit,
        onFileExtracted: (relativePath: String, byteCount: Long) -> Unit,
    ) {
        val canonicalRoot = stagingDir.canonicalFile
        var entryCount = 0
        var totalExtractedBytes = 0L
        ZipInputStream(inputStream.buffered()).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IOException("Archive contains too many entries.")
                }
                val rawName = entry.name
                if (entry.isDirectory || shouldIgnoreZipEntry(rawName)) {
                    zip.closeEntry()
                    continue
                }

                val sanitizedRel = sanitizeRelativePath(rawName)
                    ?: throw SecurityException("Unsafe archive entry path rejected: $rawName")

                if (sanitizedRel.equals("manifest.json", ignoreCase = true)) {
                    val manifestText = readLimited(zip, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
                    onManifestFound(manifestText)
                    zip.closeEntry()
                    continue
                }

                val normalizedRel = normalizeMlbbRelativePath(sanitizedRel)
                val destFile = File(canonicalRoot, normalizedRel).canonicalFile
                if (!destFile.path.startsWith(canonicalRoot.path + File.separator)) {
                    throw SecurityException("Zip-Slip traversal attempt blocked: $rawName")
                }

                destFile.parentFile?.mkdirs()
                var fileBytes = 0L
                FileOutputStream(destFile).use { out ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        fileBytes += read
                    }
                }
                zip.closeEntry()
                totalExtractedBytes += fileBytes
                if (totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                    throw IOException("Archive exceeds the safe extraction size limit.")
                }
                onFileExtracted(normalizedRel, fileBytes)
            }
        }
    }

    /**
     * Downloads a remote skin script archive or asset bundle strictly inside the app on [Dispatchers.IO].
     * Resolves common share URLs (Dropbox, Google Drive, MediaFire, GitHub blob) without ever launching
     * an external browser, safely extracts `.zip` archives with Zip-Slip protection, or stages downloaded
     * binary assets into [stagedRootDir]/[scriptId].
     */
    suspend fun downloadRemoteScript(
        scriptId: String,
        downloadUrl: String,
        fallbackRelativePaths: List<String> = emptyList(),
        onProgress: (Float) -> Unit = {},
    ): Result<StagedDownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val initialUrl = normalizeDirectDownloadUrl(downloadUrl.trim())
            if (initialUrl.isEmpty()) {
                throw IllegalArgumentException("No download URL configured for this skin.")
            }

            onProgress(0.10f)
            val safeTempId = sanitizeFileSegment(scriptId)
            val tempFile = File(context.cacheDir, "dl_${safeTempId}_${System.currentTimeMillis()}.tmp")
            var downloadedFileName = resolveFileNameFromUrl(initialUrl)

            try {
                var currentUrl = initialUrl
                var hops = 0
                var resolvedBinary = false

                while (hops < 3 && !resolvedBinary) {
                    val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 20_000
                        readTimeout = 20_000
                        instanceFollowRedirects = true
                        setRequestProperty("Accept", "*/*")
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MythicScript/1.0")
                    }

                    try {
                        val status = connection.responseCode
                        if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
                            throw RemoteSkinUnavailableException(
                                httpCode = status,
                                message = "This skin is currently unavailable ($status). Please try another skin.",
                            )
                        }
                        if (status !in 200..299) {
                            throw IOException("Download failed (HTTP $status)")
                        }

                        val contentType = connection.contentType.orEmpty().lowercase()
                        val contentDisposition = connection.getHeaderField("Content-Disposition").orEmpty()
                        parseContentDispositionFileName(contentDisposition)?.let { downloadedFileName = it }
                        val totalLength = connection.contentLengthLong

                        if (contentType.contains("text/html")) {
                            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                                readTextLimited(it, MAX_HTML_CHARS)
                            }
                            val nextUrl = extractDirectDownloadUrlFromHtml(html, currentUrl)
                            if (nextUrl != null && nextUrl != currentUrl) {
                                currentUrl = nextUrl
                                hops++
                                continue
                            }
                            throw IOException("Host returned a web page instead of a downloadable archive.")
                        }

                        var downloadedBytes = 0L
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        connection.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    downloadedBytes += read
                                    if (downloadedBytes > MAX_DOWNLOAD_BYTES) {
                                        throw IOException("Downloaded file exceeds the safe size limit.")
                                    }
                                    if (totalLength > 0L) {
                                        val fraction = 0.15f + 0.65f * (downloadedBytes.toFloat() / totalLength.toFloat())
                                        onProgress(fraction.coerceIn(0.15f, 0.80f))
                                    }
                                }
                            }
                        }

                        if (downloadedBytes == 0L) {
                            throw IOException("Downloaded file was empty (0 bytes).")
                        }
                        if (downloadedBytes > MAX_DOWNLOAD_BYTES) {
                            throw IOException("Downloaded file exceeds the safe size limit.")
                        }

                        if (looksLikeHtmlDocument(tempFile)) {
                            val html = tempFile.inputStream().bufferedReader(Charsets.UTF_8).use {
                                readTextLimited(it, MAX_HTML_CHARS)
                            }
                            val nextUrl = extractDirectDownloadUrlFromHtml(html, currentUrl)
                            if (nextUrl != null && nextUrl != currentUrl) {
                                currentUrl = nextUrl
                                hops++
                                continue
                            }
                            throw IOException("Host returned an HTML page instead of a skin file.")
                        }

                        resolvedBinary = true
                    } finally {
                        connection.disconnect()
                    }
                }

                if (!resolvedBinary) {
                    throw IOException("Could not resolve a direct download stream.")
                }

                onProgress(0.85f)
                val safeDirName = sanitizeFileSegment(scriptId)
                val stagingDir = File(stagedRootDir, safeDirName).apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }

                if (looksLikeZipArchive(tempFile)) {
                    val extractedPaths = mutableListOf<String>()
                    var extractedBytes = 0L
                    FileInputStream(tempFile).use { fis ->
                        extractZipStreamSafely(
                            inputStream = fis,
                            stagingDir = stagingDir,
                            onManifestFound = {},
                            onFileExtracted = { relPath, bytes ->
                                extractedPaths.add(relPath)
                                extractedBytes += bytes
                            },
                        )
                    }

                    if (extractedPaths.isEmpty()) {
                        stagingDir.deleteRecursively()
                        throw IOException("Downloaded archive contained no extractable skin files.")
                    }

                    onProgress(1f)
                    StagedDownloadResult(
                        relativePaths = extractedPaths.sorted(),
                        stagedDirPath = stagingDir.absolutePath,
                        totalBytes = extractedBytes,
                    )
                } else {
                    // Direct non-zip skin asset payload (e.g., .unity3d / .bin / raw bundle)
                    val safeName = sanitizeRelativePath(downloadedFileName)
                        ?: "${safeDirName.lowercase()}.unity3d"
                    val normalizedRel = fallbackRelativePaths.firstOrNull()?.let { sanitizeRelativePath(it) }
                        ?: normalizeMlbbRelativePath(safeName)
                    val destFile = File(stagingDir, normalizedRel)
                    destFile.parentFile?.mkdirs()
                    tempFile.inputStream().use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output, DEFAULT_BUFFER_SIZE)
                        }
                    }

                    onProgress(1f)
                    StagedDownloadResult(
                        relativePaths = listOf(normalizedRel),
                        stagedDirPath = stagingDir.absolutePath,
                        totalBytes = destFile.length(),
                    )
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    private fun looksLikeZipArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val header = ByteArray(4)
        FileInputStream(file).use { fis ->
            if (fis.read(header) < 4) return false
        }
        return header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            ((header[2] == 3.toByte() && header[3] == 4.toByte()) ||
                (header[2] == 5.toByte() && header[3] == 6.toByte()))
    }

    private fun looksLikeHtmlDocument(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val prefix = FileInputStream(file).use { fis ->
            val buf = ByteArray(128)
            val count = fis.read(buf)
            if (count <= 0) "" else String(buf, 0, count, Charsets.UTF_8).trimStart().lowercase()
        }
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            prefix.startsWith("<head")
    }

    private fun parseContentDispositionFileName(header: String): String? {
        if (header.isBlank()) return null
        val match = Regex("""filename\*?=(?:UTF-8''|")?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
            .find(header)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun resolveFileNameFromUrl(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val segment = clean.substringAfterLast('/', "").trim()
        return if (segment.isNotBlank() && '.' in segment) segment else "skin_bundle.unity3d"
    }

    /**
     * Performs a fast HEAD probe (falling back to 1-byte Range GET on HTTP 405/501) on [Dispatchers.IO]
     * to identify dead 404/410 links without downloading full payloads. Returns null if the network is
     * offline or times out so transient connectivity issues never mark a working skin as unavailable.
     */
    suspend fun checkUrlAvailability(rawUrl: String): Int? = withContext(Dispatchers.IO) {
        val targetUrl = normalizeDirectDownloadUrl(rawUrl.trim())
        if (!targetUrl.startsWith("http://", ignoreCase = true) &&
            !targetUrl.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext null
        }

        fun probe(method: String, withRange: Boolean): Int {
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 4_000
                readTimeout = 4_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MythicScript/1.0")
                if (withRange) {
                    setRequestProperty("Range", "bytes=0-0")
                }
            }
            return try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }

        runCatching {
            val headCode = probe("HEAD", withRange = false)
            if (headCode == 405 || headCode == 501) {
                probe("GET", withRange = true)
            } else {
                headCode
            }
        }.getOrNull()
    }

    fun deleteStagedFiles(stagedDirPath: String) {
        if (stagedDirPath.isBlank()) return
        val dir = File(stagedDirPath)
        val canonicalRoot = runCatching { stagedRootDir.canonicalPath }.getOrNull() ?: return
        val canonicalTarget = runCatching { dir.canonicalPath }.getOrNull() ?: return
        if (canonicalTarget.startsWith(canonicalRoot + File.separator) && dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 8_192))
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("Archive entry exceeds the safe size limit.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun readTextLimited(reader: Reader, maxChars: Int): String {
        val output = StringBuilder(minOf(maxChars, 8_192))
        val buffer = CharArray(8_192)
        var total = 0
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxChars) {
                throw IOException("Downloaded response exceeds the safe size limit.")
            }
            output.append(buffer, 0, read)
        }
        return output.toString()
    }

    companion object {
        const val MAX_MANIFEST_BYTES = 1_000_000
        const val MAX_HTML_CHARS = 256_000
        const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024
        const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 20_000

        fun sanitizeFileSegment(raw: String): String {
            val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") "script" else cleaned
        }

        fun shouldIgnoreZipEntry(name: String): Boolean {
            val trimmed = name.trim()
            return trimmed.isEmpty() ||
                trimmed.startsWith("__MACOSX/") ||
                trimmed.endsWith(".DS_Store")
        }

        fun sanitizeRelativePath(rawPath: String): String? {
            val normalized = rawPath.replace('\\', '/').trim().trimStart('/')
            if (normalized.isEmpty() || normalized.contains('\u0000')) return null
            val segments = normalized.split('/')
            if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
            return segments.joinToString("/")
        }

        /**
         * Preserves paths already anchored under `files/` or `dragon2017/`, otherwise prefixes
         * standard MLBB `files/dragon2017/assets/` layout so injected bundles target the game directory cleanly.
         */
        fun normalizeMlbbRelativePath(sanitizedRel: String): String {
            return when {
                sanitizedRel.contains("files/") -> sanitizedRel.substring(sanitizedRel.indexOf("files/"))
                sanitizedRel.contains("dragon2017/") -> "files/" + sanitizedRel.substring(sanitizedRel.indexOf("dragon2017/"))
                sanitizedRel.startsWith("assets/") -> "files/dragon2017/$sanitizedRel"
                else -> "files/dragon2017/assets/Art/Android/$sanitizedRel"
            }
        }

        fun normalizeDirectDownloadUrl(rawUrl: String): String {
            val url = rawUrl.trim()
            if (url.isEmpty()) return ""

            // Convert GitHub /blob/ links to raw content URLs
            if (url.contains("github.com/", ignoreCase = true) && url.contains("/blob/")) {
                return url.replace("github.com/", "raw.githubusercontent.com/")
                    .replace("/blob/", "/")
            }

            // Convert Dropbox share links (?dl=0 -> ?dl=1)
            if (url.contains("dropbox.com/", ignoreCase = true)) {
                return when {
                    "dl=0" in url -> url.replace("dl=0", "dl=1")
                    "dl=1" in url -> url
                    '?' in url -> "$url&dl=1"
                    else -> "$url?dl=1"
                }
            }

            // Convert Google Drive /file/d/<id>/view share links to uc?export=download
            val driveMatch = Regex("""drive\.google\.com/file/d/([^/?#]+)""", RegexOption.IGNORE_CASE)
                .find(url)
            if (driveMatch != null) {
                val fileId = driveMatch.groupValues[1]
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }

            return url
        }

        fun extractDirectDownloadUrlFromHtml(html: String, baseUrl: String): String? {
            if (html.isBlank()) return null

            // MediaFire download button link
            val mediaFireRegex = Regex(
                """href=["'](https?://download[^"']+)["']""",
                RegexOption.IGNORE_CASE,
            )
            mediaFireRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }

            // Google Drive confirm/direct download link
            val driveConfirmRegex = Regex(
                """href=["'](https?://drive\.(?:usercontent\.)?google\.com/[^"']*(?:export=download|confirm=)[^"']*)["']""",
                RegexOption.IGNORE_CASE,
            )
            driveConfirmRegex.find(html)?.groupValues?.getOrNull(1)?.let {
                return it.replace("&amp;", "&")
            }

            // Generic meta refresh redirect URL
            val metaRefreshRegex = Regex(
                """http-equiv=["']refresh["'][^>]*content=["']\d+;\s*url=([^"']+)["']""",
                RegexOption.IGNORE_CASE,
            )
            metaRefreshRegex.find(html)?.groupValues?.getOrNull(1)?.let { refreshTarget ->
                if (refreshTarget.startsWith("http", ignoreCase = true)) return refreshTarget
            }

            // Relative href starting with /uc?export=download (Google Drive)
            if (baseUrl.contains("drive.google.com", ignoreCase = true)) {
                val relDriveRegex = Regex("""href=["'](/uc\?[^"']+)["']""", RegexOption.IGNORE_CASE)
                relDriveRegex.find(html)?.groupValues?.getOrNull(1)?.let { rel ->
                    return "https://drive.google.com${rel.replace("&amp;", "&")}"
                }
            }

            return null
        }
    }
}
