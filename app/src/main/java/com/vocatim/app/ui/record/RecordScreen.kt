package com.vocatim.app.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.service.RecordingState
import com.vocatim.app.ui.common.formatClock

private const val AMPLITUDE_BARS = 48

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onFinished: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val storageStatus by viewModel.storageStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val amplitudes = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        viewModel.finished.collect { id -> onFinished(id) }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is RecordingState.Active && !s.paused) {
            amplitudes.add(s.amplitude)
            if (amplitudes.size > AMPLITUDE_BARS) amplitudes.removeAt(0)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.start()
        }
    }

    fun startWithPermission() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) viewModel.start()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.record_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            when (val s = state) {
                is RecordingState.Idle -> {
                    when (storageStatus) {
                        StorageStatus.FULL -> Text(
                            stringResource(R.string.record_storage_full),
                            color = MaterialTheme.colorScheme.error,
                        )
                        StorageStatus.LOW -> Text(
                            stringResource(R.string.record_storage_low),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        StorageStatus.OK -> Unit
                    }
                    Text(
                        stringResource(R.string.record_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledIconButton(
                        onClick = ::startWithPermission,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.record_start),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                is RecordingState.Active -> {
                    Text(
                        formatClock(s.elapsedMs),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    AmplitudeBars(amplitudes, paused = s.paused)
                    if (s.paused) {
                        Text(
                            stringResource(R.string.record_paused),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        Spacer(Modifier.height(20.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        FilledIconButton(
                            onClick = { if (s.paused) viewModel.resume() else viewModel.pause() },
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                if (s.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = stringResource(
                                    if (s.paused) R.string.record_resume else R.string.record_pause
                                ),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        FilledIconButton(
                            onClick = { viewModel.stop() },
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = stringResource(R.string.record_stop),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
                is RecordingState.Error -> {
                    Text(
                        stringResource(R.string.record_error, s.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    FilledIconButton(
                        onClick = ::startWithPermission,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.record_start),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmplitudeBars(amplitudes: List<Float>, paused: Boolean) {
    val color =
        if (paused) MaterialTheme.colorScheme.outlineVariant
        else MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        val barWidth = size.width / AMPLITUDE_BARS
        val center = size.height / 2
        amplitudes.forEachIndexed { i, amp ->
            val h = (amp.coerceIn(0.02f, 1f)) * size.height
            val x = i * barWidth + barWidth / 2
            drawLine(
                color = color,
                start = Offset(x, center - h / 2),
                end = Offset(x, center + h / 2),
                strokeWidth = barWidth * 0.6f,
                cap = StrokeCap.Round,
            )
        }
    }
}
