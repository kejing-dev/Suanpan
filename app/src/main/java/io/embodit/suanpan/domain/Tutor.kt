package io.embodit.suanpan.domain

import kotlin.random.Random

/**
 * A problem: start number, then operands ([ops]), or "set the number" ([setTarget], lesson 1).
 * [checkpoints] are the running totals after each operand; the last one is the answer.
 */
data class Problem(val text: String, val start: Long, val ops: List<Pair<Char, Long>>, val setTarget: Long? = null) {
    val isSet: Boolean get() = setTarget != null
    val checkpoints: List<Long> = if (setTarget != null) listOf(setTarget) else {
        val out = ArrayList<Long>(); var acc = start
        for ((op, n) in ops) { acc = if (op == '+') acc + n else acc - n; out += acc }
        out
    }
    val answer: Long get() = checkpoints.last()

    /** "35 + 7" / "拨出 2025" (Set 2025). */
    fun display(en: Boolean): String = if (setTarget != null) (if (en) "Set ${fmt(setTarget)}" else "拨出 ${fmt(setTarget)}")
    else buildString { append(start); for ((op, n) in ops) { append(if (op == '+') " + " else " − "); append(n) } }

    companion object {
        /** "=2025" or "23+48-19". */
        fun parse(s: String): Problem {
            if (s.startsWith("=")) return Problem(s, 0, emptyList(), s.drop(1).toLong())
            val ops = ArrayList<Pair<Char, Long>>()
            var cur = StringBuilder(); var first: Long? = null; var pending = '+'
            for (ch in s) {
                if (ch == '+' || ch == '-') {
                    val v = cur.toString().toLong(); if (first == null) first = v else ops += pending to v
                    pending = ch; cur = StringBuilder()
                } else cur.append(ch)
            }
            val v = cur.toString().toLong(); if (first == null) first = v else ops += pending to v
            return Problem(s, first!!, ops)
        }

        fun fmt(n: Long): String = "%,d".format(n)
    }
}

/** What a settled board means for the current problem. */
enum class Verdict { CORRECT, CHECKPOINT, PROGRESS, ON_ROUTE, OFF_ROUTE }

/** A rod whose digit is not yet the next checkpoint's: shown on the digit strip as "now→target". */
data class WrongRod(val rod: Int, val now: Int, val target: Int)

/**
 * The hint route of one problem (design gameSpec.judge / hintPath). The board is judged only by its value:
 * equal to the answer = right, whatever the path (D27); equal to a later checkpoint = that operand is done
 * (checkpoints may be skipped). Between checkpoints the route is split into rhymes ([Segment]); inside one
 * rhyme the taps may come in any order, a move outside the rhyme leaves the route ([offRoute]) and the hint
 * falls back to "which rods are not right yet".
 */
class Route(val problem: Problem) {
    /** Index of the next checkpoint to reach. */
    var cpIndex = 0; private set
    var segments: List<Segment> = emptyList(); private set
    var segIndex = 0; private set
    var segStart: Beads = Beads.EMPTY; private set
    var segTarget: Beads = Beads.EMPTY; private set
    var offRoute = false; private set
    /** The board the current operand started from (where the slow-motion demo starts). */
    var opStart: Beads = Beads.EMPTY; private set
    private var lastRemaining = Int.MAX_VALUE

    val segment: Segment? get() = segments.getOrNull(segIndex)
    val nextCheckpoint: Long get() = problem.checkpoints[cpIndex.coerceAtMost(problem.checkpoints.lastIndex)]
    val finished: Boolean get() = cpIndex >= problem.checkpoints.size

    /** Start (or restart) from [b]: the problem's start board, or a resumed board part way through. */
    fun reset(b: Beads) {
        cpIndex = 0
        val v = b.value()
        val k = problem.checkpoints.indexOfFirst { it == v }
        if (k in 0 until problem.checkpoints.lastIndex && b.canonical) cpIndex = k + 1
        val from = when {
            problem.isSet -> if (b.canonical) b else Beads.EMPTY
            cpIndex == 0 -> Beads.of(problem.start)
            else -> Beads.of(problem.checkpoints[cpIndex - 1])
        }
        plan(from)
        settle(b)
    }

    /** Boards between rhymes: bounds[i] is the board before segment i, bounds[size] after the last. */
    private var bounds: List<Beads> = listOf(Beads.EMPTY)

