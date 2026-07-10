package com.vocatim.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.repository.ImportCoordinator
import com.vocatim.app.ui.VocatimNavHost
import com.vocatim.app.ui.lock.LockScreen
import com.vocatim.app.ui.theme.VocatimTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// FragmentActivity (not ComponentActivity): required by BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var importCoordinator: ImportCoordinator
    @Inject lateinit var userPrefs: UserPrefs

    /** null = prefs not loaded yet (render nothing to avoid content flash). */
    private var locked by mutableStateOf<Boolean?>(null)
    private var appLockEnabled = false
    private var themeMode by mutableStateOf(UserPrefs.THEME_LIGHT)
    private var accent by mutableStateOf("violet")
    private var surfaceStyle by mutableStateOf("linen")
    private var onboardingDone by mutableStateOf<Boolean?>(null)
    /** Set by the Quick Settings tile: jump straight into recording. */
    private var startRecordRequest by mutableStateOf(false)
    /** Set by app shortcut: open a specific transcript. */
    private var openTranscriptId by mutableStateOf<Long?>(null)
    /** Branded intro animation; cold starts only, skipped for deep links. */
    private var showSplash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAudioIntent(intent)
        handleRecordIntent(intent)
        handleTranscriptIntent(intent)
        // Recreation (rotation, theme change) or a jump-straight-in intent
        // shouldn't replay the intro.
        if (savedInstanceState != null || startRecordRequest || openTranscriptId != null) {
            showSplash = false
        }

        lifecycleScope.launch {
            userPrefs.settings.collect { settings ->
                appLockEnabled = settings.appLock
                themeMode = settings.themeMode
                accent = settings.accent
                surfaceStyle = settings.surfaceStyle
                onboardingDone = settings.onboardingDone
                if (locked == null) {
                    locked = settings.appLock
                    if (settings.appLock) authenticate()
                }
                if (settings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val darkTheme = when (themeMode) {
                UserPrefs.THEME_LIGHT -> false
                UserPrefs.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            VocatimTheme(darkTheme = darkTheme, accentKey = accent, surfaceKey = surfaceStyle) {
                androidx.compose.foundation.layout.Box {
                    when {
                        locked == null || onboardingDone == null -> Unit // prefs loading
                        locked == true -> LockScreen(onUnlockClick = ::authenticate)
                        onboardingDone == false ->
                            com.vocatim.app.ui.onboarding.OnboardingScreen(onDone = {})
                        else -> VocatimNavHost(
                            startRecord = startRecordRequest,
                            onStartRecordConsumed = { startRecordRequest = false },
                            openTranscriptId = openTranscriptId,
                            onOpenTranscriptConsumed = { openTranscriptId = null },
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showSplash,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(450)
                        ),
                    ) {
                        com.vocatim.app.ui.common.AnimatedSplash(
                            onFinished = { showSplash = false }
                        )
                    }
                }
            }
        }
    }

    private fun handleRecordIntent(intent: Intent?) {
        if (intent?.action == ACTION_START_RECORD) {
            intent.action = null
            startRecordRequest = true
        }
    }

    override fun onStart() {
        super.onStart()
        // Coming back from background: re-engage the lock.
        if (appLockEnabled && locked == true) {
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        if (appLockEnabled) {
            locked = true
        }
    }

    private fun authenticate() {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Credential no longer available (e.g. removed): fail open rather
            // than permanently locking the user out of their own data.
            locked = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAudioIntent(intent)
        handleRecordIntent(intent)
        handleTranscriptIntent(intent)
    }

    private fun handleTranscriptIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_TRANSCRIPT_ID, -1L) ?: return
        if (id > 0) {
            intent.removeExtra(EXTRA_TRANSCRIPT_ID)
            openTranscriptId = id
        }
    }

    /** Audio shared/opened, or text shared, from another app -> import. */
    private fun handleAudioIntent(intent: Intent?) {
        // Shared plain text becomes a summarizable note.
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!text.isNullOrBlank()) {
                intent.action = null
                lifecycleScope.launch {
                    runCatching { importCoordinator.importText(text, subject) }
                }
                return
            }
        }
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) {
            // Consume so rotation doesn't re-import.
            intent?.action = null
            lifecycleScope.launch {
                runCatching { importCoordinator.startImport(uri) }
            }
        }
    }

    companion object {
        const val ACTION_START_RECORD = "com.vocatim.app.START_RECORD"
        const val EXTRA_TRANSCRIPT_ID = "com.vocatim.app.TRANSCRIPT_ID"
    }
}
