package com.cryptomacro.app

/**
 * BEGINNER: An Activity is one screen's host. MainActivity is the only Activity in this app.
 * Jetpack Compose draws the UI *inside* it (setContent { ... }).
 *
 * @AndroidEntryPoint lets Hilt inject PreferencesRepository.
 * We extend FragmentActivity (not ComponentActivity) because BiometricPrompt needs a FragmentActivity.
 *
 * Startup order (important for security):
 *  1. Splash
 *  2. FLAG_SECURE so recents cannot screenshot a flash of balances
 *  3. Wait until DataStore settings have loaded (null = still loading)
 *  4. If biometric lock is on, show LockScreen; else show AppRoot
 */
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.Color as AndroidColor
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.cryptomacro.app.data.local.PreferencesRepository
import com.cryptomacro.app.ui.lock.LockScreen
import com.cryptomacro.app.ui.navigation.AppRoot
import com.cryptomacro.app.ui.theme.CryptoMacroTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /** Filled by Hilt. Reads theme / biometric / privacy from DataStore. */
    @Inject lateinit var preferences: PreferencesRepository

    /**
     * Holds a one-shot launcher shortcut action (Add transaction).
     * MutableStateFlow so Compose can collect it and then we set it back to null.
     */
    private val pendingAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // Android 12+ branded splash (Bitcoin coin)
        super.onCreate(savedInstanceState)
        // Block screenshots/recents until the user preference is known (default is ON).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Draw behind the status bar and navigation bar (we pad content ourselves).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        pendingAction.value = shortcutAction(intent)

        // Foldables: emit the hinge/fold feature (or null on a normal phone).
        val folding = WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)
            .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)

        setContent {
            // initialValue = null means "settings not loaded yet" — we must not show AppRoot.
            val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = null)
            val fold by folding.collectAsStateWithLifecycle()
            val pending by pendingAction.collectAsStateWithLifecycle()
            var unlocked by remember { mutableStateOf(false) }

            // Keep FLAG_SECURE in sync with the Privacy shield switch.
            LaunchedEffect(settings?.privacyShield) {
                val on = settings?.privacyShield ?: true
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            // If the user turns biometric lock on, immediately lock again.
            LaunchedEffect(settings?.biometricLock) {
                if (settings?.biometricLock == true) unlocked = false
            }

            val ready = settings
            if (ready == null) return@setContent // blank frame until DataStore emits

            CryptoMacroTheme(ready.theme) {
                val needsLock = ready.biometricLock && !unlocked
                if (needsLock) {
                    LockScreen(onUnlock = { promptBiometric { unlocked = true } })
                } else {
                    AppRoot(
                        pendingAction = pending,
                        foldingFeature = fold,
                        onConsumedAction = { pendingAction.value = null },
                        onLock = { unlocked = false },
                    )
                }
            }
        }
    }

    /** Home-screen shortcut while the Activity already exists (launchMode=singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction.value = shortcutAction(intent)
    }

    /** Only honor our own "add transaction" action — ignore everything else. */
    private fun shortcutAction(intent: Intent?): String? =
        intent?.action?.takeIf { it == ACTION_ADD_TRANSACTION }

    /**
     * Shows the system fingerprint / face / PIN sheet.
     * On success we run onOk (which sets unlocked = true). On cancel we stay locked.
     */
    private fun promptBiometric(onOk: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOk()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CryptoMacro")
            .setSubtitle("Biometrics or device credential")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }

    companion object {
        /** Must match res/xml/shortcuts.xml android:action */
        const val ACTION_ADD_TRANSACTION = "com.cryptomacro.app.ADD_TRANSACTION"
    }
}
