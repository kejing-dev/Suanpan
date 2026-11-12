package io.embodit.suanpan

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import io.embodit.suanpan.ui.panel.PanelWindow
import io.embodit.suanpan.ui.panel.Windows
import io.embodit.suanpan.ui.theme.SpTheme
import io.embodit.suanpan.ui.volume.VolumeScreen

/**
 * Skeleton A (design container): the abacus in the default Volumetric container (0.74 × 0.62 × 0.40 m, manifest) and
 * the control window, a Planar WindowContainer 560 × 600 dp placed by the system to the volume's right
 * (Placement.right with extra spacing, design spatialLayout.controlWindow). Opening, reopening (after ×, or
 * [重开控制窗]) always uses the same placement: the window comes back beside the abacus, never on top of it (D44).
 */
fun mainApp(scope: SpatialAppScope) = with(scope) {
    DefaultWindowContainer { SpTheme { VolumeScreen() } }
    WindowContainer(
        Windows.PANEL,
        form = Form.Planar,
        defaultSize = WindowContainerSize(width = 560.dp, height = 600.dp),
        placement = { containers.firstOrNull { it.id == Windows.VOLUME }?.let { Placement.right(it, Windows.OFFSET_DP.dp) } ?: Placement.none() },
    ) {
        SpTheme { PanelWindow(Modifier.windowConstraints(minWidth = 520.dp, minHeight = 520.dp, maxWidth = 760.dp, maxHeight = 640.dp)) }
    }
}
