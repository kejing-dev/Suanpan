package io.embodit.suanpan.domain

enum class Language { SYSTEM, ZH, EN }
enum class Volume(val gain: Float, val percent: Int) { LOW(0.35f, 35), MID(0.65f, 65), HIGH(1f, 100) }

data class Settings(
    val language: Language = Language.SYSTEM,
    val muted: Boolean = false,
    val volume: Volume = Volume.HIGH,
    val hintDefault: Boolean = true,
    val rodDigits: Boolean = true,
    val reducedMotion: Boolean = false,
    val firstRunDone: Boolean = false,
)

/**
 * Progress of one lesson (design dataModel.progress): [queue] = problem indices still to solve, the head is the
 * current one; skipped problems go to the back and come again at the end; the lesson is passed when the queue is
 * empty. [firstTry] counts problems solved without a skip and without the slow-mo.
 */
data class LessonProgress(
    val queue: List<Int> = (0 until 8).toList(),
    val skipped: Set<Int> = emptySet(),
    val demoSeen: Set<Int> = emptySet(),
    val firstTry: Int = 0,
    val demoViews: Int = 0,
    val elapsedMs: Long = 0,
    val done: Boolean = false,
    val bestMs: Long = 0,
    val started: Boolean = false,
) {
    val current: Int? get() = queue.firstOrNull()
    val solvedCount: Int get() = 8 - queue.size

    fun solved(index: Int): LessonProgress {
        val first = index !in skipped && index !in demoSeen
        val q = queue.filter { it != index }
        return copy(queue = q, firstTry = firstTry + if (first) 1 else 0, done = done || q.isEmpty(), started = true)
    }

    fun skip(index: Int): LessonProgress {
        if (queue.size <= 1) return copy(skipped = skipped + index, started = true)
        return copy(queue = queue.filter { it != index } + index, skipped = skipped + index, started = true)
    }

    fun demo(index: Int): LessonProgress = copy(demoSeen = demoSeen + index, demoViews = demoViews + 1, started = true)

    /** A fresh run of a lesson, keeping the pass mark and best time. */
    fun restart(): LessonProgress = LessonProgress(done = done, bestMs = bestMs)

    fun encode(): String = listOf(
        "q=" + queue.joinToString("."),
        "s=" + skipped.joinToString("."),
        "d=" + demoSeen.joinToString("."),
        "f=$firstTry", "v=$demoViews", "e=$elapsedMs", "x=${if (done) 1 else 0}", "b=$bestMs", "g=${if (started) 1 else 0}",
    ).joinToString(";")

    companion object {
        fun decode(s: String?): LessonProgress {
            if (s.isNullOrBlank()) return LessonProgress()
            val m = s.split(";").mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            if (!m.containsKey("q")) return LessonProgress()
            fun ints(k: String) = m[k].orEmpty().split(".").mapNotNull { it.toIntOrNull() }.filter { it in 0 until 8 }
            return runCatching {
                LessonProgress(
                    queue = ints("q").distinct(),
                    skipped = ints("s").toSet(),
                    demoSeen = ints("d").toSet(),
                    firstTry = m["f"]?.toIntOrNull() ?: 0,
                    demoViews = m["v"]?.toIntOrNull() ?: 0,
                    elapsedMs = m["e"]?.toLongOrNull() ?: 0,
                    done = m["x"] == "1",
                    bestMs = m["b"]?.toLongOrNull() ?: 0,
                    started = m["g"] == "1",
                )
            }.getOrDefault(LessonProgress())
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
