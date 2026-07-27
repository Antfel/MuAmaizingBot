package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import kotlin.math.atan2

/**
 * War ally-search: 3 fixed taps in the **lower half** of the play ellipse
 * (SW / S / SE), ~2 HUD coords apart (calibrated ~87px @ 1280×720).
 * Round-robin with MISS / HIT cooldowns. Each cell is probed with a 5-point
 * cross (center + N/E/S/W).
 */
object ElfBuffWarTapGrid {

    private const val TAG = "ElfBuffWar"
    private const val BASE_W = 1280
    private const val BASE_H = 720

    private const val ELLIPSE_CX = 640
    private const val ELLIPSE_CY = 312
    private const val ELLIPSE_RX = 246
    private const val ELLIPSE_RY = 264

    /** Screen px per HUD coordinate (landmark calibration). */
    const val CELL_PX_BASE = 87

    /**
     * Lower-ellipse triangle in HUD units from character center (y > 0 = south).
     * ~2 HUD keeps taps inside the War play ellipse.
     */
    private val TRIANGLE_HUD_OFFSETS = listOf(
        Triple("SW", -2, 1),
        Triple("S", 0, 2),
        Triple("SE", 2, 1),
    )

    /** Cross arm offset from cell center (= cell/4 ≈ 22px @ 1280). */
    private const val CROSS_OFFSET_DIV = 4

    private const val MISS_TTL_MS = 2_000L
    private const val HIT_CD_MS = 5_000L

    /** War does not treat potions_auto as exclusion (HUD not shown in war). */
    private val WAR_EXCLUDED_ZONE_IDS = setOf(
        "minimap_ui",
        "right_icons",
        "self",
        "chat",
        "skills",
        "bottom_left",
        "bottom_mid_hud",
    )

    data class Cell(
        val index: Int,
        val screenX: Int,
        val screenY: Int,
        val angle: Double,
        val label: String = "",
    )

    data class TapPoint(
        val screenX: Int,
        val screenY: Int,
        val arm: String,
    )

    private var cells: List<Cell> = emptyList()
    private var builtForW: Int = -1
    private var builtForH: Int = -1
    private var cursor: Int = 0

    /** Until this epoch-ms the cell is blocked (MISS or HIT). */
    private val blockedUntilMs = HashMap<Int, Long>()

    private var pendingCellIndex: Int? = null
    private var awaitingResult: Boolean = false

    fun reset(reason: String) {
        blockedUntilMs.clear()
        pendingCellIndex = null
        awaitingResult = false
        cursor = 0
        Log.d(TAG, "[WAR_GRID] reset reason=$reason cells=${cells.size}")
    }

    fun ensureBuilt(screenW: Int, screenH: Int) {
        if (cells.isNotEmpty() && builtForW == screenW && builtForH == screenH) {
            return
        }
        cells = buildCells(screenW, screenH)
        builtForW = screenW
        builtForH = screenH
        cursor = 0
        blockedUntilMs.clear()
        pendingCellIndex = null
        awaitingResult = false
        Log.i(
            TAG,
            "[WAR_GRID] built cells=${cells.size} screen=${screenW}x${screenH} " +
                "cellPx=${cellPx(screenW)} " +
                "pts=${cells.joinToString { "${it.label}@(${it.screenX},${it.screenY})" }}",
        )
    }

    fun noteFocusResult(hit: Boolean) {
        val idx = pendingCellIndex
        if (!awaitingResult || idx == null) {
            return
        }
        val now = System.currentTimeMillis()
        if (hit) {
            blockedUntilMs[idx] = now + HIT_CD_MS
            Log.d(TAG, "[WAR_GRID] HIT cell=$idx cd=${HIT_CD_MS}ms")
        } else {
            blockedUntilMs[idx] = now + MISS_TTL_MS
            Log.d(TAG, "[WAR_GRID] MISS cell=$idx ttl=${MISS_TTL_MS}ms")
        }
        pendingCellIndex = null
        awaitingResult = false
    }

    /**
     * Next free cell for a cross probe, or null if all blocked.
     * Marks the cell as pending (result resolved on next [noteFocusResult]).
     */
    fun nextTapCell(screenW: Int, screenH: Int): Cell? {
        ensureBuilt(screenW, screenH)
        if (cells.isEmpty()) {
            return null
        }
        val now = System.currentTimeMillis()
        val n = cells.size
        repeat(n) {
            val idx = cursor % n
            cursor = (cursor + 1) % n
            val until = blockedUntilMs[idx] ?: 0L
            if (until > now) {
                return@repeat
            }
            blockedUntilMs.remove(idx)
            val cell = cells[idx]
            pendingCellIndex = idx
            awaitingResult = true
            Log.d(
                TAG,
                "[WAR_GRID] cell=$idx/${n - 1} label=${cell.label} " +
                    "center=(${cell.screenX},${cell.screenY})",
            )
            return cell
        }
        Log.d(TAG, "[WAR_GRID] all $n cells blocked — reset and restart sweep")
        blockedUntilMs.clear()
        pendingCellIndex = null
        awaitingResult = false
        cursor = 0
        val cell = cells[0]
        pendingCellIndex = 0
        awaitingResult = true
        cursor = 1 % n
        Log.d(
            TAG,
            "[WAR_GRID] cell=0/${n - 1} label=${cell.label} " +
                "center=(${cell.screenX},${cell.screenY}) (fresh sweep)",
        )
        return cell
    }