    private fun plan(from: Beads) {
        opStart = from
        val e = from.toEngine()
        segments = when {
            finished || e == null -> emptyList()
            problem.isSet -> Koujue.setPlan(e, problem.setTarget!!)
            else -> {
                val (op, n) = problem.ops[cpIndex]
                runCatching { Koujue.applyOp(e, n, op) }.getOrElse { emptyList() }
            }
        }
        val bs = ArrayList<Beads>(); var cur = from; bs += cur
        for (sg in segments) { cur = sg.steps.fold(cur) { acc, st -> acc.apply(st) }; bs += cur }
        bounds = bs
        setSeg(0)
        offRoute = false
        lastRemaining = Int.MAX_VALUE
    }

    private fun setSeg(i: Int) {
        segIndex = i
        segStart = bounds[i.coerceAtMost(bounds.lastIndex)]
        segTarget = bounds[(i + 1).coerceAtMost(bounds.lastIndex)]
    }

    /** Judge a settled board. */
    fun settle(b: Beads): Verdict {
        val v = b.value()
        if (v == problem.answer) { cpIndex = problem.checkpoints.size; segments = emptyList(); offRoute = false; return Verdict.CORRECT }
        for (k in cpIndex until problem.checkpoints.lastIndex) {
            if (problem.checkpoints[k] == v && b.canonical) {
                cpIndex = k + 1
                plan(b)
                return Verdict.CHECKPOINT
            }
        }
        // inside the current rhyme, or a later one of this operand (taps may run ahead)?
        val before = segIndex
        var found = -1
        for (i in segIndex until segments.size) if (within(b, i)) { found = i; break }
        if (found < 0) {
            offRoute = true
            return Verdict.OFF_ROUTE
        }
        var i = found
        while (i < segments.lastIndex && b == bounds[i + 1]) i++
        setSeg(i)
        offRoute = false
        val remaining = remainingTaps(b).sumOf { it.moving.size } + (segments.size - segIndex) * 100
        val progress = segIndex > before || remaining < lastRemaining
        lastRemaining = remaining
        return if (progress) Verdict.PROGRESS else Verdict.ON_ROUTE
    }

    /** Every (rod, layer) of rhyme [i] lies between its start and target count; nothing else moved. */
    private fun within(b: Beads, i: Int): Boolean {
        val seg = segments[i]
        val s0 = bounds[i]; val s1 = bounds[i + 1]
        val touched = seg.steps.map { it.rod to it.upper }.toSet()
        for (r in 0 until RODS) for (up in listOf(true, false)) {
            val cur = if (up) b.u(r) else b.l(r)
            val s = if (up) s0.u(r) else s0.l(r)
            val t = if (up) s1.u(r) else s1.l(r)
            if ((r to up) in touched) { if (cur < minOf(s, t) || cur > maxOf(s, t)) return false } else if (cur != s) return false
        }
        return true
    }

    /** The taps still to do in the current rhyme (any order counts). Empty off route. */
    fun remainingTaps(b: Beads): List<TapMove> {
        val seg = segment ?: return emptyList()
        if (offRoute) return emptyList()
        val out = ArrayList<TapMove>()
        val seen = HashSet<Pair<Int, Boolean>>()
        for (st in seg.steps) {
            if (!seen.add(st.rod to st.upper)) continue
            val cur = if (st.upper) b.u(st.rod) else b.l(st.rod)
            val t = if (st.upper) segTarget.u(st.rod) else segTarget.l(st.rod)
            Taps.between(st.rod, st.upper, cur, t)?.let { out += it }
        }
        return out
    }

    /** Rods that differ from the next checkpoint (the off-route hint). */
    fun wrongRods(b: Beads): List<WrongRod> {
        val target = Beads.of(nextCheckpoint)
        return (0 until RODS).filter { b.digit(it) != target.digit(it) }.map { WrongRod(it, b.digit(it), target.digit(it)) }
    }

    /** Every atomic step of the current operand from [opStart], for the slow-motion demo. */
    fun demoSteps(): List<Pair<Segment, Step>> {
        val e = opStart.toEngine() ?: return emptyList()
        val segs = when {
            finished -> emptyList()
            problem.isSet -> Koujue.setPlan(e, problem.setTarget!!)
            else -> { val (op, n) = problem.ops[cpIndex]; runCatching { Koujue.applyOp(e, n, op) }.getOrElse { emptyList() } }
        }
        return segs.flatMap { s -> s.steps.map { s to it } }
    }
}

