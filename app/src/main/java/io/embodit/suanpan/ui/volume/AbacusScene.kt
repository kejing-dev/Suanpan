package io.embodit.suanpan.ui.volume

import android.graphics.Color as AColor
import android.os.SystemClock
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import io.embodit.suanpan.domain.BeadRef
import io.embodit.suanpan.domain.Beads
import io.embodit.suanpan.domain.RODS
import io.embodit.suanpan.domain.TapMove
import io.embodit.suanpan.platform.SpLog
import io.embodit.suanpan.ui.BoardAnim
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

internal fun Entity.tf(): TransformComponent = components[TransformComponent::class.java]!!
internal fun P3.v() = Vector3(x, y, z)

/** What the scene draws, read from the UI state on every SpatialView update. */
internal data class SceneInput(
    val board: Beads,
    val anim: BoardAnim,
    val hint: List<TapMove>,
    val finger: BeadRef?,
    val glowSerial: Int,
    val speed: Float,
)

/**
 * Resources shared by the whole abacus, created once off the main thread (creating a resource waits for the renderer;
 * on the idle emulator each one is a ~1 s round trip, profile A9) and kept global until [close].
 */
internal class SceneResources {
    private val all = ArrayList<Resource>()
    private fun <T : Resource> T.keep(): T { toGlobal(); all += this; return this }

