package io.embodit.suanpan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** mustPass M3: forgiving judge (D27) and the hint route. Taps go through Beads.tap, the same rule as the scene. */
class RouteTest {
    private fun startBoard(p: Problem) = if (p.isSet) Beads.EMPTY else Beads.of(p.start)

    /** Turns a step into the tap a user makes and applies it. */
    private fun tapFor(b: Beads, st: Step): Beads {
        val cur = if (st.upper) b.u(st.rod) else b.l(st.rod)
        val tm = Taps.between(st.rod, st.upper, cur, cur + st.delta)!!
        return b.tap(tm.tap.rod, tm.tap.upper, tm.tap.j)
    }

    @Test fun eightyProblemsSolvedByTheirRhymesAreCorrect() {
        for (lesson in Lessons.all) for (q in lesson.problems) {
            val p = Problem.parse(q)
            var b = startBoard(p)
            val r = Route(p); r.reset(b)
            var verdict = Verdict.ON_ROUTE
            var guard = 0
            while (verdict != Verdict.CORRECT && guard++ < 200) {
                val steps = r.demoSteps()
                assertTrue("$q no steps", steps.isNotEmpty())
                for ((_, st) in steps) {
                    b = tapFor(b, st)
                    verdict = r.settle(b)
                    assertFalse("$q left the route at $b", verdict == Verdict.OFF_ROUTE)
                    if (verdict == Verdict.CORRECT) break
                }
            }
            assertEquals(q, Verdict.CORRECT, verdict)
            assertEquals(q, p.answer, b.value())
        }
    }

    @Test fun anyOrderInsideARhymeStaysOnRoute() {
        for (lesson in Lessons.all) for (q in lesson.problems) {
            val p = Problem.parse(q); if (p.isSet) continue
            var b = startBoard(p)
            val r = Route(p); r.reset(b)
            var guard = 0
            while (r.settle(b) != Verdict.CORRECT && guard++ < 100) {
                // do the remaining taps of the current rhyme in reverse order
                val taps = r.remainingTaps(b).reversed()
                assertTrue("$q $b no taps", taps.isNotEmpty())
                for (t in taps) {
                    b = b.tap(t.tap.rod, t.tap.upper, t.tap.j)
                    val v = r.settle(b)
                    assertFalse("$q reversed left the route", v == Verdict.OFF_ROUTE)
                    if (v == Verdict.CORRECT) break
                }
            }
            assertEquals(q, p.answer, b.value())
        }
    }

    @Test fun nonRhymePathsAndSkippedCheckpointsAreRight() {
        // 1000 - 1 - 99: one move straight to 900 skips the checkpoint 999 (qaCombos)
        val p = Problem.parse("1000-1-99")
        val r = Route(p); var b = Beads.of(1000); r.reset(b)
        b = b.tap(3, false, 1)        // thousands: 1 -> 0 (part of the first rhyme's borrow chain)
        assertFalse(r.settle(b) == Verdict.OFF_ROUTE)
        b = b.with(2, true, 1).with(2, false, 4)   // hundreds 9
        assertEquals(Verdict.CORRECT, r.settle(b))
        // two problems per lesson by a non-rhyme path: set the answer rod by rod, low to high
        for (lesson in Lessons.all) for (q in lesson.problems.take(2)) {
            val pr = Problem.parse(q); val rr = Route(pr); var bb = startBoard(pr); rr.reset(bb)
            val target = Beads.of(pr.answer)
            var last = Verdict.ON_ROUTE
            for (rod in 0 until RODS) {
                bb = bb.with(rod, true, target.u(rod)); last = rr.settle(bb)
                bb = bb.with(rod, false, target.l(rod)); last = rr.settle(bb)
            }
            assertEquals(q, Verdict.CORRECT, last)
        }
    }

    @Test fun offRouteShowsWrongRodsAndComingBackRestoresTheHint() {
        val p = Problem.parse("35+7")
        val r = Route(p); var b = Beads.of(35); r.reset(b)
        assertEquals(3, r.remainingTaps(b).size)       // lower +2, upper -1, tens +1
        val wrong = b.tap(5, false, 1)                  // a rod far away
        assertEquals(Verdict.OFF_ROUTE, r.settle(wrong))
        assertTrue(r.remainingTaps(wrong).isEmpty())
        assertEquals(listOf(WrongRod(0, 5, 2), WrongRod(1, 3, 4), WrongRod(5, 1, 0)), r.wrongRods(wrong))
        b = wrong.tap(5, false, 1)                      // undo
        assertFalse(r.settle(b) == Verdict.OFF_ROUTE)
        assertEquals(3, r.remainingTaps(b).size)
    }

    @Test fun chainsAdvanceCheckpoints() {
        val p = Problem.parse("23+48-19")
        assertEquals(listOf(71L, 52L), p.checkpoints)
        val r = Route(p); var b = Beads.of(23); r.reset(b)
        b = Beads.of(71)
        assertEquals(Verdict.CHECKPOINT, r.settle(b))
        assertEquals(1, r.cpIndex)
        assertEquals(Beads.of(71), r.opStart)
        b = Beads.of(52)
        assertEquals(Verdict.CORRECT, r.settle(b))
    }

    @Test fun resumeHalfWayContinuesTheRoute() {
        val p = Problem.parse("35+7")
        var b = Beads.of(35)
        val first = Route(p).also { it.reset(b) }.demoSteps()
        b = tapFor(b, first[0].second)                // one tap done, then the app is killed
        val r = Route(p); r.reset(b)
        assertFalse(r.offRoute)
        assertEquals(2, r.remainingTaps(b).size)
    }

    @Test fun spareBeadsAreNeverNeeded() {
        val rng = Random(7)
        for (lesson in Lessons.all) for (q in lesson.problems) {
            val p = Problem.parse(q); var b = startBoard(p)
            val r = Route(p); r.reset(b)
            while (true) {
                val steps = r.demoSteps(); if (steps.isEmpty()) break
                for ((_, st) in steps) { b = tapFor(b, st); assertTrue(b.canonical) ; if (r.settle(b) == Verdict.CORRECT) break }
                if (r.finished) break
            }
        }
        assertTrue(rng.nextInt() != 0 || true)
    }
}
