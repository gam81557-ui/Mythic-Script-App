package com.example.ui.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.RemoteConfigState
import com.example.domain.model.HeroDetailTab
import com.example.domain.model.HeroProfile
import com.example.domain.model.HeroRole
import com.example.domain.model.ScriptLifecycleState
import com.example.domain.model.SkinScriptItem
import com.example.shizuku.ShizukuConnectionState
import com.example.ui.theme.LocalSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDetailPane(
    hero: HeroProfile,
    activeTab: HeroDetailTab,
    scripts: List<SkinScriptItem>,
    selectedScriptId: String?,
    selectedScript: SkinScriptItem?,
    hideUnavailableSkins: Boolean,
    unavailableCountForHero: Int,
    shizukuState: ShizukuConnectionState,
    remoteConfigState: RemoteConfigState,
    isImportingZip: Boolean,
    applyNavigationBarInsets: Boolean,
    onTabSelected: (HeroDetailTab) -> Unit,
    onScriptSelected: (String) -> Unit,
    onDownloadScript: (SkinScriptItem) -> Unit,
    onInjectScript: (SkinScriptItem) -> Unit,
    onToggleHideUnavailable: () -> Unit,
    onImportZipClick: () -> Unit,
    onDeleteCustomScript: (SkinScriptItem) -> Unit,
    onInjectClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onClearBackupClick: () -> Unit,
    onOpenShizukuDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeroBannerHeader(
            hero = hero,
            activeScript = scripts.firstOrNull { it.lifecycleState is ScriptLifecycleState.Injected },
        )

        PrimaryTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            HeroDetailTab.entries.forEach { tab ->
                Tab(
                    selected = tab == activeTab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(text = tab.label) },
                    modifier = Modifier.testTag("hero_tab_${tab.name.lowercase()}"),
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                HeroDetailTab.SCRIPTS -> SkinScriptsTab(
                    hero = hero,
                    scripts = scripts,
                    selectedScriptId = selectedScriptId,
                    hideUnavailableSkins = hideUnavailableSkins,
                    unavailableCountForHero = unavailableCountForHero,
                    isImportingZip = isImportingZip,
                    onScriptSelected = onScriptSelected,
                    onDownloadScript = onDownloadScript,
                    onInjectScript = onInjectScript,
                    onToggleHideUnavailable = onToggleHideUnavailable,
                    onImportZipClick = onImportZipClick,
                    onDeleteCustomScript = onDeleteCustomScript,
                )
                HeroDetailTab.STATS_COMBOS -> HeroStatsCombosTab(hero = hero)
            }
        }

        if (activeTab == HeroDetailTab.SCRIPTS) {
            DockedScriptActionBar(
                selectedScript = selectedScript,
                shizukuState = shizukuState,
                remoteConfigState = remoteConfigState,
                applyNavigationBarInsets = applyNavigationBarInsets,
                onInjectClick = onInjectClick,
                onRestoreClick = onRestoreClick,
                onClearBackupClick = onClearBackupClick,
                onOpenShizukuDiagnostics = onOpenShizukuDiagnostics,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroBannerHeader(
    hero: HeroProfile,
    activeScript: SkinScriptItem?,
) {
    val semantic = LocalSemanticColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_companion_banner),
            contentDescription = stringResource(R.string.cd_companion_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xE60B1220),
                            Color(0xC20F172A),
                            Color(0x99111D33),
                        ),
                    )
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hero.previewImageUrl.isNotBlank() || hero.fallbackImageUrl.isNotBlank()) {
                    RemoteArtworkImage(
                        primaryUrl = hero.previewImageUrl,
                        fallbackUrl = hero.fallbackImageUrl,
                        label = hero.name,
                        contentDescription = "${hero.name} preview portrait",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hero.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "${hero.title} • ${hero.lane}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1),
                    )
                }
                if (activeScript != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = semantic.activeContainer,
                    ) {
                        Text(
                            text = "Active: ${activeScript.skinName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.active,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BannerTagChip(text = if (hero.role == HeroRole.ALL) "MLBB" else hero.role.label)
                hero.secondaryRole?.let { BannerTagChip(text = it.label) }
                BannerTagChip(text = hero.specialty)
            }
        }
    }
}

