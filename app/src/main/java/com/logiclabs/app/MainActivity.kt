package com.logiclabs.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.logiclabs.app.ui.navigation.LogicLabsNavHost
import com.logiclabs.core.data.settings.Settings
import com.logiclabs.core.designsystem.theme.HardwareTheme
import com.logiclabs.core.designsystem.theme.LocalRenderEffects
import com.logiclabs.core.designsystem.theme.LogicLabsTheme
import com.logiclabs.feature.tools.feedback.Haptics
import com.logiclabs.feature.tools.feedback.LocalConsoleSfx
import com.logiclabs.feature.tools.feedback.rememberConsoleSfx

/**
 * Single activity host.
 *
 * Both system bars stay on screen and are made transparent so the bench renders edge to
 * edge — the chassis gradient runs behind the status bar and the console dock behind the
 * gesture bar. The bars are deliberately NOT hidden: this activity used to force-hide
 * them with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, which zeroed every
 * `statusBarsPadding()`/`navigationBarsPadding()` in the composition (so the command bar
 * rendered at y=0, directly under the front-camera cutout) and let the swipe-summoned
 * transient bars draw over the HUD mid-gesture. Persistent bars keep those insets real.
 *
 * The dark [SystemBarStyle] is explicit rather than left to `enableEdgeToEdge()`'s default,
 * which picks light icons in a light system theme and would put dark glyphs on the app's
 * near-black chrome. Padding for the bar and cutout regions is applied inside the
 * composition (cutout-aware status padding on the command bar, navigation-bar padding on
 * the dock, the bottom nav bar and every sheet), so nothing is left under a bar or the
 * notch in any orientation.
 *
 * The activity is deliberately thin: theme resolution (settings-driven), the two
 * app-wide feedback gates (sound, haptics), and the [LogicLabsNavHost]. Everything
 * else belongs to the destinations.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Swaps the window from the branded splash theme to the bench theme. No
        // keep-on-screen condition: the Compose Power-On Self-Test takes over
        // immediately and holds its own timeline.
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // No hideSystemBars(): see the class KDoc. The window still draws edge to edge
        // under the transparent bars; the composition pads itself clear of them.
        val container = (application as LogicLabsApplication).container
        setContent {
            // Cold-start default while DataStore emits its first value: the dark bench
            // the app was designed around. The flow then takes over within a frame or
            // two, so a light-theme user sees one dark frame at most on the splash.
            val settings by container.settings.settings.collectAsState(initial = Settings())

            val isSystemDark = isSystemInDarkTheme()
            val hardwareTheme = when (settings.themeMode) {
                com.logiclabs.core.data.settings.ThemeMode.SYSTEM ->
                    if (isSystemDark) HardwareTheme.OBSIDIAN else HardwareTheme.CLEANROOM
                com.logiclabs.core.data.settings.ThemeMode.OBSIDIAN -> HardwareTheme.OBSIDIAN
                com.logiclabs.core.data.settings.ThemeMode.AMBER_CRT -> HardwareTheme.AMBER_CRT
                com.logiclabs.core.data.settings.ThemeMode.HP_SLATE -> HardwareTheme.HP_SLATE
                com.logiclabs.core.data.settings.ThemeMode.CLEANROOM -> HardwareTheme.CLEANROOM
                com.logiclabs.core.data.settings.ThemeMode.CYBERPUNK_NEON -> HardwareTheme.CYBERPUNK_NEON
                com.logiclabs.core.data.settings.ThemeMode.TOKYO_NIGHT -> HardwareTheme.TOKYO_NIGHT
                com.logiclabs.core.data.settings.ThemeMode.SOLARIZED_DARK -> HardwareTheme.SOLARIZED_DARK
                com.logiclabs.core.data.settings.ThemeMode.VINTAGE_BRITISH_LAB -> HardwareTheme.VINTAGE_BRITISH_LAB
                com.logiclabs.core.data.settings.ThemeMode.TITANIUM_FROST -> HardwareTheme.TITANIUM_FROST
            }
            val darkTheme = hardwareTheme != HardwareTheme.CLEANROOM

            // Bar glyphs follow the bench: light icons on the dark lab, dark icons on
            // the daylight lab. Re-invoking enableEdgeToEdge per flip is cheap and the
            // sanctioned way to change styles after setup.
            LaunchedEffect(darkTheme) {
                if (darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    )
                }
            }

            LogicLabsTheme(hardwareTheme = hardwareTheme, darkTheme = darkTheme) {
                // One AudioTrack pool for the whole app, released with the composition.
                CompositionLocalProvider(LocalConsoleSfx provides rememberConsoleSfx()) {
                    val sfx = LocalConsoleSfx.current
                    // The settings repository is the single source of truth for both
                    // feedback gates; these effects keep the live objects in step with
                    // it, so a toggle in Settings lands everywhere at once.
                    LaunchedEffect(settings.soundEnabled) {
                        sfx?.muted = !settings.soundEnabled
                    }
                    LaunchedEffect(settings.hapticsEnabled) {
                        Haptics.enabled = settings.hapticsEnabled
                        com.logiclabs.feature.instruments.ui.hardware.PanelHaptics.enabled = settings.hapticsEnabled
                    }

                    // Bloom gate for every renderer below (LED auras, seven-segment
                    // halos). Provided here so feature modules read one value with no
                    // parameter threading.
                    CompositionLocalProvider(LocalRenderEffects provides settings.bloomEffects) {
                        LogicLabsNavHost(container = container)
                    }
                }
            }
        }
    }
}

