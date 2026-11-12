package io.embodit.suanpan.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.TabBar
import com.pico.spatial.ui.platform.ViewPoint
import io.embodit.suanpan.BuildConfig
import io.embodit.suanpan.R
import io.embodit.suanpan.domain.Explain
import io.embodit.suanpan.domain.Language
import io.embodit.suanpan.domain.Lessons
import io.embodit.suanpan.domain.PracticeType
import io.embodit.suanpan.domain.Problem
import io.embodit.suanpan.domain.Volume
import io.embodit.suanpan.domain.formatDuration
import io.embodit.suanpan.platform.SpLog
import io.embodit.suanpan.ui.App
import io.embodit.suanpan.ui.Confirm
import io.embodit.suanpan.ui.LearnView
import io.embodit.suanpan.ui.Page
import io.embodit.suanpan.ui.UiState
import io.embodit.suanpan.ui.common.ButtonRow
import io.embodit.suanpan.ui.common.Presses
import io.embodit.suanpan.ui.common.Segments
import io.embodit.suanpan.ui.common.WordButton
import io.embodit.suanpan.ui.common.fits
import kotlin.math.roundToInt

/** Text roles (PicoTheme typography, three tiers: big problem / body / small print). */
private object T {
    val page: TextStyle @Composable get() = PicoTheme.typography.titleMedium.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
    val big: TextStyle @Composable get() = PicoTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val huge: TextStyle @Composable get() = PicoTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val body: TextStyle @Composable get() = PicoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    val label: TextStyle @Composable get() = PicoTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val small: TextStyle @Composable get() = PicoTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
}

/**
 * The control window (design panels[panel]): one Planar system window, pages switched by the native SpatialUI TabBar
 * only (D8; the TabBar shows icons only on the emulator, so every page's first line is its name). Moving, closing and
 * minimising are the system's controls under the window (D21); no drag code here.
 */
@Composable
fun PanelWindow(modifier: Modifier) {
    val ui = App.ui
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_CREATE || e == Lifecycle.Event.ON_RESUME || e == Lifecycle.Event.ON_STOP || e == Lifecycle.Event.ON_DESTROY) {
                ui.panelState = e.name; SpLog.i("WINDOW panel ${e.name}")
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); ui.panelState = "ON_DESTROY"; SpLog.i("WINDOW panel disposed") }
    }
    // tab labels resolved here in the app language, then handed to the TabBar (its own system window follows the
    // system language otherwise: gate tabbar-lang)
    val labels = mapOf(
        Page.LEARN to stringResource(R.string.tab_learn), Page.PRACTICE to stringResource(R.string.tab_practice),
        Page.FREE to stringResource(R.string.tab_free), Page.SETTINGS to stringResource(R.string.tab_settings),
    )
    val icons = mapOf(Page.LEARN to R.drawable.ic_school, Page.PRACTICE to R.drawable.ic_shuffle, Page.FREE to R.drawable.ic_touch_app, Page.SETTINGS to R.drawable.ic_settings)

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (ui.page) {
                Page.LEARN -> LearnPage(ui, labels.getValue(Page.LEARN))
                Page.PRACTICE -> PracticePage(ui, labels.getValue(Page.PRACTICE))
                Page.FREE -> FreePage(ui, labels.getValue(Page.FREE))
                Page.SETTINGS -> SettingsPage(ui, labels.getValue(Page.SETTINGS))
            }
        }
    }
    TabBar(followViewpoints = setOf(ViewPoint.Front, ViewPoint.Left)) {
        Page.entries.forEach { p ->
            val label = labels.getValue(p)
            Presses.actions["tab:${p.name.lowercase()}"] = { App.ctl.setPage(p) }
            item(
                selected = ui.page == p,
                onClick = { SpLog.i("PRESS tab:${p.name.lowercase()}"); App.ctl.setPage(p) },
                mainContent = { Icon(painter = painterResource(icons.getValue(p)), contentDescription = label) },
                supportContent = { Text(label) },
            )
        }
    }
}