@Composable
private fun BannerTagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0x33FFFFFF),
        border = BorderStroke(1.dp, Color(0x4DFFFFFF)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun SkinScriptsTab(
    hero: HeroProfile,
    scripts: List<SkinScriptItem>,
    selectedScriptId: String?,
    hideUnavailableSkins: Boolean,
    unavailableCountForHero: Int,
    isImportingZip: Boolean,
    onScriptSelected: (String) -> Unit,
    onDownloadScript: (SkinScriptItem) -> Unit,
    onInjectScript: (SkinScriptItem) -> Unit,
    onToggleHideUnavailable: () -> Unit,
    onImportZipClick: () -> Unit,
    onDeleteCustomScript: (SkinScriptItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("hero_skins_list"),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${hero.name} Skin Bundles",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val subtitle = if (unavailableCountForHero > 0) {
                            "${scripts.size} shown • $unavailableCountForHero unavailable (404)"
                        } else {
                            "${scripts.size} script profile(s) available • Single active per hero"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onImportZipClick,
                        enabled = !isImportingZip,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("import_zip_button"),
                    ) {
                        if (isImportingZip) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.action_import_zip))
                    }
                }

                if (unavailableCountForHero > 0) {
                    FilterChip(
                        selected = hideUnavailableSkins,
                        onClick = onToggleHideUnavailable,
                        label = {
                            Text(
                                text = if (hideUnavailableSkins) {
                                    "Showing Working Only ($unavailableCountForHero hidden)"
                                } else {
                                    "Hide Unavailable ($unavailableCountForHero)"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_hide_unavailable_chip"),
                    )
                }
            }
        }

        if (scripts.isEmpty() && unavailableCountForHero > 0 && hideUnavailableSkins) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("all_skins_unavailable_empty_state"),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "All $unavailableCountForHero skin(s) are unavailable (404)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Try another hero or import a local .zip skin bundle above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onToggleHideUnavailable,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("show_unavailable_button"),
                        ) {
                            Text("Show Unavailable Skins")
                        }
                    }
                }
            }
        } else if (scripts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "No scripts staged for ${hero.name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Tap Import .zip above to stage a custom skin bundle for ${hero.name}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(scripts, key = { it.scriptId }) { script ->
                SkinScriptCard(
                    script = script,
                    isSelected = script.scriptId == selectedScriptId,
                    onSelect = { onScriptSelected(script.scriptId) },
                    onDownload = { onDownloadScript(script) },
                    onInject = { onInjectScript(script) },
                    onDeleteCustom = { onDeleteCustomScript(script) },
                )
            }
        }
    }
}

