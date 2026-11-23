package com.example.data.catalog

import com.example.data.local.SkinScriptEntity
import com.example.domain.model.HeroAttributes
import com.example.domain.model.HeroProfile
import com.example.domain.model.HeroRole
import com.example.domain.model.HeroSkill
import com.example.domain.model.SkillCombo

data class CatalogScriptTemplate(
    val scriptId: String,
    val heroId: String,
    val skinName: String,
    val tierTag: String,
    val version: String,
    val compatibilityNotes: String,
    val relativeTargetPaths: List<String>,
)

object HeroCatalogProvider {

    val heroes: List<HeroProfile> = listOf(
        HeroProfile(
            id = "granger",
            name = "Granger",
            aliases = listOf("grang", "death chanter", "sonata"),
            title = "Death Chanter",
            role = HeroRole.MARKSMAN,
            specialty = "Burst / Reap",
            lane = "Gold Lane / Jungle",
            attributes = HeroAttributes(durability = 38, offense = 88, controlEffect = 28, difficulty = 58),
            skills = listOf(
                HeroSkill("Passive", "Capriccio", "Every 6th basic attack deals guaranteed critical damage; converts attack speed bonuses into physical attack."),
                HeroSkill("Skill 1", "Rhapsody", "Fires 6 rapid bullets in a line; the final shot deals guaranteed critical damage."),
                HeroSkill("Skill 2", "Rondo", "Dashes toward target direction and empowers the next two basic attacks."),
                HeroSkill("Ultimate", "Death Sonata", "Transforms violin into a super cannon, firing 3 explosive shells that slow enemies and reveal bushes."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Full Burst Ambush",
                    sequence = listOf("S2 Dash", "Enhanced AA", "S1 Rhapsody", "Ult x3", "S2 Reposition"),
                    tips = "Pre-load bullets until the 6th red bullet is ready before dashing in for instant crit burst.",
                ),
                SkillCombo(
                    title = "Kite & Finisher",
                    sequence = listOf("S1 Rhapsody", "S2 Side-step", "Ult Cannon"),
                    tips = "Cancel Rhapsody early by dashing if an assassin dives; each Ult shot triggers a mini-dash.",
                ),
            ),
            companionNotes = "Excels at snowballed mid-game turret pressure and objective zoning with Death Sonata range.",
        ),
        HeroProfile(
            id = "beatrix",
            name = "Beatrix",
            aliases = listOf("bea", "dawnbreak", "weapons"),
            title = "Dawnbreak Soldier",
            role = HeroRole.MARKSMAN,
            specialty = "Finisher / Damage",
            lane = "Gold Lane",
            attributes = HeroAttributes(durability = 34, offense = 94, controlEffect = 20, difficulty = 76),
            skills = listOf(
                HeroSkill("Passive", "Mechanical Genius", "Carries two of four distinct weapons: Renner, Bennett, Wesker, and Nibiru."),
                HeroSkill("Skill 1", "Masterful Gunner", "Swaps between primary and secondary equipped weapons instantly."),
                HeroSkill("Skill 2", "Tactical Reposition", "Rolls forward and instantly reloads the currently active firearm."),
                HeroSkill("Ultimate", "Weapon Ultimates", "Unleashes weapon-specific barrage (Apathy sniper, Rage mortar, Bulwark shotgun, Passion SMG)."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Nibiru + Wesker Duel",
                    sequence = listOf("Nibiru Ult", "S2 Reload", "S1 Swap Wesker", "Wesker Ult", "Point-Blank AA"),
                    tips = "Hold Wesker for close-range bush traps and swap to Renner/Nibiru for high-ground siege.",
                ),
            ),
            companionNotes = "Strongest wave-clear marksman with Bennett/Nibiru; keep S2 available to reload during dives.",
        ),
        HeroProfile(
            id = "brody",
            name = "Brody",
            aliases = listOf("lone star", "abyssal"),
            title = "The Lone Star",
            role = HeroRole.MARKSMAN,
            specialty = "Burst / Finisher",
            lane = "Gold Lane",
            attributes = HeroAttributes(durability = 46, offense = 84, controlEffect = 45, difficulty = 52),
            skills = listOf(
                HeroSkill("Passive", "Abyss Corrosion", "Heavy basic attacks move slowly while winding up, applying Abyss Marks up to 4 stacks."),
                HeroSkill("Skill 1", "Abyss Impact", "Fires a shockwave that pierces minions and gains extra damage & stacks per hit."),
                HeroSkill("Skill 2", "Corrosive Strike", "Dashes onto a target, briefly stunning them before leaping away in movement direction."),
                HeroSkill("Ultimate", "Torn-Apart Memory", "Locks onto all nearby marked enemies and detonates Abyss stacks based on lost HP."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "4-Stack Execution",
                    sequence = listOf("S1 Wave", "S2 Stun", "AA x2", "Ult Detonate"),
                    tips = "Line up S1 through the minion wave to apply 2–3 stacks safely before engaging with S2.",
                ),
            ),
            companionNotes = "Wins early lane trades against attack-speed marksmen thanks to high base damage and tanky build flexibility.",
        ),
        HeroProfile(
            id = "gusion",
            name = "Gusion",
            aliases = listOf("gus", "gossen", "holy blade"),
            title = "Holy Blade",
            role = HeroRole.ASSASSIN,
            specialty = "Burst / Magic Damage",
            lane = "Jungle / Mid Lane",
            attributes = HeroAttributes(durability = 39, offense = 92, controlEffect = 30, difficulty = 89),
            skills = listOf(
                HeroSkill("Passive", "Dagger Specialist", "Every 3 skill casts empowers the next basic attack to deal missing-HP magic damage and heal."),
                HeroSkill("Skill 1", "Sword Spike", "Throws a marking dagger; recasting dashes behind the marked target."),
                HeroSkill("Skill 2", "Shadowblade Slaughter", "Throws 5 daggers in a fan; recasting recalls all daggers for burst."),
                HeroSkill("Ultimate", "Incandescence", "Dashes forward, resets S1 and S2 cooldowns, and upgrades S2 to throw up to 10 daggers."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "10-Dagger Assassination",
                    sequence = listOf("S1 Mark", "S2 Fan", "Ult Reset", "S2 Fan", "S1 Dash", "S2 Recall", "Passive AA"),
                    tips = "Throw the first S2 before recasting S1 or Ult so all 10 recalled daggers converge behind the target.",
                ),
                SkillCombo(
                    title = "Quick Pickoff",
                    sequence = listOf("S2", "S1 Mark", "S1 Dash", "S2 Recall", "Passive AA"),
                    tips = "Saves Ultimate for escaping over walls after deleting a low-HP backliner.",
                ),
            ),
            companionNotes = "Timing S2 recall after S1 teleport guarantees every returning dagger passes directly through the target.",
        ),
        HeroProfile(
            id = "ling",
            name = "Ling",
            aliases = listOf("cyan finch", "finch", "wall jumper"),
            title = "Cyan Finch",
            role = HeroRole.ASSASSIN,
            specialty = "Chase / Burst",
            lane = "Jungle",
            attributes = HeroAttributes(durability = 36, offense = 90, controlEffect = 42, difficulty = 88),
            skills = listOf(
                HeroSkill("Passive", "Cloud Walker", "Leaps across walls with stealthy lightness and gains doubled critical chance."),
                HeroSkill("Skill 1", "Finch Poise", "Leaps onto a target wall with near-zero cooldown while energy lasts."),
                HeroSkill("Skill 2", "Defiant Sword", "Lunges forward with a guaranteed crit thrust; from a wall, slams down and slows."),
                HeroSkill("Ultimate", "Tempest of Blades", "Becomes untargetable, knocks up center targets, and drops 4 Tempest Swords to reset S2."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "4-Sword Tempest Reset",
                    sequence = listOf("S1 Wall", "S2 Slam", "Ult Center", "S2 Sword x4", "S1 Wall Escape"),
                    tips = "Aim S2 across each dropped Tempest Sword to refresh S2 cooldown and restore energy rapidly.",
                ),
            ),
            companionNotes = "Blue buff dependency is critical; rotate across map walls to split-push and flank squishies.",
        ),
        HeroProfile(
            id = "fanny",
            name = "Fanny",
            aliases = listOf("blade dancer", "cable"),
            title = "Blade Dancer",
            role = HeroRole.ASSASSIN,
            specialty = "Chase / Finisher",
            lane = "Jungle",
            attributes = HeroAttributes(durability = 42, offense = 95, controlEffect = 24, difficulty = 98),
            skills = listOf(
                HeroSkill("Passive", "Air Superiority", "Flight speed increases damage by up to 20% and applies Prey Marks that restore energy."),
                HeroSkill("Skill 1", "Tornado Strike", "Whirls blades around Fanny for AoE physical damage."),
                HeroSkill("Skill 2", "Steel Cable", "Fires cables that pull Fanny toward walls; triggers Tornado Strike on enemy collision."),
                HeroSkill("Ultimate", "Cut Throat", "Executes a strike whose damage scales with Prey Mark stacks."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Double-Cable Wall-Spam",
                    sequence = listOf("S2 Cable x2", "Wall Collision S1", "Ult Execute", "S2 Escape Cable"),
                    tips = "Use two staggered cables to bend around corners and conserve energy on short wall bounces.",
                ),
            ),
            companionNotes = "Dominates early jungle invades; watch out for suppression CC (Franco, Kaja) and Khufra ball.",
        ),
        HeroProfile(
            id = "hayabusa",
            name = "Hayabusa",
            aliases = listOf("haya", "shadow", "crimson"),
            title = "Crimson Shadow",
            role = HeroRole.ASSASSIN,
            specialty = "Chase / Burst",
            lane = "Jungle",
            attributes = HeroAttributes(durability = 38, offense = 89, controlEffect = 25, difficulty = 78),
            skills = listOf(
                HeroSkill("Passive", "Ninjutsu: Trace of Shadow", "Basic attacks and skills apply Shadow Marks (up to 4) amplifying all damage."),
                HeroSkill("Skill 1", "Ninjutsu: Phantom Shuriken", "Throws 3 returning shurikens that slow and restore energy on hit."),
                HeroSkill("Skill 2", "Ninjutsu: Quad Shadow", "Dashes forward leaving 4 shadows in cardinal directions for instant swaps."),
                HeroSkill("Ultimate", "Ougi: Shadow Kill", "Turns into a shadow delivering rapid untargetable slashes prioritizing marked targets."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Quad Shadow Isolation",
                    sequence = listOf("S2 Forward", "S1 Point-Blank", "AA x2", "Ult Shadow Kill", "S2 Back Shadow"),
                    tips = "Hit all 3 shurikens at melee range for instant 4 stacks before pressing Ult.",
                ),
            ),
            companionNotes = "Ensure enemies are isolated away from creeps before Ult so slashes don't scatter.",
        ),
        HeroProfile(
            id = "kagura",
            name = "Kagura",
            aliases = listOf("kag", "onmyouji", "umbrella"),
            title = "Onmyouji Master",
            role = HeroRole.MAGE,
            specialty = "Poke / Finisher",
            lane = "Mid Lane",
            attributes = HeroAttributes(durability = 40, offense = 84, controlEffect = 74, difficulty = 82),
            skills = listOf(
                HeroSkill("Passive", "Yin Yang Gathering", "Retrieving Seimei Umbrella grants a shield and stuns/slows nearby enemies."),
                HeroSkill("Skill 1", "Seimei Umbrella Open", "Sends the umbrella out for magic damage, slow, and bush vision."),
                HeroSkill("Skill 2", "Rasho Umbrella Flee", "Purifies CC and dashes (with umbrella) or teleports to the placed umbrella."),
                HeroSkill("Ultimate", "Yin Yang Overturn", "Knocks back enemies (with umbrella) or pulls enemies inward and detonates (without umbrella)."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Umbrella Pull & Burst",
                    sequence = listOf("S1 Place", "Ult Pull", "S1 Reposition", "S2 Teleport", "Ult Knockback", "S2 Escape"),
                    tips = "Casting Ult while the umbrella is out resets S1 cooldown so you can track targets before teleporting.",
                ),
            ),
            companionNotes = "Built-in purify on S2 makes Kagura one of the safest burst mages against heavy pick comps.",
        ),
        HeroProfile(
            id = "lunox",
            name = "Lunox",
            aliases = listOf("twilight", "chaos order"),
            title = "Twilight Goddess",
            role = HeroRole.MAGE,
            specialty = "Burst / Damage",
            lane = "Mid Lane / Gold / Jungle",
            attributes = HeroAttributes(durability = 45, offense = 91, controlEffect = 35, difficulty = 74),
            skills = listOf(
                HeroSkill("Passive", "Dreamland Twist", "Balanced Chaos & Order converts magic penetration into spell vamp and vice versa."),
                HeroSkill("Skill 1", "Starlight Pulse", "Order skill that damages enemies and heals Lunox per target hit."),
                HeroSkill("Skill 2", "Chaos Assault", "Chaos skill dealing % max HP magic damage—shreds tank frontlines."),
                HeroSkill("Ultimate", "Order & Chaos", "Order form turns invulnerable; Chaos form dashes and removes S2 cooldown."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Dark Ult Shred",
                    sequence = listOf("S3 Slow", "Chaos Ult Dash", "S2 Spam", "Order Ult Escape"),
                    tips = "Keep 1 stack of Chaos active before teamfights so you can unleash Chaos Ult immediately.",
                ),
            ),
            companionNotes = "Melts high-HP tanks in seconds while retaining Order Ult immunity against burst assassins.",
        ),
        HeroProfile(
            id = "chou",
            name = "Chou",
            aliases = listOf("kung fu", "bruce lee", "freestyle"),
            title = "Kung Fu Boy",
            role = HeroRole.FIGHTER,
            secondaryRole = HeroRole.TANK,
            specialty = "Chase / Control",
            lane = "EXP Lane / Roam",
            attributes = HeroAttributes(durability = 68, offense = 70, controlEffect = 86, difficulty = 80),
            skills = listOf(
                HeroSkill("Passive", "Only Fast", "Moving 8 yards empowers the next basic attack with 180% damage and slow."),
                HeroSkill("Skill 1", "Jeet Kune Do", "3-stage forward strike; the 3rd strike knocks enemies airborne and resets S2."),
                HeroSkill("Skill 2", "Shunpo", "Short dash granting CC immunity and a physical shield."),
                HeroSkill("Ultimate", "The Way of Dragon", "Kicks a target back; recasting chases them mid-air for bonus burst."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Flicker Dragon Kick",
                    sequence = listOf("S1 x2 Prep", "S2 CC-Immune", "S1 Knockup", "Flicker Behind", "Ult Kick Inward", "Ult Follow-up"),
                    tips = "Always save S2 dash to absorb enemy stuns right before landing your 3rd-stage Jeet Kune Do.",
                ),
            ),
            companionNotes = "Flexible between damage EXP assassin-fighter and full tank roamer.",
        ),
        HeroProfile(
            id = "yuzhong",
            name = "Yu Zhong",
            aliases = listOf("yz", "black dragon", "dragon"),
            title = "Black Dragon",
            role = HeroRole.FIGHTER,
            specialty = "Regen / Damage",
            lane = "EXP Lane",
            attributes = HeroAttributes(durability = 80, offense = 78, controlEffect = 72, difficulty = 66),
            skills = listOf(
                HeroSkill("Passive", "Cursing Touch", "Applies Sha Residue; at 5 stacks it erupts for missing-HP damage, speed, and heal."),
                HeroSkill("Skill 1", "Dragon Tail", "Sweeps cloak; outer sharpened edge deals 250% damage and applies 2 stacks."),
                HeroSkill("Skill 2", "Soul Grip", "Slows enemies and empowers basic attack to dash and stack Sha Residue."),
                HeroSkill("Ultimate", "Black Dragon Form", "Transforms into a dragon, ignoring terrain and knocking back enemies before Dragonoid mode."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Backline Dragon Dive",
                    sequence = listOf("Ult Fly-In", "Land Behind Carry", "S3 Knockup", "S1 Outer Edge", "S2 Enhanced AA"),
                    tips = "Trigger Petrify mid-animation during S3 dive to guarantee the airborne stun.",
                ),
            ),
            companionNotes = "Dominates Level 1–4 lane trades; land the outer ring of S1 to proc Sha Residue fast.",
        ),
        HeroProfile(
            id = "tigreal",
            name = "Tigreal",
            aliases = listOf("tig", "warrior of dawn", "setter"),
            title = "Warrior of Dawn",
            role = HeroRole.TANK,
            specialty = "Crowd Control",
            lane = "Roam",
            attributes = HeroAttributes(durability = 94, offense = 34, controlEffect = 96, difficulty = 36),
            skills = listOf(
                HeroSkill("Passive", "Fearless", "Gains Blessing stacks on skill cast or basic attack; at 4 stacks blocks the next basic attack."),
                HeroSkill("Skill 1", "Attack Wave", "Sends a shockwave slamming the ground up to 3 times with scaling slow."),
                HeroSkill("Skill 2", "Sacred Hammer", "Charges forward pushing enemies; recast knocks them airborne."),
                HeroSkill("Ultimate", "Implosion", "Pulls all nearby enemies inward and stuns them after a brief channel."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Flicker 5-Man Set",
                    sequence = listOf("Ult Channel", "Flicker Into Team", "S2 Push Toward Allies", "S2 Knockup", "S1 Slow"),
                    tips = "Press Ult first, then Flicker during the channel animation so enemies have zero reaction window.",
                ),
            ),
            companionNotes = "Premier engage roamer; check enemy purify/Diggie Ult cooldowns before committing Flicker.",
        ),
        HeroProfile(
            id = "khufra",
            name = "Khufra",
            aliases = listOf("desert tyrant", "ball", "anti dash"),
            title = "Desert Tyrant",
            role = HeroRole.TANK,
            specialty = "Initiator / Crowd Control",
            lane = "Roam",
            attributes = HeroAttributes(durability = 92, offense = 38, controlEffect = 95, difficulty = 64),
            skills = listOf(
                HeroSkill("Passive", "Spell Curse", "Every 12s empowers next basic attack with extended range, magic damage, and heal."),
                HeroSkill("Skill 1", "Tyrant's Revenge", "Stretches bandages to launch across long distances and knock enemies up."),
                HeroSkill("Skill 2", "Bouncing Ball", "Turns into a bouncing ball that increases defenses and knocks up any dashing hero."),
                HeroSkill("Ultimate", "Tyrant's Rage", "Slams nearby enemies toward Khufra; hitting a wall stuns them."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Anti-Dash Wall Slam",
                    sequence = listOf("S1 Bush Engage", "Ult Wall Slam", "S2 Bouncing Ball", "Passive AA"),
                    tips = "Hold S2 specifically to shut down Fanny, Ling, Joy, or Lance dashes in teamfights.",
                ),
            ),
            companionNotes = "Best counter-pick against dash-heavy assassin drafts.",
        ),
        HeroProfile(
            id = "angela",
            name = "Angela",
            aliases = listOf("bunnylove", "heartguard", "attach"),
            title = "Bunnylove",
            role = HeroRole.SUPPORT,
            specialty = "Guard / Support",
            lane = "Roam / Mid",
            attributes = HeroAttributes(durability = 52, offense = 44, controlEffect = 68, difficulty = 38),
            skills = listOf(
                HeroSkill("Passive", "Smart Heart", "Casting skills increases Angela's (and attached ally's) movement speed up to 30%."),
                HeroSkill("Skill 1", "Love Waves", "Heals allies and applies Lover's Marks to enemies for bonus damage and slow."),
                HeroSkill("Skill 2", "Puppet-on-a-String", "Tethers a target enemy; if unbroken after 1.5s, immobilizes them."),
                HeroSkill("Ultimate", "Heartguard", "Channels global shield and attaches to an ally anywhere on the map for 12s."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Global Dive Save",
                    sequence = listOf("Ult Attach", "S1 Stack Heal", "S2 Tether Target", "S1 Slow"),
                    tips = "Cast S1 before attaching to stack Lover's Marks early, then spam S1 continuously while attached.",
                ),
            ),
            companionNotes = "Pairs best with dive fighters/assassins (Ling, Fredrinn, Yu Zhong, Lancelot).",
        ),
        HeroProfile(
            id = "mathilda",
            name = "Mathilda",
            aliases = listOf("swift plume", "dash support"),
            title = "Swift Plume",
            role = HeroRole.SUPPORT,
            secondaryRole = HeroRole.ASSASSIN,
            specialty = "Initiator / Guard",
            lane = "Roam",
            attributes = HeroAttributes(durability = 64, offense = 58, controlEffect = 72, difficulty = 60),
            skills = listOf(
                HeroSkill("Passive", "Ancestral Guidance", "Moving charges stacks that empower her next basic attack with bonus magic damage & speed."),
                HeroSkill("Skill 1", "Soul Bloom", "Summons up to 6 wisps while moving that home in on nearby enemies."),
                HeroSkill("Skill 2", "Guiding Wind", "Dashes and grants an ally a free dash-to-Mathilda prompt plus shield."),
                HeroSkill("Ultimate", "Circling Eagle", "Locks onto an enemy, circles with knockback wisps, and slams in."),
            ),
            combos = listOf(
                SkillCombo(
                    title = "Taxi Pick & Disengage",
                    sequence = listOf("S1 Charge", "Ult Lock", "S2 Guiding Wind Taxi", "Slam Knockup"),
                    tips = "Cast S2 while circling with Ult so low-mobility marksmen or mages can dash out of danger.",
                ),
            ),
            companionNotes = "Enables immobile carries to escape or follow up engages via Guiding Wind.",
        ),
    ).map { hero ->
        hero.copy(
            previewImageUrl = HeroPortraitProvider.getPortraitUrl(hero.name),
            fallbackImageUrl = HeroPortraitProvider.getFallbackPortraitUrl(hero.name),
        )
    }

