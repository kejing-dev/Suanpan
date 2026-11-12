package io.embodit.suanpan.ui.volume

import android.os.SystemClock
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelFormat
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import io.embodit.suanpan.platform.AppContext
import io.embodit.suanpan.platform.SpLog

/**
 * The Tripo models (profile B12; assets/models/<role>.glb): decimated with bin/glb_decimate.py and calibrated offline
 * by tools/prep_models.py (D25: bead centred, along +Y, Ø 0.055 × 0.040; frame / beam / stand with the origin and
 * size of design spatialLayout), so the scene places them like the procedural parts. Loaded once as disabled templates,
 * cloned per use. Every material is forced OPAQUE and opacity components are removed (profile D24: a GLB that looks
 * see-through). A missing role leaves its template null and the scene builds the procedural stand-in.
 */
internal class Models {
    val templates = HashMap<String, Entity>()

    suspend fun load() {
        for (role in ROLES) loadOne(role)?.let { templates[role] = it }
        // the bead's two other looks, made once from the template (own material instances, then tinted)
        templates[BEAD]?.let { t ->
            variant(t, BEAD_SPARE, 0.6f, 0f)
            variant(t, BEAD_GLOW, 1.15f, 0.55f)
        }
        SpLog.i("MODELS loaded ${templates.keys.sorted()} (missing ${ROLES.filter { it !in templates }})")
    }

    private suspend fun loadOne(role: String): Entity? {
        val ctx = AppContext.ctx
        val t0 = SystemClock.elapsedRealtime()
        return runCatching {
            val bytes = ctx.assets.open("models/$role.glb").use { it.readBytes() }
            val e = Entity.loadSuspend(java.io.ByteArrayInputStream(bytes), ModelFormat.GLTF)
            e.enabled = false
            val forced = opaque(e)
            SpLog.i("MODEL $role loaded ms=${SystemClock.elapsedRealtime() - t0} bytes=${bytes.size} forcedOpaque=$forced")
            e
        }.onFailure { SpLog.i("MODEL $role not used (${it.javaClass.simpleName}: ${it.message})") }.getOrNull()
    }

    private fun variant(t: Entity, role: String, k: Float, glow: Float) {
        runCatching {
            val v = t.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = false)) ?: return
            v.enabled = false
            tint(pbrMaterials(v), k, glow)
            templates[role] = v
        }.onFailure { SpLog.e("MODEL variant $role failed: $it", it) }
    }

    /** A new, enabled copy of [role]; own material instances when [ownMaterials] (beads: dimmed spares, glow). */
    fun instance(role: String, name: String, ownMaterials: Boolean = false): Entity? {
        val t = templates[role] ?: return null
        return runCatching {
            t.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = !ownMaterials))?.also { it.enabled = true; it.setName(name) }
        }.onFailure { SpLog.e("MODEL clone $role failed: $it", it) }.getOrNull()
    }

    fun close() {
        templates.values.forEach { runCatching { it.destroy(true) } }
        templates.clear()
    }

    companion object {
        const val BEAD = "bead"
        const val FRAME = "frame"
        const val BEAM = "beam"
        const val STAND = "stand"
        const val BRACE = "brace"
        const val BEAD_SPARE = "bead.spare"
        const val BEAD_GLOW = "bead.glow"
        val ROLES = listOf(BEAD, FRAME, BEAM, STAND, BRACE)

        fun opaque(root: Entity): Int {
            var forced = 0
            fun walk(e: Entity) {
                if (e.components[OpacityControllerComponent::class.java] != null) e.components.remove(OpacityControllerComponent::class.java)
                e.components[ModelComponent::class.java]?.let { mc ->
                    runCatching {
                        mc.materials.forEach { m ->
                            if (m is PhysicallyBasedMaterial && m.getBlendingMode() != BlendingMode.OPAQUE) { m.setBlendingMode(BlendingMode.OPAQUE); forced++ }
                        }
                    }
                }
                runCatching { e.getChildren() }.getOrNull()?.forEach { walk(it) }
            }
            walk(root)
            return forced
        }

        /** Every PBR material below [root] (own instances when cloned with ownMaterials). */
        fun pbrMaterials(root: Entity): List<PhysicallyBasedMaterial> {
            val out = ArrayList<PhysicallyBasedMaterial>()
            fun walk(e: Entity) {
                e.components[ModelComponent::class.java]?.let { mc -> runCatching { mc.materials.forEach { m -> if (m is PhysicallyBasedMaterial) out += m } } }
                runCatching { e.getChildren() }.getOrNull()?.forEach { walk(it) }
            }
            walk(root)
            return out
        }

        /** Tints and lights a bead's own materials: [k] multiplies the base colour (spares 0.6), [glow] adds warm gold. */
        fun tint(materials: List<PhysicallyBasedMaterial>, k: Float, glow: Float) {
            for (m in materials) runCatching {
                m.setBaseColor(Color4(k, k, k, 1f))
                m.setEmissiveColor(Color4(1.0f * glow, 0.82f * glow, 0.48f * glow, 1f))
            }
        }
    }
}
