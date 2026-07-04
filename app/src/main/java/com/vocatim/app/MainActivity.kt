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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAudioIntent(intent)

        lifecycleScope.launch {
            userPrefs.settings.collect { settings ->
                appLockEnabled = settings.appLock
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
            VocatimTheme {
                when (locked) {
                    null -> Unit // waiting for prefs; background only
                    true -> LockScreen(onUnlockClick = ::authenticate)
                    false -> VocatimNavHost()
                }
            }
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
    }

    /** Audio shared or opened from another app goes straight to import. */
    private fun handleAudioIntent(intent: Intent?) {
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
}
