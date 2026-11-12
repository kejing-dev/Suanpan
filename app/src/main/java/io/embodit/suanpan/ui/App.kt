package io.embodit.suanpan.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.embodit.suanpan.domain.BeadRef
import io.embodit.suanpan.domain.Beads
import io.embodit.suanpan.domain.Language
import io.embodit.suanpan.domain.LessonProgress
import io.embodit.suanpan.domain.Lessons
import io.embodit.suanpan.domain.Practice
import io.embodit.suanpan.domain.PracticeType
import io.embodit.suanpan.domain.Problem
import io.embodit.suanpan.domain.RODS
import io.embodit.suanpan.domain.Route
import io.embodit.suanpan.domain.Segment
import io.embodit.suanpan.domain.Settings
import io.embodit.suanpan.domain.Step
import io.embodit.suanpan.domain.TapMove
import io.embodit.suanpan.domain.Taps
import io.embodit.suanpan.domain.Verdict
import io.embodit.suanpan.domain.Volume
import io.embodit.suanpan.domain.WrongRod
import io.embodit.suanpan.platform.AppContext
import io.embodit.suanpan.platform.Sfx
import io.embodit.suanpan.platform.Sounds
import io.embodit.suanpan.platform.SpLog
import io.embodit.suanpan.platform.Store
import kotlin.random.Random

enum class Page { LEARN, PRACTICE, FREE, SETTINGS }
enum class Mode(val key: String) { LEARN("learn"), PRACTICE("practice"), FREE("free") }
enum class LearnView { WELCOME, LESSONS, PROBLEM, DONE }
enum class Confirm { EXIT, CLEAR }

/** How the scene animates the next board change. [rodDelayMs] staggers rods (clear: left to right). */
data class BoardAnim(val serial: Int, val ms: Int, val rodDelayMs: IntArray? = null, val bounce: Boolean = true)

/** The slow-mo in progress: step [index] of [total], the bead the light ring is on. */
data class DemoUi(val index: Int, val total: Int, val step: Step, val segment: Segment, val finger: BeadRef?)

/** The result card (S08). */
data class DoneUi(val lesson: Int, val firstTry: Int, val demoViews: Int, val elapsedMs: Long)

/**
 * Process-wide app state, shared by the volume (abacus) and the control window (design native-window-move §3.4: one
 * store, both containers recompose from it). All writes on the main thread through [Controller].
 */
object App {
    lateinit var store: Store; private set
    lateinit var sounds: Sounds; private set
    val ui = UiState()
    val ctl = Controller()
    @Volatile var initialised = false; private set

    fun init(context: Context) {
        AppContext.ctx = context.applicationContext
        store = Store(context)
        sounds = Sounds(context)
        ctl.load()
        initialised = true
    }
}

class UiState {
    var settings by mutableStateOf(Settings())
    var page by mutableStateOf(Page.LEARN)
    /** The board on the abacus: the last of learn / practice / free that was open (settings keeps it). */
    var boardMode by mutableStateOf(Mode.LEARN)
    var learnView by mutableStateOf(LearnView.LESSONS)
    var learnBoard by mutableStateOf(Beads.EMPTY)
    var practiceBoard by mutableStateOf(Beads.EMPTY)
    var freeBoard by mutableStateOf(Beads.EMPTY)
    var anim by mutableStateOf(BoardAnim(0, 0))

