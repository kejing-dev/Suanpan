package io.embodit.suanpan.domain

/**
 * Abacus addition / subtraction rhymes (珠算口诀), ported line by line from the design's reference
 * apps/suanpan-abacus/koujue_reference.py. 9 rods, rod 0 = units (rightmost). Arithmetic uses one top bead
 * (u 0..1) and four bottom beads (l 0..4) per rod; the 2nd top bead and the 5th bottom bead are spares.
 *
 * Every atomic step is "one rod, one layer, one tap": a move of top and bottom beads together is split into
 * two steps, top first (Python `split`). A [Segment] is one rhyme: the digit rod's steps plus the carry /
 * borrow steps on the rods to its left (recursive, as in 99 + 1).
 */
const val RODS = 9

private const val CN = "零一二三四五六七八九"
private val EN_NUM = arrayOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

enum class Family(val zh: String, val en: String) {
    SET("认珠子", "Reading beads"),
    ZHI_JIA("直加", "Direct add"),
    MAN_WU_JIA("满五加", "Make five"),
    JIN_SHI_JIA("进十加", "Carry"),
    PO_WU_JIN_SHI_JIA("破五进十加", "Carry with five"),
    ZHI_JIAN("直减", "Direct subtract"),
    PO_WU_JIAN("破五减", "Break five"),
    TUI_SHI_JIAN("退十减", "Borrow"),
    TUI_SHI_HUAN_WU_JIAN("退十还五减", "Borrow with five");

    /** True for the families that need a five or a carry / borrow (the "interesting" ones for practice). */
    val nonDirect: Boolean get() = this != ZHI_JIA && this != ZHI_JIAN && this != SET

    companion object {
        fun byZh(zh: String): Family? = entries.firstOrNull { it.zh == zh }
    }
}

/** One tap: [rod], top layer change [du] or bottom layer change [dl] (exactly one is non-zero). */
data class Step(val rod: Int, val du: Int, val dl: Int) {
    val upper: Boolean get() = du != 0
    val delta: Int get() = if (du != 0) du else dl
}

/** Text of a rhyme in both languages, plus the numbers its "why" template needs. */
data class Rhyme(val zh: String, val en: String)

/**
 * One rhyme applied to one rod. [rod] is the digit's rod; [steps] include the carry / borrow on rods to the left.
 * [digit] and [op] are the digit being added / taken; [left] is the nested rhyme on the left rod (null when it is the
 * plain 一上一 / 一去一).
 */
data class Segment(
    val rod: Int,
    val op: Char,
    val digit: Int,
    val name: Rhyme,
    val family: Family,
    val left: Rhyme?,
    val steps: List<Step>,
) {
    /** Python's `name` incl. the nested left-rod suffix, e.g. 一去九进一（左档：一去九进一）. */
    val fullZh: String get() = name.zh + (left?.let { "（左档：${it.zh}）" } ?: "")
    val fullEn: String get() = name.en + (left?.let { " (left: ${it.en})" } ?: "")
}

/** Engine board: u 0..1, l 0..4 per rod. Mutable, used only while planning. */
class EBoard(val u: IntArray = IntArray(RODS), val l: IntArray = IntArray(RODS)) {
    fun copy() = EBoard(u.copyOf(), l.copyOf())
    fun value(): Long { var v = 0L; var p = 1L; for (i in 0 until RODS) { v += (5 * u[i] + l[i]) * p; p *= 10 }; return v }
    fun d(r: Int) = 5 * u[r] + l[r]
    fun move(r: Int, du: Int, dl: Int) {
        require(r in 0 until RODS) { "rod $r out of range" }
        u[r] += du; l[r] += dl
        check(u[r] in 0..1 && l[r] in 0..4) { "rod $r -> u=${u[r]} l=${l[r]}" }
    }

    companion object {
        fun of(n: Long): EBoard {
            val b = EBoard(); var x = n; var i = 0
            while (x > 0 && i < RODS) { val d = (x % 10).toInt(); b.u[i] = d / 5; b.l[i] = d % 5; x /= 10; i++ }
            require(x == 0L) { "$n does not fit on $RODS rods" }
            return b
        }
    }
}

