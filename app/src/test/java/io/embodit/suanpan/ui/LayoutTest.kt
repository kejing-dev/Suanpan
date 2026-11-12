package io.embodit.suanpan.ui

import io.embodit.suanpan.domain.BeadRef
import io.embodit.suanpan.ui.volume.AbacusLayout
import io.embodit.suanpan.ui.volume.P3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * D35 / D16 / D17 (mustPass M6): the strips sit above the abacus, never inside its box, and no line from the 9 design
 * eyes to a strip corner crosses the abacus or the stand; everything fits in the 0.74 × 0.62 × 0.40 m volume; bead
 * colliders never overlap; the layout numbers match design spatialLayout.
 */
class LayoutTest {
    private val L = AbacusLayout

    private fun inBox(p: P3, lo: P3, hi: P3, eps: Float = 0f) =
        p.x > lo.x + eps && p.x < hi.x - eps && p.y > lo.y + eps && p.y < hi.y - eps && p.z > lo.z + eps && p.z < hi.z - eps

    /** Does the segment a→b pass through the box (slab test)? */
    private fun segmentHitsBox(a: P3, b: P3, lo: P3, hi: P3): Boolean {
        var t0 = 0f; var t1 = 1f
        val d = b - a
        for (axis in 0..2) {
            val o = when (axis) { 0 -> a.x; 1 -> a.y; else -> a.z }
            val v = when (axis) { 0 -> d.x; 1 -> d.y; else -> d.z }
            val mn = when (axis) { 0 -> lo.x; 1 -> lo.y; else -> lo.z }
            val mx = when (axis) { 0 -> hi.x; 1 -> hi.y; else -> hi.z }
            if (abs(v) < 1e-7f) { if (o < mn || o > mx) return false; continue }
            var ta = (mn - o) / v; var tb = (mx - o) / v
            if (ta > tb) { val t = ta; ta = tb; tb = t }
            t0 = maxOf(t0, ta); t1 = minOf(t1, tb)
            if (t0 > t1) return false
        }
        return true
    }

    /** The abacus face as a thin tilted slab is what can hide a strip; test against the tilted face's own box per eye. */
    private fun faceHit(eye: P3, corner: P3): Boolean {
        // sample the segment and test each point in face space against the frame + bead slab
        val c = Math.cos(Math.toRadians(L.TILT_DEG.toDouble())).toFloat(); val s = Math.sin(Math.toRadians(L.TILT_DEG.toDouble())).toFloat()
        for (i in 0..400) {
            val p = eye + (corner - eye) * (i / 400f)
            val q = p - L.PIVOT
            // inverse of the -25° rotation about X
            val fy = q.y * c - q.z * s
            val fz = q.y * s + q.z * c
            if (abs(q.x) <= L.FRAME_W / 2 && fy in 0f..L.FRAME_H && abs(fz) <= L.BEAD_W / 2) return true
        }
        return false
    }

    @Test fun designNumbers() {
        // v0.1.1: the design's abacus × K (1.5); the face still leans back 25° about its bottom edge
        val top = L.faceToVolume(P3(0f, L.FRAME_H, 0f))
        val c = Math.cos(Math.toRadians(25.0)).toFloat(); val sn = Math.sin(Math.toRadians(25.0)).toFloat()
        assertEquals(L.PIVOT.y + L.FRAME_H * c, top.y, 1e-4f)
        assertEquals(L.PIVOT.z - L.FRAME_H * sn, top.z, 1e-4f)
        assertEquals(0.240f * L.K, L.rodX(0), 1e-5f)
        assertEquals(-0.240f * L.K, L.rodX(8), 1e-5f)
        assertEquals(0.090f, L.ROD_SPACING, 1e-5f)
        // bead stack fills the deck exactly: 5 beads + 4.4 × K cm travel; 2 beads + 4.4 × K cm
        assertEquals(L.BEAM_Y0, L.beadY(false, 1, 1) + L.BEAD_H / 2, 1e-5f)
        assertEquals(L.LOWER_Y0, L.beadY(false, 5, 0) - L.BEAD_H / 2, 1e-5f)
        assertEquals(L.BEAM_Y1, L.beadY(true, 1, 1) - L.BEAD_H / 2, 1e-5f)
        assertEquals(L.UPPER_Y1, L.beadY(true, 2, 0) + L.BEAD_H / 2, 1e-5f)
        assertEquals(0.044f * L.K, L.beadY(false, 4, 4) - L.BEAD_H / 2 - (L.beadY(false, 5, 4) + L.BEAD_H / 2), 1e-4f)
    }

    @Test fun everythingFitsTheVolume() {
        val half = P3(L.VOLUME_W / 2, L.VOLUME_H / 2, L.VOLUME_D / 2)
        val all = L.abacusCorners() + L.panelCorners(L.STRIP_CENTER, L.STRIP_SIZE) + L.panelCorners(L.DIGITS_CENTER, L.DIGITS_SIZE)
        for (p in all) assertTrue("$p outside the volume", abs(p.x) <= half.x && p.y >= -half.y - 1e-4f && p.y <= half.y && abs(p.z) <= half.z)
        // slow-mo light ring 4 cm in front of the nearest bead stays inside too
        val ring = L.faceToVolume(P3(L.rodX(0), L.LOWER_Y0 + L.BEAD_H / 2, 0.04f + 0.0125f))
        assertTrue("$ring", ring.z <= half.z)
    }

