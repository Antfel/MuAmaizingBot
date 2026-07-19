package com.example.muamaizingbot.profile

/**
 * Bot operating modes stored in [BotProfile.botMode].
 *
 * - [FARM]: classic farm loop (may seek NPC elf buff).
 * - [ELF_BUFF_GIVER]: stay on farm spot as buff post; periodic / manual skill cast (no NPC seek).
 * - [ELF_BUFF_WAR]: Divine War/APEX — random taps, green-bar buff, no PK switch.
 */
object BotMode {
    const val FARM = "farm"
    const val ELF_BUFF_GIVER = "elf_buff_giver"
    const val ELF_BUFF_WAR = "elf_buff_war"

    fun normalize(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            ELF_BUFF_GIVER, "elf-buff-giver", "elf_giver", "giver" -> ELF_BUFF_GIVER
            ELF_BUFF_WAR, "elf-buff-war", "war", "apex", "war_apex" -> ELF_BUFF_WAR
            else -> FARM
        }
    }

    fun label(mode: String): String = when (normalize(mode)) {
        ELF_BUFF_GIVER -> "Elf Buff · Mundo abierto"
        ELF_BUFF_WAR -> "Elf Buff · War / APEX"
        else -> "Farm"
    }
}
fun BotProfile.normalizedBotMode(): String = BotMode.normalize(botMode)

fun BotProfile.isElfBuffGiverMode(): Boolean = normalizedBotMode() == BotMode.ELF_BUFF_GIVER

fun BotProfile.isElfBuffWarMode(): Boolean = normalizedBotMode() == BotMode.ELF_BUFF_WAR

/** Open-world giver or War — both skip NPC elf seek. */
fun BotProfile.isElfBuffPostMode(): Boolean = isElfBuffGiverMode() || isElfBuffWarMode()

fun BotProfile.isFarmMode(): Boolean = !isElfBuffPostMode()
