package io.embodit.suanpan.ui.volume

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Programmatic meshes (MeshModel). Lathe solids have >= 12 segments and smooth normals (gate ring-from-boxes, B12);
 * the frame / beam / stand slabs and the bead lathe are the stand-ins used only while the Tripo models are missing
 * (Models.kt decides). Hint rings, arrows, the slow-mo light ring and the contact shadow are abstract indicators.
 */
internal object Meshes {
    /**
     * Solid of revolution about +Y: [profile] = (y, radius) from bottom to top. Smooth normals from the profile
     * tangent; uv = (angle / 2π, profile fraction).
     */
    fun lathe(profile: List<Pair<Float, Float>>, segments: Int, name: String): MeshResource {
        val pos = ArrayList<Vector3>(); val nrm = ArrayList<Vector3>(); val uv = ArrayList<Vector2>(); val idx = ArrayList<Int>()
        val n = profile.size
        // per-profile-point 2D normal (radial, y)
        val pn = Array(n) { i ->
            val (y0, r0) = profile[(i - 1).coerceAtLeast(0)]
            val (y1, r1) = profile[(i + 1).coerceAtMost(n - 1)]
            val dy = y1 - y0; val dr = r1 - r0
            val len = sqrt(dy * dy + dr * dr).coerceAtLeast(1e-6f)
            dy / len to -dr / len   // (radial, y) perpendicular to the tangent, pointing out
        }
        for (s in 0..segments) {
            val a = 2 * PI * s / segments
            val c = cos(a).toFloat(); val sn = sin(a).toFloat()
            for (i in 0 until n) {
                val (y, r) = profile[i]
                pos += Vector3(c * r, y, sn * r)
                nrm += Vector3(c * pn[i].first, pn[i].second, sn * pn[i].first)
                uv += Vector2(s.toFloat() / segments, i.toFloat() / (n - 1))
            }
        }
        for (s in 0 until segments) for (i in 0 until n - 1) {
            val a = s * n + i; val b = (s + 1) * n + i
            idx += a; idx += a + 1; idx += b
            idx += b; idx += a + 1; idx += b + 1
        }
        val rMax = profile.maxOf { it.second }
        val y0 = profile.minOf { it.first }; val y1 = profile.maxOf { it.first }
        return MeshResource.createWithMeshModel(
            model = MeshModel(positions = pos, triangleIndices = idx, normals = nrm, uv0 = uv),
            bounds = BoundingBox(center = Vector3(0f, (y0 + y1) / 2, 0f), halfExtent = Vector3(rMax, (y1 - y0) / 2, rMax)),
            name = name,
        )
    }

    /** A turned bead: double cone with a rounded rim, Ø [d], [h] along the rod, a hole of Ø [hole] (design entities3d.beads). */
    fun beadProfile(d: Float, h: Float, hole: Float): List<Pair<Float, Float>> {
        val r = d / 2; val e = h / 2; val hr = hole / 2
        val pts = listOf(
            -1f to 0.0f, -0.92f to 0.30f, -0.72f to 0.58f, -0.45f to 0.80f, -0.2f to 0.95f, 0f to 1f,
            0.2f to 0.95f, 0.45f to 0.80f, 0.72f to 0.58f, 0.92f to 0.30f, 1f to 0.0f,
        )
        return listOf(-e to hr) + pts.map { (t, k) -> t * e to (hr + 0.002f + (r - hr - 0.002f) * k) } + listOf(e to hr)
    }

    fun cylinderProfile(r: Float, y0: Float, y1: Float): List<Pair<Float, Float>> =
        listOf(y0 to 0f, y0 to r, y1 to r, y1 to 0f)