object Koujue {
    private fun cn(n: Int) = CN[n].toString()
    private fun en(n: Int) = EN_NUM[n]

    private fun split(steps: MutableList<Step>, r: Int, du: Int, dl: Int) {
        if (du != 0) steps += Step(r, du, 0)
        if (dl != 0) steps += Step(r, 0, dl)
    }

    private data class R(val name: Rhyme, val family: Family, val left: Rhyme?)

    /** Python add_digit. */
    private fun addDigit(b: EBoard, r: Int, n: Int, steps: MutableList<Step>): R {
        val l = b.l[r]; val d = b.d(r)
        if (d + n <= 9) {
            if (n < 5) {
                if (l + n <= 4) {
                    b.move(r, 0, n); split(steps, r, 0, n)
                    return R(Rhyme("${cn(n)}上${cn(n)}", "Up ${en(n)}"), Family.ZHI_JIA, null)
                }
                b.move(r, 1, 0); split(steps, r, 1, 0)
                b.move(r, 0, -(5 - n)); split(steps, r, 0, -(5 - n))
                return R(Rhyme("${cn(n)}下五去${cn(5 - n)}", "Down five, off ${en(5 - n)}"), Family.MAN_WU_JIA, null)
            }
            b.move(r, 1, n - 5); split(steps, r, 1, n - 5)
            return R(Rhyme("${cn(n)}上${cn(n)}", "Up ${en(n)}"), Family.ZHI_JIA, null)
        }
        val k = 10 - n
        val name: Rhyme
        val fam: Family
        if (k < 5) {
            if (l >= k) {
                b.move(r, 0, -k); split(steps, r, 0, -k)
                name = Rhyme("${cn(n)}去${cn(k)}进一", "Off ${en(k)}, carry one"); fam = Family.JIN_SHI_JIA
            } else {
                b.move(r, 0, 5 - k); split(steps, r, 0, 5 - k)
                b.move(r, -1, 0); split(steps, r, -1, 0)
                name = Rhyme("${cn(n)}上${cn(5 - k)}去五进一", "Up ${en(5 - k)}, off five, carry one"); fam = Family.PO_WU_JIN_SHI_JIA
            }
        } else if (k == 5) {
            b.move(r, -1, 0); split(steps, r, -1, 0)
            name = Rhyme("五去五进一", "Off five, carry one"); fam = Family.JIN_SHI_JIA
        } else {
            b.move(r, -1, -(k - 5)); split(steps, r, -1, -(k - 5))
            name = Rhyme("${cn(n)}去${cn(k)}进一", "Off ${en(k)}, carry one"); fam = Family.JIN_SHI_JIA
        }
        val sub = addDigit(b, r + 1, 1, steps)
        val subFull = Rhyme(sub.name.zh + (sub.left?.let { "（左档：${it.zh}）" } ?: ""), sub.name.en + (sub.left?.let { " (left: ${it.en})" } ?: ""))
        return R(name, fam, if (subFull.zh == "一上一") null else subFull)
    }

