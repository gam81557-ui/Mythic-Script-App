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
    /** Explicit "show_notice" flag. Missing values mean no notice unless maintenance is active. */
    val showNotice: Boolean = false,
    /** Stable identifier supplied by files.json for the current notice. */
    val noticeId: String = "",
    /** Optional remote title, e.g. title / update_title. */
    val noticeTitle: String = "",
    /** Optional remote body, e.g. message / release_note / description. */
    val noticeMessage: String = "",
    /** Optional maintenance-specific title; falls back to noticeTitle. */
    val maintenanceTitle: String = "",
) {
    val canInjectRemotely: Boolean
        get() = !maintenanceMode && injectionEnabled

    val hasNotification: Boolean
        get() = maintenanceMode || showNotice

    /**
     * Changes whenever the notice id, title, or body changes. The ViewModel uses this
     * to show a previously dismissed notice again only when its content changes.
     */
    val notificationContentKey: String
        get() = listOf(
            if (maintenanceMode) "maintenance" else "notice",
            noticeId.trim(),
            noticeTitle.trim(),
            noticeMessage.trim(),
            maintenanceTitle.trim(),
            maintenanceMessage.trim(),
        ).joinToString("|")
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