    private fun woodMat(base: Int, seed: Int, roughness: Float): PhysicallyBasedMaterial = PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
        val tex = runCatching { TextureResource.create(Bitmaps.wood(base, seed)).keep() }.getOrNull()
        if (tex != null) { setBaseColor(Color4(1f, 1f, 1f, 1f)); setBaseColorTexture(tex) } else setBaseColor(c4(base))
        setRoughness(roughness); setMetallic(0f)
        setEmissiveColor(Color4(AColor.red(base) / 255f * 0.04f, AColor.green(base) / 255f * 0.04f, AColor.blue(base) / 255f * 0.04f, 1f))
    }.keep()

    private fun c4(c: Int, a: Float = 1f) = Color4(AColor.red(c) / 255f, AColor.green(c) / 255f, AColor.blue(c) / 255f, a)

    private fun unlit(c: Int, opacity: Float = 1f): UnlitMaterial =
        UnlitMaterial.create(if (opacity < 1f) BlendingMode.TRANSPARENT else BlendingMode.OPAQUE).apply {
            setBaseColor(c4(c)); if (opacity < 1f) { setOpacity(opacity); setDepthWrite(false) }
            runCatching { setApplyToneMapping(false) }
            setCullingMode(MaterialCullingMode.NONE)
        }.keep()

    // palette (design palette.brandColors, W1 红木框 · 黄杨珠)
    val hongmu = 0xFF5B2A1E.toInt()
    val huangyang = 0xFFE2B46E.toInt()

    val frameWood: Material = woodMat(hongmu, 11, 0.5f)
    val brass: Material = PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
        setBaseColor(c4(0xFFC9A15A.toInt())); setMetallic(0.75f); setRoughness(0.35f); setEmissiveColor(Color4(0.05f, 0.04f, 0.02f, 1f))
    }.keep()
    val hintRing: Material = unlit(0xFFFFD27A.toInt(), 0.92f)
    val hintArrow: Material = unlit(0xFFFFD27A.toInt())
    val fingerRing: Material = unlit(0xFFEAF6FF.toInt(), 0.75f)
    val fingerDot: Material = unlit(0xFFEAF6FF.toInt())
    val shadow: Material = runCatching {
        val tex = TextureResource.create(Bitmaps.shadow()).keep()
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4(1f, 1f, 1f, 1f)); setBaseColorTexture(tex); setDepthWrite(false)
            runCatching { setApplyToneMapping(false) }; setCullingMode(MaterialCullingMode.NONE)
        }.keep() as Material
    }.getOrElse { unlit(0xFF000000.toInt(), 0.25f) }

    /** Bead looks (procedural stand-in): normal, spare (base × 0.6), right-answer glow (warm gold emission). */
    private val beadTex: TextureResource? = runCatching { TextureResource.create(Bitmaps.wood(huangyang, 23, 128)).keep() }.getOrNull()

    private fun beadMat(k: Float, glow: Float): Material = PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
        if (beadTex != null) setBaseColorTexture(beadTex)
        setBaseColor(Color4(k, k, k, 1f)); setRoughness(0.42f); setMetallic(0f)
        setEmissiveColor(Color4(1.0f * glow, 0.82f * glow, 0.48f * glow, 1f))
    }.keep()

    val beadNormal: Material = beadMat(1f, 0.03f)
    val beadSpare: Material = beadMat(0.6f, 0f)
    val beadGlow: Material = beadMat(1.15f, 0.55f)

    // meshes
    val beadMesh: MeshResource = Meshes.lathe(Meshes.beadProfile(AbacusLayout.BEAD_W, AbacusLayout.BEAD_H, AbacusLayout.HOLE), 24, "bead").keep()
    val rodMesh: MeshResource = Meshes.lathe(Meshes.cylinderProfile(AbacusLayout.ROD_D / 2, AbacusLayout.LOWER_Y0 - 0.002f, AbacusLayout.UPPER_Y1 + 0.002f), 16, "brassRod").keep()
    val dotMesh: MeshResource = Meshes.lathe(Meshes.cylinderProfile(0.003f, 0f, 0.0015f), 16, "brassDot").keep()
    val barH: MeshResource = Meshes.slab(P3(AbacusLayout.FRAME_W, AbacusLayout.BAR, AbacusLayout.FRAME_T), "frameBarH").keep()
    val barV: MeshResource = Meshes.slab(P3(AbacusLayout.BAR, AbacusLayout.FRAME_H - 2 * AbacusLayout.BAR, AbacusLayout.FRAME_T), "frameBarV").keep()
    val beamMesh: MeshResource = Meshes.slab(P3(AbacusLayout.BEAM_W, AbacusLayout.BEAM_Y1 - AbacusLayout.BEAM_Y0, AbacusLayout.BEAM_D), "beam").keep()
    val cornerMesh: MeshResource = Meshes.slab(P3(0.034f, 0.034f, AbacusLayout.FRAME_T + 0.002f), "cornerCap").keep()
    val standMesh: MeshResource = Meshes.slab(AbacusLayout.STAND_SIZE, "standBase").keep()
    val braceMesh: MeshResource = Meshes.slab(P3(0.03f * AbacusLayout.K, 0.22f * AbacusLayout.K, 0.02f * AbacusLayout.K), "standBrace").keep()
    val ringMesh: MeshResource = Meshes.ovalBand(0.0300f, 0.0215f, 0.0035f, "hintRing").keep()
    val arrowMesh: MeshResource = Meshes.arrow(0.020f, 0.014f, "hintArrow").keep()
    val fingerRingMesh: MeshResource = Meshes.ovalBand(0.0105f, 0.0105f, 0.002f, "fingerRing").keep()
    val fingerDotMesh: MeshResource = Meshes.disc(0.003f, "fingerDot").keep()
    val shadowMesh: MeshResource = Meshes.floorQuad(0.70f, 0.32f, "contactShadow").keep()

    // collision
    val physics: PhysicsMaterialResource = PhysicsMaterialResource().keep()
    /** Deck targets (v0.1.1 two-stage input): one shape per deck, offset to the deck centre on a holder at the rod's x. */
    val deckShape: Map<Boolean, ShapeResource> = listOf(true, false).associateWith { up ->
        val (c, size) = AbacusLayout.deckCollider(0, up)
        ShapeResource.createBox(size.v()).offsetByTranslation(Vector3(0f, c.y, 0f)).keep()
    }
    val frameShape: Map<Boolean, ShapeResource> = listOf(true, false).associateWith { top ->
        ShapeResource.createBox(AbacusLayout.frameCollider(top).second.v()).keep()
    }

    fun close() { all.forEach { runCatching { it.close() } }; all.clear() }
}

