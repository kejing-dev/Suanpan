package io.embodit.suanpan.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.embodit.suanpan.domain.BeadRef
import io.embodit.suanpan.domain.Language
import io.embodit.suanpan.domain.PracticeType
import io.embodit.suanpan.domain.Volume
import io.embodit.suanpan.platform.SpLog
import io.embodit.suanpan.ui.App
import io.embodit.suanpan.ui.Confirm
import io.embodit.suanpan.ui.LearnView
import io.embodit.suanpan.ui.Page
import io.embodit.suanpan.ui.common.Presses
import io.embodit.suanpan.ui.input.DebugInput
import io.embodit.suanpan.ui.input.InputSource
import io.embodit.suanpan.ui.input.resolveUp
import io.embodit.suanpan.ui.panel.Windows
import io.embodit.suanpan.ui.volume.AbacusSceneHolder
import io.embodit.suanpan.ui.volume.AbacusTargets

/**
 * Debug builds only (design verification.debugHooks). Every command takes the user's path (Controller methods, the
 * board-input listener, the window buttons' own onClick):
 *
 *   adb shell am broadcast -a io.embodit.suanpan.DEBUG -p io.embodit.suanpan --es cmd "lesson 7 problem 5"
 *
 * goto S01|S02|S03|S08|S10|S11|S13|S14exit|S14clear|S15 · lesson <1-10> [problem <1-8>] · set <N> · tap <rod 0-8> <upper|lower> <1-5>
 * replay <rod> <upper|lower> <j> [hand-ray|eye-hand|controller] (real scene entities -> resolveUp -> listener.onTap)
 * solve [reverse] · hint on|off · demo · stop · clear · stuck · skip · lessondone <n> · practice <add|sub|mix> <1-3>
 * seed N · free [set N] · tab learn|practice|free|settings · lang system|zh|en · mute on|off · volume low|mid|high
 * digits on|off · reduced on|off · freeze <speed> · closepanel · reopen · press <button name> · why · state | dump · beads
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val line = intent.getStringExtra("cmd")?.trim().orEmpty()
        val a = line.split(Regex("\\s+"))
        val ctl = App.ctl
        val ui = App.ui
        SpLog.i("DEBUG cmd='$line'")
        fun on(i: Int) = a.getOrNull(i) in setOf("on", "1", "true")
        runCatching {
            when (a[0]) {
                "goto" -> when (a.getOrNull(1)) {
                    "S01" -> { ctl.updateSettings(ui.settings.copy(firstRunDone = false)); ctl.debugView(LearnView.WELCOME) }
                    "S02" -> { ctl.setPage(Page.LEARN); ctl.showLessons() }
                    "S03" -> ctl.debugLesson(7, 5)
                    "S08" -> ctl.debugLessonDone(4)
                    "S10" -> ctl.setPage(Page.PRACTICE)
                    "S11" -> ctl.setPage(Page.FREE)
                    "S13" -> ctl.setPage(Page.SETTINGS)
                    "S14exit" -> { ctl.setPage(Page.SETTINGS); ctl.askConfirm(Confirm.EXIT) }
                    "S14clear" -> { ctl.setPage(Page.SETTINGS); ctl.askConfirm(Confirm.CLEAR) }
                    "S15" -> Windows.navigator?.closeWindowContainer(Windows.PANEL, Windows.PANEL_TAG)
                    else -> SpLog.i("DEBUG goto: S01 S02 S03 S08 S10 S11 S13 S14exit S14clear S15")
                }
                "lesson" -> ctl.debugLesson(a[1].toInt(), if (a.getOrNull(2) == "problem") a[3].toInt() else a.getOrNull(2)?.toIntOrNull())
                "set" -> ctl.debugSet(a[1].toLong())
                "tap" -> ctl.tapBead(BeadRef(a[1].toInt(), a[2] == "upper", a[3].toInt()), "debug")
                "replay" -> replay(BeadRef(a[1].toInt(), a[2] == "upper", a[3].toInt()), a.getOrNull(4) ?: "hand-ray")
                "solve" -> ctl.debugSolve(a.getOrNull(1) == "reverse")
                "hint" -> ctl.setHint(on(1))
                "demo" -> ctl.demo()
                "stop" -> ctl.stopDemo()
                "clear" -> ctl.clear()
                "stuck" -> ctl.forceStuck()
                "skip" -> ctl.skip()
                "lessondone" -> ctl.debugLessonDone(a[1].toInt())
                "practice" -> {
                    ctl.setPage(Page.PRACTICE)
                    a.getOrNull(1)?.let { ctl.setPracticeType(PracticeType.valueOf(it.uppercase())) }
                    a.getOrNull(2)?.toIntOrNull()?.let { ctl.setPracticeDigits(it) }
                }
                "seed" -> { ctl.seed(a[1].toLong()); if (ui.page == Page.PRACTICE) ctl.newPracticeProblem(keepStreak = false) }
                "free" -> { ctl.setPage(Page.FREE); if (a.getOrNull(1) == "set") ctl.debugSet(a[2].toLong()) }
                "tab" -> ctl.setPage(Page.valueOf(a[1].uppercase()))
                "lang" -> ctl.setLanguage(Language.valueOf(a[1].uppercase()))
                "mute" -> ctl.setMuted(on(1))
                "volume" -> ctl.setVolume(Volume.valueOf(a[1].uppercase()))
                "digits" -> ctl.updateSettings(ui.settings.copy(rodDigits = on(1)))
                "reduced" -> ctl.updateSettings(ui.settings.copy(reducedMotion = on(1)))
                "freeze" -> { ui.speed = a.getOrNull(1)?.toFloatOrNull()?.coerceIn(0.02f, 1f) ?: 1f; SpLog.i("DEBUG speed=${ui.speed}") }
                "closepanel" -> Windows.navigator?.closeWindowContainer(Windows.PANEL, Windows.PANEL_TAG)
                "reopen" -> Windows.reopen()
                "press" -> { val f = Presses.actions[a[1]]; if (f != null) f() else SpLog.i("DEBUG press: no button '${a[1]}' (known: ${Presses.actions.keys.sorted()})") }
                "why" -> ctl.toggleWhy()
                "near" -> ui.nearHint = on(1)
                "state", "dump" -> SpLog.i("STATE ${ctl.dump()}")
                "beads" -> SpLog.i(AbacusSceneHolder.scene?.dumpBeads() ?: "no scene")
                else -> SpLog.i("DEBUG unknown '$line'")
            }
        }.onFailure { SpLog.e("DEBUG '$line' failed: $it", it) }
    }

    /** Collects the real entities a ray can land on for [ref], resolves each like a hit, then taps with the deepest. */
    private fun replay(ref: BeadRef, src: String) {
        val scene = AbacusSceneHolder.scene ?: return
        val ents = scene.beadEntities(ref)
        var last: io.embodit.suanpan.ui.volume.AbacusTarget? = null
        for (e in ents) {
            val (hit, path) = resolveUp(e, AbacusTargets)
            SpLog.i("REPLAY src=$src path=$path -> target=$hit")
            last = hit
        }
        val source = InputSource.entries.firstOrNull { it.label == src } ?: InputSource.HAND_RAY
        DebugInput.listener?.onTap(last, source, null)
    }
}
