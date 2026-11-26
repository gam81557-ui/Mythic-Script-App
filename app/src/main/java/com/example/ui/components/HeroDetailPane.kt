package com.example.ui.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.RemoteConfigState
import com.example.domain.model.HeroDetailTab
import com.example.domain.model.HeroProfile
import com.example.domain.model.HeroRole
import com.example.domain.model.ScriptLifecycleState
import com.example.domain.model.SkinScriptItem
import com.example.localization.localizedLabel
import com.example.localization.localizedShortLabel
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeroBannerHeader(
            hero = hero,
            activeScript = scripts.firstOrNull { it.lifecycleState is ScriptLifecycleState.Injected },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .liquidGlass(
                    shape = RoundedCornerShape(22.dp),
                    tint = LiquidGlass.Surface,
                    elevation = 4.dp,
                    opacity = 0.96f,
                ),
        ) {
            PrimaryTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = Color.Transparent,
            ) {
                HeroDetailTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == activeTab,
                        onClick = { onTabSelected(tab) },
                        selectedContentColor = LiquidGlass.TealDeep,
                        unselectedContentColor = LiquidGlass.Muted,
                        text = {
                            Text(
                                text = tab.localizedLabel(),
                                fontWeight = if (tab == activeTab) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.testTag("hero_tab_${tab.name.lowercase()}"),
                    )
                }
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
    val bannerShape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(bannerShape)
            .border(1.dp, Color.White.copy(alpha = 0.68f), bannerShape),
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
                        contentDescription = stringResource(R.string.hero_portrait, hero.name),
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${hero.title} • ${hero.lane}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (activeScript != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = semantic.activeContainer,
                        modifier = Modifier.widthIn(max = 140.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.active_script_prefix, activeScript.skinName),
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
                BannerTagChip(text = if (hero.role == HeroRole.ALL) "MLBB" else hero.role.localizedLabel())
                hero.secondaryRole?.let { BannerTagChip(text = it.localizedLabel()) }
                BannerTagChip(text = hero.specialty)
            }
        }
    }
}

