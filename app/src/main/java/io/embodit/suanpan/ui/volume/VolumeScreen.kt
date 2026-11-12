package io.embodit.suanpan.ui.volume

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.geometry.Offset3D
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.Material
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.suanpan.R
import io.embodit.suanpan.domain.Explain
import io.embodit.suanpan.domain.Problem
import io.embodit.suanpan.domain.RODS
import io.embodit.suanpan.platform.SpLog
import io.embodit.suanpan.ui.App
import io.embodit.suanpan.ui.LearnView
import io.embodit.suanpan.ui.Mode
import io.embodit.suanpan.ui.Page
import io.embodit.suanpan.ui.UiState
import io.embodit.suanpan.ui.common.fits
import io.embodit.suanpan.ui.input.BoardInputListener
import io.embodit.suanpan.ui.input.InputSource
import io.embodit.suanpan.ui.input.boardInput
import io.embodit.suanpan.ui.panel.Windows
import io.embodit.suanpan.ui.theme.SpColors
import io.embodit.suanpan.ui.theme.SpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val STRIP = "strip"
private const val DIGITS = "digits"
private const val PICKER = "picker"

/**
 * The volume: the abacus (ECS, [AbacusScene]) and two display-only strips above it (problem strip, digit strip;
 * AttachmentPanels facing +Z, no buttons, they take no rays). Opens the control window to its right at launch and
 * closes it with itself (D44 exit: both containers).
 */