/**
 * Owns every entity of the abacus and plays the bead slides, the right-answer glow, the hint rings and the slow-mo
 * light ring. All calls on the main thread: [install] / [apply] from Compose, [onFrame] from [AbacusFrameSystem].
 *
 * ```
 * root (volume floor aligned)
 * ├ shadow, stand (Tripo stand.glb or slabs), keyLight
 * └ face  (pivot = bottom edge centre, leans back 25°)
 *   ├ frame (Tripo frame.glb or 4 bars + brass corners), beam (+2 brass dots), 9 brass rods
 *   ├ frame:top / frame:bottom   colliders (S15 reopen)
 *   ├ bead:r:u|l:j  holder: collider + Interactable + system HoverEffect → vis (Tripo bead.glb clone or lathe bead)
 *   └ hint rings ×16, arrows ×8, slow-mo light ring
 * ```
 */
internal class AbacusScene {
    val root: Entity = Entity().apply { setName("abacusRoot") }
    private val face: Entity = Entity().apply { setName("abacusFace") }
    private var res: SceneResources? = null
    private var models: Models? = null
    var built = false; private set
    private var regionShift = 0f

    private class BeadView(val ref: BeadRef, val holder: Entity, val vis: Entity, val glowVis: Entity?, val spare: Boolean) {
        var y = 0f; var fromY = 0f; var toY = 0f
        var t0 = 0L; var delay = 0L; var dur = 0L; var bounce = false
        var glowUntil = 0L; var glowFrom = 0L; var glowing = false
    }

    private val beads = HashMap<BeadRef, BeadView>()
    private val rings = ArrayList<Entity>()
    private val arrows = ArrayList<Entity>()
    private var finger: Entity? = null
    private var fingerFrom = P3(0f, 0f, 0f); private var fingerTo = P3(0f, 0f, 0f); private var fingerT0 = 0L
    private var hintT0 = 0L
    private var input: SceneInput? = null
    private var lastAnimSerial = -1
    private var lastGlowSerial = -1
    private var pendingBeads = ArrayList<() -> Unit>()

    fun provide(r: SceneResources) { if (res == null) res = r else r.close(); tryBuild() }
    fun install(m: Models) { models = m; tryBuild() }

    /** The SpatialView's half extents (metres): the stand rests on the volume floor. */
    fun setRegion(halfY: Float) {
        val shift = -halfY - AbacusLayout.FLOOR_Y
        if (abs(shift - regionShift) < 1e-4f) return
        regionShift = shift
        root.tf().setPosition(Vector3(0f, shift, 0f))
        SpLog.i("REGION halfY=$halfY shift=$shift")
    }
    val shift: Float get() = regionShift

    private fun tryBuild() {
        val r = res ?: return
        val m = models ?: return
        if (built) return
        built = true
        val t0 = SystemClock.elapsedRealtime()
        // first frame: shadow, stand and the empty frame (the fallback shape the user sees at once, design lifecycle)
        root.addChild(ModelEntity(r.shadowMesh, r.shadow).apply { setName("contactShadow"); tf().setPosition(Vector3(0f, AbacusLayout.FLOOR_Y + 0.0015f, 0.03f)) })
        buildStand(r, m)
        face.tf().setPosition(AbacusLayout.PIVOT.v())
        face.tf().setQuaternion(Quat(Vector3(1f, 0f, 0f), Math.toRadians(-AbacusLayout.TILT_DEG.toDouble()).toFloat()))
        root.addChild(face)
        buildFrame(r, m)
        root.addChild(Entity().apply {
            setName("keyLight")
            components.set(DirectionalLightComponent(Color4(1f, 0.93f, 0.84f, 1f), 3500f, false))
            tf().setQuaternion(lookRotation(P3(0.45f, -0.7f, -0.55f)))
        })
        // 63 beads in three batches over three frames (A9 cold start)
        for (batch in 0 until 3) pendingBeads.add { for (rod in (batch * 3) until (batch * 3 + 3)) buildRodBeads(r, m, rod) }
        pendingBeads.add { buildIndicators(r); io.embodit.suanpan.ui.App.ui.sceneReady = true; SpLog.i("SCENE beads built (${beads.size}) total_ms=${SystemClock.elapsedRealtime() - t0}"); input?.let { apply(it, force = true) } }
        SpLog.i("SCENE frame built ms=${SystemClock.elapsedRealtime() - t0} models=${m.templates.keys}")
    }

