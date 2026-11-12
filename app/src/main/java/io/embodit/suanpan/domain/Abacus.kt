package io.embodit.suanpan.domain

/**
 * What is on the abacus: per rod the beads touching the beam, top [u] 0..2 and bottom [l] 0..5 (spares included,
 * although no page lets the user move them: they are dimmed and carry no collider, design gameSpec.abacus).
 * Immutable; every change returns a new value.
 */
class Beads private constructor(private val uu: IntArray, private val ll: IntArray) {
    fun u(r: Int) = uu[r]
    fun l(r: Int) = ll[r]
    fun digit(r: Int) = 5 * uu[r] + ll[r]
    fun value(): Long { var v = 0L; var p = 1L; for (i in 0 until RODS) { v += digit(i) * p; p *= 10 }; return v }

    /** Tap rule (design gameSpec.tap): bead j (1-based, counted from the beam) of a layer that has c beads at the beam. */
    fun tap(rod: Int, upper: Boolean, j: Int): Beads {
        require(rod in 0 until RODS)
        val c = if (upper) uu[rod] else ll[rod]
        val n = if (j > c) j else j - 1
        return with(rod, upper, n)
    }

    fun with(rod: Int, upper: Boolean, count: Int): Beads {
        val u2 = uu.copyOf(); val l2 = ll.copyOf()
        if (upper) u2[rod] = count.coerceIn(0, 2) else l2[rod] = count.coerceIn(0, 5)
        return Beads(u2, l2)
    }

    fun apply(step: Step): Beads = if (step.upper) with(step.rod, true, uu[step.rod] + step.du) else with(step.rod, false, ll[step.rod] + step.dl)

    fun toEngine(): EBoard? {
        if ((0 until RODS).any { uu[it] > 1 || ll[it] > 4 }) return null
        return EBoard(uu.copyOf(), ll.copyOf())
    }

    /** True when no rod uses a spare bead, so the value fully determines the beads. */
    val canonical: Boolean get() = (0 until RODS).none { uu[it] > 1 || ll[it] > 4 }

    fun encode(): String = (0 until RODS).joinToString(",") { "${uu[it]}${ll[it]}" }

    override fun equals(other: Any?) = other is Beads && uu.contentEquals(other.uu) && ll.contentEquals(other.ll)
    override fun hashCode() = 31 * uu.contentHashCode() + ll.contentHashCode()
    override fun toString() = "Beads(${value()} ${encode()})"

    companion object {
        val EMPTY = Beads(IntArray(RODS), IntArray(RODS))

        fun of(n: Long): Beads {
            val e = EBoard.of(n)
            return Beads(e.u.copyOf(), e.l.copyOf())
        }

        fun of(e: EBoard) = Beads(e.u.copyOf(), e.l.copyOf())

        fun decode(s: String?): Beads? = runCatching {
            val parts = s!!.split(",")
            require(parts.size == RODS)
            Beads(IntArray(RODS) { parts[it][0].digitToInt().coerceIn(0, 2) }, IntArray(RODS) { parts[it][1].digitToInt().coerceIn(0, 5) })
        }.getOrNull()

        /** Bead j (1-based from the beam) is a spare: the 2nd top bead or the 5th bottom bead. */
        fun isSpare(upper: Boolean, j: Int) = if (upper) j == 2 else j == 5
    }
}

/** A bead on the abacus: rod 0 = units, [upper] layer, [j] 1-based counted from the beam. */
data class BeadRef(val rod: Int, val upper: Boolean, val j: Int)

/**
 * One tap of a hint / demo step: which bead to tap ([tap]) and the group that moves with it ([moving]), and whether
 * the group moves toward the beam.
 */
data class TapMove(val tap: BeadRef, val moving: List<BeadRef>, val toBeam: Boolean)

object Taps {
    /** The tap that brings layer ([rod], [upper]) from [from] to [to] beads at the beam, and the beads that move. */
    fun between(rod: Int, upper: Boolean, from: Int, to: Int): TapMove? {
        if (from == to) return null
        return if (to > from) {
            TapMove(BeadRef(rod, upper, to), (from + 1..to).map { BeadRef(rod, upper, it) }, true)
        } else {
            TapMove(BeadRef(rod, upper, to + 1), (to + 1..from).map { BeadRef(rod, upper, it) }, false)
        }
    }
}
