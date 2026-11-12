package io.embodit.suanpan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** mustPass M2: every bucket 1000 problems, all solvable, subtraction >= 0, 2+ digits >= 50 % need a five or a carry. */
class PracticeTest {
    @Test fun thousandPerBucket() {
        for (type in PracticeType.entries) for (digits in 1..3) {
            val rng = Random(1000 * digits + type.ordinal)
            var hard = 0
            repeat(1000) {
                val p = Practice.generate(rng, type, digits)
                val (op, n) = p.ops.single()
                assertTrue("$p digits", p.start.toString().length == digits && n.toString().length == digits)
                if (type == PracticeType.ADD) assertEquals('+', op)
                if (type == PracticeType.SUB) assertEquals('-', op)
                assertTrue("$p negative", p.answer >= 0)
                val b = EBoard.of(p.start)
                Koujue.applyOp(b, n, op)
                assertEquals("$p solvable", p.answer, b.value())
                // the route solves it by taps alone
                if (Practice.isNonDirect(p)) hard++
            }
            if (digits >= 2) assertTrue("$type $digits hard=$hard", hard >= 500)
            println("PRACTICE $type digits=$digits nonDirect=${hard / 10.0}%")
        }
    }

    @Test fun mixHasBothSigns() {
        val rng = Random(3)
        val ops = (1..200).map { Practice.generate(rng, PracticeType.MIX, 2).ops.single().first }.toSet()
        assertEquals(setOf('+', '-'), ops)
    }
}
