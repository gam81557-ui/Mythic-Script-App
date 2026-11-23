package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches and parses the remote GitHub Raw configuration JSON on [Dispatchers.IO].
 * Reads root control flags ("maintenance", "maintenance_message", "enabled") first,
 * then iterates every top-level key in the root JSONObject to extract all skin entries
 * ("key", "hero", "skin_name", "download", "img", "img2", "sort", "date_upload", "tag"),
 * collect all unique trimmed hero names alphabetically, and group skins by hero.
 */
class RemoteConfigManager(
    private val defaultUrl: String = DEFAULT_CONFIG_URL,
) {

    suspend fun fetchConfig(
        baseUrl: String = defaultUrl,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Result<RemoteConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val requestUrl = buildCacheBustedUrl(baseUrl, timestampMillis)
            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    throw IOException(
                        "Remote config HTTP $statusCode${errorBody?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
                    )
                }

                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseConfigJson(body, timestampMillis).getOrThrow()
            } finally {
                connection.disconnect()
            }
        }
    }

    fun parseConfigJson(
        rawJson: String,
        fetchedAtMillis: Long = System.currentTimeMillis(),
    ): Result<RemoteConfig> = runCatching {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Remote config response is empty.")
        }

        when (val root = JSONTokener(trimmed).nextValue()) {
            is JSONObject -> parseObjectRoot(root, fetchedAtMillis)
            is JSONArray -> parseArrayRoot(root, fetchedAtMillis)
            else -> throw IllegalArgumentException(
                "Unsupported JSON root type: ${root?.javaClass?.simpleName ?: "null"}"
            )
        }
    }

    private fun parseObjectRoot(root: JSONObject, fetchedAtMillis: Long): RemoteConfig {
        val nestedConfig = root.optJSONObject("config")
            ?: root.optJSONObject("status")
            ?: root.optJSONObject("settings")

        // 1. Check root control flags first.
        val maintenanceMode = readBooleanAny(
            sources = listOfNotNull(root, nestedConfig),
            keys = MAINTENANCE_KEYS,
            defaultValue = false,
        )
        val maintenanceMessage = readStringAny(
            sources = listOfNotNull(root, nestedConfig),
            keys = MAINTENANCE_MSG_KEYS,
            defaultValue = if (maintenanceMode) DEFAULT_MAINTENANCE_MESSAGE else "",
        )
        val injectionEnabled = readBooleanAny(
            sources = listOfNotNull(root, nestedConfig),
            keys = INJECTION_ENABLED_KEYS,
            defaultValue = true,
        )

        val entriesArray = root.optJSONArray("skins")
            ?: root.optJSONArray("items")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("entries")

        val discoveredPortraits = linkedMapOf<String, String>()
        val discoveredFallbackPortraits = linkedMapOf<String, String>()

        val allSkinEntries = mutableListOf<RemoteSkinEntry>()
        if (entriesArray != null) {
            allSkinEntries.addAll(parseSkinEntriesArray(entriesArray, discoveredPortraits, discoveredFallbackPortraits))
        }

        // 2. Loop through every top-level key in root JSONObject, ignoring non-object fields
        //    such as "maintenance", "maintenance_message", and "enabled".
        val rootKeys = root.keys()
        while (rootKeys.hasNext()) {
            val entryKey = rootKeys.next()
            val entryObj = root.optJSONObject(entryKey) ?: continue
            if (entryKey in RESERVED_CONFIG_CONTAINER_KEYS && !hasNonBlankString(entryObj, HERO_KEYS)) {
                continue
            }

            extractHeroPortraitIfMatch(entryObj, discoveredPortraits, discoveredFallbackPortraits)

            val skinEntry = parseSkinObject(entryKey = entryKey, item = entryObj) ?: continue
            allSkinEntries.add(skinEntry)
        }

        val (availableHeroes, skinsByHero) = buildHeroIndex(allSkinEntries)

        val hasAnyUnlockedEntry = allSkinEntries.any { it.unlocked }
        val skinsUnlocked = readBooleanAny(
            sources = listOfNotNull(root, nestedConfig),
            keys = GLOBAL_UNLOCK_KEYS,
            defaultValue = if (allSkinEntries.isNotEmpty()) hasAnyUnlockedEntry else true,
        )

        val configVersion = readStringAny(
            sources = listOfNotNull(root, nestedConfig),
            keys = listOf("version", "configVersion", "config_version", "updatedAt", "updated_at"),
            defaultValue = "1.0",
        )

        val flags = extractFeatureFlags(root, nestedConfig)

        return RemoteConfig(
            maintenanceMode = maintenanceMode,
            maintenanceMessage = maintenanceMessage,
            skinsUnlocked = skinsUnlocked,
            injectionEnabled = injectionEnabled,
            configVersion = configVersion,
            featureFlags = flags,
            availableHeroes = availableHeroes,
            skinsByHero = skinsByHero,
            skinEntries = allSkinEntries,
            rawRootType = "JSONObject",
            fetchedAtMillis = fetchedAtMillis,
            heroPortraits = discoveredPortraits,
            heroFallbackPortraits = discoveredFallbackPortraits,
        )
    }

    private fun parseArrayRoot(array: JSONArray, fetchedAtMillis: Long): RemoteConfig {
        var maintenanceMode = false
        var maintenanceMessage = ""
        var skinsUnlocked: Boolean? = null
        var injectionEnabled: Boolean? = null
        var configVersion = "1.0"
        val featureFlags = linkedMapOf<String, Boolean>()
        val skinEntries = mutableListOf<RemoteSkinEntry>()

        val discoveredPortraits = linkedMapOf<String, String>()
        val discoveredFallbackPortraits = linkedMapOf<String, String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            if (hasAnyKey(item, MAINTENANCE_KEYS)) {
                maintenanceMode = readBooleanAny(listOf(item), MAINTENANCE_KEYS, maintenanceMode)
            }
            if (hasAnyKey(item, MAINTENANCE_MSG_KEYS)) {
                maintenanceMessage = readStringAny(listOf(item), MAINTENANCE_MSG_KEYS, maintenanceMessage)
            }
            if (hasAnyKey(item, GLOBAL_UNLOCK_KEYS)) {
                skinsUnlocked = readBooleanAny(listOf(item), GLOBAL_UNLOCK_KEYS, skinsUnlocked ?: true)
            }
            if (hasAnyKey(item, INJECTION_ENABLED_KEYS)) {
                injectionEnabled = readBooleanAny(listOf(item), INJECTION_ENABLED_KEYS, injectionEnabled ?: true)
            }
            if (item.has("version") && !item.isNull("version")) {
                val ver = item.optString("version", "").trim()
                if (ver.isNotEmpty()) configVersion = ver
            }

            extractBooleansInto(item, featureFlags)
            extractHeroPortraitIfMatch(item, discoveredPortraits, discoveredFallbackPortraits)

            val parsed = parseSkinObject(entryKey = "remote_$i", item = item)
            if (parsed != null) {
                skinEntries.add(parsed)
            }
        }

        val (availableHeroes, skinsByHero) = buildHeroIndex(skinEntries)
        val resolvedUnlock = skinsUnlocked ?: if (skinEntries.isNotEmpty()) {
            skinEntries.any { it.unlocked }
        } else {
            true
        }
        val resolvedMessage = maintenanceMessage.ifBlank {
            if (maintenanceMode) DEFAULT_MAINTENANCE_MESSAGE else ""
        }

        return RemoteConfig(
            maintenanceMode = maintenanceMode,
            maintenanceMessage = resolvedMessage,
            skinsUnlocked = resolvedUnlock,
            injectionEnabled = injectionEnabled ?: true,
            configVersion = configVersion,
            featureFlags = featureFlags,
            availableHeroes = availableHeroes,
            skinsByHero = skinsByHero,
            skinEntries = skinEntries,
            rawRootType = "JSONArray",
            fetchedAtMillis = fetchedAtMillis,
            heroPortraits = discoveredPortraits,
            heroFallbackPortraits = discoveredFallbackPortraits,
        )
    }

    private fun parseSkinEntriesArray(
        array: JSONArray,
        portraits: MutableMap<String, String>? = null,
        fallbackPortraits: MutableMap<String, String>? = null,
    ): List<RemoteSkinEntry> {
        val list = mutableListOf<RemoteSkinEntry>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (portraits != null && fallbackPortraits != null) {
                extractHeroPortraitIfMatch(item, portraits, fallbackPortraits)
            }
            val parsed = parseSkinObject(entryKey = "entry_$i", item = item)
            if (parsed != null) {
                list.add(parsed)
            }
        }
        return list
    }

    private fun parseSkinObject(entryKey: String, item: JSONObject): RemoteSkinEntry? {
        val heroName = readStringAny(listOf(item), HERO_KEYS, "")
        if (heroName.isEmpty()) return null

        val entryId = readStringAny(
            sources = listOf(item),
            keys = listOf("key", "scriptId", "script_id", "id"),
            defaultValue = entryKey,
        )
        val rawSkinName = readStringAny(
            sources = listOf(item),
            keys = listOf("skin_name", "skinName", "skin", "name", "title"),
            defaultValue = "",
        )
        val resolvedName = rawSkinName.ifBlank { "Default Skin" }

        val roleHint = readStringAny(
            sources = listOf(item),
            keys = listOf("role", "heroRole", "hero_role", "category"),
            defaultValue = "",
        )
        val rawTag = readStringAny(
            sources = listOf(item),
            keys = listOf("tag", "tier", "tierTag", "tier_tag", "rarity", "type"),
            defaultValue = "",
        )
        val isTagImageUrl = isHttpUrl(rawTag)
        val tagBadgeUrl = if (isTagImageUrl) rawTag else ""
        val tierTag = if (isTagImageUrl) "" else rawTag

        val dateUpload = readStringAny(
            sources = listOf(item),
            keys = listOf("date_upload", "dateUpload", "updated_at", "date"),
            defaultValue = "",
        )
        val version = readStringAny(
            sources = listOf(item),
            keys = listOf("version", "patch", "ver"),
            defaultValue = "v1.0",
        )
        val unlocked = readBooleanAny(
            sources = listOf(item),
            keys = listOf("unlocked", "unlock", "enabled", "active", "status"),
            defaultValue = true,
        )
        val downloadUrl = readStringAny(
            sources = listOf(item),
            keys = listOf("download", "url", "downloadUrl", "download_url", "zipUrl", "zip_url", "link"),
            defaultValue = "",
        )
        val primaryImg = readStringAny(
            sources = listOf(item),
            keys = listOf("img", "image", "thumb", "thumbnail"),
            defaultValue = "",
        )
        val secondaryImg = readStringAny(
            sources = listOf(item),
            keys = listOf("img2", "image2", "fallback_img", "preview"),
            defaultValue = "",
        )
        val resolvedImg = primaryImg.ifBlank { secondaryImg }
        val resolvedFallbackImg = if (secondaryImg.isNotBlank() && secondaryImg != resolvedImg) {
            secondaryImg
        } else {
            primaryImg
        }

        val sortOrder = readIntAny(item, listOf("sort", "order", "priority"), Int.MAX_VALUE)

        val notes = readStringAny(
            sources = listOf(item),
            keys = listOf("notes", "description", "info", "message"),
            defaultValue = "",
        )
        val targetPaths = readStringListAny(
            item = item,
            keys = listOf("targetPaths", "target_paths", "files", "paths"),
        )

        // Skip known dead / 404 legacy release URLs
        if (downloadUrl.contains("github.com/", ignoreCase = true) &&
            downloadUrl.contains("/releases/download/", ignoreCase = true)
        ) {
            return null
        }

        return RemoteSkinEntry(
            id = entryId,
            heroName = heroName,
            name = resolvedName,
            roleHint = roleHint,
            tierTag = tierTag,
            tagBadgeUrl = tagBadgeUrl,
            version = version,
            unlocked = unlocked,
            notes = notes,
            targetRelativePaths = targetPaths,
            downloadUrl = downloadUrl,
            imageUrl = resolvedImg,
            fallbackImageUrl = resolvedFallbackImg,
            sortOrder = sortOrder,
            dateUpload = dateUpload,
        )
    }

    private fun isHttpUrl(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    private fun buildHeroIndex(
        entries: List<RemoteSkinEntry>,
    ): Pair<List<String>, Map<String, List<RemoteSkinEntry>>> {
        if (entries.isEmpty()) return emptyList<String>() to emptyMap()

        // Deduplicate skin entries per hero preferring working raw links or non-blank skin names
        val uniqueByHeroAndSkin = linkedMapOf<String, RemoteSkinEntry>()
        for (entry in entries) {
            val trimmedHero = entry.heroName.trim()
            if (trimmedHero.isEmpty()) continue
            val cleanSkinName = entry.name.trim().ifBlank { "Default Skin" }
            val dedupeKey = "${trimmedHero.lowercase()}|${cleanSkinName.lowercase()}"

            val existing = uniqueByHeroAndSkin[dedupeKey]
            if (existing == null) {
                uniqueByHeroAndSkin[dedupeKey] = entry
            } else {
                // Prefer entry with a valid download URL / non-empty date upload / non-default name
                val existingHasDl = existing.downloadUrl.isNotBlank()
                val currentHasDl = entry.downloadUrl.isNotBlank()
                if (!existingHasDl && currentHasDl) {
                    uniqueByHeroAndSkin[dedupeKey] = entry
                }
            }
        }

        val canonicalNameByLower = linkedMapOf<String, String>()
        val grouped = linkedMapOf<String, MutableList<RemoteSkinEntry>>()

        for (entry in uniqueByHeroAndSkin.values) {
            val trimmedHero = entry.heroName.trim()
            val lower = trimmedHero.lowercase()
            val canonicalHero = canonicalNameByLower.getOrPut(lower) { trimmedHero }
            grouped.getOrPut(canonicalHero) { mutableListOf() }.add(entry)
        }

        val sortedHeroes = canonicalNameByLower.values.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val sortedMap = linkedMapOf<String, List<RemoteSkinEntry>>()
        for (hero in sortedHeroes) {
            sortedMap[hero] = grouped[hero]
                .orEmpty()
                .sortedWith(compareBy<RemoteSkinEntry> { it.sortOrder }.thenBy { it.name.lowercase() })
        }
        return sortedHeroes to sortedMap
    }

    private fun extractFeatureFlags(
        root: JSONObject,
        nestedConfig: JSONObject?,
    ): Map<String, Boolean> {
        val flags = linkedMapOf<String, Boolean>()
        extractBooleansInto(root, flags)
        if (nestedConfig != null) {
            extractBooleansInto(nestedConfig, flags)
        }
        listOf("featureFlags", "feature_flags", "features", "flags").forEach { containerKey ->
            root.optJSONObject(containerKey)?.let { extractBooleansInto(it, flags) }
            nestedConfig?.optJSONObject(containerKey)?.let { extractBooleansInto(it, flags) }
        }
        return flags
    }

    private fun extractBooleansInto(obj: JSONObject, target: MutableMap<String, Boolean>) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            if (value is Boolean) {
                target[key] = value
            }
        }
    }

    private fun hasAnyKey(obj: JSONObject, keys: List<String>): Boolean =
        keys.any { obj.has(it) && !obj.isNull(it) }

    private fun hasNonBlankString(obj: JSONObject, keys: List<String>): Boolean =
        readStringAny(listOf(obj), keys, "").isNotEmpty()

    private fun readIntAny(obj: JSONObject, keys: List<String>, defaultValue: Int): Int {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val raw = obj.opt(key)
            when (raw) {
                is Number -> return raw.toInt()
                is String -> raw.trim().toIntOrNull()?.let { return it }
            }
        }
        return defaultValue
    }

    private fun readBooleanAny(
        sources: List<JSONObject>,
        keys: List<String>,
        defaultValue: Boolean,
    ): Boolean {
        for (source in sources) {
            for (key in keys) {
                if (source.has(key) && !source.isNull(key)) {
                    val raw = source.opt(key)
                    when (raw) {
                        is Boolean -> return raw
                        is Number -> return raw.toInt() != 0
                        is String -> {
                            val clean = raw.trim()
                            if (clean.equals("true", ignoreCase = true) || clean == "1") return true
                            if (clean.equals("false", ignoreCase = true) || clean == "0") return false
                        }
                    }
                }
            }
        }
        return defaultValue
    }

    private fun readStringAny(
        sources: List<JSONObject>,
        keys: List<String>,
        defaultValue: String,
    ): String {
        for (source in sources) {
            for (key in keys) {
                if (source.has(key) && !source.isNull(key)) {
                    val raw = source.opt(key)
                    if (raw != null && raw !== JSONObject.NULL) {
                        val value = raw.toString().trim()
                        if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                            return value
                        }
                    }
                }
            }
        }
        return defaultValue
    }

    private fun readStringListAny(item: JSONObject, keys: List<String>): List<String> {
        for (key in keys) {
            if (!item.has(key) || item.isNull(key)) continue
            val arr = item.optJSONArray(key) ?: continue
            val result = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                if (!arr.isNull(i)) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) {
                        result.add(v)
                    }
                }
            }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun extractHeroPortraitIfMatch(
        item: JSONObject,
        portraits: MutableMap<String, String>,
        fallbackPortraits: MutableMap<String, String>,
    ) {
        val hero = readStringAny(listOf(item), HERO_KEYS, "").trim()
        if (hero.isEmpty()) return
        val img = readStringAny(listOf(item), listOf("img", "image", "thumb", "thumbnail"), "").trim()
        val img2 = readStringAny(listOf(item), listOf("img2", "image2", "fallback_img", "preview"), "").trim()
        if (img.isEmpty() && img2.isEmpty()) return

        val primary = img.ifEmpty { img2 }
        val fallback = if (img2.isNotEmpty()) img2 else primary

        val sort = readStringAny(listOf(item), listOf("sort", "order"), "").trim()
        val skinName = readStringAny(listOf(item), listOf("skin_name", "skinName", "skin", "name"), "").trim()
        val dl = readStringAny(listOf(item), listOf("download", "url"), "").trim()

        val isExplicitHeroPortrait = sort == "0" ||
            skinName.isEmpty() ||
            skinName.equals("default", ignoreCase = true) ||
            skinName.equals("default skin", ignoreCase = true) ||
            dl.contains("backdefault", ignoreCase = true) ||
            HERO_PORTRAIT_REGEX.containsMatchIn(primary)

        val key = hero.lowercase()
        if (isExplicitHeroPortrait || !portraits.containsKey(key)) {
            portraits[key] = primary
            fallbackPortraits[key] = fallback
        }
    }

    companion object {
        const val DEFAULT_CONFIG_URL =
            "https://raw.githubusercontent.com/gam81557-ui/MLBBSkin-Config/refs/heads/main/unlockskins_fixed.json"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 20_000
        private const val DEFAULT_MAINTENANCE_MESSAGE =
            "Script injection is temporarily paused for maintenance."

        private val RESERVED_CONFIG_CONTAINER_KEYS = setOf(
            "config",
            "status",
            "settings",
            "featureFlags",
            "feature_flags",
            "features",
            "flags",
        )
        private val HERO_KEYS = listOf("hero", "heroName", "hero_name", "heroId", "hero_id")
        private val MAINTENANCE_KEYS = listOf(
            "maintenance",
            "maintenanceMode",
            "maintenance_mode",
            "isMaintenance",
            "is_maintenance",
        )
        private val MAINTENANCE_MSG_KEYS = listOf(
            "maintenanceMessage",
            "maintenance_message",
            "maintenanceNote",
            "message",
            "notice",
            "reason",
        )
        private val GLOBAL_UNLOCK_KEYS = listOf(
            "unlockSkins",
            "unlock_skins",
            "skinsUnlocked",
            "skins_unlocked",
            "unlockAll",
            "unlock_all",
        )
        private val INJECTION_ENABLED_KEYS = listOf(
            "injectionEnabled",
            "injection_enabled",
            "injectEnabled",
            "inject_enabled",
            "enabled",
            "isActive",
        )

        private val HERO_PORTRAIT_REGEX = Regex("Hero\\d+1-portrait", RegexOption.IGNORE_CASE)

        fun buildCacheBustedUrl(baseUrl: String, timestampMillis: Long): String {
            val separator = if ('?' in baseUrl) '&' else '?'
            return "$baseUrl${separator}t=$timestampMillis"
        }
    }
}
