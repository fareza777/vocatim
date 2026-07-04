package com.vocatim.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.vocatim.app.data.repository.ImportCoordinator
import com.vocatim.app.ui.VocatimNavHost
import com.vocatim.app.ui.theme.VocatimTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var importCoordinator: ImportCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAudioIntent(intent)
        setContent {
            VocatimTheme {
                VocatimNavHost()
            }
        }
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