    var lessonId by mutableIntStateOf(1)
    var progress by mutableStateOf(List(10) { LessonProgress() })
    var problem by mutableStateOf<Problem?>(null)
    var problemIndex by mutableIntStateOf(0)
    var practiceProblem by mutableStateOf<Problem?>(null)
    var practiceType by mutableStateOf(PracticeType.MIX)
    var practiceDigits by mutableIntStateOf(2)
    var streak by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)

    var hintOn by mutableStateOf(true)
    var segment by mutableStateOf<Segment?>(null)
    var hintTaps by mutableStateOf<List<TapMove>>(emptyList())
    var offRoute by mutableStateOf(false)
    var wrongRods by mutableStateOf<List<WrongRod>>(emptyList())
    var stuck by mutableStateOf(false)
    var correct by mutableStateOf<Long?>(null)
    var correctSegment by mutableStateOf<Segment?>(null)
    var glowSerial by mutableIntStateOf(0)
    var demo by mutableStateOf<DemoUi?>(null)
    var done by mutableStateOf<DoneUi?>(null)
    var whyOpen by mutableStateOf(false)
    var skippedAgain by mutableStateOf(false)

    var nearHint by mutableStateOf(true)
    /** v0.1.1: the rod whose lower-deck count picker is open, or null. */
    var picker by mutableStateOf<Int?>(null)
    var panelState by mutableStateOf("never")
    var confirm by mutableStateOf<Confirm?>(null)
    var sceneReady by mutableStateOf(false)
    /** Debug: animation speed factor (freeze 0.1 = ten times slower). */
    var speed by mutableStateOf(1f)

    val board: Beads get() = when (boardMode) { Mode.LEARN -> learnBoard; Mode.PRACTICE -> practiceBoard; Mode.FREE -> freeBoard }
    val en: Boolean get() = when (settings.language) {
        Language.EN -> true
        Language.ZH -> false
        Language.SYSTEM -> !java.util.Locale.getDefault().language.startsWith("zh")
    }
    val panelClosed: Boolean get() = panelState == "ON_DESTROY"
    /** Problem judging is live: learn problem view or practice, not during clear / slow-mo / the right-answer glow. */
    val judging: Boolean get() = (boardMode == Mode.LEARN && learnView == LearnView.PROBLEM && page != Page.SETTINGS) || (boardMode == Mode.PRACTICE && page == Page.PRACTICE)
}

/**
 * The app's state machine (design states S00–S16, interactions T1–T16). Real input (board-input taps, window buttons)
 * and the debug receiver call the same methods (build.md: debug commands take the user's path).
 */
class Controller {
    private val ui get() = App.ui
    private val main = Handler(Looper.getMainLooper())
    private var route: Route? = null
    private var practiceRoute: Route? = null
    private val rodLockUntil = LongArray(RODS)
    private var clearing = false
    private var demoToken = 0
    private var lessonStartedMs = 0L
    private var rng = Random(System.currentTimeMillis())

    // ---------------------------------------------------------------- load / save

    fun load() {
        val s = App.store
        ui.settings = s.settings()
        App.sounds.muted = ui.settings.muted
        App.sounds.volume = ui.settings.volume.gain
        ui.progress = s.progress()
        ui.learnBoard = s.board(Mode.LEARN.key)
        ui.practiceBoard = s.board(Mode.PRACTICE.key)
        ui.freeBoard = s.board(Mode.FREE.key)
        ui.lessonId = s.lessonId()
        ui.practiceType = s.practiceType()
        ui.practiceDigits = s.practiceDigits()
        ui.streak = s.streak()
        ui.best = s.best()
        ui.practiceProblem = s.practiceProblem()?.let { runCatching { Problem.parse(it) }.getOrNull() }
        ui.nearHint = true
        ui.page = Page.LEARN
        ui.boardMode = Mode.LEARN
        val lp = ui.progress[ui.lessonId - 1]
        ui.learnView = when {
            !ui.settings.firstRunDone -> LearnView.WELCOME
            s.problemActive() && lp.current != null -> LearnView.PROBLEM   // S16 resume, beads as they were
            else -> LearnView.LESSONS
        }
        if (ui.learnView == LearnView.PROBLEM) {
            ui.problemIndex = lp.current!!
            ui.problem = problemOf(ui.lessonId, ui.problemIndex)
            ui.skippedAgain = ui.problemIndex in lp.skipped
            ui.hintOn = ui.settings.hintDefault
            route = Route(ui.problem!!).also { it.reset(ui.learnBoard) }
            refreshHint()
            lessonStartedMs = SystemClock.elapsedRealtime() - lp.elapsedMs
            restartStuck()
        }
        SpLog.i("STATE load view=${ui.learnView} lesson=${ui.lessonId} board=${ui.learnBoard.value()} first=${!ui.settings.firstRunDone}")
    }

