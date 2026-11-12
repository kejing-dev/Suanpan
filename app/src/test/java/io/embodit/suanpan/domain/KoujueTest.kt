package io.embodit.suanpan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * mustPass M1: the 80 lesson problems against koujue_reference.py (tools/gen_expected.py wrote the table):
 * rhyme name, family, every atomic step and the final value; all 52 rhymes are covered.
 */
class KoujueTest {
    private data class Row(val lesson: Int, val q: String, val start: Long, val answer: Long, val segs: List<Triple<String, String, List<Step>>>)

    private val rows: List<Row> by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("koujue_expected.txt")!!.bufferedReader(Charsets.UTF_8).readText()
        text.lines().filter { it.isNotBlank() }.map { line ->
            val p = line.split("|")
            val segs = if (p[4].isEmpty()) emptyList() else p[4].split(";").map { s ->
                val (name, fam, steps) = s.split("~")
                Triple(name, fam, steps.split(",").map { st -> val (r, du, dl) = st.split(":").map(String::toInt); Step(r, du, dl) })
            }
            Row(p[0].toInt(), p[1], p[2].toLong(), p[3].toLong(), segs)
        }
    }

    @Test fun eightyProblemsMatchTheReference() {
        assertEquals(80, rows.size)
        var lessonIdx = 0
        for (lesson in Lessons.all) {
            lesson.problems.forEachIndexed { i, q ->
                val row = rows[lessonIdx * 8 + i]
                val p = Problem.parse(q)
                assertEquals("answer $q", row.answer, p.answer)
                if (p.isSet) { assertTrue(row.segs.isEmpty()); return@forEachIndexed }
                assertEquals("start $q", row.start, p.start)
                val b = EBoard.of(p.start)
                val segs = p.ops.flatMap { (op, n) -> Koujue.applyOp(b, n, op) }
                assertEquals("segments $q", row.segs.size, segs.size)
                segs.zip(row.segs).forEach { (got, want) ->
                    assertEquals("name $q", want.first, got.fullZh)
                    assertEquals("family $q", want.second, got.family.zh)
                    assertEquals("steps $q ${got.fullZh}", want.third, got.steps)
                }
                assertEquals("final $q", row.answer, b.value())
            }
            lessonIdx++
        }
    }

    @Test fun all52RhymesAreCovered() {
        val names = HashSet<String>()
        for (lesson in Lessons.all) for (q in lesson.problems) {
            val p = Problem.parse(q)
            if (p.isSet) continue
            val b = EBoard.of(p.start)
            for ((op, n) in p.ops) for (s in Koujue.applyOp(b, n, op)) {
                names += s.name.zh
                // nested carries / borrows are rhymes too
                s.left?.zh?.let { l -> names += l.substringBefore("（") }
            }
        }
        // the 52 classic rhymes: 26 add + 26 subtract
        val add = (1..9).map { "${cn(it)}上${cn(it)}" } +
            (1..4).map { "${cn(it)}下五去${cn(5 - it)}" } +
            (1..9).map { n -> val k = 10 - n; if (k == 5) "五去五进一" else "${cn(n)}去${cn(k)}进一" } +
            (6..9).map { n -> "${cn(n)}上${cn(5 - (10 - n))}去五进一" }
        val sub = (1..9).map { "${cn(it)}去${cn(it)}" } +
            (1..4).map { "${cn(it)}上${cn(5 - it)}去五" } +
            (1..9).map { n -> val k = 10 - n; if (k == 5) "五退一还五" else "${cn(n)}退一还${cn(k)}" } +
            (6..9).map { n -> "${cn(n)}退一还五去${cn(5 - (10 - n))}" }
        assertEquals(26, add.size); assertEquals(26, sub.size)
        val missing = (add + sub).filter { it !in names }
        assertTrue("not covered: $missing", missing.isEmpty())
    }

    @Test fun everyRhymeTouchesEachLayerOnce() {
        for (lesson in Lessons.all) for (q in lesson.problems) {
            val p = Problem.parse(q); if (p.isSet) continue
            val b = EBoard.of(p.start)
            for ((op, n) in p.ops) for (s in Koujue.applyOp(b, n, op)) {
                val keys = s.steps.map { it.rod to it.upper }
                assertEquals("$q ${s.fullZh}", keys.size, keys.toSet().size)
            }
        }
    }

    @Test fun formulaAndWhyFillEveryPlaceholder() {
        val b = EBoard.of(35)
        val s = Koujue.applyOp(b, 7, '+').single()
        assertEquals("七上二去五进一 · +7 = −3 +10", Explain.line(s, en = false))
        for (lesson in Lessons.all) for (q in lesson.problems) {
            val p = Problem.parse(q); if (p.isSet) continue
            val e = EBoard.of(p.start)
            for ((op, n) in p.ops) for (sg in Koujue.applyOp(e, n, op)) for (en in listOf(true, false)) {
                val w = Explain.why(sg, en)
                assertTrue("$q $w", w.isNotEmpty() && !w.contains("{"))
            }
        }
    }

    private fun cn(n: Int) = "零一二三四五六七八九"[n].toString()
}
