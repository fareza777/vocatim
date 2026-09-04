package com.vocatim.app.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.ui.theme.BrandGradient
import kotlinx.coroutines.launch

/**
 * First run: pitch → language → engine choice → set-up.
 *
 * The engine choice is the point of the flow. Vocatim can transcribe on the
 * phone or in the cloud, and those need completely different set-up (a model
 * download versus an API key), so the last page follows the choice rather
 * than trying to serve both.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val recommendedModel by viewModel.recommendedModel.collectAsStateWithLifecycle()
    val parakeetOffered by viewModel.parakeetOffered.collectAsStateWithLifecycle()
    val useParakeet by viewModel.useParakeet.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val cloudKeySaved by viewModel.cloudKeySaved.collectAsStateWithLifecycle()

    fun finish() {
        viewModel.finish()
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false,
        ) { page ->
            when (page) {
                0 -> Page(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.onboarding_privacy_title),
                    body = stringResource(R.string.onboarding_privacy_body),
                )
                1 -> Page(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.onboarding_language_title),
                    body = stringResource(R.string.onboarding_language_body),
                ) {
                    val languages = listOf("id", "en", "auto")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        languages.forEachIndexed { index, code ->
                            SegmentedButton(
                                selected = selectedLanguage == code,
                                onClick = { viewModel.selectLanguage(code) },
                                shape = SegmentedButtonDefaults.itemShape(index, languages.size),
                            ) {
                                Text(
                                    when (code) {
                                        "id" -> stringResource(R.string.onboarding_lang_id)
                                        "en" -> stringResource(R.string.onboarding_lang_en)
                                        else -> stringResource(R.string.lang_auto)
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> Page(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.onboarding_mode_title),
                    body = stringResource(R.string.onboarding_mode_body),
                ) {
                    ModeCard(
                        title = stringResource(R.string.onboarding_mode_offline),
                        desc = stringResource(R.string.onboarding_mode_offline_desc),
                        icon = Icons.Default.CloudOff,
                        selected = mode == OnboardingViewModel.Mode.OFFLINE,
                        onClick = {
                            viewModel.selectMode(OnboardingViewModel.Mode.OFFLINE)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    ModeCard(
                        title = stringResource(R.string.onboarding_mode_online),
                        desc = stringResource(R.string.onboarding_mode_online_desc),
                        icon = Icons.Default.CloudQueue,
                        selected = mode == OnboardingViewModel.Mode.ONLINE,
                        onClick = {
                            viewModel.selectMode(OnboardingViewModel.Mode.ONLINE)
                        },
                    )
                }
                3 -> if (mode == OnboardingViewModel.Mode.ONLINE) {
                    OnlineSetupPage(
                        provider = viewModel.cloudProvider,
                        saved = cloudKeySaved,
                        onSave = viewModel::saveCloudKey,
                    )
                } else Page(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.onboarding_model_title),
                    body = stringResource(
                        R.string.onboarding_model_body,
                        com.vocatim.app.ui.common.modelDisplayName(
                            if (useParakeet) com.vocatim.app.data.model.ParakeetModel.ID
                            else recommendedModel.id
                        ),
                        (if (useParakeet) viewModel.parakeetBytes
                        else recommendedModel.approxSizeBytes) / (1024 * 1024),
                    ),
                ) {
                    // English gets a choice here so nobody has to discover the
                    // better engine buried in Settings. Names and one-liners
                    // do the explaining — the size alone means nothing to a
                    // first-time user.
                    if (parakeetOffered) {
                        val options = listOf(false, true)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, parakeet ->
                                SegmentedButton(
                                    selected = useParakeet == parakeet,
                                    onClick = { viewModel.setUseParakeet(parakeet) },
                                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                ) {
                                    Text(
                                        com.vocatim.app.ui.common.modelDisplayName(
                                            if (parakeet) com.vocatim.app.data.model.ParakeetModel.ID
                                            else recommendedModel.id
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                if (useParakeet) R.string.onboarding_engine_parakeet_desc
                                else R.string.onboarding_engine_standard_desc
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    when (val state = modelState) {
                        is ModelState.NotDownloaded ->
                            Button(onClick = viewModel::downloadRecommended) {
                                Text(stringResource(R.string.debug_download))
                            }
                        is ModelState.Downloading -> {
                            if (state.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        is ModelState.Downloaded -> Text(
                            stringResource(R.string.onboarding_model_ready),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        is ModelState.Failed -> {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = viewModel::downloadRecommended) {
                                Text(stringResource(R.string.debug_retry))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(4) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = ::finish) {
                Text(
                    stringResource(R.string.onboarding_skip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (pagerState.currentPage < 3) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    finish()
                }
            }) {
                Text(
                    stringResource(
                        if (pagerState.currentPage < 3) R.string.onboarding_next
                        else R.string.onboarding_start
                    )
                )
            }
        }
    }
}

/** One of the two engine choices, sized so the trade-off is readable. */
@Composable
private fun ModeCard(
    title: String,
    desc: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Online set-up. Numbered steps rather than a bare field: the wall is not
 * typing the key, it is not knowing where a key comes from.
 */
@Composable
private fun OnlineSetupPage(
    provider: com.vocatim.app.data.cloud.CloudTranscribeProvider,
    saved: Boolean,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }

    Page(
        icon = Icons.Default.CloudQueue,
        title = stringResource(R.string.onboarding_online_title),
        body = stringResource(R.string.onboarding_online_body),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                R.string.onboarding_online_step1,
                R.string.onboarding_online_step2,
                R.string.onboarding_online_step3,
            ).forEach {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(provider.keyUrl),
                    )
                )
            }
        }) { Text(stringResource(R.string.cloud_tx_get_key)) }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.cloud_tx_api_key)) },
            placeholder = { Text(stringResource(R.string.cloud_tx_api_key_hint)) },
        )
        Spacer(Modifier.height(8.dp))
        when {
            saved && key.isBlank() -> Text(
                stringResource(R.string.onboarding_online_ready),
                color = MaterialTheme.colorScheme.secondary,
            )
            key.isNotBlank() -> Button(onClick = { onSave(key); key = "" }) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun Page(
    icon: ImageVector,
    title: String,
    body: String,
    content: @Composable () -> Unit = {},
) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "hero")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "heroPulse",
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        content()
    }
}
