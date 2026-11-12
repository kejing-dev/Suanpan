package io.embodit.suanpan.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import io.embodit.suanpan.platform.SpLog
import java.util.Collections

private val seen = Collections.synchronizedSet(HashSet<String>())

/**
 * Overflow check of a fixed-line text (D13): logs `OVERFLOW <where>/<text>` once when it does not fit, so QA runs
 * every state in the longest language and reads the log instead of hunting for clipped words. No ellipsis anywhere.
 */
fun fits(where: String, text: String): (TextLayoutResult) -> Unit = { r ->
    if (r.hasVisualOverflow && seen.add("$where/$text")) SpLog.i("OVERFLOW $where/${text.take(48)}")
}

/** Debug: names of pressable controls, so `debug press <name>` takes the same onClick as a pinch. */
object Presses {
    val actions = HashMap<String, () -> Unit>()
}

/**
 * A worded SpatialUI button, 48 dp (B19) or 56 dp for the main one; [primary] = interaction fill with ink text.
 * Disabled = 45 % and presses ignored (legible, unlike the stock disabled colours).
 */
@Composable
fun WordButton(
    name: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    minWidth: Dp = 96.dp,
    icon: Int? = null,
) {
    val press = { if (enabled) { SpLog.i("PRESS $name"); onClick() } }
    Presses.actions[name] = press
    // primary = an ink capsule with white text: white on the interaction blue measured 2.5–3.5 : 1 on the emulator
    // glass (theoretical max ≈ 4 : 1), below D13's 4.5 : 1; ink / white is > 15 : 1
    val container = if (primary) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.fillTertiary
    val content = if (primary) Color.White else PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = press,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f).heightIn(min = height),
        size = ButtonDefaults.Regular.copy(height = height, minWidth = minWidth),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = content, style = PicoTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false,
            textAlign = TextAlign.Center, onTextLayout = fits("button/$name", text))
    }
}

/** A row of evenly weighted buttons. */
@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** SpatialUI segment control inside one page (an option switch, not page switching), 52 dp high (design panels.buttons). */
@Composable
fun Segments(name: String, items: List<String>, selected: Int, onSelect: (Int) -> Unit, width: Dp, height: Dp = 52.dp) {
    val itemW = width / items.size
    SegmentControl(modifier = Modifier.width(width).height(height), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        items.forEachIndexed { i, label ->
            val press = { SpLog.i("PRESS $name:$i"); onSelect(i) }
            Presses.actions["$name:$i"] = press
            SegmentItem(
                selected = selected == i,
                onClick = press,
                modifier = Modifier.width(itemW).height(height),
                textStyle = PicoTheme.typography.labelLarge,
                colors = SegmentControlDefaults.colors(
                    itemBackgroundColor = Color.Transparent,
                    itemContentColor = PicoTheme.colorScheme.labelPrimary,
                    selectedItemBackgroundColor = PicoTheme.colorScheme.labelPrimary,   // QA #1 K1: ink pill + white text (light-blue pill was 3.6–4.4 : 1)
                    selectedItemContentColor = Color.White,
                ),
                title = { Text(label, maxLines = 1, softWrap = false, style = PicoTheme.typography.labelLarge, onTextLayout = fits("seg/$name/$i", label)) },
            )
        }
    }
}
