package io.embodit.suanpan.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Profile D25 (外部模型进场先校准): every Tripo GLB the app loads is already calibrated by tools/prep_models.py.
 * Reads the GLB's own glTF JSON (POSITION accessor min / max, node transforms must be absent) and checks: centre /
 * origin within ±1 cm, size within 10 % of the design, the bead's axis along +Y (its shortest extent), the frame's
 * thin side along Z (its face toward +Z), no transparent materials (D24).
 */
class ModelCalibrationTest {
    /** v0.1.1: everything is the design size × AbacusLayout.K (1.5). */
    private val K = io.embodit.suanpan.ui.volume.AbacusLayout.K.toDouble()

    private val dir = listOf(File("src/main/assets/models"), File("app/src/main/assets/models")).first { it.isDirectory }

    private data class Box(val min: DoubleArray, val max: DoubleArray, val json: JSONObject) {
        fun size(i: Int) = max[i] - min[i]
        fun centre(i: Int) = (max[i] + min[i]) / 2
    }

    private fun box(name: String): Box {
        val bytes = File(dir, "$name.glb").readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("glTF magic", 0x46546C67, bb.getInt(0))
        val len = bb.getInt(12)
        val json = JSONObject(String(bytes, 20, len, Charsets.UTF_8))
        val acc = json.getJSONArray("accessors")
        val mn = doubleArrayOf(1e9, 1e9, 1e9); val mx = doubleArrayOf(-1e9, -1e9, -1e9)
        val meshes = json.getJSONArray("meshes")
        for (i in 0 until meshes.length()) {
            val prims = meshes.getJSONObject(i).getJSONArray("primitives")
            for (k in 0 until prims.length()) {
                val a = acc.getJSONObject(prims.getJSONObject(k).getJSONObject("attributes").getInt("POSITION"))
                val amin = a.getJSONArray("min"); val amax = a.getJSONArray("max")
                for (d in 0..2) { mn[d] = minOf(mn[d], amin.getDouble(d)); mx[d] = maxOf(mx[d], amax.getDouble(d)) }
            }
        }
        val nodes = json.optJSONArray("nodes")
        if (nodes != null) for (i in 0 until nodes.length()) {
            val n = nodes.getJSONObject(i)
            assertTrue("$name node $i has a transform: calibration must be baked", !n.has("rotation") && !n.has("scale") && !n.has("translation") || isIdentity(n))
        }
        json.optJSONArray("materials")?.let { mats -> for (i in 0 until mats.length()) assertTrue("$name material $i not opaque", mats.getJSONObject(i).optString("alphaMode", "OPAQUE") == "OPAQUE") }
        return Box(mn, mx, json)
    }

    private fun isIdentity(n: JSONObject): Boolean {
        n.optJSONArray("translation")?.let { t -> for (i in 0..2) if (kotlin.math.abs(t.getDouble(i)) > 1e-6) return false }
        n.optJSONArray("scale")?.let { s -> for (i in 0..2) if (kotlin.math.abs(s.getDouble(i) - 1) > 1e-6) return false }
        n.optJSONArray("rotation")?.let { r -> if (kotlin.math.abs(r.getDouble(3)) < 1 - 1e-6) return false }
        n.optJSONArray("matrix")?.let { m -> val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0); for (i in 0 until 16) if (kotlin.math.abs(m.getDouble(i) - id[i]) > 1e-6) return false }
        return true
    }

    private fun near(msg: String, want: Double, got: Double, rel: Double = 0.10) = assertTrue("$msg want $want got $got", kotlin.math.abs(got - want) <= want * rel)

    @Test fun beadIsCentredAlongYAndDesignSized() {
        val b = box("bead")
        for (i in 0..2) assertEquals("bead centre $i", 0.0, b.centre(i), 0.01)
        near("bead Ø x", 0.055 * K, b.size(0)); near("bead along rod", 0.040 * K, b.size(1)); near("bead Ø z", 0.055 * K, b.size(2))
        assertTrue("bead axis must be its shortest extent (along the rod)", b.size(1) < b.size(0) && b.size(1) < b.size(2))
    }

    @Test fun frameStandsOnItsBottomEdgeFacingZ() {
        val b = box("frame")
        assertEquals(0.0, b.centre(0), 0.01); assertEquals(0.0, b.min[1], 0.01); assertEquals(0.0, b.centre(2), 0.01)
        near("frame w", 0.600 * K, b.size(0)); near("frame h", 0.448 * K, b.size(1)); near("frame t", 0.030 * K, b.size(2))
        assertTrue("thin side along Z", b.size(2) < b.size(1) && b.size(1) < b.size(0))
    }

    @Test fun beamStandIsBraceSized() {
        val beam = box("beam")
        near("beam", 0.540 * K, beam.size(0)); near("beam h", 0.020 * K, beam.size(1)); near("beam d", 0.026 * K, beam.size(2))
        for (i in 0..2) assertEquals(0.0, beam.centre(i), 0.01)
        val stand = box("stand")
        near("stand w", 0.600 * K, stand.size(0)); near("stand d", 0.240 * K, stand.size(2)); assertEquals(0.0, stand.min[1], 0.01)
        assertTrue("board stays low", stand.size(1) <= 0.03 * K)
        val brace = box("brace")
        near("brace length", 0.220 * K, brace.size(1)); for (i in 0..2) assertEquals(0.0, brace.centre(i), 0.01)
    }
}