    /** Axis-aligned slab centred at the origin with flat normals and box-mapped uv (frame bars, beam, stand). */
    fun slab(size: P3, name: String): MeshResource {
        val hx = size.x / 2; val hy = size.y / 2; val hz = size.z / 2
        val pos = ArrayList<Vector3>(); val nrm = ArrayList<Vector3>(); val uv = ArrayList<Vector2>(); val idx = ArrayList<Int>()
        fun face(nx: Float, ny: Float, nz: Float, corners: List<Vector3>, uScale: Float, vScale: Float) {
            val b = pos.size
            corners.forEach { pos += it; nrm += Vector3(nx, ny, nz) }
            uv += Vector2(0f, 0f); uv += Vector2(uScale, 0f); uv += Vector2(uScale, vScale); uv += Vector2(0f, vScale)
            idx += b; idx += b + 1; idx += b + 2; idx += b; idx += b + 2; idx += b + 3
        }
        val ux = size.x * 4; val uy = size.y * 4; val uz = size.z * 4
        face(0f, 0f, 1f, listOf(Vector3(-hx, -hy, hz), Vector3(hx, -hy, hz), Vector3(hx, hy, hz), Vector3(-hx, hy, hz)), ux, uy)
        face(0f, 0f, -1f, listOf(Vector3(hx, -hy, -hz), Vector3(-hx, -hy, -hz), Vector3(-hx, hy, -hz), Vector3(hx, hy, -hz)), ux, uy)
        face(1f, 0f, 0f, listOf(Vector3(hx, -hy, hz), Vector3(hx, -hy, -hz), Vector3(hx, hy, -hz), Vector3(hx, hy, hz)), uz, uy)
        face(-1f, 0f, 0f, listOf(Vector3(-hx, -hy, -hz), Vector3(-hx, -hy, hz), Vector3(-hx, hy, hz), Vector3(-hx, hy, -hz)), uz, uy)
        face(0f, 1f, 0f, listOf(Vector3(-hx, hy, hz), Vector3(hx, hy, hz), Vector3(hx, hy, -hz), Vector3(-hx, hy, -hz)), ux, uz)
        face(0f, -1f, 0f, listOf(Vector3(-hx, -hy, -hz), Vector3(hx, -hy, -hz), Vector3(hx, -hy, hz), Vector3(-hx, -hy, hz)), ux, uz)
        return MeshResource.createWithMeshModel(
            model = MeshModel(positions = pos, triangleIndices = idx, normals = nrm, uv0 = uv),
            bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(hx, hy, hz)),
            name = name,
        )
    }

    /** Flat elliptical band in the local XY plane facing +Z (hint ring round a bead seen from the front). */
    fun ovalBand(rx: Float, ry: Float, band: Float, name: String, segments: Int = 40): MeshResource {
        val pos = ArrayList<Vector3>(); val nrm = ArrayList<Vector3>(); val idx = ArrayList<Int>()
        for (s in 0..segments) {
            val a = 2 * PI * s / segments
            val c = cos(a).toFloat(); val sn = sin(a).toFloat()
            pos += Vector3(c * rx, sn * ry, 0f); pos += Vector3(c * (rx + band), sn * (ry + band), 0f)
            nrm += Vector3(0f, 0f, 1f); nrm += Vector3(0f, 0f, 1f)
        }
        for (s in 0 until segments) { val a = s * 2; idx += a; idx += a + 1; idx += a + 2; idx += a + 2; idx += a + 1; idx += a + 3 }
        return MeshResource.createWithMeshModel(
            model = MeshModel(positions = pos, triangleIndices = idx, normals = nrm),
            bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(rx + band, ry + band, 0.001f)),
            name = name,
        )
    }

    /** Solid disc in the local XY plane facing +Z. */
    fun disc(r: Float, name: String, segments: Int = 32): MeshResource {
        val pos = arrayListOf(Vector3(0f, 0f, 0f)); val nrm = arrayListOf(Vector3(0f, 0f, 1f)); val idx = ArrayList<Int>()
        for (s in 0 until segments) {
            val a = 2 * PI * s / segments
            pos += Vector3((cos(a) * r).toFloat(), (sin(a) * r).toFloat(), 0f); nrm += Vector3(0f, 0f, 1f)
        }
        for (s in 0 until segments) { idx += 0; idx += 1 + s; idx += 1 + (s + 1) % segments }
        return MeshResource.createWithMeshModel(
            model = MeshModel(positions = pos, triangleIndices = idx, normals = nrm),
            bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(r, r, 0.001f)),
            name = name,
        )
    }

    /** A small flat arrow in the XY plane pointing +Y (toward the beam on the lower deck), facing +Z. */
    fun arrow(len: Float, width: Float, name: String): MeshResource {
        val hw = width / 2
        val shaft = width * 0.36f
        val head = len * 0.5f
        val pos = arrayListOf(
            Vector3(0f, len / 2, 0f), Vector3(-hw, len / 2 - head, 0f), Vector3(hw, len / 2 - head, 0f),
            Vector3(-shaft / 2, len / 2 - head, 0f), Vector3(shaft / 2, len / 2 - head, 0f), Vector3(shaft / 2, -len / 2, 0f), Vector3(-shaft / 2, -len / 2, 0f),
        )
        val nrm = ArrayList<Vector3>(); repeat(pos.size) { nrm += Vector3(0f, 0f, 1f) }
        val idx = arrayListOf(0, 1, 2, 3, 6, 5, 3, 5, 4)
        return MeshResource.createWithMeshModel(
            model = MeshModel(positions = pos, triangleIndices = idx, normals = nrm),
            bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(hw, len / 2, 0.001f)),
            name = name,
        )
    }

    /** Horizontal quad in XZ facing +Y with uv, for the contact shadow. */
    fun floorQuad(w: Float, d: Float, name: String): MeshResource {
        val pos = arrayListOf(Vector3(-w / 2, 0f, d / 2), Vector3(w / 2, 0f, d / 2), Vector3(w / 2, 0f, -d / 2), Vector3(-w / 2, 0f, -d / 2))
        val nrm = arrayListOf(Vector3(0f, 1f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 1f, 0f))
        val uv = arrayListOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))
        return MeshResource.createWithMeshModel(
            model = MeshModel(positions = pos, triangleIndices = arrayListOf(0, 1, 2, 0, 2, 3), normals = nrm, uv0 = uv),
            bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(w / 2, 0.001f, d / 2)),
            name = name,
        )
    }
}

/** Programmatic bitmaps: wood grain (stand-in look until the Tripo textures load) and the soft contact shadow. */
internal object Bitmaps {
    /** Straight-grain wood, [base] colour, fine streaks + a few darker rays. */
    fun wood(base: Int, seed: Int, size: Int = 256): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rng = Random(seed)
        val r0 = Color.red(base); val g0 = Color.green(base); val b0 = Color.blue(base)
        val phase = FloatArray(6) { rng.nextFloat() * 6.28f }
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val fx = x.toFloat() / size; val fy = y.toFloat() / size
            val wave = sin(fy * 9f + sin(fx * 3f + phase[0]) * 0.6f + phase[1]) * 0.5f + 0.5f
            val streak = sin(fx * 140f + sin(fy * 6f + phase[2]) * 3f + phase[3]) * 0.5f + 0.5f
            val k = 0.86f + 0.10f * wave + 0.06f * streak * streak - 0.05f * (if (streak > 0.93f) 1f else 0f)
            px[y * size + x] = Color.rgb((r0 * k).toInt().coerceIn(0, 255), (g0 * k).toInt().coerceIn(0, 255), (b0 * k).toInt().coerceIn(0, 255))
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }

    fun shadow(size: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(size / 2f, size / 2f, size / 2f, intArrayOf(Color.argb(150, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        return bmp
    }
}
