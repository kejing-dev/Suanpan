package io.embodit.suanpan.ui

import io.embodit.suanpan.domain.Beads
import io.embodit.suanpan.domain.RODS
import io.embodit.suanpan.ui.input.parseLoggedPath
import io.embodit.suanpan.ui.input.resolvePath
import io.embodit.suanpan.ui.volume.AbacusNames
import io.embodit.suanpan.ui.volume.AbacusTarget
import io.embodit.suanpan.ui.volume.AbacusTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Board-input must-have test (template ExampleBoardTargetTest, build.md 命中回放), v0.1.1 two-stage input: every node a
 * ray can land on — the deck holder, any bead under it (spares included: they sit in the deck too), the bead's mesh,
 * the GLB's own nodes, the glow twin, the frame — resolves to the right target with the names the scene really sets.
 */
class AbacusTargetTest {
    private fun resolve(chain: List<String>) = resolvePath(chain, AbacusTargets::resolveName)

    @Test fun everyBeadNodeResolvesToItsDeck() {
        var n = 0
        for (rod in 0 until RODS) for (upper in listOf(true, false)) {
            val want = AbacusTarget.Deck(rod, upper)
            val deck = AbacusNames.deck(rod, upper)
            assertEquals(want, resolve(listOf(deck, "abacusFace", "abacusRoot")))
            for (j in 1..(if (upper) 2 else 5)) {
                val bead = AbacusNames.bead(rod, upper, j)
                val vis = AbacusNames.beadVis(rod, upper, j)
                for (c in listOf(listOf(bead, deck), listOf(vis, bead, deck), listOf("Mesh_0", "node_1", vis, bead, deck), listOf("$vis:glow", bead, deck)))
                    assertEquals("$c", want, resolve(c))
                n++
            }
        }
        assertEquals(63, n)
    }

    @Test fun beadsAloneAreNotTargets() {
        // a bead node without its deck above it would be a naming bug: it must not resolve on its own
        for (rod in 0 until RODS) for (j in 1..5) assertNull(resolve(listOf(AbacusNames.bead(rod, false, j))))
    }

    @Test fun frameBringsTheWindowBack() {
        assertEquals(AbacusTarget.Frame, resolve(listOf(AbacusNames.FRAME_TOP, "abacusFace", "abacusRoot")))
        assertEquals(AbacusTarget.Frame, resolve(listOf(AbacusNames.FRAME_BOTTOM)))
    }

    @Test fun nothingElseIsATarget() {
        for (c in listOf(listOf("rod3", "abacusFace"), listOf("beam"), listOf("standBase"), listOf("deck:9:l"), listOf("deck:0:x"), listOf("deck:-1:u"), listOf("deck:0:l:1")))
            assertNull("$c", resolve(c))
    }

    /** Paste real `path=` values from the headset files/input-log.txt here after every device round. */
    @Test fun replayLoggedPaths() {
        assertEquals(AbacusTarget.Deck(0, false), resolve(parseLoggedPath("vis:0:l:3 < bead:0:l:3 < deck:0:l < abacusFace < abacusRoot")))
        assertEquals(AbacusTarget.Deck(8, true), resolve(parseLoggedPath("Mesh_0 < vis:8:u:1 < bead:8:u:1 < deck:8:u")))
        assertEquals(AbacusTarget.Deck(4, false), resolve(parseLoggedPath("vis:4:l:5 < bead:4:l:5 < deck:4:l")))   // a spare inside the deck
    }

    @Test fun countRuleMatchesTheOldTapRule() {
        // the picker sets the count directly; the old "tap bead j" rule (debug tap / solve) maps onto the same counts
        for (c in 0..4) for (j in 1..4) {
            val viaTap = Beads.EMPTY.with(0, false, c).tap(0, false, j).l(0)
            val n = if (j > c) j else j - 1
            assertEquals(viaTap, Beads.EMPTY.with(0, false, c).with(0, false, n).l(0))
        }
    }
}
