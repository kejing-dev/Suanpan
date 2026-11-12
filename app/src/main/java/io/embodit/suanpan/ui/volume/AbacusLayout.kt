package io.embodit.suanpan.ui.volume

import io.embodit.suanpan.domain.BeadRef
import kotlin.math.cos
import kotlin.math.sin

/** A point in metres (pure Kotlin so the layout is unit-tested on the JVM). */
data class P3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: P3) = P3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: P3) = P3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = P3(x * k, y * k, z * k)
}

/**
 * Geometry of the abacus in the volume (design spatialLayout, metres). Volume origin = volume centre, +X right,
 * +Y up, +Z toward the user, floor y = -0.31.
 *
 * "Face" space is the abacus's own: x along the beam (rod 0 = units at x = +0.24), y up the face from the bottom
 * edge of the lower frame bar, z out of the face. The face leans back 25° about its bottom edge, whose centre sits
 * at (0, -0.29, +0.10) in the volume.
 */
object AbacusLayout {
    /**
     * v0.1.1 scale (QA #1 q1-1, profile D17 補 relaxed to 2°, user 2026-11-11「直接改吧」): the design's 9-rod abacus × 1.5,
     * so every tap target is >= 6.8 cm with >= 2.2 cm between neighbours and >= 2° from the system's default placement
     * (volume ~1.75 m ahead). Taps no longer pick a bead: a rod deck is the target (two-stage input, see [deckCollider]).
     */
    const val K = 1.5f

    const val VOLUME_W = 1.04f
    const val VOLUME_H = 0.86f
    const val VOLUME_D = 0.60f
    const val FLOOR_Y = -VOLUME_H / 2

    const val FRAME_W = 0.600f * K
    const val FRAME_H = 0.448f * K
    const val FRAME_T = 0.030f * K
    const val BAR = 0.030f * K
    const val LOWER_Y0 = 0.030f * K
    const val BEAM_Y0 = 0.274f * K
    const val BEAM_Y1 = 0.294f * K
    const val UPPER_Y1 = 0.418f * K
    const val BEAM_W = 0.540f * K
    const val BEAM_D = 0.026f * K

    const val BEAD_W = 0.055f * K      // diameter
    const val BEAD_H = 0.040f * K      // along the rod
    const val HOLE = 0.007f * K
    const val ROD_D = 0.006f * K
    const val ROD_SPACING = 0.060f * K

    /** Tap target of one rod deck (x width): 6.8 cm, leaving 2.2 cm to the neighbour deck (>= 0.3 × 6.8 = 2.04 cm). */
    const val DECK_W = 0.068f
    /** Deck targets stop this far short of the beam and the frame bars (gaps across the beam / to the frame targets). */
    const val DECK_INSET = 0.013f
    /** The frame targets (S15: bring the window back): full frame width, 8 cm tall, reaching past the bar. */
    const val FRAME_TARGET_H = 0.080f
    /** Lower-deck count picker keys (0..4): 6.8 cm square, 2.2 cm apart. */
    const val KEY = 0.068f
    const val KEY_GAP = 0.022f
    /** The picker floats this far in front of the face (in front of the beads' front surface). */
    const val PICKER_Z = BEAD_W / 2 + 0.05f

    const val TILT_DEG = 25f
    const val STAND_H = 0.025f * K   // the calibrated Tripo board (tools/prep_models.py)
    val PIVOT = P3(0f, FLOOR_Y + STAND_H, 0.10f * K)

    /** Stand base plate: 0.90 × 0.03 × 0.36 on the volume floor. */
    val STAND_SIZE = P3(0.60f * K, STAND_H, 0.24f * K)
    val STAND_CENTER = P3(0f, FLOOR_Y + STAND_H / 2, 0.01f * K)

    /** Top of the frame in the volume (the strips sit above it). */
    val FRAME_TOP_Y: Float get() = PIVOT.y + FRAME_H * cos(Math.toRadians(TILT_DEG.toDouble())).toFloat()

    /**
     * Digit strip (one cell per rod, 9 × rod spacing) and problem strip, display only, facing +Z. The problem strip is
     * 0.70 m wide now (QA #1 K2: the English second line had to shrink to fit 0.50 m).
     */
    val DIGITS_SIZE = P3(9 * ROD_SPACING, 0.035f, 0f)
    val DIGITS_CENTER: P3 get() = P3(0f, FRAME_TOP_Y + 0.03f + 0.0175f, -0.07f * K)
    val STRIP_SIZE = P3(0.70f, 0.10f, 0f)
    val STRIP_CENTER: P3 get() = P3(0f, DIGITS_CENTER.y + 0.0175f + 0.012f + 0.05f, -0.07f * K)

    /** The deck target of [rod]: centre and size in face space (x across, y up the face, z out). */
    fun deckCollider(rod: Int, upper: Boolean): Pair<P3, P3> {
        val y0 = (if (upper) BEAM_Y1 else LOWER_Y0) + DECK_INSET
        val y1 = (if (upper) UPPER_Y1 else BEAM_Y0) - DECK_INSET
        return P3(rodX(rod), (y0 + y1) / 2, 0f) to P3(DECK_W, y1 - y0, BEAD_W)
    }

