package io.embodit.suanpan.platform

import android.app.Application
import android.content.Context
import android.util.Log
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity
import io.embodit.suanpan.mainApp
import io.embodit.suanpan.ui.App
import io.embodit.suanpan.ui.input.BoardInputLog

class LaunchActivity : SpatialLaunchActivity()

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SpLog.i("APP onCreate")
        BoardInputLog.attach(this)
        App.init(this)
        launch(::mainApp)
    }
}

/** One log tag for the whole app (QA greps `Suanpan`). */
object SpLog {
    const val TAG = "Suanpan"
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String) { Log.w(TAG, msg) }
    fun e(msg: String, t: Throwable? = null) { Log.e(TAG, msg, t) }
}

/** Application context for code that has none of its own (models, sounds). */
object AppContext {
    lateinit var ctx: Context
}
