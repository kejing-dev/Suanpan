package io.embodit.suanpan.platform

import android.content.Context
import io.embodit.suanpan.domain.Beads
import io.embodit.suanpan.domain.Language
import io.embodit.suanpan.domain.LessonProgress
import io.embodit.suanpan.domain.PracticeType
import io.embodit.suanpan.domain.Settings
import io.embodit.suanpan.domain.Volume

/**
 * Everything the app keeps, on this device only (design dataModel.storage): the three boards, lesson progress,
 * the learn session (lesson / page / view), practice settings and streaks, and the settings. SharedPreferences
 * `apply()` writes atomically in the background; it is called every time the beads settle.
 */
class Store(context: Context) {
    private val p = context.getSharedPreferences("suanpan", Context.MODE_PRIVATE)

    fun settings(): Settings = Settings(
        language = runCatching { Language.valueOf(p.getString("lang", "SYSTEM")!!) }.getOrDefault(Language.SYSTEM),
        muted = p.getBoolean("muted", false),
        volume = runCatching { Volume.valueOf(p.getString("volume", "HIGH")!!) }.getOrDefault(Volume.HIGH),
        hintDefault = p.getBoolean("hintDefault", true),
        rodDigits = p.getBoolean("rodDigits", true),
        reducedMotion = p.getBoolean("reduced", false),
        firstRunDone = p.getBoolean("firstRunDone", false),
    )

    fun save(s: Settings) {
        p.edit().putString("lang", s.language.name).putBoolean("muted", s.muted).putString("volume", s.volume.name)
            .putBoolean("hintDefault", s.hintDefault).putBoolean("rodDigits", s.rodDigits).putBoolean("reduced", s.reducedMotion)
            .putBoolean("firstRunDone", s.firstRunDone).apply()
    }

    fun board(mode: String): Beads = Beads.decode(p.getString("board.$mode", null)) ?: Beads.EMPTY
    fun saveBoard(mode: String, b: Beads) { p.edit().putString("board.$mode", b.encode()).apply() }

    fun progress(): List<LessonProgress> = (1..10).map { LessonProgress.decode(p.getString("lesson.$it", null)) }
    fun saveProgress(n: Int, lp: LessonProgress) { p.edit().putString("lesson.$n", lp.encode()).apply() }

    /** The learn session: lesson number (1..10) and whether a problem was on the abacus (S16 resume). */
    fun lessonId(): Int = p.getInt("lessonId", 1).coerceIn(1, 10)
    fun problemActive(): Boolean = p.getBoolean("problemActive", false)
    fun saveSession(lessonId: Int, problemActive: Boolean) { p.edit().putInt("lessonId", lessonId).putBoolean("problemActive", problemActive).apply() }

    fun practiceType(): PracticeType = runCatching { PracticeType.valueOf(p.getString("pType", "MIX")!!) }.getOrDefault(PracticeType.MIX)
    fun practiceDigits(): Int = p.getInt("pDigits", 2).coerceIn(1, 3)
    fun practiceProblem(): String? = p.getString("pProblem", null)
    fun best(): Int = p.getInt("best", 0)
    fun streak(): Int = p.getInt("streak", 0)
    fun savePractice(type: PracticeType, digits: Int, problem: String?, streak: Int, best: Int) {
        p.edit().putString("pType", type.name).putInt("pDigits", digits).putString("pProblem", problem).putInt("streak", streak).putInt("best", best).apply()
    }

    fun clearProgress() {
        val e = p.edit()
        for (n in 1..10) e.remove("lesson.$n")
        e.remove("lessonId").remove("problemActive").remove("board.learn").remove("board.practice").remove("pProblem").remove("streak").remove("best").apply()
    }
}