    private fun saveBoards() {
        App.store.saveBoard(Mode.LEARN.key, ui.learnBoard)
        App.store.saveBoard(Mode.PRACTICE.key, ui.practiceBoard)
        App.store.saveBoard(Mode.FREE.key, ui.freeBoard)
        App.store.saveSession(ui.lessonId, ui.learnView == LearnView.PROBLEM)
    }

    private fun saveProgress(n: Int, lp: LessonProgress) {
        ui.progress = ui.progress.toMutableList().also { it[n - 1] = lp }
        App.store.saveProgress(n, lp)
    }

    private fun savePractice() = App.store.savePractice(ui.practiceType, ui.practiceDigits, ui.practiceProblem?.text, ui.streak, ui.best)

    private fun problemOf(lesson: Int, index: Int) = Problem.parse(Lessons.all[lesson - 1].problems[index])

    // ---------------------------------------------------------------- board helpers

    private fun setBoard(mode: Mode, b: Beads, ms: Int, rodDelay: IntArray? = null, bounce: Boolean = true) {
        when (mode) { Mode.LEARN -> ui.learnBoard = b; Mode.PRACTICE -> ui.practiceBoard = b; Mode.FREE -> ui.freeBoard = b }
        val dur = if (ui.settings.reducedMotion) 0 else ms
        ui.anim = BoardAnim(ui.anim.serial + 1, dur, if (ui.settings.reducedMotion) null else rodDelay, bounce && !ui.settings.reducedMotion)
    }

    private fun slideMs(): Int = if (ui.settings.reducedMotion) 0 else ((120 + 60) / ui.speed).toInt()

    // ---------------------------------------------------------------- T1 tap a bead

    /** A pinch / trigger on a bead (board-input) or `debug tap`. */
    /**
     * v0.1.1 two-stage input (profile D17 ≥ 2°, QA #1 q1-1): a pinch / trigger lands on a rod deck. The upper deck has
     * only two useful states, so it flips the five straight away; the lower deck opens the count picker (0–4 beads at
     * the beam) in front of it, a second tap on the same deck closes it, a tap on another deck moves it there.
     */
    fun tapDeck(rod: Int, upper: Boolean, source: String = "user") {
        if (ui.demo != null) { SpLog.i("DECK during demo -> stop"); stopDemo(); return }
        if (ui.correct != null) { SpLog.i("DECK ignored: right-answer glow"); return }
        if (clearing) { SpLog.i("DECK ignored: clearing"); return }
        ui.nearHint = false
        if (upper) {
            ui.picker = null
            val n = if (ui.board.u(rod) == 0) 1 else 0
            SpLog.i("DECK src=$source rod=$rod upper -> u=$n")
            applyCount(rod, true, n, source)
        } else {
            ui.picker = if (ui.picker == rod) null else rod
            SpLog.i("DECK src=$source rod=$rod lower -> picker=${ui.picker}")
        }
    }

    /** A key of the lower-deck picker: [n] beads at the beam on the picked rod. */
    fun pickCount(n: Int, source: String = "user") {
        val rod = ui.picker ?: return
        ui.picker = null
        SpLog.i("PICK src=$source rod=$rod lower n=$n")
        applyCount(rod, false, n.coerceIn(0, 4), source)
    }

    fun closePicker() { if (ui.picker != null) { ui.picker = null; SpLog.i("PICKER closed") } }