    val catalogScripts: List<CatalogScriptTemplate> = listOf(
        CatalogScriptTemplate(
            scriptId = "sc_granger_exorcist",
            heroId = "granger",
            skinName = "Lightborn / Exorcist Style FX",
            tierTag = "Collector",
            version = "v2.4.1",
            compatibilityNotes = "Targets Unity3D model, audio bank & Art assets for Patch 1.9+",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/granger_collector.unity3d",
                "files/dragon2017/assets/Audio/Android/granger_sfx.bnk",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_granger_megatron",
            heroId = "granger",
            skinName = "Cybernetic Mecha Overdrive",
            tierTag = "Collaboration",
            version = "v1.8.0",
            compatibilityNotes = "Includes custom Death Sonata projectile trail & voice lines",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/granger_mecha.unity3d",
                "files/dragon2017/assets/UI/Android/granger_mecha_portrait.bytes",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_gusion_k",
            heroId = "gusion",
            skinName = "K' Crimson Flame Pack",
            tierTag = "KOF",
            version = "v3.1.2",
            compatibilityNotes = "Full 10-dagger trail VFX + KOF voiceover swap",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/gusion_kof.unity3d",
                "files/dragon2017/assets/Audio/Android/gusion_kof_vo.bnk",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_gusion_dimension",
            heroId = "gusion",
            skinName = "Dimension Walker",
            tierTag = "11.11",
            version = "v2.0.5",
            compatibilityNotes = "Cosmic dagger recall particles & intro animation bundle",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/gusion_dimension.unity3d",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_ling_collector",
            heroId = "ling",
            skinName = "Serene Plume",
            tierTag = "Collector",
            version = "v2.2.0",
            compatibilityNotes = "Tempest of Blades golden ink VFX + wall leap trail",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/ling_serene.unity3d",
                "files/dragon2017/assets/Audio/Android/ling_serene.bnk",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_fanny_aspirant",
            heroId = "fanny",
            skinName = "Blade of Kibou",
            tierTag = "Aspirants",
            version = "v2.7.1",
            compatibilityNotes = "Anime cel-shaded rig & neon cable trails",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/fanny_kibou.unity3d",
                "files/dragon2017/assets/Audio/Android/fanny_kibou.bnk",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_kagura_waterlily",
            heroId = "kagura",
            skinName = "Water Lily",
            tierTag = "Annual Starlight",
            version = "v1.9.4",
            compatibilityNotes = "Lotus umbrella shader & Yin Yang Overturn ripple FX",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/kagura_waterlily.unity3d",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_chou_iori",
            heroId = "chou",
            skinName = "Iori Yagami",
            tierTag = "KOF",
            version = "v3.0.0",
            compatibilityNotes = "Violet flame Shunpo + Way of Dragon sound effects",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/chou_iori.unity3d",
                "files/dragon2017/assets/Audio/Android/chou_iori.bnk",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_yuzhong_prime",
            heroId = "yuzhong",
            skinName = "Cosmic Dragon",
            tierTag = "M5 Prime",
            version = "v1.5.2",
            compatibilityNotes = "Prime dragon transformation rig & Sha eruption particles",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/yuzhong_prime.unity3d",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_tigreal_lightborn",
            heroId = "tigreal",
            skinName = "Lightborn Defender",
            tierTag = "Lightborn",
            version = "v1.6.0",
            compatibilityNotes = "Golden implosion shockwave & shield aura",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/tigreal_lightborn.unity3d",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_angela_aspirant",
            heroId = "angela",
            skinName = "Cyber Cherubin",
            tierTag = "Aspirants",
            version = "v2.1.0",
            compatibilityNotes = "Heartguard hologram shield & Love Waves synth audio",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/angela_cyber.unity3d",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_beatrix_prime",
            heroId = "beatrix",
            skinName = "Stellar Brilliance",
            tierTag = "M4 Prime",
            version = "v1.4.3",
            compatibilityNotes = "Custom 4-weapon muzzle & ultimate particle swaps",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/beatrix_prime.unity3d",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_lunox_legend",
            heroId = "lunox",
            skinName = "Divine Goddess",
            tierTag = "Legend",
            version = "v2.0.1",
            compatibilityNotes = "Order/Chaos dual celestial aura & recall animation",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/lunox_legend.unity3d",
            ),
        ),
        CatalogScriptTemplate(
            scriptId = "sc_hayabusa_shura",
            heroId = "hayabusa",
            skinName = "Shura",
            tierTag = "11.11",
            version = "v2.3.0",
            compatibilityNotes = "Oni mask Shadow Kill slashes & Quad Shadow trail",
            relativeTargetPaths = listOf(
                "files/dragon2017/assets/Art/Android/hayabusa_shura.unity3d",
            ),
        ),
    )

    fun toSeedEntities(stagedRootDir: String): List<SkinScriptEntity> {
        val baseTime = 1_700_000_000_000L
        return catalogScripts.mapIndexed { index, tpl ->
            SkinScriptEntity(
                scriptId = tpl.scriptId,
                heroId = tpl.heroId,
                skinName = tpl.skinName,
                tierTag = tpl.tierTag,
                version = tpl.version,
                compatibilityNotes = tpl.compatibilityNotes,
                targetRelativePathsJoined = tpl.relativeTargetPaths.joinToString("\n"),
                stagedDirPath = "$stagedRootDir/${tpl.scriptId}",
                isUserImported = false,
                persistedStatus = "IDLE",
                errorMessage = null,
                lastUpdatedAt = baseTime - index,
            )
        }
    }

    fun getHeroById(heroId: String): HeroProfile? = heroes.find { it.id == heroId }
}
