package com.example.data.remote

/**
 * Individual skin entry parsed from the remote configuration JSON.
 * Maps fields: "key", "hero", "skin_name", "download", "img", "img2", "sort", "date_upload", "tag".
 */
data class RemoteSkinEntry(
    val id: String,
    val heroName: String,
    val name: String,
    val roleHint: String = "",
    val tierTag: String = "",
    val tagBadgeUrl: String = "",
    val version: String = "v1.0",
    val unlocked: Boolean = true,
    val notes: String = "",
    val targetRelativePaths: List<String> = emptyList(),
    val downloadUrl: String = "",
    val imageUrl: String = "",
    val fallbackImageUrl: String = "",
    val sortOrder: Int = Int.MAX_VALUE,
    val dateUpload: String = "",
)

/**
 * Parsed remote configuration flags and full hero/skin mappings fetched from GitHub Raw.
 */
data class RemoteConfig(
    val maintenanceMode: Boolean,
    val maintenanceMessage: String,
    val skinsUnlocked: Boolean,
    val injectionEnabled: Boolean,
    val configVersion: String,
    val featureFlags: Map<String, Boolean>,
    val availableHeroes: List<String>,
    val skinsByHero: Map<String, List<RemoteSkinEntry>>,
    val skinEntries: List<RemoteSkinEntry>,
    val rawRootType: String,
    val fetchedAtMillis: Long,
    val heroPortraits: Map<String, String> = emptyMap(),
    val heroFallbackPortraits: Map<String, String> = emptyMap(),
    /** When true (the default) a rewarded/interstitial ad must be watched before a skin download. */
    val requireAdForDownload: Boolean = true,
) {
    val canInjectRemotely: Boolean
        get() = !maintenanceMode && injectionEnabled
}

/**
 * Lifecycle wrapper for UI observation with graceful fallback support.
 */
sealed class RemoteConfigState {
    data object Loading : RemoteConfigState()
    data class Ready(val config: RemoteConfig) : RemoteConfigState()
    data class Failed(
        val errorMessage: String,
        val lastKnownConfig: RemoteConfig? = null,
    ) : RemoteConfigState()
}