@Composable
private fun PageTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = T.page, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f), maxLines = 1, onTextLayout = fits("title", text))
        // S00: the abacus is still being built (beads in three frames); the page is already there, its buttons wait
        if (!App.ui.sceneReady) {
            CircularProgressIndicator(modifier = Modifier.width(28.dp).height(28.dp), color = PicoTheme.colorScheme.labelPrimary, progressSize = CircularProgressDefaults.circleProgressSize(28.dp))
            Text(stringResource(R.string.loading), style = T.small, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("title/loading", "l"))
        }
        trailing?.invoke()
    }
}

@Composable
private fun StepRow(icon: Int, text: String) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(44.dp).height(44.dp).clip(CircleShape).background(PicoTheme.colorScheme.fillTertiary), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.width(26.dp).height(26.dp), tint = PicoTheme.colorScheme.labelPrimary)
        }
        Text(text, style = T.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, modifier = Modifier.weight(1f), onTextLayout = fits("step", text))
    }
}

@Composable
private fun BodyText(text: String, dim: Boolean = false, center: Boolean = false, maxLines: Int = 3) {
    Text(text, style = T.body, color = if (dim) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelPrimary,
        textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth(), maxLines = maxLines, onTextLayout = fits("body", text))
}

@Composable
private fun MuteButton(ui: UiState, modifier: Modifier = Modifier) {
    val muted = ui.settings.muted
    WordButton("mute", stringResource(if (muted) R.string.unmute else R.string.mute), { App.ctl.setMuted(!muted) }, modifier,
        icon = if (muted) R.drawable.ic_sound_off else R.drawable.ic_sound)
}

// ------------------------------------------------------------------------------------------- learn

@Composable
private fun ColumnScope.LearnPage(ui: UiState, title: String) {
    when (ui.learnView) {
        LearnView.WELCOME -> Welcome(ui, title)
        LearnView.LESSONS -> LessonList(ui, title)
        LearnView.PROBLEM -> ProblemView(ui)
        LearnView.DONE -> DoneView(ui, title)
    }
}

@Composable
private fun ColumnScope.Welcome(ui: UiState, title: String) {
    PageTitle(title)
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
        Text(Lessons.welcome.get(ui.en), style = T.label.copy(fontSize = 24.sp, lineHeight = 33.sp), color = PicoTheme.colorScheme.labelPrimary, maxLines = 4, onTextLayout = fits("welcome", "body"))
        StepRow(R.drawable.ic_menu_book, stringResource(R.string.welcome_step1))
        StepRow(R.drawable.ic_touch_app, stringResource(R.string.welcome_step2))
        StepRow(R.drawable.ic_lightbulb, stringResource(R.string.welcome_step3))
        BodyText(stringResource(R.string.welcome_far), dim = true)
        WordButton("start_lesson1", stringResource(R.string.start_lesson1), { App.ctl.startFirstLesson() }, Modifier.fillMaxWidth(), primary = true, height = 56.dp, icon = R.drawable.ic_play_arrow, enabled = ui.sceneReady)
    }
}

@Composable
private fun ColumnScope.LessonList(ui: UiState, title: String) {
    PageTitle(title)
    val resume = ui.progress.withIndex().firstOrNull { (i, p) -> p.started && !p.done && p.queue.isNotEmpty() && i + 1 == ui.lessonId }
        ?: ui.progress.withIndex().firstOrNull { (_, p) -> p.started && !p.done && p.queue.isNotEmpty() }
    if (resume != null) WordButton("resume", stringResource(R.string.lessons_resume, resume.index + 1), { App.ctl.openLesson(resume.index + 1) }, Modifier.fillMaxWidth(), primary = true, icon = R.drawable.ic_play_arrow)
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Lessons.all.forEachIndexed { i, L ->
            val lp = ui.progress[i]
            val meta = when {
                lp.done -> stringResource(R.string.lesson_meta_done)
                lp.started && lp.queue.isNotEmpty() -> stringResource(R.string.lesson_meta_on, lp.solvedCount + 1)
                else -> stringResource(R.string.lesson_meta_todo)
            }
            LessonCard("lesson:${i + 1}", "${L.n}. ${L.name.get(ui.en)}", "${L.term.get(ui.en)} · $meta", lp.done) { App.ctl.openLesson(i + 1) }
        }
    }
}

