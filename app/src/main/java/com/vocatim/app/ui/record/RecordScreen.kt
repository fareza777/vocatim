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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
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

    val markers by viewModel.markers.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.finished.collect { id ->
            viewModel.saveMarkers(id)
            onFinished(id)
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is RecordingState.Active && !s.paused) {
            amplitudes.add(s.amplitude)
            if (amplitudes.size > AMPLITUDE_BARS) amplitudes.removeAt(0)
        }
    }

    // Live preview runs only while this screen is composed.
    androidx.compose.runtime.DisposableEffect(Unit) {
        viewModel.startLivePreview()
        onDispose { viewModel.stopLivePreview() }
    }

    // Which start the permission grant should resume: normal or live mode.
    var pendingLiveStart by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            if (pendingLiveStart) viewModel.startLive() else viewModel.start()
        }
    }

    fun launchWithPermission(live: Boolean) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        pendingLiveStart = live
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            if (live) viewModel.startLive() else viewModel.start()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun startWithPermission() = launchWithPermission(live = false)
    fun startLiveWithPermission() = launchWithPermission(live = true)

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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 48.dp),
                    ) {
                        RecordButton(
                            recording = false,
                            onClick = ::startWithPermission,
                        )
                        // Second, deliberate mode: recording + streaming
                        // English captions. Never mixed with the normal path.
                        androidx.compose.material3.OutlinedButton(onClick = {
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            if (!viewModel.liveModelReady) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.record_live_need_model),
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                startLiveWithPermission()
                            }
                        }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.record_live_mode))
                        }
                    }
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

                    val isLive by viewModel.liveMode.collectAsStateWithLifecycle()
                    if (isLive) {
                        val captionState by viewModel.captions.collectAsStateWithLifecycle()
                        val throttled by viewModel.liveThrottled.collectAsStateWithLifecycle()
                        LiveCaptions(
                            captions = captionState,
                            throttled = throttled,
                        )
                    }

                    val livePreview by viewModel.livePreview.collectAsStateWithLifecycle()
                    if (!isLive) livePreview?.let { preview ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Pill(
                                stringResource(R.string.record_live_preview),
                                color = MaterialTheme.colorScheme.primary,
                                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                dot = false,
                            )
                            Text(
                                // The tail is the freshest part; lead with it.
                                preview.trim().takeLast(220),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 3,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (markers.isNotEmpty()) {
                        Pill(
                            stringResource(R.string.record_markers_count, markers.size),
                            color = MaterialTheme.colorScheme.tertiary,
                            background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            dot = true,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
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
                        Surface(
                            onClick = {
                                viewModel.addMarker()
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                )
                            },
                            enabled = !s.paused,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = stringResource(R.string.record_add_marker),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(26.dp),
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

/** Streaming caption panel: finalized lines plus the sentence in flight. */
@Composable
private fun LiveCaptions(
    captions: com.vocatim.app.data.transcribe.CaptionState,
    throttled: Boolean,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(captions) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(min = 72.dp, max = 200.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (throttled) {
                Text(
                    stringResource(R.string.record_live_throttled),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (captions.lines.isEmpty() && captions.partial.isEmpty() && !throttled) {
                Text(
                    stringResource(R.string.record_live_listening),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            captions.lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            if (captions.partial.isNotEmpty()) {
                Text(
                    captions.partial,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        // Normalize against the loudest bar in view: hardware AGC compresses
        // the absolute level, so only relative scaling shows speech dynamics.
        val windowMax = (amplitudes.maxOrNull() ?: 0f).coerceAtLeast(0.02f)
        for (i in 0 until AMPLITUDE_BARS) {
            val amp = amplitudes.getOrNull(amplitudes.size - AMPLITUDE_BARS + i) ?: 0f
            // Perceptual curve: sqrt-ish response keeps quiet syllables visible.
            val level = Math.pow((amp / windowMax).toDouble(), 0.7)
                .toFloat().coerceIn(0.05f, 1f)
            val h = level * size.height
            val x = i * barWidth + barWidth / 2
            // Louder = brighter toward the teal tip; recency adds a small lift.
            val color = when {
                paused -> idle
                else -> androidx.compose.ui.graphics.lerp(
                    active, activeTip,
                    (level * 0.7f + (i.toFloat() / AMPLITUDE_BARS) * 0.3f).coerceIn(0f, 1f),
                )
            }
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
