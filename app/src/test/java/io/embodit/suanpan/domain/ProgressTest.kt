package io.embodit.suanpan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lessons keep their own problem order and skips (D44: switching lessons loses nothing); skipped ones come again at the end. */
class ProgressTest {
    @Test fun skipsComeBackAndAllEightMustBeRight() {
        var p = LessonProgress()
        p = p.solved(0)
        p = p.skip(1)
        p = p.skip(2)
        assertEquals(listOf(3, 4, 5, 6, 7, 1, 2), p.queue)
        for (i in 3..7) p = p.solved(i)
        assertFalse(p.done)
        assertEquals(1, p.current)
        p = p.demo(1).solved(1)
        p = p.solved(2)
        assertTrue(p.done)
        // first time right = no skip, no slow-mo: 0, 3..7
        assertEquals(6, p.firstTry)
        assertEquals(1, p.demoViews)
    }

    @Test fun codecRoundTrips() {
        val p = LessonProgress(queue = listOf(5, 6, 7, 1), skipped = setOf(1), demoSeen = setOf(2, 3), firstTry = 2, demoViews = 3, elapsedMs = 12345, done = false, bestMs = 0, started = true)
        assertEquals(p, LessonProgress.decode(p.encode()))
        assertEquals(LessonProgress(), LessonProgress.decode(null))
        assertEquals(LessonProgress(), LessonProgress.decode("garbage"))
    }

    @Test fun beadsCodecRoundTrips() {
        val b = Beads.of(4072).tap(6, false, 2)
        assertEquals(b, Beads.decode(b.encode()))
    }

    @Test fun restartKeepsThePassMark() {
        val done = LessonProgress(queue = emptyList(), done = true, bestMs = 9000, firstTry = 8)
        val r = done.restart()
        assertTrue(r.done); assertEquals(9000, r.bestMs); assertEquals(8, r.queue.size); assertEquals(0, r.firstTry)
    }
}