    /** The two frame targets in face space: (centre, size). */
    fun frameCollider(top: Boolean): Pair<P3, P3> {
        val y = if (top) UPPER_Y1 + DECK_INSET + FRAME_TARGET_H / 2 else LOWER_Y0 - DECK_INSET - FRAME_TARGET_H / 2
        return P3(0f, y, 0f) to P3(FRAME_W, FRAME_TARGET_H, FRAME_T + 0.02f)
    }

    /** Width of the picker row: 5 keys + 4 gaps + 1 cm margin each side. */
    val PICKER_W: Float get() = 5 * KEY + 4 * KEY_GAP + 0.02f
    val PICKER_H: Float get() = KEY + 0.045f

    /** Picker centre in face space for [rod]: in front of the lower deck, centred on the rod but kept inside the frame. */
    fun pickerFace(rod: Int): P3 {
        val half = FRAME_W / 2 - PICKER_W / 2
        val (c, _) = deckCollider(rod, false)
        return P3(rodX(rod).coerceIn(-half, half), c.y, PICKER_Z)
    }

    /** Face x of key n (0..4) relative to the picker centre. */
    fun keyX(n: Int): Float = (n - 2) * (KEY + KEY_GAP)

    fun rodX(rod: Int): Float = 4 * ROD_SPACING - ROD_SPACING * rod

    /** Face y of bead [j] (1 from the beam) on a layer with [atBeam] beads at the beam. */
    fun beadY(upper: Boolean, j: Int, atBeam: Int): Float = if (upper) {
        if (j <= atBeam) BEAM_Y1 + (j - 0.5f) * BEAD_H else UPPER_Y1 - (2.5f - j) * BEAD_H
    } else {
        if (j <= atBeam) BEAM_Y0 - (j - 0.5f) * BEAD_H else LOWER_Y0 + (5.5f - j) * BEAD_H
    }

    fun beadFace(ref: BeadRef, atBeam: Int) = P3(rodX(ref.rod), beadY(ref.upper, ref.j, atBeam), 0f)

    private val tilt = Math.toRadians(-TILT_DEG.toDouble())

    /** Face space -> volume space: rotate about X by -25° (top leans back), then move to the pivot. */
    fun faceToVolume(p: P3): P3 {
        val c = cos(tilt).toFloat(); val s = sin(tilt).toFloat()
        return P3(p.x, p.y * c - p.z * s, p.y * s + p.z * c) + PIVOT
    }

    /** The 8 corners of the abacus + stand box in volume space (D35 / D41 checks). */
    fun abacusCorners(): List<P3> {
        val out = ArrayList<P3>()
        for (x in listOf(-FRAME_W / 2, FRAME_W / 2)) for (y in listOf(0f, FRAME_H)) for (z in listOf(-BEAD_W / 2, BEAD_W / 2)) out += faceToVolume(P3(x, y, z))
        for (x in listOf(-STAND_SIZE.x / 2, STAND_SIZE.x / 2)) for (y in listOf(STAND_CENTER.y - STAND_SIZE.y / 2, STAND_CENTER.y + STAND_SIZE.y / 2))
            for (z in listOf(STAND_CENTER.z - STAND_SIZE.z / 2, STAND_CENTER.z + STAND_SIZE.z / 2)) out += P3(x, y, z)
        return out
    }

    /** Axis-aligned box of [abacusCorners]. */
    fun abacusBox(): Pair<P3, P3> {
        val c = abacusCorners()
        return P3(c.minOf { it.x }, c.minOf { it.y }, c.minOf { it.z }) to P3(c.maxOf { it.x }, c.maxOf { it.y }, c.maxOf { it.z })
    }

    /** The 4 corners of a vertical panel facing +Z. */
    fun panelCorners(center: P3, size: P3): List<P3> = listOf(
        P3(center.x - size.x / 2, center.y - size.y / 2, center.z), P3(center.x + size.x / 2, center.y - size.y / 2, center.z),
        P3(center.x - size.x / 2, center.y + size.y / 2, center.z), P3(center.x + size.x / 2, center.y + size.y / 2, center.z),
    )

    /** Design eye positions (volume space): pulled close (0.85 m) seated / standing ±30°, and the system's default 1.75 m. */
    val EYES: List<P3> = listOf(
        P3(0f, 0.14f, 0.85f), P3(0f, 0.44f, 0.85f), P3(0.425f, 0.14f, 0.736f), P3(-0.425f, 0.14f, 0.736f),
        P3(0.425f, 0.44f, 0.736f), P3(-0.425f, 0.44f, 0.736f),
        P3(0f, 0.14f, 1.75f), P3(0.875f, 0.14f, 1.516f), P3(-0.875f, 0.14f, 1.516f),
    )
}