    /**
     * The old one-tap bead rule (bead j from the beam: the group up to it comes to / leaves the beam), kept for the debug
     * `tap` / `solve` commands; it goes through the same deck / picker path as a real pinch.
     */
    fun tapBead(ref: BeadRef, source: String = "user") {
        if (Beads.isSpare(ref.upper, ref.j)) { SpLog.i("TAP spare ignored $ref"); return }
        val c = if (ref.upper) ui.board.u(ref.rod) else ui.board.l(ref.rod)
        val n = if (ref.j > c) ref.j else ref.j - 1
        if (ref.upper) { if (n != c) tapDeck(ref.rod, true, source) } else { ui.picker = null; tapDeck(ref.rod, false, source); pickCount(n, source) }
    }

    /** Moves layer ([rod], [upper]) to [n] beads at the beam: the group slides, the rod is locked while it slides. */
    private fun applyCount(rod: Int, upper: Boolean, n: Int, source: String) {
        val now = SystemClock.elapsedRealtime()
        if (now < rodLockUntil[rod]) { SpLog.i("TAP ignored: rod $rod sliding"); return }
        val before = ui.board
        val after = before.with(rod, upper, n)
        if (after == before) return
        val c0 = if (upper) before.u(rod) else before.l(rod)
        val toBeam = n > c0
        val moved = kotlin.math.abs(n - c0)
        rodLockUntil[rod] = now + (180 / ui.speed).toLong()
        setBoard(ui.boardMode, after, (120 / ui.speed).toInt())
        SpLog.i("TAP src=$source rod=$rod ${if (upper) "upper" else "lower"} $c0->$n value=${after.value()}")
        val delay = slideMs().toLong() + 30
        // the beads land at the end of their slide: the click and the judge both wait for it (design animation.settle)
        main.postDelayed({
            App.sounds.play(if (toBeam) Sfx.CLICK else Sfx.DROP, rod, gain = if (moved >= 3) 1f else 0.9f)
            settle()
        }, delay)
    }

    /** The beads came to rest: judge (S04 → S07 / checkpoint / back), save. */
    private fun settle() {
        saveBoards()
        if (!ui.judging || ui.demo != null || clearing || ui.correct != null) return
        val r = currentRoute() ?: return
        val b = ui.board
        val v = r.settle(b)
        SpLog.i("JUDGE value=${b.value()} verdict=$v cp=${r.cpIndex} seg=${r.segIndex} off=${r.offRoute}")
        when (v) {
            Verdict.CORRECT -> onCorrect()
            Verdict.CHECKPOINT, Verdict.PROGRESS -> { ui.stuck = false; restartStuck() }
            else -> {}
        }
        refreshHint()
    }

    private fun currentRoute(): Route? = when (ui.boardMode) {
        Mode.LEARN -> route
        Mode.PRACTICE -> practiceRoute
        Mode.FREE -> null
    }

    private fun refreshHint() {
        val r = currentRoute()
        val b = ui.board
        if (r == null || !ui.judging) {
            ui.segment = null; ui.hintTaps = emptyList(); ui.offRoute = false; ui.wrongRods = emptyList(); return
        }
        ui.segment = r.segment
        ui.offRoute = r.offRoute
        ui.hintTaps = if (ui.hintOn && !r.offRoute && ui.correct == null) r.remainingTaps(b) else emptyList()
        ui.wrongRods = if (ui.hintOn && r.offRoute) r.wrongRods(b) else emptyList()
    }

    // ---------------------------------------------------------------- S07 right

    private fun onCorrect() {
        ui.picker = null
        val r = currentRoute()
        ui.correct = ui.board.value()
        ui.correctSegment = ui.segment ?: r?.segments?.lastOrNull()
        ui.hintTaps = emptyList(); ui.wrongRods = emptyList(); ui.stuck = false
        ui.glowSerial++
        cancelStuck()
        App.sounds.play(Sfx.DING)
        SpLog.i("CORRECT value=${ui.correct} mode=${ui.boardMode}")
        val mode = ui.boardMode
        main.postDelayed({
            ui.correct = null
            if (mode == Mode.LEARN) nextLearnProblem(solved = true) else { ui.streak++; if (ui.streak > ui.best) ui.best = ui.streak; newPracticeProblem(keepStreak = true) }
        }, (1200 / ui.speed).toLong())
    }