    private fun model(mesh: MeshResource, mat: Material, name: String, at: P3, parent: Entity = face): ModelEntity =
        ModelEntity(mesh, mat).apply { setName(name); tf().setPosition(at.v()); parent.addChild(this) }

    private fun buildStand(r: SceneResources, m: Models) {
        // calibrated board: bottom centre at the origin, 0.60 × 0.025 × 0.24 (tools/prep_models.py)
        val board = m.instance(Models.STAND, "stand")
        if (board != null) {
            board.tf().setPosition(Vector3(0f, AbacusLayout.FLOOR_Y, AbacusLayout.STAND_CENTER.z))
            root.addChild(board)
        } else model(r.standMesh, r.frameWood, "standBase", AbacusLayout.STAND_CENTER, root)
        // two braces prop the face from behind like easel legs: bottom on the board at the back, top against the
        // face's back at y -0.10 (the face leans back 25°, the braces lean forward 25°)
        for ((i, sx) in listOf(-0.22f, 0.22f).withIndex()) {
            val brace = m.instance(Models.BRACE, "standBrace$i")
                ?: ModelEntity(r.braceMesh, r.frameWood).apply { setName("standBrace$i") }
            brace.tf().setPosition(Vector3(sx * AbacusLayout.K, AbacusLayout.FLOOR_Y + 0.11f * AbacusLayout.K, -0.052f * AbacusLayout.K))
            brace.tf().setQuaternion(Quat(Vector3(1f, 0f, 0f), Math.toRadians(25.0).toFloat()))
            root.addChild(brace)
        }
    }

    private fun buildFrame(r: SceneResources, m: Models) {
        val L = AbacusLayout
        val frame = m.instance(Models.FRAME, "frame")
        if (frame != null) face.addChild(frame) else {
            model(r.barH, r.frameWood, "frameBottom", P3(0f, L.BAR / 2, 0f))
            model(r.barH, r.frameWood, "frameTop", P3(0f, L.FRAME_H - L.BAR / 2, 0f))
            model(r.barV, r.frameWood, "frameLeft", P3(-L.FRAME_W / 2 + L.BAR / 2, L.FRAME_H / 2, 0f))
            model(r.barV, r.frameWood, "frameRight", P3(L.FRAME_W / 2 - L.BAR / 2, L.FRAME_H / 2, 0f))
            for (cx in listOf(-1f, 1f)) for (cy in listOf(0f, 1f)) {
                model(r.cornerMesh, r.brass, "brassCorner", P3(cx * (L.FRAME_W / 2 - 0.017f), 0.017f + cy * (L.FRAME_H - 0.034f), 0.001f))
            }
        }
        val beam = m.instance(Models.BEAM, "beam")
        if (beam != null) { beam.tf().setPosition(Vector3(0f, (L.BEAM_Y0 + L.BEAM_Y1) / 2, 0f)); face.addChild(beam) }
        else model(r.beamMesh, r.frameWood, "beam", P3(0f, (L.BEAM_Y0 + L.BEAM_Y1) / 2, 0f))
        // brass unit dots on the beam, 4th and 7th rod from the right (like the commas in a number)
        for (rod in listOf(3, 6)) {
            val dot = model(r.dotMesh, r.brass, "beamDot", P3(L.rodX(rod), (L.BEAM_Y0 + L.BEAM_Y1) / 2, L.BEAM_D / 2))
            dot.tf().setQuaternion(Quat(Vector3(1f, 0f, 0f), (PI / 2).toFloat()))
        }
        for (rod in 0 until RODS) model(r.rodMesh, r.brass, "rod$rod", P3(L.rodX(rod), 0f, 0f))
        // frame targets: tap the frame to reopen the closed window (S15); 8 cm tall (D17), reaching past the bar
        for ((name, top) in listOf(AbacusNames.FRAME_TOP to true, AbacusNames.FRAME_BOTTOM to false)) {
            face.addChild(Entity().apply {
                setName(name); tf().setPosition(L.frameCollider(top).first.v())
                components.set(CollisionComponent(collisionShape = listOf(r.frameShape.getValue(top)), physicsMaterial = r.physics))
                components.set(InteractableComponent())
            })
        }
    }

