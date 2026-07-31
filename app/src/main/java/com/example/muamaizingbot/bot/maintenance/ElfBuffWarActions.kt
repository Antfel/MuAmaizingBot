package com.example.muamaizingbot.bot.maintenance

import android.graphics.Color
import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Divine War / APEX elf loop: grid taps → green HP buff → hard-clear HUD → resume taps.
 * Never touches PK All/Union. Does not force Auto combat.
 * Death aborts the cycle immediately so the loop can revive + return to post.
 *
 * Search: south-biased cells prioritized by empty-reference visual change, with
 * round-robin fallback and MISS/HIT cooldowns. Each cell: 5-point cross
 * (C+N/E/S/W); aborts early if focus appears mid-cross.
 */
object ElfBuffWarActions {

    private const val TAG = "ElfBuffWar"
    private const val BETWEEN_SKILLS_MS = 280L
    private const val POST_CAST_MS = 1_000L
    private const val POST_CLEAR_MS = 220L
    /** Keep taps aggressive while hunting ally focus. */
    private const val BETWEEN_TAPS_MS = 40L

    private val CAST_ORDER = listOf(
        ElfBuffSkillMapper.SkillId.GREATER_DAMAGE,
        ElfBuffSkillMapper.SkillId.GREATER_DEFENSE,
    )

    enum class TickResult {
        OK,
        DEAD,
    }

    suspend fun tick(profile: BotProfile): TickResult {
        if (!profile.isElfBuffWarMode()) {
            return TickResult.OK
        }

        if (ElfBuffSummonDismiss.dismissIfPresent()) {
            Log.d(TAG, "[WAR] summon dismissed — resume taps next tick")
            return TickResult.OK
        }

        val focus = ElfBuffFocusHud.classifyUnionFocus()
        // Resolve previous tap → HIT/MISS before acting on current focus.
        when (focus) {
            ElfBuffFocusHud.HpBarColor.GREEN,
            ElfBuffFocusHud.HpBarColor.RED,
            -> ElfBuffWarTapGrid.noteFocusResult(hit = true)
            null -> ElfBuffWarTapGrid.noteFocusResult(hit = false)
        }

        when (focus) {
            ElfBuffFocusHud.HpBarColor.GREEN -> {
                Log.d(TAG, "[WAR] green focus → buff + clear")
                if (castMappedSkills() == CastResult.DEAD) {
                    ElfBuffWarTapGrid.reset("dead-mid-cast")
                    return TickResult.DEAD
                }
                if (DeathActions.isDead()) {
                    Log.d(TAG, "[WAR] death after cast — abort clear")
                    ElfBuffWarTapGrid.reset("dead-after-cast")
                    return TickResult.DEAD
                }
                delay(POST_CAST_MS)
                if (DeathActions.isDead()) {
                    Log.d(TAG, "[WAR] death during post-cast — abort")
                    ElfBuffWarTapGrid.reset("dead-post-cast")
                    return TickResult.DEAD
                }
                if (!ElfBuffFocusHud.clearFocusHardTapAndVerify()) {
                    Log.w(TAG, "[WAR] hard clear failed / green HUD still visible")
                }
                return if (DeathActions.isDead()) {
                    ElfBuffWarTapGrid.reset("dead-after-buff")
                    TickResult.DEAD
                } else {
                    TickResult.OK
                }
            }
            ElfBuffFocusHud.HpBarColor.RED -> {
                Log.d(TAG, "[WAR] red focus → clear only")
                if (DeathActions.isDead()) {
                    ElfBuffWarTapGrid.reset("dead-red")
                    return TickResult.DEAD
                }
                if (!ElfBuffFocusHud.clearFocusHardTapAndVerify()) {
                    Log.w(TAG, "[WAR] hard clear failed (red)")
                }
                delay(POST_CLEAR_MS)
                return if (DeathActions.isDead()) {
                    ElfBuffWarTapGrid.reset("dead-after-red")
                    TickResult.DEAD
                } else {
                    TickResult.OK
                }
            }
            null -> {
                // No focus — next free grid cell, probe with 5-point cross.
                // If all cells were blocked, grid clears CDs and restarts the sweep.
                val (w, h) = RefCoords.activeScreenSize()
                val cell = ElfBuffWarTapGrid.nextTapCell(w, h)
                if (cell == null) {
                    Log.w(TAG, "[WAR] grid empty — nothing to tap")
                    return TickResult.OK
                }
                val points = ElfBuffWarTapGrid.crossTapPoints(cell, w, h)
                if (points.isEmpty()) {
                    Log.w(TAG, "[WAR] cross empty cell=${cell.index} — skip")
                    return TickResult.OK
                }
                for ((i, pt) in points.withIndex()) {
                    if (DeathActions.isDead()) {
                        Log.d(TAG, "[WAR] death mid-cross cell=${cell.index} arm=${pt.arm}")
                        ElfBuffWarTapGrid.reset("dead-mid-cross")
                        return TickResult.DEAD
                    }
                    NavigationVision.tapScreen(
                        pt.screenX,
                        pt.screenY,
                        label = "war_grid_${cell.index}_${pt.arm}",
                    )
                    delay(BETWEEN_TAPS_MS)
                    // Early exit if an arm already acquired focus (next tick buffs/clears).
                    if (i < points.lastIndex &&
                        ElfBuffFocusHud.classifyUnionFocus() != null
                    ) {
                        Log.d(TAG, "[WAR] focus mid-cross cell=${cell.index} after=${pt.arm}")
                        return TickResult.OK
                    }
                }
                return TickResult.OK
            }
        }
    }

    private enum class CastResult {
        OK,
        FAILED,
        DEAD,
    }

    private suspend fun castMappedSkills(): CastResult {
        if (!ElfBuffSkillMapper.ensureMapped()) {
            Log.w(TAG, "[WAR] cast skipped — skills not mapped")
            return CastResult.FAILED
        }
        val byId = ElfBuffSkillMapper.mappedSkills().associateBy { it.id }
        var allOk = true
        for ((index, skillId) in CAST_ORDER.withIndex()) {
            if (DeathActions.isDead()) {
                Log.d(TAG, "[WAR] death mid-cast — abort remaining skills")
                return CastResult.DEAD
            }
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
        return if (allOk) CastResult.OK else CastResult.FAILED
    }
}