    // ---------------------------------------------------------------- learn

    fun startFirstLesson() {
        ui.settings = ui.settings.copy(firstRunDone = true)
        App.store.save(ui.settings)
        openLesson(1)
    }

    fun showLessons() {
        ui.picker = null
        cancelStuck(); stopDemo(quiet = true)
        ui.learnView = LearnView.LESSONS
        ui.problem = null; route = null
        refreshHint(); saveBoards()
        SpLog.i("STATE S02 lessons")
    }

    /** A lesson card / [下一课] / [再练这课]: a passed lesson starts over, one in progress continues. */
    fun openLesson(n: Int, restart: Boolean = false) {
        val lp0 = ui.progress[n - 1]
        val lp = if (restart || lp0.queue.isEmpty()) lp0.restart() else lp0
        if (lp != lp0) saveProgress(n, lp)
        ui.lessonId = n
        lessonStartedMs = SystemClock.elapsedRealtime() - lp.elapsedMs
        ui.learnView = LearnView.PROBLEM
        loadLearnProblem(lp.current ?: 0, fromStart = true)
    }

    private fun loadLearnProblem(index: Int, fromStart: Boolean) {
        ui.picker = null
        val p = problemOf(ui.lessonId, index)
        ui.problemIndex = index
        ui.problem = p
        ui.whyOpen = false
        ui.stuck = false
        ui.hintOn = ui.settings.hintDefault
        ui.skippedAgain = index in ui.progress[ui.lessonId - 1].skipped
        val start = if (p.isSet) Beads.EMPTY else Beads.of(p.start)
        if (fromStart) setBoard(Mode.LEARN, start, (300 / ui.speed).toInt(), bounce = false)
        route = Route(p).also { it.reset(ui.learnBoard) }
        refreshHint()
        restartStuck()
        saveBoards()
        SpLog.i("STATE S03 lesson=${ui.lessonId} problem=${index + 1} '${p.text}' start=${start.value()}")
    }

    private fun nextLearnProblem(solved: Boolean) {
        val n = ui.lessonId
        var lp = ui.progress[n - 1]
        lp = if (solved) lp.solved(ui.problemIndex) else lp.skip(ui.problemIndex)
        lp = lp.copy(elapsedMs = SystemClock.elapsedRealtime() - lessonStartedMs)
        if (lp.queue.isEmpty()) {
            val best = if (lp.bestMs == 0L || lp.elapsedMs < lp.bestMs) lp.elapsedMs else lp.bestMs
            lp = lp.copy(bestMs = best, done = true)
            saveProgress(n, lp)
            ui.done = DoneUi(n, lp.firstTry, lp.demoViews, lp.elapsedMs)
            ui.learnView = LearnView.DONE
            route = null; ui.problem = null
            refreshHint(); cancelStuck(); saveBoards()
            App.sounds.lessonPassed()
            SpLog.i("STATE S08 lesson=$n firstTry=${lp.firstTry} demos=${lp.demoViews} time=${lp.elapsedMs}")
            return
        }
        saveProgress(n, lp)
        loadLearnProblem(lp.current!!, fromStart = true)
    }

    /** [跳过这题]: no penalty, it comes again at the end of the lesson. */
    fun skip() {
        if (ui.learnView != LearnView.PROBLEM || ui.correct != null) return
        stopDemo(quiet = true)
        SpLog.i("SKIP lesson=${ui.lessonId} problem=${ui.problemIndex + 1}")
        nextLearnProblem(solved = false)
    }

    fun toggleWhy() { ui.whyOpen = !ui.whyOpen }

    // ---------------------------------------------------------------- hint / stuck

    fun setHint(on: Boolean) { ui.hintOn = on; refreshHint(); SpLog.i("HINT $on") }

    private val stuckRunnable = Runnable {
        if (ui.judging && ui.demo == null && ui.correct == null) { ui.stuck = true; SpLog.i("STATE S09 stuck") }
    }

