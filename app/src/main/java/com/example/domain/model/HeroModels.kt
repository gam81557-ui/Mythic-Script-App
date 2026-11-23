package com.example.domain.model

enum class HeroRole(val label: String) {
    ALL("Show All"),
    ASSASSIN("Assassin"),
    FIGHTER("Fighter"),
    MAGE("Mage"),
    MARKSMAN("Marksman"),
    TANK("Tank"),
    SUPPORT("Support"),
}

data class HeroSkill(
    val slot: String,
    val name: String,
    val summary: String,
)

data class SkillCombo(
    val title: String,
    val sequence: List<String>,
    val tips: String,
)

data class HeroAttributes(
    val durability: Int,
    val offense: Int,
    val controlEffect: Int,
    val difficulty: Int,
)

data class HeroProfile(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val title: String,
    val role: HeroRole,
    val secondaryRole: HeroRole? = null,
    val specialty: String,
    val lane: String,
    val previewImageUrl: String = "",
    val fallbackImageUrl: String = "",
    val skinCount: Int = 0,
    val attributes: HeroAttributes,
    val skills: List<HeroSkill>,
    val combos: List<SkillCombo>,
    val companionNotes: String,
) {
    fun matchesQuery(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return name.contains(trimmed, ignoreCase = true) ||
            title.contains(trimmed, ignoreCase = true) ||
            specialty.contains(trimmed, ignoreCase = true) ||
            aliases.any { it.contains(trimmed, ignoreCase = true) }
    }

    fun matchesRole(roleFilter: HeroRole): Boolean {
        if (roleFilter == HeroRole.ALL) return true
        return role == roleFilter || secondaryRole == roleFilter
    }
}
