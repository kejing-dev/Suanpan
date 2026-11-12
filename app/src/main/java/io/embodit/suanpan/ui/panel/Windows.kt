package io.embodit.suanpan.ui.panel

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import io.embodit.suanpan.platform.AppContext
import io.embodit.suanpan.platform.SpLog
import io.embodit.suanpan.ui.App
import kotlin.system.exitProcess

/**
 * The two window containers. The control window is reopened with the same id + tag ("reuse"), so it always comes
 * back to the right of the abacus through mainApp's placement (D44 correction: Shared Space reopens beside the
 * volume, never "in front of you").
 */
object Windows {
    const val VOLUME = "SuanpanAbacus"
    const val PANEL = "SuanpanPanel"
    const val PANEL_TAG = "panel"
    /**
     * Extra spacing of Placement.right (dp). Design: the window's left edge 0.284 m outside the volume so it clears the
     * abacus by >= 10° from both 0.85 m and 1.75 m; the dp→m factor of a Planar window is measured on the emulator
     * (ui-audit), this is the value used.
     */
    const val OFFSET_DP = 120

    @Volatile var navigator: SpatialNavigator? = null
    private var reopenPending = false
    private val main = Handler(Looper.getMainLooper())

    fun open(why: String) {
        SpLog.i("WINDOW open why=$why state=${App.ui.panelState}")
        runCatching { navigator?.openWindowContainer(PANEL, PANEL_TAG) }.onFailure { SpLog.e("WINDOW open failed", it) }
    }

    /** [重开控制窗]: close, wait for ON_DESTROY (an open right after a close is swallowed), open again beside the abacus. */
    fun reopen() {
        val st = App.ui.panelState
        if (st == "ON_DESTROY" || st == "never") { open("reopen/closed"); return }
        reopenPending = true
        SpLog.i("WINDOW reopen: close first")
        runCatching { navigator?.closeWindowContainer(PANEL, PANEL_TAG) }
        // safety net: if no ON_DESTROY arrives, open anyway after 3 s
        main.postDelayed({ if (reopenPending) { reopenPending = false; open("reopen/timeout") } }, 3000)
    }

    fun onPanelState(state: String) {
        if (reopenPending && state == "ON_DESTROY") {
            reopenPending = false
            main.postDelayed({ open("reopen/destroyed") }, 150)
        }
    }

    /** D26 / D44 exit: close the control window and the volume, finish every task, end the process 400 ms later. */
    fun exitApp() {
        SpLog.i("EXIT close panel + volume, finishAndRemoveTask, exitProcess")
        runCatching { App.sounds.release() }
        runCatching { navigator?.closeWindowContainer(PANEL, PANEL_TAG) }
        runCatching { navigator?.closeWindowContainer(VOLUME) }
        runCatching {
            val am = AppContext.ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { runCatching { it.finishAndRemoveTask() } }
        }.onFailure { SpLog.e("EXIT finishAndRemoveTask failed", it) }
        main.postDelayed({ exitProcess(0) }, 400)
    }
}