    /** Python sub_digit. */
    private fun subDigit(b: EBoard, r: Int, n: Int, steps: MutableList<Step>): R {
        val l0 = b.l[r]; val d = b.d(r)
        if (d >= n) {
            if (n < 5) {
                if (l0 >= n) {
                    b.move(r, 0, -n); split(steps, r, 0, -n)
                    return R(Rhyme("${cn(n)}去${cn(n)}", "Take ${en(n)}"), Family.ZHI_JIAN, null)
                }
                b.move(r, 0, 5 - n); split(steps, r, 0, 5 - n)
                b.move(r, -1, 0); split(steps, r, -1, 0)
                return R(Rhyme("${cn(n)}上${cn(5 - n)}去五", "Up ${en(5 - n)}, off five"), Family.PO_WU_JIAN, null)
            }
            b.move(r, -1, -(n - 5)); split(steps, r, -1, -(n - 5))
            return R(Rhyme("${cn(n)}去${cn(n)}", "Take ${en(n)}"), Family.ZHI_JIAN, null)
        }
        val k = 10 - n
        val leftR = subDigit(b, r + 1, 1, steps)
        val leftFull = Rhyme(leftR.name.zh + (leftR.left?.let { "（左档：${it.zh}）" } ?: ""), leftR.name.en + (leftR.left?.let { " (left: ${it.en})" } ?: ""))
        val l = b.l[r]
        val name: Rhyme
        val fam: Family
        if (k < 5) {
            if (l + k <= 4) {
                b.move(r, 0, k); split(steps, r, 0, k)
                name = Rhyme("${cn(n)}退一还${cn(k)}", "Borrow one, back ${en(k)}"); fam = Family.TUI_SHI_JIAN
            } else {
                b.move(r, 1, 0); split(steps, r, 1, 0)
                b.move(r, 0, -(5 - k)); split(steps, r, 0, -(5 - k))
                name = Rhyme("${cn(n)}退一还五去${cn(5 - k)}", "Borrow one, back five, off ${en(5 - k)}"); fam = Family.TUI_SHI_HUAN_WU_JIAN
            }
        } else if (k == 5) {
            b.move(r, 1, 0); split(steps, r, 1, 0)
            name = Rhyme("五退一还五", "Borrow one, back five"); fam = Family.TUI_SHI_JIAN
        } else {
            b.move(r, 1, k - 5); split(steps, r, 1, k - 5)
            name = Rhyme("${cn(n)}退一还${cn(k)}", "Borrow one, back ${en(k)}"); fam = Family.TUI_SHI_JIAN
        }
        return R(name, fam, if (leftFull.zh == "一去一") null else leftFull)
    }

    /**
     * Python apply_op: from the highest digit of [n] down, one rhyme per non-zero digit. Mutates [b].
     * Throws when the result does not fit (negative or more than [RODS] rods).
     */
    fun applyOp(b: EBoard, n: Long, op: Char): List<Segment> {
        require(op == '+' || op == '-')
        val ds = digitsOf(n)
        val out = ArrayList<Segment>()
        for (r in ds.indices.reversed()) {
            val digit = ds[r]
            if (digit == 0) continue
            val steps = ArrayList<Step>()
            val res = if (op == '+') addDigit(b, r, digit, steps) else subDigit(b, r, digit, steps)
            out += Segment(r, op, digit, res.name, res.family, res.left, steps)
        }
        return out
    }

    /** Plans for "set the number": rods high to low, each rod's top then bottom move from [from] to [target]. */
    fun setPlan(from: EBoard, target: Long): List<Segment> {
        val to = EBoard.of(target)
        val out = ArrayList<Segment>()
        for (r in (RODS - 1) downTo 0) {
            val du = to.u[r] - from.u[r]
            val dl = to.l[r] - from.l[r]
            if (du == 0 && dl == 0) continue
            val steps = ArrayList<Step>()
            split(steps, r, du, dl)
            val digit = to.d(r)
            out += Segment(r, '=', digit, Rhyme("拨 ${digit}", "Set $digit"), Family.SET, null, steps)
        }
        return out
    }

    fun digitsOf(n: Long): IntArray {
        require(n >= 0)
        val out = ArrayList<Int>(); var x = n
        do { out += (x % 10).toInt(); x /= 10 } while (x > 0)
        return out.toIntArray()
    }

    /** The formula after the rhyme on the problem strip, e.g. `+7 = −3 +10` (digit-level, both languages). */
    fun formula(s: Segment): String {
        val n = s.digit
        return when (s.family) {
            Family.SET -> ""
            Family.ZHI_JIA -> "+$n"
            Family.MAN_WU_JIA -> "+$n = +5 −${5 - n}"
            Family.JIN_SHI_JIA -> "+$n = −${10 - n} +10"
            Family.PO_WU_JIN_SHI_JIA -> "+$n = −${10 - n} +10"
            Family.ZHI_JIAN -> "−$n"
            Family.PO_WU_JIAN -> "−$n = +${5 - n} −5"
            Family.TUI_SHI_JIAN -> "−$n = −10 +${10 - n}"
            Family.TUI_SHI_HUAN_WU_JIAN -> "−$n = −10 +${10 - n}"
        }
    }
}
