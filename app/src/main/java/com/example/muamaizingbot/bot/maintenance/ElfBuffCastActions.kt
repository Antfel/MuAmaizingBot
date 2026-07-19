package com.example.muamaizingbot.bot.maintenance

import android.graphics.Color
import android.util.Log
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Giver cast (UI): All → spam Focus → Union → classify HP bar → buff or retry.
 *
 * After Union:
 * - green HP bar = ally → Damage+Defense → Focus Boss → ensure All
 * - red HP bar = not ally → Focus Boss → ensure All → seek new focus
 *
 * PK All is also forced once at giver startup ([BotPriorityLoop.runStartup]).
 */
object ElfBuffCastActions {

    private const val TAG = "ElfBuffCast"
    private const val BETWEEN_SKILLS_MS = 280L
    private const val POST_UNION_MS = 280L
    /** Wait for both buff cast animations before Focus Boss, or focus drops mid-cast. */
    private const val POST_CAST_MS = 1_000L
    private const val POST_UNFOCUS_MS = 220L
    private const val MAX_FOCUS_TRIES = 6
    private const val ENSURE_ALL_RETRIES = 1

    private val CAST_ORDER = listOf(
        ElfBuffSkillMapper.SkillId.GREATER_DAMAGE,
        ElfBuffSkillMapper.SkillId.GREATER_DEFENSE,
    )

    suspend fun maybeCast(profile: BotProfile): Boolean {
        if (!profile.isElfBuffGiverMode()) {
            return true
        }
        if (!ElfBuffCastGate.shouldCast(profile)) {
            return true
        }
        val forced = ElfBuffCastGate.status.value.forcePending
        val reason = if (forced) "manual" else "interval"
        return castBuffNow(profile, reason)
    }

    suspend fun castBuffNow(
        profile: BotProfile,
        reason: String,
        @Suppress("UNUSED_PARAMETER") targets: List<NearbyAllyDetector.NameplateHit> = emptyList(),
    ): Boolean {
        if (!ElfBuffSkillMapper.ensureMapped()) {
            Log.w(TAG, "[ELF_GIVER] cast skipped reason=$reason — skills not mapped")
            return false
        }

        ElfBuffDebugDump.beginSession(reason)
        ElfBuffDebugDump.saveRaw("01_start")
        Log.d(TAG, "[ELF_GIVER] cast start reason=$reason ui_cycle")

        for (tryIndex in 1..MAX_FOCUS_TRIES) {
            Log.d(TAG, "[ELF_GIVER] focus try=$tryIndex/$MAX_FOCUS_TRIES")

            // Always start each try from All (startup also does this once).
            if (!ensurePkModeAllWithRetry()) {
                Log.w(TAG, "[ELF_GIVER] ensure All failed try=$tryIndex")
                continue
            }
            ElfBuffDebugDump.saveRaw("02_t${tryIndex}_pk_all")

            if (!ElfBuffTargetingActions.spamFocusUntilHud()) {
                Log.d(TAG, "[ELF_GIVER] no focus HUD after spam try=$tryIndex")
                ElfBuffDebugDump.saveRaw("03_t${tryIndex}_no_focus")
                continue
            }
            ElfBuffDebugDump.saveRaw("03_t${tryIndex}_focus_hud")

            if (!ElfBuffTargetingActions.switchPkModeUnion()) {
                Log.w(TAG, "[ELF_GIVER] switch Union failed try=$tryIndex — Focus Boss then All")
                clearFocusThenEnsureAll()
                continue
            }
            delay(POST_UNION_MS)
            ElfBuffDebugDump.saveRaw("04_t${tryIndex}_pk_union")

            when (ElfBuffFocusHud.classifyUnionFocus()) {
                ElfBuffFocusHud.HpBarColor.GREEN -> {
                    Log.d(TAG, "[ELF_GIVER] ally confirmed (green HP) try=$tryIndex")
                    val castOk = castMappedSkillsWithDebug(tryIndex)
                    delay(POST_CAST_MS)
                    ElfBuffDebugDump.saveRaw("06_t${tryIndex}_after_cast")

                    // Required order: Focus Boss first, then restore All.
                    val restored = clearFocusThenEnsureAll()
                    ElfBuffDebugDump.saveRaw("08_t${tryIndex}_pk_all_restored")

                    if (castOk && restored) {
                        ElfBuffCastGate.noteCastDone()
                    } else if (castOk && !restored) {
                        Log.w(TAG, "[ELF_GIVER] buff ok but PK All not restored")
                        ElfBuffCastGate.noteCastDone()
                    }
                    Log.d(
                        TAG,
                        "[ELF_GIVER] cast done reason=$reason success=$castOk restoredAll=$restored",
                    )
                    Log.i(TAG, "[ELF_DEBUG] session=${ElfBuffDebugDump.sessionPath()}")
                    return castOk
                }
                ElfBuffFocusHud.HpBarColor.RED -> {
                    Log.d(TAG, "[ELF_GIVER] still red after Union — Focus Boss then All + new focus")
                    ElfBuffDebugDump.saveRaw("05_t${tryIndex}_not_ally_red")
                    clearFocusThenEnsureAll()
                }
                null -> {
                    Log.d(TAG, "[ELF_GIVER] no HP bar after Union — Focus Boss then All + new focus")
                    ElfBuffDebugDump.saveRaw("05_t${tryIndex}_no_hud")
                    clearFocusThenEnsureAll()
                }
            }
        }

        Log.d(TAG, "[ELF_GIVER] cast exhausted focus tries reason=$reason")
        ensurePkModeAllWithRetry()
        if (ElfBuffCastGate.status.value.forcePending) {
            ElfBuffCastGate.noteCastDone()
        }
        Log.i(TAG, "[ELF_DEBUG] session=${ElfBuffDebugDump.sessionPath()}")
        return false
    }