@Composable
fun VolumeScreen() {
    val ui = App.ui
    val navigator = LocalSpatialNavigator.current
    val scene = remember { AbacusScene() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var rebindTick by remember { mutableIntStateOf(0) }
    var panelsLogged by remember { mutableStateOf(false) }
    LaunchedEffect(rebindTick) { if (rebindTick in 1..40) { delay(250); rebindTick++ } }
    val converter = LocalPhysicalLengthConverter.current
    val mPerDp = remember(converter) { converter.dpToLength(100.dp, LengthUnit.Meters) / 100f }

    DisposableEffect(scene) {
        AbacusSceneHolder.scene = scene
        registerSystem<AbacusFrameSystem>()
        Windows.navigator = navigator
        navigator.openWindowContainer(Windows.PANEL, Windows.PANEL_TAG)
        SpLog.i("WINDOW open panel at launch; attachment 1 dp = ${"%.5f".format(mPerDp)} m")
        onDispose {
            unregisterSystem<AbacusFrameSystem>()
            if (AbacusSceneHolder.scene === scene) AbacusSceneHolder.scene = null
            scene.dispose()
            runCatching { navigator.closeWindowContainer(Windows.PANEL, Windows.PANEL_TAG) }
        }
    }

    // reopen = close, wait for ON_DESTROY, open again (native-window-move §3.5: open right after close is swallowed)
    LaunchedEffect(ui.panelState) { Windows.onPanelState(ui.panelState) }

    LaunchedEffect(scene) {
        if (scene.built) return@LaunchedEffect
        val res = withContext(NonCancellable + Dispatchers.Default) { SceneResources() }
        scene.provide(res)
    }

    val listener = remember {
        object : BoardInputListener<AbacusTarget> {
            override fun onTap(target: AbacusTarget?, source: InputSource, position: Offset3D?) {
                when (target) {
                    is AbacusTarget.Deck -> App.ctl.tapDeck(target.rod, target.upper, source.label)
                    AbacusTarget.Frame -> if (App.ui.panelClosed) { SpLog.i("FRAME tap -> reopen panel"); Windows.open("frame") }
                    null -> {}
                }
            }

            /**
             * Shared Space does not hand gaze / ray hover to the app (template README, 九路围棋 v0.1.6 headset): the system
             * HoverEffect draws on the bead mesh under each holder. Kept for when the SDK delivers it; hint only, no state.
             */
            override fun onHover(target: AbacusTarget?, source: InputSource) {}
        }
    }
    io.embodit.suanpan.ui.input.DebugInput.listener = listener

    val digitsW = (AbacusLayout.DIGITS_SIZE.x / mPerDp).dp
    val digitsH = (AbacusLayout.DIGITS_SIZE.y / mPerDp).dp
    val stripW = (AbacusLayout.STRIP_SIZE.x / mPerDp).dp
    val stripH = (AbacusLayout.STRIP_SIZE.y / mPerDp).dp

    SpatialView(
        modifier = Modifier.fillMaxSize().onSizeChanged { viewSize = it }.boardInput(AbacusTargets, listener),
        attachments = {
            // each attachment is its own composition: give it the app-language theme again (else system language)
            AttachmentPanel(STRIP) { SpTheme { Refreshing(stripKey(ui)) { ProblemStrip(ui, stripW, stripH, mPerDp) } } }
            AttachmentPanel(DIGITS) { SpTheme { Refreshing(digitsKey(ui)) { DigitStrip(ui, digitsW, digitsH, mPerDp) } } }
            AttachmentPanel(PICKER) { SpTheme { Refreshing(listOf(ui.picker, ui.board.encode(), ui.hintTaps, ui.settings.language)) { CountPicker(ui, mPerDp) } } }
        },
        initial = { content, _ ->
            content.addEntity(scene.root)
            val models = Models()
            models.load()
            scene.install(models)
        },
        update = { content, attachments ->
            @Suppress("UNUSED_EXPRESSION") rebindTick
            if (viewSize != IntSize.Zero) {
                val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
                scene.setRegion(abs(corner.y))
            }
            scene.apply(SceneInput(ui.board, ui.anim, ui.hintTaps, ui.demo?.finger, ui.glowSerial, ui.speed))
            val stripE = attachments.entity(STRIP); val digitsE = attachments.entity(DIGITS); val pickerE = attachments.entity(PICKER)
            if (stripE == null || digitsE == null || pickerE == null) {
                SpLog.i("PANEL attachments not bound yet strip=${stripE != null} digits=${digitsE != null}")
                rebindTick++
            } else {
                // an attachment entity is not in the scene until it hangs under one of ours (pruning-class PanelRig)
                if (!panelsLogged) {
                    panelsLogged = true
                    scene.root.addChild(stripE); scene.root.addChild(digitsE); scene.root.addChild(pickerE)
                    SpLog.i("PANEL strips bound under abacusRoot")
                }
                stripE.tf().setPosition(AbacusLayout.STRIP_CENTER.v())
                // hidden by moving it away, never by enabled = false (a panel disabled once may not come back, factory finding)
                val on = ui.settings.rodDigits
                digitsE.tf().setPosition(if (on) AbacusLayout.DIGITS_CENTER.v() else Vector3(0f, -40f, 0f))
                digitsE.tf().setScaleVector(if (on) Vector3(1f, 1f, 1f) else Vector3(0.001f, 0.001f, 0.001f))
                // the lower-deck picker floats in front of the picked rod's lower deck, upright, facing +Z
                val pr = ui.picker
                pickerE.tf().setPosition(if (pr != null) AbacusLayout.faceToVolume(AbacusLayout.pickerFace(pr)).v() else Vector3(0f, -40f, 0f))
                pickerE.tf().setScaleVector(if (pr != null) Vector3(1f, 1f, 1f) else Vector3(0.001f, 0.001f, 0.001f))
            }
        },
    )
}

/** The idle emulator can keep a half-drawn panel frame: re-compose 1.5 / 4 / 8 s after each change (factory finding). */
@Composable
private fun Refreshing(change: Any?, content: @Composable () -> Unit) {
    var gen by remember { mutableIntStateOf(0) }
    LaunchedEffect(change) { for (w in longArrayOf(1_500, 2_500, 4_000)) { delay(w); gen++ } }
    key(gen) { content() }
}

private fun stripKey(ui: UiState): Any = listOf(ui.settings.language, ui.page, ui.learnView, ui.problem?.text, ui.practiceProblem?.text, ui.segment, ui.offRoute, ui.stuck, ui.correct, ui.demo?.index, ui.nearHint, ui.panelState, ui.hintOn, ui.board.value())
private fun digitsKey(ui: UiState): Any = listOf(ui.board.encode(), ui.wrongRods, ui.settings.language)

/** Lines of the problem strip for the current state (design states[].scene, typography.strip). */
@Composable
internal fun stripLines(ui: UiState): Pair<String, String> {
    val en = ui.en
    val demo = ui.demo
    fun problemLine(p: Problem?) = p?.display(en) ?: ""
    val activeProblem = when (ui.boardMode) { Mode.LEARN -> ui.problem; Mode.PRACTICE -> ui.practiceProblem; Mode.FREE -> null }
    return when {
        demo != null -> problemLine(activeProblem) to Explain.step(demo.index, demo.step, en)
        ui.correct != null -> stringResource(R.string.strip_correct, Problem.fmt(ui.correct!!)) to (ui.correctSegment?.let { Explain.rhyme(it, en) } ?: "")
        ui.panelClosed -> stringResource(R.string.strip_closed) to ""
        ui.nearHint -> stringResource(R.string.strip_near1) to stringResource(R.string.strip_near2)
        ui.page == Page.SETTINGS -> stringResource(R.string.strip_free, Problem.fmt(ui.board.value())) to stringResource(R.string.strip_settings)
        ui.boardMode == Mode.FREE -> stringResource(R.string.strip_free, Problem.fmt(ui.board.value())) to stringResource(R.string.free_line)
        ui.boardMode == Mode.LEARN && ui.learnView == LearnView.WELCOME -> stringResource(R.string.strip_try) to ""
        ui.boardMode == Mode.LEARN && ui.learnView == LearnView.LESSONS -> stringResource(R.string.strip_pick) to ""
        ui.boardMode == Mode.LEARN && ui.learnView == LearnView.DONE -> stringResource(R.string.strip_passed, ui.done?.lesson ?: ui.lessonId) to ""
        else -> {
            val second = when {
                ui.stuck -> stringResource(R.string.strip_stuck)
                ui.hintOn && ui.offRoute && ui.wrongRods.isNotEmpty() ->
                    stringResource(R.string.strip_wrong, ui.wrongRods.reversed().joinToString(" · ") { "${Explain.place(it.rod, en)} ${it.now}→${it.target}" })
                ui.hintOn && activeProblem?.isSet == true -> stringResource(R.string.set_hint)
                ui.hintOn && ui.segment != null -> Explain.line(ui.segment!!, en)
                ui.boardMode == Mode.LEARN -> stringResource(R.string.strip_problem_n, ui.problemIndex + 1)
                else -> stringResource(R.string.streak, ui.streak, ui.best)
            }
            problemLine(activeProblem) to second
        }
    }
}

/** Rough width of [text] in ems: CJK / full-width 1.0, digits and Latin 0.56 (for one-line fitting on the strips). */
internal fun ems(text: String): Float = text.sumOf { c -> (if (c.code >= 0x2E80) 1.0 else if (c == ' ') 0.3 else 0.56) }.toFloat()

@Composable
private fun ProblemStrip(ui: UiState, w: Dp, h: Dp, mPerDp: Float) {
    val (l1, l2) = stripLines(ui)
    // design typography.strip: 32 mm and 20 mm letters (em ≈ letter height / 0.72); a long line shrinks to fit one
    // line of the strip's inner width instead of wrapping or being cut (D13)
    val inner = AbacusLayout.STRIP_SIZE.x - 0.03f
    val em1 = minOf(0.032f / 0.72f, inner / ems(l1).coerceAtLeast(1f))
    val em2 = minOf(0.020f / 0.72f, inner / ems(l2).coerceAtLeast(1f))
    val big = (em1 / mPerDp).sp
    val small = (em2 / mPerDp).sp
    Box(Modifier.size(w, h).clip(RoundedCornerShape((0.02f / mPerDp).dp)).backgroundMaterial(enable = true, style = Material.Regular), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = (0.012f / mPerDp).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(l1, style = TextStyle(fontSize = big, lineHeight = big * 1.1f, fontWeight = FontWeight.SemiBold, color = PicoTheme.colorScheme.labelPrimary, fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false, onTextLayout = fits("strip/line1", l1))
            if (l2.isNotEmpty()) Text(l2, style = TextStyle(fontSize = small, lineHeight = small * 1.15f, fontWeight = FontWeight.Medium, color = PicoTheme.colorScheme.labelPrimary, fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false, onTextLayout = fits("strip/line2", l2))
        }
    }
}

/** Nine cells over the rods (rod 8 left … rod 0 right); leading zeros hidden; rods still wrong show "now→target" in gold. */
@Composable
private fun DigitStrip(ui: UiState, w: Dp, h: Dp, mPerDp: Float) {
    val b = ui.board
    val lead = (RODS - 1 downTo 0).firstOrNull { b.digit(it) != 0 } ?: 0
    val wrong = ui.wrongRods.associateBy { it.rod }
    // 26 mm em (digits ≈ 19 mm) on the 35 mm strip; the design's 26 mm letters do not fit its own 35 mm cell height
    val size = (0.026f / mPerDp).sp
    Row(Modifier.size(w, h).clip(RoundedCornerShape((0.01f / mPerDp).dp)).backgroundMaterial(enable = true, style = Material.Thin), verticalAlignment = Alignment.CenterVertically) {
        for (rod in RODS - 1 downTo 0) {
            val wr = wrong[rod]
            val text = when {
                wr != null -> "${wr.now}→${wr.target}"
                rod > lead -> ""
                else -> b.digit(rod).toString()
            }
            // a rod still wrong: ink on a warm-gold chip (≈ 12 : 1; gold text on the glass was only ≈ 2.3 : 1)
            val color = if (wr != null) SpColors.Ink else PicoTheme.colorScheme.labelPrimary
            val chip = if (wr != null) Modifier.padding(2.dp).clip(RoundedCornerShape(6.dp)).background(SpColors.Glow) else Modifier
            Box(Modifier.width(w / RODS).fillMaxSize().then(chip), contentAlignment = Alignment.Center) {
                Text(text, style = TextStyle(fontSize = if (wr != null) size * 0.62f else size, lineHeight = if (wr != null) size * 0.7f else size * 1.08f, fontWeight = FontWeight.Bold, color = color, fontFeatureSettings = "tnum"),
                    maxLines = 1, softWrap = false, onTextLayout = fits("digits/$rod", text))
            }
        }
    }
}


/** The count the hint wants on [rod]'s lower deck, or null (no hint for that deck). */
internal fun hintedCount(ui: UiState, rod: Int): Int? = ui.hintTaps.firstOrNull { it.tap.rod == rod && !it.tap.upper }?.let { if (it.toBeam) it.tap.j else it.tap.j - 1 }

/**
 * v0.1.1 lower-deck picker (two-stage input, profile D17 ≥ 2°): "how many beads at the beam" 0–4, five SpatialUI keys of
 * 6.8 cm with 2.2 cm between them, in front of the picked rod. The current count is drawn as the selected key, the
 * count the hint wants carries a warm-gold rim. A key press goes to Controller.pickCount (debug: `press pick:<n>`).
 */
@Composable
private fun CountPicker(ui: UiState, mPerDp: Float) {
    val rod = ui.picker
    val w = (AbacusLayout.PICKER_W / mPerDp).dp
    val h = (AbacusLayout.PICKER_H / mPerDp).dp
    val key = (AbacusLayout.KEY / mPerDp).dp
    val gap = (AbacusLayout.KEY_GAP / mPerDp).dp
    Box(Modifier.size(w, h).clip(RoundedCornerShape((0.02f / mPerDp).dp)).backgroundMaterial(enable = true, style = Material.Regular), contentAlignment = Alignment.Center) {
        if (rod == null) return@Box
        val cur = ui.board.l(rod)
        val want = if (ui.hintOn) hintedCount(ui, rod) else null
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val title = stringResource(R.string.picker_title, Explain.place(rod, ui.en))
            Text(title, style = TextStyle(fontSize = (0.016f / 0.72f / mPerDp).sp, fontWeight = FontWeight.SemiBold, color = PicoTheme.colorScheme.labelPrimary),
                maxLines = 1, softWrap = false, onTextLayout = fits("picker/title", title))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (n in 0..4) {
                    val press = { io.embodit.suanpan.platform.SpLog.i("PRESS pick:$n"); App.ctl.pickCount(n, "picker") }
                    io.embodit.suanpan.ui.common.Presses.actions["pick:$n"] = press
                    val selected = n == cur
                    val rim = if (n == want) Modifier.border(3.dp, SpColors.Glow, RoundedCornerShape(14.dp)) else Modifier
                    com.pico.spatial.ui.design.Button(
                        onClick = press,
                        modifier = Modifier.size(key, key).then(rim),
                        size = com.pico.spatial.ui.design.ButtonDefaults.Regular.copy(height = key, minWidth = key),
                        shape = RoundedCornerShape(14.dp),
                        colors = com.pico.spatial.ui.design.ButtonDefaults.buttonColors(
                            containerColor = if (selected) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.fillTertiary,
                            contentColor = if (selected) androidx.compose.ui.graphics.Color.White else PicoTheme.colorScheme.labelPrimary,
                        ),
                    ) {
                        Text("$n", style = TextStyle(fontSize = (0.034f / 0.72f / mPerDp).sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum",
                            color = if (selected) androidx.compose.ui.graphics.Color.White else PicoTheme.colorScheme.labelPrimary),
                            maxLines = 1, softWrap = false, onTextLayout = fits("picker/key", "$n"))
                    }
                }
            }
        }
    }
}
