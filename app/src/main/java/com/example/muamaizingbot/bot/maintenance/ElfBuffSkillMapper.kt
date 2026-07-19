package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Finds Greater Defense / Greater Damage once (startup or remap) and stores tap coords.
 * Subsequent casts use those coords — no per-cast template match.
 */
object ElfBuffSkillMapper {

    private const val TAG = "ElfBuffCast"
    private const val SKILL_THRESHOLD = 0.70f
    /** Reject two skills collapsing onto the same icon. */
    private const val MIN_SKILL_SEPARATION_PX = 48
    private const val FAILED_CALIBRATE_BACKOFF_MS = 8_000L

    enum class SkillId(val label: String, val templatePath: String) {
        GREATER_DEFENSE(
            label = "Greater Defense",
            templatePath = "templates/mu/ui/skills/greater_defense.png",
        ),
        GREATER_DAMAGE(
            label = "Greater Damage",
            templatePath = "templates/mu/ui/skills/greater_damage.png",
        ),
    }

    data class MappedSkill(
        val id: SkillId,
        val refX: Int,
        val refY: Int,
        val screenX: Int,
        val screenY: Int,
        val score: Float,
    )

    data class Status(
        val mappedCount: Int = 0,
        val expectedCount: Int = SkillId.entries.size,
        val detail: String = "Skills: —",
    ) {
        val isReady: Boolean
            get() = mappedCount >= expectedCount
    }

    @Volatile
    private var mapped: List<MappedSkill> = emptyList()

    @Volatile
    private var lastFailedCalibrateAtMs = 0L

    @Volatile
    private var forceCalibrate = false

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun mappedSkills(): List<MappedSkill> = mapped

    fun isReady(): Boolean = mapped.size >= SkillId.entries.size

    fun clear() {
        mapped = emptyList()
        publishStatus()
        Log.d(TAG, "[ELF_GIVER] skill map cleared")
    }

    /** Clears map and forces the next loop cycle to re-run template match immediately. */
    fun requestRemap() {
        Log.d(TAG, "[ELF_GIVER] skill remap requested")
        forceCalibrate = true
        lastFailedCalibrateAtMs = 0L
        clear()
    }

    suspend fun ensureMapped(force: Boolean = false): Boolean {
        if (!force && !forceCalibrate && isReady()) {
            return true
        }
        return calibrate()
    }

    suspend fun calibrate(): Boolean {
        val now = System.currentTimeMillis()
        if (!forceCalibrate &&
            lastFailedCalibrateAtMs > 0L &&
            now - lastFailedCalibrateAtMs < FAILED_CALIBRATE_BACKOFF_MS &&
            !isReady()
        ) {
            Log.d(TAG, "[ELF_GIVER] skill calibrate backoff")
            return false
        }
        forceCalibrate = false

        Log.d(TAG, "[ELF_GIVER] skill calibrate start")
        val frame = NavigationVision.captureFrame()
        if (frame == null) {
            Log.w(TAG, "[ELF_GIVER] skill calibrate failed — no frame")
            lastFailedCalibrateAtMs = now
            publishStatus()
            return false
        }

        val found = mutableListOf<MappedSkill>()
        try {
            val roi = MuCombatRois.skillBarRoi(frame)
            for (skill in SkillId.entries) {
                val match = NavigationVision.findTemplate(
                    skill.templatePath,
                    SKILL_THRESHOLD,
                    roi,
                )
                if (match == null) {
                    NavigationVision.logBestScore(skill.templatePath, roi)
                    Log.w(TAG, "[ELF_GIVER] skill not found id=${skill.name}")
                    continue
                }
                if (conflictsWithMapped(found, match)) {
                    Log.w(
                        TAG,
                        "[ELF_GIVER] skill overlap ignored id=${skill.name} " +
                            "at=(${match.centerX},${match.centerY}) score=${"%.3f".format(match.score)}",
                    )
                    continue
                }
                val (refX, refY) = RefCoords.unscalePoint(
                    match.centerX,
                    match.centerY,
                    frame.width,
                    frame.height,
                )
                val mappedSkill = MappedSkill(
                    id = skill,
                    refX = refX,
                    refY = refY,
                    screenX = match.centerX,
                    screenY = match.centerY,
                    score = match.score,
                )
                found.add(mappedSkill)
                Log.d(
                    TAG,
                    "[ELF_GIVER] skill mapped id=${skill.name} " +
                        "screen=(${match.centerX},${match.centerY}) " +
                        "ref=($refX,$refY) score=${"%.3f".format(match.score)}",
                )
            }
        } finally {
            frame.recycle()
        }

        mapped = found
        publishStatus()
        val ok = isReady()
        if (ok) {
            lastFailedCalibrateAtMs = 0L
            Log.d(TAG, "[ELF_GIVER] skill calibrate ok count=${found.size}")
        } else {
            lastFailedCalibrateAtMs = now
            Log.w(TAG, "[ELF_GIVER] skill calibrate incomplete count=${found.size}/${SkillId.entries.size}")
        }
        return ok
    }

    private fun conflictsWithMapped(
        existing: List<MappedSkill>,
        match: PcTemplateMatchResult,
    ): Boolean {
        return existing.any { mapped ->
            hypot(
                (mapped.screenX - match.centerX).toDouble(),
                (mapped.screenY - match.centerY).toDouble(),
            ) < MIN_SKILL_SEPARATION_PX
        }
    }

    private fun publishStatus() {
        val detail = when {
            mapped.isEmpty() -> "Skills: sin mapear"
            mapped.size < SkillId.entries.size -> "Skills: ${mapped.size}/${SkillId.entries.size}"
            else -> "Skills: ok"
        }
        _status.value = Status(
            mappedCount = mapped.size,
            expectedCount = SkillId.entries.size,
            detail = detail,
        )
    }
}