@Composable
private fun BannerTagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = LiquidGlass.SurfaceStrong,
        border = BorderStroke(1.dp, LiquidGlass.Hairline),
        modifier = Modifier.widthIn(max = 180.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = LiquidGlass.Ink,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
                            text = stringResource(R.string.hero_skin_bundles, hero.name),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val subtitle = if (unavailableCountForHero > 0) {
                            stringResource(R.string.skins_shown_unavailable, scripts.size, unavailableCountForHero)
                        } else {
                            stringResource(R.string.skins_available, scripts.size)
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
                                    stringResource(R.string.show_working_only, unavailableCountForHero)
                                } else {
                                    stringResource(R.string.hide_unavailable, unavailableCountForHero)
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
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            tint = LiquidGlass.Surface,
                            elevation = 2.dp,
                            opacity = 0.96f,
                        )
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
                            text = stringResource(R.string.all_skins_unavailable, unavailableCountForHero),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.all_skins_unavailable_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onToggleHideUnavailable,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("show_unavailable_button"),
                        ) {
                            Text(stringResource(R.string.show_unavailable_skins))
                        }
                    }
                }
            }
        } else if (scripts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            tint = LiquidGlass.Surface,
                            elevation = 2.dp,
                            opacity = 0.96f,
                        ),
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
                            text = stringResource(R.string.no_scripts_staged, hero.name),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.no_scripts_staged_hint, hero.name),
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
    val defaultSkin = stringResource(R.string.default_skin)
    val isActive = script.lifecycleState is ScriptLifecycleState.Injected
    val isError = script.lifecycleState is ScriptLifecycleState.Error
    val isUnavailable = script.isUnavailable

    val borderStroke = when {
        isUnavailable && isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        isUnavailable -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        isError -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
        isActive -> BorderStroke(2.dp, semantic.active)
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, LiquidGlass.Hairline)
    }

    val cardShape = RoundedCornerShape(20.dp)
    val glassTint = when {
        isUnavailable -> LiquidGlass.SurfaceMuted
        isActive -> LiquidGlass.ActiveSurface
        isSelected -> LiquidGlass.SelectedSurface
        else -> LiquidGlass.Surface
    }
    Card(
        onClick = onSelect,
        shape = cardShape,
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = cardShape,
                tint = glassTint,
                elevation = if (isSelected) 4.dp else 2.dp,
                opacity = 0.96f,
            )
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
                    contentDescription = stringResource(R.string.hero_portrait, script.skinName),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val displayTitle = script.skinName.ifBlank { defaultSkin }
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
                                    contentDescription = stringResource(R.string.tier_badge, displayTitle),
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
                            text = stringResource(R.string.imported_suffix, script.version),
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
                                contentDescription = stringResource(R.string.skin_download_unavailable),
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
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LiquidGlass.SurfaceMuted,
                            contentColor = LiquidGlass.Ink,
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("download_script_${script.scriptId}"),
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Filled.Refresh else Icons.Filled.Download,
                            contentDescription = if (isError) stringResource(R.string.action_retry_download) else stringResource(R.string.action_download_skin),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isError) stringResource(R.string.action_retry) else stringResource(R.string.action_download_skin),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else if (script.isDownloaded && !isActive && !script.lifecycleState.isBusy && !script.isUserImported) {
                    FilledTonalButton(
                        onClick = onInject,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LiquidGlass.SurfaceMuted,
                            contentColor = LiquidGlass.Ink,
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("inject_card_script_${script.scriptId}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = stringResource(R.string.inject_downloaded_skin),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_inject),
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
                            contentDescription = stringResource(R.string.remove_imported_script),
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
                            text = stringResource(R.string.files_count, script.targetRelativePaths.size),
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
                                    text = stringResource(R.string.backup_size, sizeText),
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
                        label = stringResource(R.string.downloading_remote_package),
                        progress = state.progress,
                    )
                }
                is ScriptLifecycleState.BackingUp -> {
                    OperationProgressBlock(
                        label = stringResource(R.string.backing_up_original, state.currentFile),
                        progress = state.progress,
                    )
                }
                is ScriptLifecycleState.Injecting -> {
                    OperationProgressBlock(
                        label = stringResource(R.string.injecting_asset, state.currentFile),
                        progress = state.progress,
                    )
                }
                is ScriptLifecycleState.Restoring -> {
                    OperationProgressBlock(
                        label = stringResource(R.string.restoring_original, state.currentFile),
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(20.dp),
                        tint = LiquidGlass.Surface,
                        elevation = 2.dp,
                        opacity = 0.96f,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.hero_attribute_profile),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AttributeMeterRow(stringResource(R.string.attribute_durability), hero.attributes.durability, MaterialTheme.colorScheme.secondary)
                    AttributeMeterRow(stringResource(R.string.attribute_offense), hero.attributes.offense, MaterialTheme.colorScheme.error)
                    AttributeMeterRow(stringResource(R.string.attribute_control_effect), hero.attributes.controlEffect, MaterialTheme.colorScheme.primary)
                    AttributeMeterRow(stringResource(R.string.attribute_difficulty), hero.attributes.difficulty, MaterialTheme.colorScheme.tertiary)

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
                    text = stringResource(R.string.recommended_skill_combos),
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
                        text = stringResource(R.string.dynamic_hero_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        items(hero.combos) { combo ->
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, LiquidGlass.Hairline),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(20.dp),
                        tint = LiquidGlass.Surface,
                        elevation = 2.dp,
                        opacity = 0.96f,
                    ),
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
                    text = stringResource(R.string.skill_kit_breakdown),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        items(hero.skills) { skill ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        tint = LiquidGlass.Surface,
                        elevation = 2.dp,
                        opacity = 0.96f,
                    ),
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
) {
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

    val barShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Surface(
        shape = barShape,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = barShape,
                tint = LiquidGlass.SurfaceStrong,
                elevation = 18.dp,
                opacity = 0.97f,
            ),
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
                        text = selectedScript?.skinName ?: stringResource(R.string.no_script_selected),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            isUnavailable -> stringResource(R.string.unavailable_choose_other)
                            isBlockedByRemote -> stringResource(R.string.injection_paused_remote)
                            selectedScript == null -> stringResource(R.string.select_script_hint)
                            shizukuState.isReadyForIo -> stringResource(
                                R.string.ready_via_shizuku,
                                shizukuState.localizedShortLabel(),
                                selectedScript.lifecycleState.localizedLabel(),
                            )
                            else -> stringResource(
                                R.string.ready_direct_or_shizuku,
                                selectedScript.lifecycleState.localizedLabel(),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUnavailable || isBlockedByRemote) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onInjectClick,
                    enabled = selectedScript != null && !isBusy && !isBlockedByRemote && !isUnavailable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInjected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.55f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("inject_selected_button"),
                ) {
                    Icon(
                        imageVector = if (isInjected) Icons.Filled.Refresh else Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isInjected) {
                            stringResource(R.string.action_reinject)
                        } else {
                            stringResource(R.string.action_inject_selected)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalButton(
                    onClick = onRestoreClick,
                    enabled = selectedScript != null && !isBusy && (hasBackup || isInjected),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = LiquidGlass.SurfaceMuted,
                        contentColor = LiquidGlass.Ink,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("restore_original_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_restore_original),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = onClearBackupClick,
                    enabled = selectedScript != null && !isBusy && hasBackup && !isInjected,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp)
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
