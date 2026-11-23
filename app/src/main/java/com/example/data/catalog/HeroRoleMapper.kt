package com.example.data.catalog

import com.example.domain.model.HeroRole

/**
 * Built-in MLBB hero-to-role lookup table so role filtering works accurately across
 * all heroes discovered from remote JSON configurations even when "role" is omitted.
 */
object HeroRoleMapper {

    private val roleByHeroLower: Map<String, HeroRole> = mapOf(
        // Assassins
        "aamon" to HeroRole.ASSASSIN,
        "benedetta" to HeroRole.ASSASSIN,
        "fanny" to HeroRole.ASSASSIN,
        "gusion" to HeroRole.ASSASSIN,
        "hanzo" to HeroRole.ASSASSIN,
        "hayabusa" to HeroRole.ASSASSIN,
        "helcurt" to HeroRole.ASSASSIN,
        "joy" to HeroRole.ASSASSIN,
        "karina" to HeroRole.ASSASSIN,
        "lancelot" to HeroRole.ASSASSIN,
        "ling" to HeroRole.ASSASSIN,
        "natalia" to HeroRole.ASSASSIN,
        "nolan" to HeroRole.ASSASSIN,
        "saber" to HeroRole.ASSASSIN,
        "selena" to HeroRole.ASSASSIN,
        "suyou" to HeroRole.ASSASSIN,
        "yi sun-shin" to HeroRole.ASSASSIN,
        // Marksmen
        "beatrix" to HeroRole.MARKSMAN,
        "brody" to HeroRole.MARKSMAN,
        "bruno" to HeroRole.MARKSMAN,
        "claude" to HeroRole.MARKSMAN,
        "clint" to HeroRole.MARKSMAN,
        "granger" to HeroRole.MARKSMAN,
        "hanabi" to HeroRole.MARKSMAN,
        "irithel" to HeroRole.MARKSMAN,
        "ixia" to HeroRole.MARKSMAN,
        "karrie" to HeroRole.MARKSMAN,
        "kimmy" to HeroRole.MARKSMAN,
        "layla" to HeroRole.MARKSMAN,
        "lesley" to HeroRole.MARKSMAN,
        "melissa" to HeroRole.MARKSMAN,
        "miya" to HeroRole.MARKSMAN,
        "moskov" to HeroRole.MARKSMAN,
        "natan" to HeroRole.MARKSMAN,
        "popol and kupa" to HeroRole.MARKSMAN,
        "popol & kupa" to HeroRole.MARKSMAN,
        "wanwan" to HeroRole.MARKSMAN,
        // Mages
        "alice" to HeroRole.MAGE,
        "aurora" to HeroRole.MAGE,
        "cecilion" to HeroRole.MAGE,
        "chang'e" to HeroRole.MAGE,
        "change" to HeroRole.MAGE,
        "cyclops" to HeroRole.MAGE,
        "eudora" to HeroRole.MAGE,
        "faramis" to HeroRole.MAGE,
        "gord" to HeroRole.MAGE,
        "harith" to HeroRole.MAGE,
        "harley" to HeroRole.MAGE,
        "kadita" to HeroRole.MAGE,
        "kagura" to HeroRole.MAGE,
        "kimmy" to HeroRole.MARKSMAN,
        "luo yi" to HeroRole.MAGE,
        "lunox" to HeroRole.MAGE,
        "lylia" to HeroRole.MAGE,
        "nana" to HeroRole.MAGE,
        "novaria" to HeroRole.MAGE,
        "odette" to HeroRole.MAGE,
        "pharsa" to HeroRole.MAGE,
        "vale" to HeroRole.MAGE,
        "valentina" to HeroRole.MAGE,
        "valir" to HeroRole.MAGE,
        "vexana" to HeroRole.MAGE,
        "xavier" to HeroRole.MAGE,
        "yve" to HeroRole.MAGE,
        "zhask" to HeroRole.MAGE,
        "zhuxin" to HeroRole.MAGE,
        // Fighters
        "aldous" to HeroRole.FIGHTER,
        "alpha" to HeroRole.FIGHTER,
        "alucard" to HeroRole.FIGHTER,
        "argus" to HeroRole.FIGHTER,
        "arlott" to HeroRole.FIGHTER,
        "aulus" to HeroRole.FIGHTER,
        "badang" to HeroRole.FIGHTER,
        "balmond" to HeroRole.FIGHTER,
        "bane" to HeroRole.FIGHTER,
        "chou" to HeroRole.FIGHTER,
        "cici" to HeroRole.FIGHTER,
        "dyrroth" to HeroRole.FIGHTER,
        "freya" to HeroRole.FIGHTER,
        "guinevere" to HeroRole.FIGHTER,
        "hilda" to HeroRole.FIGHTER,
        "jawhead" to HeroRole.FIGHTER,
        "julian" to HeroRole.FIGHTER,
        "kaja" to HeroRole.FIGHTER,
        "khaleed" to HeroRole.FIGHTER,
        "lapu-lapu" to HeroRole.FIGHTER,
        "leomord" to HeroRole.FIGHTER,
        "lukas" to HeroRole.FIGHTER,
        "martis" to HeroRole.FIGHTER,
        "masha" to HeroRole.FIGHTER,
        "minsitthar" to HeroRole.FIGHTER,
        "paquito" to HeroRole.FIGHTER,
        "phoveus" to HeroRole.FIGHTER,
        "roger" to HeroRole.FIGHTER,
        "ruby" to HeroRole.FIGHTER,
        "silvanna" to HeroRole.FIGHTER,
        "sun" to HeroRole.FIGHTER,
        "terizla" to HeroRole.FIGHTER,
        "thamuz" to HeroRole.FIGHTER,
        "x.borg" to HeroRole.FIGHTER,
        "xborg" to HeroRole.FIGHTER,
        "yin" to HeroRole.FIGHTER,
        "yu zhong" to HeroRole.FIGHTER,
        "zilong" to HeroRole.FIGHTER,
        // Tanks
        "akai" to HeroRole.TANK,
        "atlas" to HeroRole.TANK,
        "barats" to HeroRole.TANK,
        "baxia" to HeroRole.TANK,
        "belerick" to HeroRole.TANK,
        "chip" to HeroRole.TANK,
        "edith" to HeroRole.TANK,
        "esmeralda" to HeroRole.TANK,
        "franco" to HeroRole.TANK,
        "fredrinn" to HeroRole.TANK,
        "gatotkaca" to HeroRole.TANK,
        "gloo" to HeroRole.TANK,
        "grock" to HeroRole.TANK,
        "hylos" to HeroRole.TANK,
        "johnson" to HeroRole.TANK,
        "khufra" to HeroRole.TANK,
        "lolita" to HeroRole.TANK,
        "minotaur" to HeroRole.TANK,
        "tigreal" to HeroRole.TANK,
        "uranus" to HeroRole.TANK,
        // Supports
        "angela" to HeroRole.SUPPORT,
        "carmilla" to HeroRole.SUPPORT,
        "diggie" to HeroRole.SUPPORT,
        "estes" to HeroRole.SUPPORT,
        "floryn" to HeroRole.SUPPORT,
        "kalea" to HeroRole.SUPPORT,
        "mathilda" to HeroRole.SUPPORT,
        "rafaela" to HeroRole.SUPPORT,
    )

    fun resolveRole(heroName: String, explicitRoleHint: String = ""): HeroRole {
        if (explicitRoleHint.isNotBlank()) {
            val matched = HeroRole.entries.firstOrNull {
                it != HeroRole.ALL && it.label.equals(explicitRoleHint.trim(), ignoreCase = true)
            }
            if (matched != null) return matched
        }
        val normalized = heroName.trim().lowercase()
        return roleByHeroLower[normalized]
            ?: roleByHeroLower.entries.firstOrNull { normalized.contains(it.key) }?.value
            ?: HeroRole.ALL
    }
}