@Composable
private fun LessonCard(name: String, title: String, sub: String, done: Boolean, onClick: () -> Unit) {
    val press = { SpLog.i("PRESS $name"); onClick() }
    Presses.actions[name] = press
    com.pico.spatial.ui.design.Button(
        onClick = press,
        modifier = Modifier.fillMaxWidth().height(68.dp),
        size = com.pico.spatial.ui.design.ButtonDefaults.Regular.copy(height = 68.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = com.pico.spatial.ui.design.ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = T.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, softWrap = false, onTextLayout = fits("lesson/title", title))
            Text(sub, style = T.small, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, softWrap = false, onTextLayout = fits("lesson/sub", sub))
        }
        if (done) Icon(painter = painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = PicoTheme.colorScheme.labelPrimary)
    }
}

@Composable
private fun ColumnScope.ProblemView(ui: UiState) {
    val L = Lessons.all[ui.lessonId - 1]
    val p = ui.problem ?: return
    val lp = ui.progress[ui.lessonId - 1]
    val count = stringResource(R.string.problem_count, ui.problemIndex + 1)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WordButton("back_lessons", stringResource(R.string.back_lessons), { App.ctl.showLessons() }, minWidth = 88.dp, enabled = ui.demo == null)
        val head = "${L.n}. ${L.name.get(ui.en)} · ${L.term.get(ui.en)} · $count"
        Text(head, style = T.small, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, modifier = Modifier.weight(1f), onTextLayout = fits("problem/head", head))
    }
    BodyText(L.goal.get(ui.en), dim = true, maxLines = 2)
    ProblemBlock(ui, p, Modifier.weight(1f))
    LessonButtons(ui)
}

/** Big problem + rhyme chip + why (or the slow-mo status / right answer / stuck line); shared by learn and practice. */
@Composable
private fun ProblemBlock(ui: UiState, p: Problem, modifier: Modifier) {
    val demo = ui.demo
    val seg = ui.segment
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            ui.correct != null -> {
                val t = stringResource(R.string.correct, Problem.fmt(ui.correct!!))
                Text("✓ $t", style = T.big, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("problem/correct", t))
                ui.correctSegment?.let { BodyText(Explain.rhyme(it, ui.en), center = true) }
            }
            else -> {
                val t = p.display(ui.en)
                Text(t, style = T.big, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("problem/text", t))
                if (demo != null) {
                    val st = stringResource(R.string.demo_status, demo.index + 1, demo.total, Explain.step(demo.index, demo.step, ui.en).drop(2))
                    Text(st, style = T.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("problem/demo", st))
                } else if (ui.stuck) {
                    BodyText(stringResource(R.string.stuck), center = true)
                } else if (ui.skippedAgain && ui.boardMode == io.embodit.suanpan.ui.Mode.LEARN) {
                    BodyText(stringResource(R.string.skipped_again), dim = true, center = true)
                }
                if (ui.hintOn && seg != null && demo == null && !p.isSet) {
                    val chip = Explain.line(seg, ui.en)
                    Text(chip, style = T.label, color = PicoTheme.colorScheme.labelPrimary, textAlign = TextAlign.Center, maxLines = 2, onTextLayout = fits("problem/chip", chip))
                    WordButton("why", stringResource(if (ui.whyOpen) R.string.why_hide else R.string.why_show), { App.ctl.toggleWhy() }, minWidth = 120.dp)
                    if (ui.whyOpen) BodyText(Explain.why(seg, ui.en), center = true, maxLines = 4)
                } else if (ui.hintOn && p.isSet && demo == null) {
                    BodyText(stringResource(R.string.set_hint), center = true)
                } else if (!ui.hintOn && demo == null) {
                    // hint off: the lesson's one-line motto (practice: the generic reminder), so the page does not go empty
                    val motto = if (ui.boardMode == io.embodit.suanpan.ui.Mode.LEARN) Lessons.all[ui.lessonId - 1].tagline.get(ui.en) else stringResource(R.string.set_hint)
                    Text(motto, style = T.label, color = PicoTheme.colorScheme.labelPrimary, textAlign = TextAlign.Center, maxLines = 2, onTextLayout = fits("problem/motto", motto))
                }
            }
        }
    }
}