    /**
     * 5-point cross around [cell] center: C, N, E, S, W.
     * Drops arms outside screen / ellipse / War HUD exclusions.
     */
    fun crossTapPoints(cell: Cell, screenW: Int, screenH: Int): List<TapPoint> {
        ensureBuilt(screenW, screenH)
        val cx = ELLIPSE_CX * screenW / BASE_W
        val cy = ELLIPSE_CY * screenH / BASE_H
        val rx = ELLIPSE_RX * screenW / BASE_W
        val ry = ELLIPSE_RY * screenH / BASE_H
        val excl = warExclusionRects(screenW, screenH)
        val off = (cellPx(screenW) / CROSS_OFFSET_DIV).coerceAtLeast(8)
        val candidates = listOf(
            TapPoint(cell.screenX, cell.screenY, "C"),
            TapPoint(cell.screenX, cell.screenY - off, "N"),
            TapPoint(cell.screenX + off, cell.screenY, "E"),
            TapPoint(cell.screenX, cell.screenY + off, "S"),
            TapPoint(cell.screenX - off, cell.screenY, "W"),
        )
        return candidates.filter { p ->
            p.screenX in 0 until screenW &&
                p.screenY in 0 until screenH &&
                inEllipse(p.screenX, p.screenY, cx, cy, rx, ry) &&
                excl.none { it.contains(p.screenX, p.screenY) }
        }.also { pts ->
            Log.d(
                TAG,
                "[WAR_GRID] cross cell=${cell.index} label=${cell.label} " +
                    "off=$off arms=${pts.joinToString { it.arm }}",
            )
        }
    }

    private fun cellPx(screenW: Int): Int =
        (CELL_PX_BASE * screenW / BASE_W).coerceAtLeast(24)

    /** Three triangle cells around character center; drop any that hit exclusions. */
    private fun buildCells(screenW: Int, screenH: Int): List<Cell> {
        val cx = ELLIPSE_CX * screenW / BASE_W
        val cy = ELLIPSE_CY * screenH / BASE_H
        val rx = ELLIPSE_RX * screenW / BASE_W
        val ry = ELLIPSE_RY * screenH / BASE_H
        val step = cellPx(screenW)
        val excl = warExclusionRects(screenW, screenH)

        val raw = ArrayList<Cell>()
        for ((label, dxHud, dyHud) in TRIANGLE_HUD_OFFSETS) {
            val tapX = cx + dxHud * step
            val tapY = cy + dyHud * step
            if (tapX !in 0 until screenW || tapY !in 0 until screenH) {
                Log.w(TAG, "[WAR_GRID] skip $label off-screen=($tapX,$tapY)")
                continue
            }
            if (!inEllipse(tapX, tapY, cx, cy, rx, ry)) {
                Log.w(TAG, "[WAR_GRID] skip $label outside ellipse=($tapX,$tapY)")
                continue
            }
            if (excl.any { it.contains(tapX, tapY) }) {
                Log.w(TAG, "[WAR_GRID] skip $label excluded=($tapX,$tapY)")
                continue
            }
            val angle = atan2((tapY - cy).toDouble(), (tapX - cx).toDouble())
            raw += Cell(
                index = -1,
                screenX = tapX,
                screenY = tapY,
                angle = angle,
                label = label,
            )
        }
        return raw.mapIndexed { index, c -> c.copy(index = index) }
    }

    private fun inEllipse(
        x: Int,
        y: Int,
        cx: Int,
        cy: Int,
        rx: Int,
        ry: Int,
    ): Boolean {
        val nx = (x - cx).toDouble() / rx
        val ny = (y - cy).toDouble() / ry
        return nx * nx + ny * ny <= 1.0
    }

    private fun warExclusionRects(frameW: Int, frameH: Int): List<android.graphics.Rect> {
        return ElfBuffExclusionZones.BASE_ZONES
            .filter { it.id in WAR_EXCLUDED_ZONE_IDS }
            .map { z ->
                android.graphics.Rect(
                    z.left * frameW / BASE_W,
                    z.top * frameH / BASE_H,
                    z.right * frameW / BASE_W,
                    z.bottom * frameH / BASE_H,
                )
            }
    }
}
