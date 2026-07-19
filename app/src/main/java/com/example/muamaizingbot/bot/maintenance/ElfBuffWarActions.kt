package com.example.muamaizingbot.bot.maintenance

import android.graphics.Color
import android.util.Log
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Divine War / APEX elf loop: random world taps → green HP buff → Focus Boss clear.
 * Never touches PK All/Union. Does not force Auto combat.
 */
object ElfBuffWarActions {

    private const val TAG = "ElfBuffWar"
    private const val BETWEEN_SKILLS_MS = 280L
    private const val POST_CAST_MS = 1_000L
    private const val POST_CLEAR_MS = 220L
    private const val BETWEEN_TAPS_MS = 450L
    private const val AFTER_BUFF_GAP_MS = 800L

    /** World tap band @ 1280×720 — center playfield, away from HUD clusters. */
    private const val TAP_LEFT = 280
    private const val TAP_TOP = 120
    private const val TAP_RIGHT = 900
    private const val TAP_BOTTOM = 480
    private const val BASE_W = 1280
    private const val BASE_H = 720

    private val CAST_ORDER = listOf(
        ElfBuffSkillMapper.SkillId.GREATER_DAMAGE,
        ElfBuffSkillMapper.SkillId.GREATER_DEFENSE,
    )

    suspend fun tick(profile: BotProfile): Boolean {
        if (!profile.isElfBuffWarMode()) {
            return true
        }

        if (ElfBuffSummonDismiss.dismissIfPresent()) {
            Log.d(TAG, "[WAR] summon dismissed — resume taps next tick")
            return true
        }

        when (ElfBuffFocusHud.classifyUnionFocus()) {
            ElfBuffFocusHud.HpBarColor.GREEN -> {
                Log.d(TAG, "[WAR] green focus → buff + clear")
                val castOk = castMappedSkills()
                delay(POST_CAST_MS)
                if (!ElfBuffFocusHud.clearFocus()) {
                    Log.w(TAG, "[WAR] Focus Boss clear failed after buff")
                }
                delay(POST_CLEAR_MS)
                delay(AFTER_BUFF_GAP_MS)
                return castOk
            }
            ElfBuffFocusHud.HpBarColor.RED -> {
                Log.d(TAG, "[WAR] red focus → clear only")
                if (!ElfBuffFocusHud.clearFocus()) {
                    Log.w(TAG, "[WAR] Focus Boss clear failed (red)")
                }
                delay(POST_CLEAR_MS)
                return true
            }
            null -> {
                // No focus — random world tap (acquire focus).
                randomWorldTap()
                delay(BETWEEN_TAPS_MS)
                return true
            }
        }
    }

    private suspend fun randomWorldTap(): Boolean {
        val (w, h) = RefCoords.activeScreenSize()
        var x: Int
        var y: Int
        var attempts = 0
        do {
            x = Random.nextInt(TAP_LEFT * w / BASE_W, TAP_RIGHT * w / BASE_W)
            y = Random.nextInt(TAP_TOP * h / BASE_H, TAP_BOTTOM * h / BASE_H)
            attempts++
        } while (
            ElfBuffExclusionZones.containsPoint(x, y, w, h) && attempts < 12
        )
        if (ElfBuffExclusionZones.containsPoint(x, y, w, h)) {
            // Fallback: hard center of world band.
            x = (TAP_LEFT + TAP_RIGHT) / 2 * w / BASE_W
            y = (TAP_TOP + TAP_BOTTOM) / 2 * h / BASE_H
        }
        Log.d(TAG, "[WAR] random tap screen=($x,$y)")
        return NavigationVision.tapScreen(x, y, label = "war_random")
    }

    private suspend fun castMappedSkills(): Boolean {
        if (!ElfBuffSkillMapper.ensureMapped()) {
            Log.w(TAG, "[WAR] cast skipped — skills not mapped")
            return false
        }
        val byId = ElfBuffSkillMapper.mappedSkills().associateBy { it.id }
        var allOk = true
        for ((index, skillId) in CAST_ORDER.withIndex()) {
            val skill = byId[skillId]
            if (skill == null) {
                Log.w(TAG, "[WAR] missing mapped skill=${skillId.name}")
                allOk = false
                continue
            }
            Log.d(
                TAG,
                "[WAR] cast skill=${skill.id.name} " +
                    "ref=(${skill.refX},${skill.refY}) screen=(${skill.screenX},${skill.screenY})",
            )
            ElfBuffDebugDump.saveTapPlan(
                label = "war_before_${skill.id.name.lowercase()}",
                title = "WAR ${skill.id.name} tap=(${skill.screenX},${skill.screenY})",
                taps = listOf(
                    ElfBuffDebugDump.TapMark(
                        skill.screenX,
                        skill.screenY,
                        skill.id.name,
                        Color.CYAN,
                    ),
                ),
            )
            val ok = NavigationVision.tap(skill.refX, skill.refY, label = "war_${skill.id.name}")
            if (!ok) {
                allOk = false
                Log.w(TAG, "[WAR] cast tap failed skill=${skill.id.name}")
            }
            if (index < CAST_ORDER.lastIndex) {
                delay(BETWEEN_SKILLS_MS)
            }
        }
        return allOk
    }
}
