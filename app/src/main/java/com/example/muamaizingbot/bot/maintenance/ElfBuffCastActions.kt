package com.example.muamaizingbot.bot.maintenance

import android.graphics.Color
import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Giver cast (UI): All → spam Focus → Union → classify HP bar → buff or retry.
 *
 * After Union:
 * - poll HP bar a few times (late green paint on some chars)
 * - green HP bar = ally → Damage+Defense → Focus Boss → ensure All
 * - red HP bar = not ally → Focus Boss → ensure All → seek new focus
 *
 * PK All is also forced once at giver startup ([BotPriorityLoop.runStartup]).
 */
object ElfBuffCastActions {

    private const val TAG = "ElfBuffCast"
    private const val BETWEEN_SKILLS_MS = 350L
    private const val POST_UNION_MS = 280L
    /**
     * Some chars paint the Union green bar late; null once used to clear focus.
     * Re-read bar-only (no portrait gate). GREEN/RED decide immediately.
     */
    private const val UNION_CLASSIFY_ATTEMPTS = 5
    private const val UNION_CLASSIFY_POLL_MS = 220L
    /** Wait for both buff cast animations before Focus Boss, or focus drops mid-cast. */
    private const val POST_CAST_MS = 1_000L
    private const val POST_UNFOCUS_MS = 220L
    private const val MAX_FOCUS_TRIES = 3
    private const val ENSURE_ALL_RETRIES = 1
    private const val SWITCH_UNION_ATTEMPTS = 2
    private const val SWITCH_UNION_RETRY_MS = 250L

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
        if (DeathActions.isDead()) {
            Log.w(TAG, "[ELF_GIVER] cast skipped reason=$reason — dead")
            return false
        }

        ElfBuffDebugDump.beginSession(reason)
        ElfBuffDebugDump.saveRaw("01_start")
        Log.d(TAG, "[ELF_GIVER] cast start reason=$reason ui_cycle")

        for (tryIndex in 1..MAX_FOCUS_TRIES) {
            if (DeathActions.isDead()) {
                Log.w(TAG, "[ELF_GIVER] cast aborted mid-cycle — dead")
                return false
            }
            Log.d(TAG, "[ELF_GIVER] focus try=$tryIndex/$MAX_FOCUS_TRIES")

            // Always start each try from All (startup also does this once).
            if (!ensurePkModeAllWithRetry()) {
                Log.w(TAG, "[ELF_GIVER] ensure All failed try=$tryIndex")
                continue
            }
            ElfBuffDebugDump.saveRaw("02_t${tryIndex}_pk_all")

            if (!ElfBuffTargetingActions.spamFocusUntilRedHud()) {
                Log.d(TAG, "[ELF_GIVER] no red focus HUD under All after spam try=$tryIndex")
                ElfBuffDebugDump.saveRaw("03_t${tryIndex}_no_focus")
                continue
            }
            ElfBuffDebugDump.saveRaw("03_t${tryIndex}_focus_hud")

            if (!switchPkModeUnionPreservingFocus()) {
                Log.w(TAG, "[ELF_GIVER] switch Union failed try=$tryIndex — Focus Boss then All")
                clearFocusThenEnsureAll()
                continue
            }
            delay(POST_UNION_MS)
            ElfBuffDebugDump.saveRaw("04_t${tryIndex}_pk_union")

            when (classifyUnionFocusWithRetry(tryIndex)) {
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

    /**
     * After Union: poll HP bar until green/red, or budget exhausted.
     * Null mid-window is "not painted yet" — do not clear focus on the first miss.
     */
    private suspend fun classifyUnionFocusWithRetry(
        tryIndex: Int,
    ): ElfBuffFocusHud.HpBarColor? {
        repeat(UNION_CLASSIFY_ATTEMPTS) { attempt ->
            val color = ElfBuffFocusHud.classifyUnionFocus()
            Log.d(
                TAG,
                "[ELF_GIVER] union hud try=$tryIndex " +
                    "poll=${attempt + 1}/$UNION_CLASSIFY_ATTEMPTS bar=$color",
            )
            if (color != null) return color
            if (attempt < UNION_CLASSIFY_ATTEMPTS - 1) {
                delay(UNION_CLASSIFY_POLL_MS)
            }
        }
        return null
    }

    /**
     * A busy combat frame can delay the PK popup beyond its first detection window.
     * Retry while keeping the current target; clearing Focus here would force a full
     * and unnecessary player-search cycle.
     */
    private suspend fun switchPkModeUnionPreservingFocus(): Boolean {
        repeat(SWITCH_UNION_ATTEMPTS) { attempt ->
            if (attempt > 0 && ElfBuffTargetingActions.isPkModeUnion()) {
                Log.d(TAG, "[ELF_GIVER] Union confirmed after prior switch attempt")
                return true
            }
            if (ElfBuffTargetingActions.switchPkModeUnionFromAll()) {
                return true
            }
            if (attempt < SWITCH_UNION_ATTEMPTS - 1) {
                Log.w(
                    TAG,
                    "[ELF_GIVER] switch Union retry=${attempt + 1}/" +
                        "${SWITCH_UNION_ATTEMPTS - 1} preserving Focus",
                )
                delay(SWITCH_UNION_RETRY_MS)
            }
        }
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