    private fun restartStuck() { main.removeCallbacks(stuckRunnable); main.postDelayed(stuckRunnable, 25_000L) }
    private fun cancelStuck() { main.removeCallbacks(stuckRunnable) }

    fun forceStuck() { main.removeCallbacks(stuckRunnable); stuckRunnable.run() }

    // ---------------------------------------------------------------- T6 clear

    fun clear() {
        ui.picker = null
        if (clearing || ui.correct != null) return
        stopDemo(quiet = true)
        clearing = true
        val mode = ui.boardMode
        val delays = IntArray(RODS) { rod -> ((RODS - 1 - rod) * 25 / ui.speed).toInt() }   // left (rod 8) first
        setBoard(mode, Beads.EMPTY, (200 / ui.speed).toInt(), delays, bounce = false)
        App.sounds.play(Sfx.CLEAR)
        SpLog.i("STATE S12 clear mode=$mode")
        main.postDelayed({
            clearing = false
            val r = currentRoute()
            if (r != null && ui.judging) {
                // lessons / practice: put this step's start back
                val start = r.opStart
                setBoard(mode, start, (300 / ui.speed).toInt(), bounce = false)
                r.reset(start)
                restartStuck()
            }
            refreshHint(); saveBoards()
        }, ((200 + 8 * 25) / ui.speed).toLong() + 60)
    }

    // ---------------------------------------------------------------- T5 slow-mo

    fun demo() {
        ui.picker = null
        val r = currentRoute() ?: return
        if (ui.correct != null || clearing) return
        if (ui.demo != null) { stopDemo(); return }
        val steps = r.demoSteps()
        if (steps.isEmpty()) return
        cancelStuck()
        ui.stuck = false
        val mode = ui.boardMode
        if (mode == Mode.LEARN) saveProgress(ui.lessonId, ui.progress[ui.lessonId - 1].demo(ui.problemIndex))
        val token = ++demoToken
        val start = r.opStart
        val sp = ui.speed
        SpLog.i("STATE S06 demo steps=${steps.size} from=${start.value()}")
        // 300 ms back to this step's start (also from a half-done board)
        setBoard(mode, start, (300 / sp).toInt(), bounce = false)
        ui.demo = DemoUi(0, steps.size, steps[0].second, steps[0].first, null)
        refreshHint()
        var t = (300 / sp).toLong()
        var b = start
        steps.forEachIndexed { i, (seg, st) ->
            val cur = if (st.upper) b.u(st.rod) else b.l(st.rod)
            val tm = Taps.between(st.rod, st.upper, cur, cur + st.delta)!!
            val before = b
            b = b.apply(st)
            val after = b
            main.postDelayed({ if (token == demoToken) ui.demo = DemoUi(i, steps.size, st, seg, tm.tap) }, t)
            t += (400 / sp).toLong()                                    // the light ring flies to the bead
            main.postDelayed({
                if (token != demoToken) return@postDelayed
                setBoard(mode, after, (300 / sp).toInt(), bounce = false)  // 0.4x speed slide
                App.sounds.play(Sfx.TICK, st.rod)
                SpLog.i("DEMO step ${i + 1}/${steps.size} rod=${st.rod} ${if (st.upper) "upper" else "lower"} ${st.delta} ${before.value()}->${after.value()}")
            }, t)
            t += (300 / sp).toLong() + (400 / sp).toLong()            // slide, then read the strip
        }
        t += (600 / sp).toLong()
        main.postDelayed({
            if (token != demoToken) return@postDelayed
            setBoard(mode, start, (300 / sp).toInt(), bounce = false)
            r.reset(start)
            ui.demo = null
            refreshHint(); restartStuck(); saveBoards()
            SpLog.i("STATE S06 -> S03 demo finished, back to ${start.value()}")
        }, t)
    }