/** Problem-strip / panel texts derived from the engine (design content.whyTemplates). */
object Explain {
    private val PLACE_ZH = arrayOf("个位", "十位", "百位", "千位", "万位", "十万位", "百万位", "千万位", "亿位")
    private val PLACE_EN = arrayOf("ones", "tens", "hundreds", "thousands", "ten-thousands", "100k", "millions", "10M", "100M")

    fun place(rod: Int, en: Boolean) = if (en) PLACE_EN[rod] else PLACE_ZH[rod]

    fun rhyme(s: Segment, en: Boolean): String = if (en) s.fullEn else s.fullZh

    /** "七上二去五进一 · +7 = −3 +10" */
    fun line(s: Segment, en: Boolean): String {
        val f = Koujue.formula(s)
        return if (f.isEmpty()) rhyme(s, en) else "${rhyme(s, en)} · $f"
    }

    fun why(s: Segment, en: Boolean): String {
        val t = Lessons.why[s.family.zh] ?: return ""
        val n = s.digit
        val c10 = 10 - n
        val vars = mapOf("n" to "$n", "c5" to "${5 - n}", "c10" to "$c10", "m5" to "${5 - c10}")
        var out = t.get(en)
        for ((k, v) in vars) out = out.replace("{$k}", v)
        s.left?.let { left ->
            val key = if (s.op == '+') "leftCarry" else "leftBorrow"
            Lessons.why[key]?.let { lt -> out += " " + lt.get(en).replace("{left}", if (en) left.en else left.zh) }
        }
        return out
    }

    /** One demo step: "② 个位 去五" / "② ones: five off". */
    fun step(index: Int, st: Step, en: Boolean): String {
        val circled = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯".getOrNull(index)?.toString() ?: "${index + 1}."
        val k = kotlin.math.abs(st.delta)
        val what = if (en) {
            if (st.upper) (if (st.delta > 0) "five down" else "five off") else (if (st.delta > 0) "up $k" else "off $k")
        } else {
            if (st.upper) (if (st.delta > 0) "下五" else "去五") else (if (st.delta > 0) "上$k" else "去$k")
        }
        return if (en) "$circled ${place(st.rod, true)}: $what" else "$circled ${place(st.rod, false)} $what"
    }
}

enum class PracticeType { ADD, SUB, MIX }

/** Random practice (design gameSpec.practiceGenerator): user-chosen type and digit count, never auto-levelled. */
object Practice {
    /** Share of problems (2+ digits) forced to need a five or a carry / borrow; the rest are free draws. */
    const val NON_DIRECT_SHARE = 0.6

    fun generate(rng: Random, type: PracticeType, digits: Int): Problem {
        require(digits in 1..3)
        // about 60 % need a five or a carry / borrow (design); the rest are kept direct so the share is not ~90 %
        val wantHard = digits >= 2 && rng.nextDouble() < NON_DIRECT_SHARE
        val wantEasy = digits >= 2 && !wantHard
        var last: Problem? = null
        repeat(400) {
            val lo = if (digits == 1) 1L else pow10(digits - 1)
            val hi = pow10(digits) - 1
            var a = rng.nextLong(lo, hi + 1)
            var b = rng.nextLong(lo, hi + 1)
            val op = when (type) { PracticeType.ADD -> '+'; PracticeType.SUB -> '-'; PracticeType.MIX -> if (rng.nextBoolean()) '+' else '-' }
            if (op == '-' && a < b) { val t = a; a = b; b = t }
            if (op == '-' && a == b) return@repeat
            val p = Problem("$a$op$b", a, listOf(op to b))
            last = p
            val hard = isNonDirect(p)
            if ((!wantHard || hard) && (!wantEasy || !hard)) return p
        }
        return last ?: Problem("1+1", 1, listOf('+' to 1L))
    }

    fun isNonDirect(p: Problem): Boolean {
        val b = EBoard.of(p.start)
        return p.ops.any { (op, n) -> Koujue.applyOp(b, n, op).any { it.family.nonDirect } }
    }

    private fun pow10(n: Int): Long { var x = 1L; repeat(n) { x *= 10 }; return x }
}