@Composable
private fun LessonButtons(ui: UiState) {
    val demo = ui.demo != null
    val busy = ui.correct != null
    ButtonRow {
        WordButton("hint", stringResource(if (ui.hintOn) R.string.hint_on else R.string.hint_off), { App.ctl.setHint(!ui.hintOn) }, Modifier.weight(1f), enabled = !demo && !busy, icon = R.drawable.ic_lightbulb)
        if (demo) WordButton("stop", stringResource(R.string.stop), { App.ctl.stopDemo() }, Modifier.weight(1.4f), primary = true, icon = R.drawable.ic_stop)
        else WordButton("demo", stringResource(R.string.demo), { App.ctl.demo() }, Modifier.weight(1.4f).alpha(if (ui.stuck) breathe() else 1f), primary = ui.stuck, enabled = !busy, icon = R.drawable.ic_slow)
    }
    ButtonRow {
        WordButton("clear", stringResource(R.string.clear), { App.ctl.clear() }, Modifier.weight(1f), enabled = !demo && !busy, icon = R.drawable.ic_restart_alt)
        if (ui.page == Page.LEARN) WordButton("skip", stringResource(R.string.skip), { App.ctl.skip() }, Modifier.weight(1.2f), enabled = !demo && !busy, icon = R.drawable.ic_skip_next)
        else WordButton("new_problem", stringResource(R.string.new_problem), { App.ctl.newPracticeProblem(keepStreak = false) }, Modifier.weight(1.2f), enabled = !demo && !busy, icon = R.drawable.ic_refresh)
        MuteButton(ui, Modifier.weight(1f))
    }
}