    /** [停] / a tap during the slow-mo: back to this step's start, your turn. */
    fun stopDemo(quiet: Boolean = false) {
        if (ui.demo == null) return
        demoToken++
        val r = currentRoute()
        ui.demo = null
        if (r != null && !quiet) {
            setBoard(ui.boardMode, r.opStart, (300 / ui.speed).toInt(), bounce = false)
            r.reset(r.opStart)
        }
        refreshHint(); restartStuck(); saveBoards()
        SpLog.i("DEMO stopped")
    }

    // ---------------------------------------------------------------- practice

    fun setPracticeType(t: PracticeType) { if (t != ui.practiceType) { ui.practiceType = t; newPracticeProblem(keepStreak = false) } }
    fun setPracticeDigits(d: Int) { if (d != ui.practiceDigits) { ui.practiceDigits = d; newPracticeProblem(keepStreak = false) } }

    fun seed(n: Long) { rng = Random(n); SpLog.i("SEED $n") }

    /** [换一题] breaks the streak; a right answer keeps it. */
    fun newPracticeProblem(keepStreak: Boolean) {
        ui.picker = null
        stopDemo(quiet = true)
        if (!keepStreak) ui.streak = 0
        val p = Practice.generate(rng, ui.practiceType, ui.practiceDigits)
        ui.practiceProblem = p
        ui.hintOn = ui.settings.hintDefault
        setBoard(Mode.PRACTICE, Beads.of(p.start), (300 / ui.speed).toInt(), bounce = false)
        practiceRoute = Route(p).also { it.reset(ui.practiceBoard) }
        refreshHint(); restartStuck(); savePractice(); saveBoards()
        SpLog.i("STATE S10 practice ${ui.practiceType} ${ui.practiceDigits} '${p.text}' streak=${ui.streak}")
    }

    private fun ensurePractice() {
        val p = ui.practiceProblem
        if (p == null) { newPracticeProblem(keepStreak = true); return }
        if (practiceRoute == null) practiceRoute = Route(p).also { it.reset(ui.practiceBoard) }
    }

    // ---------------------------------------------------------------- T10 pages