    /**
     * One deck holder per rod and deck ("deck:<rod>:u|l"): the tap target (collider + Interactable + system HoverEffect,
     * D17 sizes from AbacusLayout.deckCollider) with that deck's beads as its children, so the system hover lights the
     * whole deck. The beads themselves carry no collider any more (v0.1.1).
     */
    private fun buildRodBeads(r: SceneResources, m: Models, rod: Int) {
        val decks = listOf(true, false).associateWith { up ->
            Entity().apply {
                setName(AbacusNames.deck(rod, up))
                tf().setPosition(Vector3(AbacusLayout.rodX(rod), 0f, 0f))
                components.set(CollisionComponent(collisionShape = listOf(r.deckShape.getValue(up)), physicsMaterial = r.physics))
                components.set(InteractableComponent())
                components.set(HoverEffectComponent())
                face.addChild(this)
            }
        }
        for (upper in listOf(true, false)) for (j in 1..(if (upper) 2 else 5)) {
            val ref = BeadRef(rod, upper, j)
            val spare = Beads.isSpare(upper, j)
            val holder = Entity().apply { setName(AbacusNames.bead(rod, upper, j)) }
            // Tripo bead clones share the template's (or the dimmed / glowing variant's) materials: no resource is
            // created on the main thread here (profile A9)
            val visName = AbacusNames.beadVis(rod, upper, j)
            val vis = m.instance(if (spare) Models.BEAD_SPARE else Models.BEAD, visName)
                ?: ModelEntity(r.beadMesh, if (spare) r.beadSpare else r.beadNormal).apply { setName(visName) }
            val glowVis = if (spare) null else (m.instance(Models.BEAD_GLOW, "$visName:glow") ?: ModelEntity(r.beadMesh, r.beadGlow).apply { setName("$visName:glow") })
            glowVis?.enabled = false
            holder.addChild(vis)
            glowVis?.let { holder.addChild(it) }
            val y = AbacusLayout.beadY(upper, j, 0)
            holder.tf().setPosition(Vector3(0f, y, 0f))
            decks.getValue(upper).addChild(holder)
            beads[ref] = BeadView(ref, holder, vis, glowVis, spare).also { it.y = y; it.fromY = y; it.toY = y }
        }
    }

    private fun buildIndicators(r: SceneResources) {
        repeat(16) { i -> rings += ModelEntity(r.ringMesh, r.hintRing).apply { setName("hintRing$i"); enabled = false; face.addChild(this) } }
        repeat(8) { i -> arrows += ModelEntity(r.arrowMesh, r.hintArrow).apply { setName("hintArrow$i"); enabled = false; face.addChild(this) } }
        finger = Entity().apply {
            setName("demoFinger"); enabled = false
            addChild(ModelEntity(r.fingerRingMesh, r.fingerRing).apply { setName("fingerRing") })
            addChild(ModelEntity(r.fingerDotMesh, r.fingerDot).apply { setName("fingerDot") })
            face.addChild(this)
        }
    }

    // ------------------------------------------------------------------ state