/** The slow-mo button breathes twice when stuck (S09). */
@Composable
private fun breathe(): Float {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "stuck")
    val a by t.animateFloat(0.55f, 1f, androidx.compose.animation.core.infiniteRepeatable<Float>(androidx.compose.animation.core.tween<Float>(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    return a
}

@Composable
private fun ColumnScope.DoneView(ui: UiState, title: String) {
    val d = ui.done ?: return
    val L = Lessons.all[d.lesson - 1]
    PageTitle(title)
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✓ " + stringResource(R.string.done_title), style = T.big, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("done/title", "t"))
        Text("${L.n}. ${L.name.get(ui.en)}", style = T.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("done/name", L.name.en))
        BodyText(stringResource(R.string.done_stats, d.firstTry, d.demoViews, formatDuration(d.elapsedMs)), center = true)
        BodyText(L.goal.get(ui.en), dim = true, center = true)
        ButtonRow {
            if (d.lesson < 10) WordButton("next_lesson", stringResource(R.string.next_lesson), { App.ctl.openLesson(d.lesson + 1) }, Modifier.weight(1f), primary = true, icon = R.drawable.ic_play_arrow)
            WordButton("again", stringResource(R.string.again), { App.ctl.openLesson(d.lesson, restart = true) }, Modifier.weight(1f), icon = R.drawable.ic_refresh)
            WordButton("to_lessons", stringResource(R.string.to_lessons), { App.ctl.showLessons() }, Modifier.weight(1f), icon = R.drawable.ic_menu_book)
        }
        Text(stringResource(R.string.source_note), style = T.small, color = PicoTheme.colorScheme.labelPrimary, textAlign = TextAlign.Center, maxLines = 2, onTextLayout = fits("done/source", "s"))
    }
}

// ------------------------------------------------------------------------------------------- practice

@Composable
private fun ColumnScope.PracticePage(ui: UiState, title: String) {
    PageTitle(title)
    val types = listOf(stringResource(R.string.type_add), stringResource(R.string.type_sub), stringResource(R.string.type_mix))
    val digits = listOf(stringResource(R.string.digits_1), stringResource(R.string.digits_2), stringResource(R.string.digits_3))
    Segments("ptype", types, ui.practiceType.ordinal, { App.ctl.setPracticeType(PracticeType.entries[it]) }, 504.dp)
    Segments("pdigits", digits, ui.practiceDigits - 1, { App.ctl.setPracticeDigits(it + 1) }, 504.dp)
    val streak = stringResource(R.string.streak, ui.streak, ui.best)
    Text(streak, style = T.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, onTextLayout = fits("practice/streak", streak))
    val p = ui.practiceProblem
    if (p != null) ProblemBlock(ui, p, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    LessonButtons(ui)
}

// ------------------------------------------------------------------------------------------- free

@Composable
private fun ColumnScope.FreePage(ui: UiState, title: String) {
    PageTitle(title)
    val b = ui.freeBoard
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        val v = Problem.fmt(b.value())
        Text(v, style = T.huge, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("free/value", v))
        BodyText(stringResource(R.string.free_line), center = true)
        // rod by rod: the place name over each rod's digit, like reading the abacus aloud
        Text(stringResource(R.string.free_rods), style = T.small, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("free/rods", "r"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (rod in io.embodit.suanpan.domain.RODS - 1 downTo 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(54.dp)) {
                    val place = Explain.place(rod, ui.en)
                    Text(if (ui.en) placeShort(rod) else place.take(2), style = T.small, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("free/place", place))
                    Text(b.digit(rod).toString(), style = T.big.copy(fontSize = 30.sp, lineHeight = 36.sp), color = if (b.digit(rod) == 0) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("free/digit", "d"))
                }
            }
        }
    }
    ButtonRow {
        WordButton("clear", stringResource(R.string.clear), { App.ctl.clear() }, Modifier.weight(1f), icon = R.drawable.ic_restart_alt)
        MuteButton(ui, Modifier.weight(1f))
    }
}

private fun placeShort(rod: Int) = arrayOf("1", "10", "100", "1k", "10k", "100k", "1M", "10M", "100M")[rod]

// ------------------------------------------------------------------------------------------- settings

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = T.body, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false, onTextLayout = fits("settings/label", label))
        content()
    }
}

@Composable
private fun SettingSwitch(name: String, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Presses.actions[name] = { onChange(!checked) }
    SettingRow(label) { Switch(checked = checked, onCheckedChange = { SpLog.i("PRESS $name=$it"); onChange(it) }) }
}