    /** Focus Boss (clear HUD), then force PK All. */
    private suspend fun clearFocusThenEnsureAll(): Boolean {
        if (!ElfBuffFocusHud.clearFocus()) {
            Log.w(TAG, "[ELF_GIVER] Focus Boss tap failed")
        }
        delay(POST_UNFOCUS_MS)
        ElfBuffDebugDump.saveRaw("07_after_unfocus_boss")
        return ensurePkModeAllWithRetry()
    }

    private suspend fun ensurePkModeAllWithRetry(): Boolean {
        repeat(ENSURE_ALL_RETRIES) { attempt ->
            if (ElfBuffTargetingActions.ensurePkModeAll()) {
                return true
            }
            Log.w(TAG, "[ELF_GIVER] ensurePkModeAll retry=${attempt + 1}/$ENSURE_ALL_RETRIES")
            delay(POST_UNFOCUS_MS)
        }
        return ElfBuffTargetingActions.ensurePkModeAll()
    }

    private suspend fun castMappedSkillsWithDebug(tryIndex: Int): Boolean {
        val byId = ElfBuffSkillMapper.mappedSkills().associateBy { it.id }
        var allOk = true
        for ((index, skillId) in CAST_ORDER.withIndex()) {
            val skill = byId[skillId]
            if (skill == null) {
                Log.w(TAG, "[ELF_GIVER] cast missing mapped skill=${skillId.name}")
                allOk = false
                continue
            }
            Log.d(
                TAG,
                "[ELF_GIVER] cast skill=${skill.id.name} " +
                    "ref=(${skill.refX},${skill.refY}) screen=(${skill.screenX},${skill.screenY})",
            )
            ElfBuffDebugDump.saveTapPlan(
                label = "05_t${tryIndex}_before_${skill.id.name.lowercase()}",
                title = "BEFORE ${skill.id.name} tap=(${skill.screenX},${skill.screenY})",
                taps = listOf(
                    ElfBuffDebugDump.TapMark(
                        skill.screenX,
                        skill.screenY,
                        skill.id.name,
                        Color.MAGENTA,
                    ),
                ),
            )
            val ok = NavigationVision.tap(skill.refX, skill.refY, label = "skill_${skill.id.name}")
            if (!ok) {
                allOk = false
                Log.w(TAG, "[ELF_GIVER] cast tap failed skill=${skill.id.name}")
            }
            if (index < CAST_ORDER.lastIndex) {
                delay(BETWEEN_SKILLS_MS)
            }
        }
        return allOk
    }
}