    @Test fun stripsAreNeverInsideTheAbacusBox() {
        val (lo, hi) = L.abacusBox()
        for (p in L.panelCorners(L.STRIP_CENTER, L.STRIP_SIZE) + L.panelCorners(L.DIGITS_CENTER, L.DIGITS_SIZE)) assertFalse("$p", inBox(p, lo, hi))
        // the digit strip's lower edge is above the top frame bar's front-top corner (design occlusionCheck)
        assertTrue(L.DIGITS_CENTER.y - L.DIGITS_SIZE.y / 2 > L.faceToVolume(P3(0f, L.FRAME_H, L.FRAME_T / 2)).y)
        assertTrue(L.STRIP_CENTER.y - L.STRIP_SIZE.y / 2 > L.DIGITS_CENTER.y + L.DIGITS_SIZE.y / 2)
    }

    @Test fun eyeToStripCornersNeverCrossTheAbacus() {
        val stand = P3(-L.STAND_SIZE.x / 2, L.STAND_CENTER.y - L.STAND_SIZE.y / 2, L.STAND_CENTER.z - L.STAND_SIZE.z / 2) to
            P3(L.STAND_SIZE.x / 2, L.STAND_CENTER.y + L.STAND_SIZE.y / 2, L.STAND_CENTER.z + L.STAND_SIZE.z / 2)
        for (eye in L.EYES) for (c in L.panelCorners(L.STRIP_CENTER, L.STRIP_SIZE) + L.panelCorners(L.DIGITS_CENTER, L.DIGITS_SIZE)) {
            assertFalse("eye $eye -> $c crosses the abacus face", faceHit(eye, c))
            assertFalse("eye $eye -> $c crosses the stand", segmentHitsBox(eye, c, stand.first, stand.second))
        }
    }

    @Test fun beadsStayInsideTheirDeck() {
        val h = L.BEAD_H
        for (atBeam in 0..5) for (j in 1..5) {
            val y = L.beadY(false, j, atBeam)
            assertTrue(y - h / 2 >= L.LOWER_Y0 - 1e-5f && y + h / 2 <= L.BEAM_Y0 + 1e-5f)
        }
        for (atBeam in 0..2) for (j in 1..2) {
            val y = L.beadY(true, j, atBeam)
            assertTrue(y - h / 2 >= L.BEAM_Y1 - 1e-5f && y + h / 2 <= L.UPPER_Y1 + 1e-5f)
        }
    }

    // ---------------------------------------------------------------- D17 (QA #1 q1-2)

    /** The system's default placement eye (volume ~1.75 m ahead), the eye D17 is judged at, plus the pulled-close eyes. */
    private val defaultEye = P3(0f, 0.14f, 1.75f)
    private val closeEyes = listOf(P3(0f, 0.14f, 0.85f), P3(0f, 0.44f, 0.85f))

    private fun deg(e: P3, a: P3, b: P3): Double {
        val va = a - e; val vb = b - e
        val d = (va.x * vb.x + va.y * vb.y + va.z * vb.z) / (Math.sqrt((va.x * va.x + va.y * va.y + va.z * va.z).toDouble()) * Math.sqrt((vb.x * vb.x + vb.y * vb.y + vb.z * vb.z).toDouble()))
        return Math.toDegrees(Math.acos(d.coerceIn(-1.0, 1.0)))
    }

    /**
     * The smaller of the two angles a target subtends at [eye]: across (edge midpoints left / right) and along (bottom /
     * top) — corners are in volume space, so the 25° tilt and the eye's offset (perspective) are in the numbers.
     */
    private fun minAngle(eye: P3, left: P3, right: P3, bottom: P3, top: P3) = minOf(deg(eye, left, right), deg(eye, bottom, top))

    private fun faceTargetAngle(eye: P3, centre: P3, size: P3): Double {
        val z = size.z / 2   // the target's front face, nearest the user
        return minAngle(eye,
            L.faceToVolume(P3(centre.x - size.x / 2, centre.y, z)), L.faceToVolume(P3(centre.x + size.x / 2, centre.y, z)),
            L.faceToVolume(P3(centre.x, centre.y - size.y / 2, z)), L.faceToVolume(P3(centre.x, centre.y + size.y / 2, z)))
    }

    private val minGap = 0.015f