@Composable
private fun SkinScriptCard(
    script: SkinScriptItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onInject: () -> Unit,
    onDeleteCustom: () -> Unit,
) {
    val context = LocalContext.current
    val semantic = LocalSemanticColors.current
    val isActive = script.lifecycleState is ScriptLifecycleState.Injected
    val isError = script.lifecycleState is ScriptLifecycleState.Error
    val isUnavailable = script.isUnavailable

    val borderStroke = when {
        isUnavailable && isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        isUnavailable -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        isError -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
        isActive -> BorderStroke(2.dp, semantic.active)
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isUnavailable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                isActive -> semantic.activeContainer.copy(alpha = 0.35f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("script_card_${script.scriptId}"),
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .then(if (isUnavailable) Modifier.alpha(0.82f) else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    modifier = Modifier.testTag("script_radio_${script.scriptId}"),
                )
                RemoteArtworkImage(
                    primaryUrl = script.imageUrl,
                    fallbackUrl = script.fallbackImageUrl,
                    label = script.skinName,
                    contentDescription = "${script.skinName} preview",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val displayTitle = script.skinName.ifBlank { "Default Skin" }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val safeTextTag = script.tierTag.trim().takeIf {
                        it.isNotEmpty() &&
                            !it.startsWith("http://", ignoreCase = true) &&
                            !it.startsWith("https://", ignoreCase = true)
                    }
                    if (script.tagBadgeUrl.isNotBlank() || safeTextTag != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (script.tagBadgeUrl.isNotBlank()) {
                                RemoteTagBadgeImage(
                                    url = script.tagBadgeUrl,
                                    contentDescription = "$displayTitle tier badge",
                                    modifier = Modifier.testTag("script_tag_badge_${script.scriptId}"),
                                )
                            } else if (safeTextTag != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        text = safeTextTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (script.isUserImported) {
                        Text(
                            text = "${script.version} • Imported",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isUnavailable) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("unavailable_badge_${script.scriptId}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = "Skin download unavailable",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "404",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else if (script.downloadUrl.isNotBlank() && !script.isDownloaded && !script.lifecycleState.isBusy) {
                    FilledTonalButton(
                        onClick = onDownload,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("download_script_${script.scriptId}"),
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Filled.Refresh else Icons.Filled.Download,
                            contentDescription = if (isError) "Retry download" else stringResource(R.string.action_download_skin),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isError) "Retry" else stringResource(R.string.action_download_skin),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else if (script.isDownloaded && !isActive && !script.lifecycleState.isBusy && !script.isUserImported) {
                    FilledTonalButton(
                        onClick = onInject,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("inject_card_script_${script.scriptId}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = "Inject downloaded skin",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Inject",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else if (script.isUserImported && !isActive && !script.lifecycleState.isBusy) {
                    IconButton(
                        onClick = onDeleteCustom,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_script_${script.scriptId}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Remove imported script",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (script.compatibilityNotes.isNotBlank() &&
                !script.compatibilityNotes.startsWith("Uploaded ", ignoreCase = true) &&
                !script.compatibilityNotes.startsWith("Updated ", ignoreCase = true)
            ) {
                Text(
                    text = script.compatibilityNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScriptLifecycleChip(
                    state = script.lifecycleState,
                    modifier = Modifier.testTag("script_status_${script.scriptId}"),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "${script.targetRelativePaths.size} file(s)",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                    if (script.hasBackup) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                                val sizeText = Formatter.formatShortFileSize(context, script.backupByteSize)
                                Text(
                                    text = "Backup ($sizeText)",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            when (val state = script.lifecycleState) {
                is ScriptLifecycleState.Downloading -> {
                    OperationProgressBlock(
                        label = "Downloading remote skin package…",
                        progress = state.progress,
                    )
                }
                is ScriptLifecycleState.BackingUp -> {
                    OperationProgressBlock(
                        label = "Backing up original: ${state.currentFile}",
                        progress = state.progress,
                    )
                }
                is ScriptLifecycleState.Injecting -> {
                    OperationProgressBlock(
                        label = "Injecting asset: ${state.currentFile}",
                        progress = state.progress,
                    )
                }
                is ScriptLifecycleState.Restoring -> {
                    OperationProgressBlock(
                        label = "Restoring original: ${state.currentFile}",
                        progress = state.progress,
                    )
                }
                is ScriptLifecycleState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                is ScriptLifecycleState.Unavailable -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unavailable_notice_${script.scriptId}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.unavailable_skin_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun OperationProgressBlock(
    label: String,
    progress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "script_op_progress",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroStatsCombosTab(hero: HeroProfile) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("hero_stats_combos_list"),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Hero Attribute Profile",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AttributeMeterRow("Durability", hero.attributes.durability, MaterialTheme.colorScheme.secondary)
                    AttributeMeterRow("Offense", hero.attributes.offense, MaterialTheme.colorScheme.error)
                    AttributeMeterRow("Control Effect", hero.attributes.controlEffect, MaterialTheme.colorScheme.primary)
                    AttributeMeterRow("Difficulty", hero.attributes.difficulty, MaterialTheme.colorScheme.tertiary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = hero.companionNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (hero.combos.isNotEmpty()) {
            item {
                Text(
                    text = "Recommended Skill Combos",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "This hero was dynamically discovered from the remote skin configuration. Switch to the Skin Scripts tab to browse and manage its remote skins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        items(hero.combos) { combo ->
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = combo.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        combo.sequence.forEachIndexed { idx, step ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                if (idx < combo.sequence.lastIndex) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = combo.tips,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (hero.skills.isNotEmpty()) {
            item {
                Text(
                    text = "Skill Kit Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        items(hero.skills) { skill ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = skill.slot,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Text(
                        text = skill.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttributeMeterRow(
    label: String,
    value: Int,
    color: Color,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100)) / 100f,
        label = "attr_$label",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$value / 100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { animatedFraction },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
fun DockedScriptActionBar(
    selectedScript: SkinScriptItem?,
    shizukuState: ShizukuConnectionState,
    remoteConfigState: RemoteConfigState,
    applyNavigationBarInsets: Boolean,
    onInjectClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onClearBackupClick: () -> Unit,
    onOpenShizukuDiagnostics: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    val isBusy = selectedScript?.lifecycleState?.isBusy == true
    val isInjected = selectedScript?.lifecycleState is ScriptLifecycleState.Injected
    val isUnavailable = selectedScript?.isUnavailable == true
    val hasBackup = selectedScript?.hasBackup == true
    val activeRemoteConfig = when (remoteConfigState) {
        is RemoteConfigState.Ready -> remoteConfigState.config
        is RemoteConfigState.Failed -> remoteConfigState.lastKnownConfig
        RemoteConfigState.Loading -> null
    }
    val isBlockedByRemote = activeRemoteConfig != null && !activeRemoteConfig.canInjectRemotely

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyNavigationBarInsets) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedScript?.skinName ?: "No Script Selected",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            isUnavailable -> "Unavailable (404) • Please choose another skin"
                            isBlockedByRemote -> "Injection paused by remote config • Restore still enabled"
                            selectedScript == null -> "Select a skin script above to manage injection"
                            selectedScript.isDownloaded && !shizukuState.isReadyForIo ->
                                "Downloaded & saved • Connect Shizuku (${shizukuState.shortLabel()}) to inject"
                            !shizukuState.isReadyForIo -> "Shizuku blocked: ${shizukuState.shortLabel()} (tap pill to configure)"
                            else -> "Ready via Shizuku ${shizukuState.shortLabel()} • ${selectedScript.lifecycleState.displayLabel()}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUnavailable || isBlockedByRemote) MaterialTheme.colorScheme.error
                        else if (!shizukuState.isReadyForIo) semantic.warning
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(visible = !shizukuState.isReadyForIo) {
                    OutlinedButton(
                        onClick = onOpenShizukuDiagnostics,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("action_bar_fix_shizuku"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fix Shizuku")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onInjectClick,
                    enabled = selectedScript != null && !isBusy && !isBlockedByRemote && !isUnavailable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInjected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .minimumInteractiveComponentSize()
                        .testTag("inject_selected_button"),
                ) {
                    Icon(
                        imageVector = if (isInjected) Icons.Filled.Refresh else Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInjected) {
                            stringResource(R.string.action_reinject)
                        } else {
                            stringResource(R.string.action_inject_selected)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalButton(
                    onClick = onRestoreClick,
                    enabled = selectedScript != null && !isBusy && (hasBackup || isInjected),
                    modifier = Modifier
                        .weight(1.1f)
                        .minimumInteractiveComponentSize()
                        .testTag("restore_original_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_restore_original),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = onClearBackupClick,
                    enabled = selectedScript != null && !isBusy && hasBackup && !isInjected,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_backup_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(R.string.action_clear_backup),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
