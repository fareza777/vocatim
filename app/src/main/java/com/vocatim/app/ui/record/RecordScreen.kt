package com.vocatim.app.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.service.RecordingState
import com.vocatim.app.ui.common.Pill
import com.vocatim.app.ui.common.formatClock
import com.vocatim.app.ui.theme.BrandGradient

private const val AMPLITUDE_BARS = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onFinished: (Long) -> Unit,
    onBack: () -> Unit,
    autoStart: Boolean = false,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val storageStatus by viewModel.storageStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
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

    // Quick Settings tile arrives with autoStart: begin immediately.
    LaunchedEffect(Unit) {
        if (autoStart && state is RecordingState.Idle) {
            startWithPermission()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            when (val s = state) {
                is RecordingState.Idle, is RecordingState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 48.dp),
                    ) {
                        Text(
                            stringResource(R.string.record_title_idle),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            stringResource(R.string.record_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when (storageStatus) {
                            StorageStatus.FULL -> Pill(
                                stringResource(R.string.record_storage_full),
                                color = MaterialTheme.colorScheme.error,
                                background = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                dot = true,
                            )
                            StorageStatus.LOW -> Pill(
                                stringResource(R.string.record_storage_low),
                                color = MaterialTheme.colorScheme.tertiary,
                                background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                dot = true,
                            )
                            StorageStatus.OK -> Unit
                        }
                        if (s is RecordingState.Error) {
                            Text(
                                stringResource(R.string.record_error, s.message),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(0.dp))
                    RecordButton(
                        recording = false,
                        onClick = ::startWithPermission,
                        modifier = Modifier.padding(bottom = 72.dp),
                    )
                }
                is RecordingState.Active -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 48.dp),
                    ) {
                        Text(
                            formatClock(s.elapsedMs),
                            style = MaterialTheme.typography.displayLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Pill(
                            stringResource(
                                if (s.paused) R.string.record_paused else R.string.record_live
                            ),
                            color = if (s.paused) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.secondary,
                            background = (
                                if (s.paused) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.secondary
                                ).copy(alpha = 0.12f),
                            dot = true,
                        )
                    }

                    AmplitudeBars(amplitudes, paused = s.paused)

                    if (s.partialText.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.record_live_preview),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    s.partialText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 5,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 72.dp),
                    ) {
                        Surface(
                            onClick = { if (s.paused) viewModel.resume() else viewModel.pause() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (s.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = stringResource(
                                        if (s.paused) R.string.record_resume else R.string.record_pause
                                    ),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        RecordButton(
                            recording = !s.paused,
                            icon = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.record_stop),
                            onClick = {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                )
                                viewModel.stop()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Mic,
    contentDescription: String = stringResource(R.string.record_start),
) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Box(
        modifier = modifier
            .size(88.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(BrandGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun AmplitudeBars(amplitudes: List<Float>, paused: Boolean) {
    val active = MaterialTheme.colorScheme.primary
    val activeTip = MaterialTheme.colorScheme.secondary
    val idle = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 8.dp)
    ) {
        val barWidth = size.width / AMPLITUDE_BARS
        val center = size.height / 2
        for (i in 0 until AMPLITUDE_BARS) {
            val amp = amplitudes.getOrNull(amplitudes.size - AMPLITUDE_BARS + i) ?: 0f
            val h = (amp.coerceIn(0.03f, 1f)) * size.height
            val x = i * barWidth + barWidth / 2
            // Newest bars blend toward the teal end of the brand gradient.
            val color = when {
                paused -> idle
                else -> androidx.compose.ui.graphics.lerp(
                    active, activeTip, i.toFloat() / AMPLITUDE_BARS
                )
            }
            drawLine(
                color = color,
                start = Offset(x, center - h / 2),
                end = Offset(x, center + h / 2),
                strokeWidth = barWidth * 0.55f,
                cap = StrokeCap.Round,
            )
        }
    }
}
