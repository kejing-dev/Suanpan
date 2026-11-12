package io.embodit.suanpan.ui.volume

import io.embodit.suanpan.domain.RODS
import io.embodit.suanpan.ui.input.TargetResolver

/**
 * What a pinch / trigger can land on (v0.1.1, profile D17 ≥ 2°): a rod deck — the upper or lower half of one rod,
 * beads included — or the frame (S15: brings the closed window back). Beads are no longer targets of their own: one
 * rod's beads touch each other, so no bead can have the 2 cm gap D17 asks for. The lower-deck count picker is a
 * SpatialUI panel whose keys take their own clicks.
 */
sealed interface AbacusTarget {
    data class Deck(val rod: Int, val upper: Boolean) : AbacusTarget
    data object Frame : AbacusTarget
}

/**
 * Every name the scene gives a tap target comes from here (board-input template: never a string template inside
 * `Entity().apply {}`, where a bare `id` is the ECS entity id). Deck holders carry the collider + Interactable + system
 * HoverEffect; the beads ("bead:…", their meshes "vis:…") hang below them.
 */
object AbacusNames {
    fun deck(rod: Int, upper: Boolean) = "deck:$rod:${if (upper) "u" else "l"}"
    fun bead(rod: Int, upper: Boolean, j: Int) = "bead:$rod:${if (upper) "u" else "l"}:$j"
    fun beadVis(rod: Int, upper: Boolean, j: Int) = "vis:$rod:${if (upper) "u" else "l"}:$j"
    const val FRAME_TOP = "frame:top"
    const val FRAME_BOTTOM = "frame:bottom"
}

object AbacusTargets : TargetResolver<AbacusTarget> {
    override fun resolve(entity: com.pico.spatial.core.ecs.Entity): AbacusTarget? = runCatching { resolveName(entity.getName()) }.getOrNull()

    /** Pure, unit-tested. Only deck holders and the frame are targets; bead / mesh nodes resolve through their parent deck. */
    fun resolveName(n: String): AbacusTarget? {
        if (n == AbacusNames.FRAME_TOP || n == AbacusNames.FRAME_BOTTOM) return AbacusTarget.Frame
        if (!n.startsWith("deck:")) return null
        val p = n.split(":")
        if (p.size != 3) return null
        val rod = p[1].toIntOrNull()?.takeIf { it in 0 until RODS } ?: return null
        val upper = when (p[2]) { "u" -> true; "l" -> false; else -> return null }
        return AbacusTarget.Deck(rod, upper)
    }
}