    fun apply(si: SceneInput, force: Boolean = false) {
        input = si
        if (beads.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (force || si.anim.serial != lastAnimSerial) {
            lastAnimSerial = si.anim.serial
            for (bv in beads.values) {
                val atBeam = if (bv.ref.upper) si.board.u(bv.ref.rod) else si.board.l(bv.ref.rod)
                val target = AbacusLayout.beadY(bv.ref.upper, bv.ref.j, atBeam)
                if (abs(target - bv.toY) < 1e-5f && !force) continue
                bv.fromY = bv.y; bv.toY = target; bv.t0 = now
                bv.delay = (si.anim.rodDelayMs?.getOrNull(bv.ref.rod) ?: 0).toLong()
                bv.dur = if (force) 0 else si.anim.ms.toLong()
                bv.bounce = si.anim.bounce
                if (bv.dur == 0L && bv.delay == 0L) { bv.y = target; place(bv, 0f) }
            }
        }
        if (si.glowSerial != lastGlowSerial) {
            if (lastGlowSerial >= 0 && !force) {
                // right answer: from the units rod leftward, every bead at the beam glows 500 ms, rods 40 ms apart
                for (bv in beads.values) {
                    val atBeam = if (bv.ref.upper) si.board.u(bv.ref.rod) else si.board.l(bv.ref.rod)
                    if (bv.ref.j <= atBeam) { bv.glowFrom = now + (bv.ref.rod * 40 / si.speed).toLong(); bv.glowUntil = bv.glowFrom + (500 / si.speed).toLong() }
                }
            }
            lastGlowSerial = si.glowSerial
        }
        hintT0 = if (si.hint.isNotEmpty()) (if (hintT0 == 0L) now else hintT0) else 0L
        val f = finger
        if (f != null) {
            if (si.finger == null) f.enabled = false else {
                val bv = beads[si.finger]
                val to = P3(AbacusLayout.rodX(si.finger.rod), bv?.toY ?: 0f, 0.04f)
                if (!f.enabled) { fingerFrom = P3(to.x, to.y + 0.06f, to.z + 0.03f) } else if (to != fingerTo) fingerFrom = currentFinger(now)
                if (to != fingerTo || !f.enabled) { fingerTo = to; fingerT0 = now }
                f.enabled = true
            }
        }
        layoutHints(now)
    }

    private fun currentFinger(now: Long): P3 {
        val k = ((now - fingerT0) / (400f / (input?.speed ?: 1f))).coerceIn(0f, 1f)
        val e = 1 - (1 - k) * (1 - k)
        return fingerFrom + (fingerTo - fingerFrom) * e
    }

    private fun layoutHints(now: Long) {
        val si = input ?: return
        val moving = si.hint.flatMap { it.moving }.take(rings.size)
        val breathe = if (hintT0 == 0L) 1f else { val t = (now - hintT0) / 600f; if (t < 1f) 1f + 0.12f * sin((t * PI).toFloat()) else 1f }
        rings.forEachIndexed { i, e ->
            val ref = moving.getOrNull(i)
            if (ref == null) { e.enabled = false; return@forEachIndexed }
            val bv = beads[ref] ?: run { e.enabled = false; return@forEachIndexed }
            e.tf().setPosition(Vector3(AbacusLayout.rodX(ref.rod), bv.y, AbacusLayout.BEAD_W / 2 + 0.0015f))
            e.tf().setScaleVector(Vector3(breathe, breathe, 1f))
            e.enabled = true
        }
        arrows.forEachIndexed { i, e ->
            val tm = si.hint.getOrNull(i)
            if (tm == null) { e.enabled = false; return@forEachIndexed }
            val bv = beads[tm.tap] ?: run { e.enabled = false; return@forEachIndexed }
            // toward the beam: up on the lower deck, down on the upper deck; away from it the other way
            val up = tm.tap.upper != tm.toBeam
            e.tf().setPosition(Vector3(AbacusLayout.rodX(tm.tap.rod), bv.y, AbacusLayout.BEAD_W / 2 + 0.003f))
            e.tf().setQuaternion(Quat(Vector3(0f, 0f, 1f), if (up) 0f else PI.toFloat()))
            e.enabled = true
        }
    }

    private fun place(bv: BeadView, bounceOffset: Float) {
        bv.holder.tf().setPosition(Vector3(0f, bv.y + bounceOffset, 0f))
    }

    fun onFrame() {
        val si = input
        if (pendingBeads.isNotEmpty()) { pendingBeads.removeAt(0).invoke(); return }
        if (beads.isEmpty() || si == null) return
        val now = SystemClock.elapsedRealtime()
        for (bv in beads.values) {
            // slide
            if (bv.y != bv.toY || bv.bounce) {
                val t = now - bv.t0 - bv.delay
                if (t < 0) continue
                if (bv.dur <= 0L || t >= bv.dur) {
                    bv.y = bv.toY
                    val bt = t - bv.dur
                    val bounceMs = 60f / si.speed
                    if (bv.bounce && bv.fromY != bv.toY && bt < bounceMs) {
                        val dir = if (bv.toY > bv.fromY) -1f else 1f
                        place(bv, dir * 0.002f * sin((PI * bt / bounceMs).toFloat()))
                    } else { bv.bounce = false; place(bv, 0f) }
                } else {
                    val k = t.toFloat() / bv.dur
                    val e = 1 - (1 - k) * (1 - k) * (1 - k)
                    bv.y = bv.fromY + (bv.toY - bv.fromY) * e
                    place(bv, 0f)
                }
            }
            // glow: swap to the glowing twin for the 500 ms window
            val g = now in bv.glowFrom until bv.glowUntil
            if (g != bv.glowing && bv.glowVis != null) { bv.glowVis.enabled = g; bv.vis.enabled = !g; bv.glowing = g }
        }
        finger?.takeIf { it.enabled }?.let { it.tf().setPosition(currentFinger(now).v()) }
        layoutHints(now)
    }

    /** Debug / audit: where each bead holder is, in volume metres. */
    fun dumpBeads(): String = beads.values.sortedWith(compareBy({ it.ref.rod }, { !it.ref.upper }, { it.ref.j })).joinToString("\n") { bv ->
        val p = AbacusLayout.faceToVolume(P3(AbacusLayout.rodX(bv.ref.rod), bv.y, 0f))
        "BEAD ${AbacusNames.bead(bv.ref.rod, bv.ref.upper, bv.ref.j)} spare=${bv.spare} deck=${AbacusNames.deck(bv.ref.rod, bv.ref.upper)} vol=(%.3f, %.3f, %.3f) faceY=%.3f".format(p.x, p.y + regionShift, p.z, bv.y)
    }

    /** All bead holders (debug replay: the real entities a ray can land on). */
    fun beadEntities(ref: BeadRef): List<Entity> = beads[ref]?.let { listOf(it.holder.getParent(), it.holder, it.vis) + deepest(it.vis) }?.filterNotNull() ?: emptyList()

    private fun deepest(e: Entity): List<Entity> {
        val kids = runCatching { e.getChildren() }.getOrNull().orEmpty()
        return if (kids.isEmpty()) emptyList() else listOf(kids.first()) + deepest(kids.first())
    }

    fun frameEntity(): Entity? = runCatching { face.getChildren().firstOrNull { it.getName() == AbacusNames.FRAME_TOP } }.getOrNull()

    fun dispose() {
        runCatching { root.destroy(true) }
        models?.close(); res?.close()
        beads.clear(); rings.clear(); arrows.clear()
    }

    companion object {
        /** Rotation that turns the light's forward (-Z) to [dir]. */
        fun lookRotation(dir: P3): Quat {
            val len = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z)
            val d = Vector3(dir.x / len, dir.y / len, dir.z / len)
            val f = Vector3(0f, 0f, -1f)
            val dot = f.x * d.x + f.y * d.y + f.z * d.z
            val axis = Vector3(f.y * d.z - f.z * d.y, f.z * d.x - f.x * d.z, f.x * d.y - f.y * d.x)
            val al = sqrt(axis.x * axis.x + axis.y * axis.y + axis.z * axis.z)
            if (al < 1e-6f) return Quat()
            return Quat(Vector3(axis.x / al, axis.y / al, axis.z / al), kotlin.math.acos(dot.coerceIn(-1f, 1f)))
        }
    }
}

/** The SDK instantiates the system; it finds the scene through [AbacusSceneHolder]. */
class AbacusFrameSystem : System() {
    override fun update(context: SceneUpdateContext) { AbacusSceneHolder.scene?.onFrame() }
}

internal object AbacusSceneHolder { @Volatile var scene: AbacusScene? = null }
