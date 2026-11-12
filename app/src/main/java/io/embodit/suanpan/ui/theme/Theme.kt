package io.embodit.suanpan.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme
import io.embodit.suanpan.domain.Language
import io.embodit.suanpan.ui.App
import java.util.Locale

/** Brand colours, used only on the abacus and the strips' accent text (design palette: PicoTheme roles untouched). */
object SpColors {
    val Glow = Color(0xFFFFD27A)
    /** "now→target" on the digit strip: a deep warm gold that keeps >= 4.5 : 1 on the light glass. */
    val GoldInk = Color(0xFF8A5200)
    val Overfull = Color(0xFFFF8A1F)
    val Ink = Color(0xFF1B1410)
    val StripText = Color(0xFFFFFFFF)
    val StripDim = Color(0xFFE9E1D2)
}

/**
 * The app's PicoTheme: the system vibrant scheme, all 16 roles untouched (spatial-ui-design-style). [Language]
 * localises the resources below this point without a restart: a context with the chosen locale is installed as
 * LocalContext (a ContextWrapper, not the bare configuration context: the SDK walks the context chain to the
 * Activity that owns a SpatialView — Cloud Air's crash).
 */
@Composable
fun SpTheme(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val language = App.ui.settings.language
    val localized = remember(base, language) { base.localized(language) }
    CompositionLocalProvider(LocalContext provides localized) {
        PicoTheme(colorScheme = systemColorScheme(localized), content = content)
    }
}

fun Context.localized(language: Language): Context {
    val locale = when (language) {
        Language.SYSTEM -> return this
        Language.ZH -> Locale.SIMPLIFIED_CHINESE
        Language.EN -> Locale.ENGLISH
    }
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    val res = createConfigurationContext(config).resources
    return object : ContextWrapper(this) { override fun getResources(): Resources = res }
}
