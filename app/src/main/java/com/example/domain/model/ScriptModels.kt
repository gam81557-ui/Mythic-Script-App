package com.example.domain.model

sealed class ScriptLifecycleState {
    data object Idle : ScriptLifecycleState()
    data object Selected : ScriptLifecycleState()
    data class Downloading(val progress: Float) : ScriptLifecycleState()
    data object Downloaded : ScriptLifecycleState()
    data class BackingUp(val progress: Float, val currentFile: String) : ScriptLifecycleState()
    data class Injecting(val progress: Float, val currentFile: String) : ScriptLifecycleState()
    data object Injected : ScriptLifecycleState()
    data class Restoring(val progress: Float, val currentFile: String) : ScriptLifecycleState()
    data class Error(val message: String) : ScriptLifecycleState()
    data class Unavailable(
        val httpCode: Int = 404,
        val reason: String = "This skin is currently unavailable. Please try another skin.",
    ) : ScriptLifecycleState()

    val isBusy: Boolean
        get() = this is Downloading || this is BackingUp || this is Injecting || this is Restoring

    fun displayLabel(): String = when (this) {
        Idle -> "Idle / Not Injected"
        Selected -> "Selected (Ready to Inject)"
        is Downloading -> "Downloading"
        Downloaded -> "Downloaded"
        is BackingUp -> "Backing Up Original"
        is Injecting -> "Injecting"
        Injected -> "Injected / Active"
        is Restoring -> "Restoring"
        is Error -> "Failed"
        is Unavailable -> "Unavailable ($httpCode)"
    }
}

data class SkinScriptItem(
    val scriptId: String,
    val heroId: String,
    val skinName: String,
    val tierTag: String,
    val version: String,
    val compatibilityNotes: String,
    val targetRelativePaths: List<String>,
    val stagedDirPath: String,
    val isUserImported: Boolean,
    val isDownloaded: Boolean = false,
    val hasBackup: Boolean,
    val backupFileCount: Int,
    val backupByteSize: Long,
    val lastUpdatedAt: Long,
    val lifecycleState: ScriptLifecycleState,
    val imageUrl: String = "",
    val fallbackImageUrl: String = "",
    val tagBadgeUrl: String = "",
    val downloadUrl: String = "",
    val dateUpload: String = "",
    val sortOrder: Int = Int.MAX_VALUE,
) {
    val isUnavailable: Boolean
        get() = lifecycleState is ScriptLifecycleState.Unavailable
}

enum class HeroDetailTab(val label: String) {
    SCRIPTS("Skin Scripts"),
    STATS_COMBOS("Stats & Combos"),
}

data class StagedBundlePreview(
    val scriptId: String,
    val suggestedHeroId: String,
    val suggestedSkinName: String,
    val suggestedTierTag: String,
    val suggestedVersion: String,
    val suggestedNotes: String,
    val relativeTargetPaths: List<String>,
    val stagedDirPath: String,
    val totalBytes: Long,
)