    fun setPage(p: Page) {
        ui.picker = null
        if (p == ui.page) return
        stopDemo(quiet = true)
        ui.page = p
        when (p) {
            Page.LEARN -> ui.boardMode = Mode.LEARN
            Page.PRACTICE -> { ui.boardMode = Mode.PRACTICE; ensurePractice() }
            Page.FREE -> ui.boardMode = Mode.FREE
            Page.SETTINGS -> {}
        }
        ui.confirm = null
        ui.anim = BoardAnim(ui.anim.serial + 1, 0)   // design: switch boards directly, no slide
        if (ui.judging) restartStuck() else cancelStuck()
        refreshHint()
        SpLog.i("PAGE $p board=${ui.boardMode} value=${ui.board.value()}")
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(s: Settings) {
        ui.settings = s
        App.sounds.muted = s.muted
        App.sounds.volume = s.volume.gain
        App.store.save(s)
        refreshHint()
        SpLog.i("SETTINGS $s")
    }

    fun setMuted(m: Boolean) = updateSettings(ui.settings.copy(muted = m))
    fun setVolume(v: Volume) = updateSettings(ui.settings.copy(volume = v, muted = false))
    fun setLanguage(l: Language) = updateSettings(ui.settings.copy(language = l))

    fun askConfirm(c: Confirm?) { ui.confirm = c; SpLog.i("CONFIRM $c") }

    /** [清掉] in S14: every lesson, the learn and practice boards, streaks. */
    fun clearProgress() {
        App.store.clearProgress()
        ui.progress = List(10) { LessonProgress() }
        ui.streak = 0; ui.best = 0
        ui.practiceProblem = null; practiceRoute = null
        route = null; ui.problem = null
        ui.lessonId = 1
        ui.learnBoard = Beads.EMPTY; ui.practiceBoard = Beads.EMPTY
        ui.confirm = null
        ui.page = Page.LEARN; ui.boardMode = Mode.LEARN
        ui.learnView = LearnView.LESSONS
        ui.anim = BoardAnim(ui.anim.serial + 1, 0)
        refreshHint(); cancelStuck(); saveBoards()
        SpLog.i("STATE S14 -> S02 progress cleared")
    }

    // ---------------------------------------------------------------- debug-only shortcuts that still take the user's path

    /** `debug set N`: the board of the current page shows N (free page, or a lesson board before a tap). */
    fun debugSet(n: Long) { setBoard(ui.boardMode, Beads.of(n), 300, bounce = false); main.postDelayed({ settle() }, 360) }

    /** `debug solve [reverse]`: tap the remaining rhymes bead by bead through [tapBead]. */
    fun debugSolve(reverse: Boolean) {
        val r = currentRoute() ?: return
        val taps = ArrayList<BeadRef>()
        var b = ui.board
        val steps = r.demoSteps()
        // replay from the current board: only the remaining taps of each rhyme, in rhyme order (or reversed inside a rhyme)
        val bySeg = steps.groupBy { it.first }
        var sim = r.opStart
        for ((_, list) in bySeg) {
            val segTaps = ArrayList<BeadRef>()
            for ((_, st) in list) {
                val cur = if (st.upper) sim.u(st.rod) else sim.l(st.rod)
                val tm = Taps.between(st.rod, st.upper, cur, cur + st.delta)!!
                segTaps += tm.tap
                sim = sim.apply(st)
            }
            taps += if (reverse) segTaps.reversed() else segTaps
        }
        if (b != r.opStart) { setBoard(ui.boardMode, r.opStart, 0); r.reset(r.opStart); b = r.opStart }
        taps.forEachIndexed { i, t -> main.postDelayed({ tapBead(t, "debug-solve") }, 450L * (i + 1)) }
        SpLog.i("DEBUG solve taps=${taps.size} reverse=$reverse")
    }

    fun debugLessonDone(n: Int) {
        ui.lessonId = n
        val lp = LessonProgress(queue = emptyList(), firstTry = 6, demoViews = 2, elapsedMs = 220_000, done = true, bestMs = 220_000, started = true)
        saveProgress(n, lp)
        ui.page = Page.LEARN; ui.boardMode = Mode.LEARN
        ui.done = DoneUi(n, lp.firstTry, lp.demoViews, lp.elapsedMs)
        ui.learnView = LearnView.DONE
        route = null; ui.problem = null; refreshHint(); cancelStuck()
    }

    fun debugLesson(n: Int, problem: Int?) {
        ui.page = Page.LEARN; ui.boardMode = Mode.LEARN
        if (problem == null) { openLesson(n); return }
        ui.lessonId = n
        val lp0 = ui.progress[n - 1].restart()
        val idx = (problem - 1).coerceIn(0, 7)
        val lp = lp0.copy(queue = listOf(idx) + (0 until 8).filter { it != idx }, started = true)
        saveProgress(n, lp)
        lessonStartedMs = SystemClock.elapsedRealtime()
        ui.learnView = LearnView.PROBLEM
        loadLearnProblem(idx, fromStart = true)
    }

    fun debugView(v: LearnView) { ui.page = Page.LEARN; ui.boardMode = Mode.LEARN; ui.learnView = v; if (v != LearnView.PROBLEM) { route = null; ui.problem = null }; refreshHint() }

    fun dump(): String = "page=${ui.page} board=${ui.boardMode} view=${ui.learnView} lesson=${ui.lessonId} problem=${ui.problemIndex + 1} " +
        "'${ui.problem?.text}' value=${ui.board.value()} beads=${ui.board.encode()} hint=${ui.hintOn} off=${ui.offRoute} taps=${ui.hintTaps.size} " +
        "wrong=${ui.wrongRods} stuck=${ui.stuck} demo=${ui.demo?.index} correct=${ui.correct} near=${ui.nearHint} panel=${ui.panelState} " +
        "practice='${ui.practiceProblem?.text}' streak=${ui.streak}/${ui.best} settings=${ui.settings}"
}