@Composable
private fun ColumnScope.SettingsPage(ui: UiState, title: String) {
    val s = ui.settings
    val confirm = ui.confirm
    if (confirm != null) { ConfirmBlock(confirm, title); return }
    PageTitle(title)
    // ① language
    SettingRow(stringResource(R.string.set_language)) {
        Segments("lang", listOf(stringResource(R.string.lang_system), stringResource(R.string.lang_zh), stringResource(R.string.lang_en)), s.language.ordinal,
            { App.ctl.setLanguage(Language.entries[it]) }, 300.dp, 48.dp)
    }
    // ② sound: on / off + volume, three detents + percent, one row
    SettingRow(stringResource(R.string.set_sound)) {
        Presses.actions["sound"] = { App.ctl.setMuted(!s.muted) }
        Switch(checked = !s.muted, onCheckedChange = { SpLog.i("PRESS sound=$it"); App.ctl.setMuted(!it) })
        val idx = s.volume.ordinal
        Slider(
            value = idx / 2f,
            onValueChange = { x -> val v = Volume.entries[(x * 2).roundToInt().coerceIn(0, 2)]; if (v != s.volume || s.muted) App.ctl.setVolume(v) },
            valueRange = 0f..1f,
            modifier = Modifier.width(150.dp).height(48.dp),
        )
        val pct = "${s.volume.percent}%"
        Text(pct, style = T.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(52.dp), maxLines = 1, onTextLayout = fits("settings/pct", pct))
    }
    // ③ – ⑤
    SettingSwitch("hint_default", stringResource(R.string.set_hint_default), s.hintDefault) { App.ctl.updateSettings(s.copy(hintDefault = it)) }
    SettingSwitch("rod_digits", stringResource(R.string.set_rod_digits), s.rodDigits) { App.ctl.updateSettings(s.copy(rodDigits = it)) }
    SettingSwitch("reduced", stringResource(R.string.set_reduced), s.reducedMotion) { App.ctl.updateSettings(s.copy(reducedMotion = it)) }
    // ⑥ reopen the window (beside the abacus) + reset progress
    ButtonRow {
        WordButton("reopen", stringResource(R.string.reopen_panel), { Windows.reopen() }, Modifier.weight(1f), icon = R.drawable.ic_open_in_new)
        WordButton("clear_progress", stringResource(R.string.clear_progress), { App.ctl.askConfirm(Confirm.CLEAR) }, Modifier.weight(1f), icon = R.drawable.ic_refresh)
    }
    // ⑦ about
    val about = stringResource(R.string.about, BuildConfig.VERSION_NAME)
    Text(about, style = T.small, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, modifier = Modifier.fillMaxWidth(), onTextLayout = fits("settings/about", about))
    Spacer(Modifier.weight(1f))
    // ⑧ quit, last
    WordButton("exit", stringResource(R.string.exit_app), { App.ctl.askConfirm(Confirm.EXIT) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_logout)
}

/** S14: the confirmation stays in this window, on this page (no second panel). */
@Composable
private fun ColumnScope.ConfirmBlock(c: Confirm, title: String) {
    PageTitle(title)
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        val q = stringResource(if (c == Confirm.EXIT) R.string.exit_q else R.string.clear_q)
        Text(q, style = T.big.copy(fontSize = 34.sp, lineHeight = 42.sp), color = PicoTheme.colorScheme.labelPrimary, textAlign = TextAlign.Center, maxLines = 2, onTextLayout = fits("confirm/q", q))
        BodyText(stringResource(if (c == Confirm.EXIT) R.string.exit_body else R.string.clear_body), center = true)
        val ui = App.ui
        val keep = if (c == Confirm.EXIT) stringResource(R.string.exit_keep, ui.lessonId, ui.progress[ui.lessonId - 1].solvedCount)
            else stringResource(R.string.clear_keep, ui.progress.count { it.done }, ui.best)
        Text(keep, style = T.label, color = PicoTheme.colorScheme.labelPrimary, textAlign = TextAlign.Center, maxLines = 2, onTextLayout = fits("confirm/keep", keep))
        ButtonRow {
            WordButton("cancel", stringResource(R.string.cancel), { App.ctl.askConfirm(null) }, Modifier.weight(1f), height = 64.dp)
            if (c == Confirm.EXIT) WordButton("exit_yes", stringResource(R.string.exit_yes), { Windows.exitApp() }, Modifier.weight(1f), primary = true, height = 64.dp)
            else WordButton("clear_yes", stringResource(R.string.clear_yes), { App.ctl.clearProgress() }, Modifier.weight(1f), primary = true, height = 64.dp)
        }
    }
}