    @Test fun d17DeckTargetsAreBigEnoughAndFarEnoughApart() {
        var worst = 99.0
        for (rod in 0 until 9) for (up in listOf(true, false)) {
            val (c, size) = L.deckCollider(rod, up)
            assertTrue("deck $rod $up side ${size.x} x ${size.y}", minOf(size.x, size.y) >= 0.04f)
            val a = faceTargetAngle(defaultEye, c, size)
            worst = minOf(worst, a)
            assertTrue("deck $rod upper=$up only ${"%.2f".format(a)}° at 1.75 m", a >= 2.0)
            for (e in closeEyes) assertTrue(faceTargetAngle(e, c, size) >= 2.0)
        }
        // neighbour decks across: rod spacing minus target width; across the beam: upper bottom to lower top
        val across = L.ROD_SPACING - L.DECK_W
        assertTrue("gap across $across", across >= maxOf(minGap, 0.3f * L.DECK_W))
        val (lc, ls) = L.deckCollider(0, false); val (uc, us) = L.deckCollider(0, true)
        val beamGap = (uc.y - us.y / 2) - (lc.y + ls.y / 2)
        assertTrue("gap across the beam $beamGap", beamGap >= maxOf(minGap, 0.3f * L.DECK_W))
        println("D17 deck ${L.DECK_W} m wide, lower ${"%.3f".format(ls.y)} / upper ${"%.3f".format(us.y)} m tall, gaps ${"%.3f".format(across)} / ${"%.3f".format(beamGap)} m, min angle at 1.75 m ${"%.2f".format(worst)}°")
    }

    @Test fun d17FrameTargetsAreBigEnoughAndClearOfTheDecks() {
        for (top in listOf(true, false)) {
            val (c, size) = L.frameCollider(top)
            assertTrue(minOf(size.x, size.y) >= 0.04f)
            val a = faceTargetAngle(defaultEye, c, size)
            assertTrue("frame top=$top only ${"%.2f".format(a)}°", a >= 2.0)
            val deck = L.deckCollider(0, top)
            val gap = if (top) (c.y - size.y / 2) - (deck.first.y + deck.second.y / 2) else (deck.first.y - deck.second.y / 2) - (c.y + size.y / 2)
            assertTrue("frame top=$top gap $gap", gap >= maxOf(minGap, 0.3f * minOf(size.y, L.DECK_W)) - 1e-5f)
            println("D17 frame top=$top ${"%.3f".format(size.x)} x ${"%.3f".format(size.y)} m, ${"%.2f".format(a)}° at 1.75 m, gap to decks ${"%.3f".format(gap)} m")
        }
    }

    @Test fun d17PickerKeysAreBigEnoughAndFarEnoughApart() {
        assertTrue(L.KEY >= 0.04f)
        assertTrue("key gap", L.KEY_GAP >= maxOf(minGap, 0.3f * L.KEY))
        var worst = 99.0
        for (rod in 0 until 9) {
            val p = L.faceToVolume(L.pickerFace(rod))   // the picker is upright, facing +Z, centred here
            for (n in 0..4) {
                val cx = p.x + L.keyX(n); val cy = p.y - (L.PICKER_H - L.KEY) / 2 + 0.01f
                val a = minAngle(defaultEye, P3(cx - L.KEY / 2, cy, p.z), P3(cx + L.KEY / 2, cy, p.z), P3(cx, cy - L.KEY / 2, p.z), P3(cx, cy + L.KEY / 2, p.z))
                worst = minOf(worst, a)
                assertTrue("key $n rod $rod ${"%.2f".format(a)}°", a >= 2.0)
            }
            // the picker stays inside the frame width and the volume
            assertTrue(abs(p.x) + L.PICKER_W / 2 <= L.FRAME_W / 2 + 1e-4f)
            assertTrue(p.z <= L.VOLUME_D / 2)
        }
        println("D17 picker keys ${L.KEY} m, gap ${L.KEY_GAP} m, min angle at 1.75 m ${"%.2f".format(worst)}°")
    }

    @Test fun d17SideEyesForTheRecord() {
        // ±30° at the default distance (not the D17 reference eye): printed for the design package, not asserted
        for (e in listOf(P3(0.875f, 0.14f, 1.516f), P3(-0.875f, 0.14f, 1.516f))) {
            var w = 99.0
            for (rod in 0 until 9) for (up in listOf(true, false)) { val (c, size) = L.deckCollider(rod, up); w = minOf(w, faceTargetAngle(e, c, size)) }
            println("D17 side eye $e: min deck angle ${"%.2f".format(w)}°")
        }
    }

    @Test fun faceNormalWithin15DegOfTheViewingEyes() {
        // design: seated, standing, on a table, the view line and the face normal stay within 15° (pulled close, 0.85 m)
        val n = L.faceToVolume(P3(0f, 0f, 1f)) - L.PIVOT
        val centre = L.faceToVolume(P3(0f, L.FRAME_H / 2, 0f))
        for (eye in listOf(L.EYES[0], L.EYES[1])) {
            val v = eye - centre
            val cos = (n.x * v.x + n.y * v.y + n.z * v.z) / (Math.sqrt((n.x * n.x + n.y * n.y + n.z * n.z).toDouble()) * Math.sqrt((v.x * v.x + v.y * v.y + v.z * v.z).toDouble()))
            val deg = Math.toDegrees(Math.acos(cos))
            assertTrue("eye $eye $deg°", deg < 25.0)
        }
    }
}
