package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.HeroProfile
import com.example.domain.model.HeroRole
import com.example.data.catalog.HeroPortraitProvider

private val DarkGridCanvas = Color(0xFF0B101B)
private val DarkSearchSurface = Color(0xFF141C2E)
private val DarkCardSurface = Color(0xFF161F33)
private val DarkCardBorder = Color(0xFF283550)
private val ActiveGreen = Color(0xFF10B981)
private val SelectedBlue = Color(0xFF3B82F6)

@Composable
fun HeroRosterPane(
    heroes: List<HeroProfile>,
    roleFilter: HeroRole,
    searchQuery: String,
    selectedHeroId: String,
    activeScriptIdByHero: Map<String, String>,
    onRoleSelected: (HeroRole) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onHeroClick: (HeroProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = DarkGridCanvas,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("hero_search_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_heroes_hint),
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_search_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                tint = Color(0xFFCBD5E1),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSearchSurface,
                    unfocusedContainerColor = DarkSearchSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = SelectedBlue,
                    unfocusedBorderColor = DarkCardBorder,
                    cursorColor = SelectedBlue,
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroRole.entries.forEach { role ->
                    val selected = role == roleFilter
                    FilterChip(
                        selected = selected,
                        onClick = { onRoleSelected(role) },
                        label = {
                            Text(
                                text = role.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSearchSurface,
                            labelColor = Color(0xFFCBD5E1),
                            selectedContainerColor = SelectedBlue,
                            selectedLabelColor = Color.White,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = DarkCardBorder,
                            selectedBorderColor = SelectedBlue,
                        ),
                        modifier = Modifier.testTag("role_chip_${role.name.lowercase()}"),
                    )
                }
            }

            if (heroes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color(0xFF94A3B8),
                        )
                        Text(
                            text = "No matching heroes found",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            text = "Try clearing the search term or tapping Show All.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hero_grid"),
                ) {
                    items(heroes, key = { it.id }) { hero ->
                        HeroGridCard(
                            hero = hero,
                            isSelected = hero.id == selectedHeroId,
                            hasActiveScript = activeScriptIdByHero.containsKey(hero.id),
                            onClick = { onHeroClick(hero) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroGridCard(
    hero: HeroProfile,
    isSelected: Boolean,
    hasActiveScript: Boolean,
    onClick: () -> Unit,
) {
    val borderStroke = when {
        hasActiveScript -> BorderStroke(2.dp, ActiveGreen)
        isSelected -> BorderStroke(2.dp, SelectedBlue)
        else -> BorderStroke(1.dp, DarkCardBorder)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .testTag("hero_card_${hero.id}"),
    ) {
        val heroPortrait = HeroPortraitProvider.resolveHeroPortrait(hero.name, hero.previewImageUrl)
        val heroFallback = HeroPortraitProvider.resolveHeroFallback(hero.name, hero.fallbackImageUrl)

        Box(modifier = Modifier.fillMaxSize()) {
            RemoteArtworkImage(
                primaryUrl = heroPortrait,
                fallbackUrl = heroFallback,
                label = hero.name,
                contentDescription = "${hero.name} portrait",
                accentColor = roleAccentColor(hero.role),
                modifier = Modifier.fillMaxSize(),
            )

            // Bottom gradient scrim for crisp readable hero name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xB3070B13),
                                Color(0xF0070B13),
                            )
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = hero.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (hero.role != HeroRole.ALL) {
                        Text(
                            text = hero.role.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (hasActiveScript) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ActiveGreen.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Active script injected",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (hero.skinCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC0B101B),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        text = "${hero.skinCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun roleAccentColor(role: HeroRole): Color = when (role) {
    HeroRole.ASSASSIN -> Color(0xFFA855F7)
    HeroRole.FIGHTER -> Color(0xFFF97316)
    HeroRole.MAGE -> Color(0xFF38BDF8)
    HeroRole.MARKSMAN -> Color(0xFFFACC15)
    HeroRole.TANK -> Color(0xFF22C55E)
    HeroRole.SUPPORT -> Color(0xFF14B8A6)
    HeroRole.ALL -> Color(0xFF3B82F6)
}
