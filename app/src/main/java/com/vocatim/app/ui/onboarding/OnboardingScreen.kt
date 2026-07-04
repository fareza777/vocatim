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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.ui.theme.BrandGradient
import kotlinx.coroutines.launch

/** Three-step first-run flow: pitch → language → recommended model. */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val recommendedModel by viewModel.recommendedModel.collectAsStateWithLifecycle()

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
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.onboarding_model_title),
                    body = stringResource(
                        R.string.onboarding_model_body,
                        recommendedModel.id,
                        recommendedModel.approxSizeBytes / (1024 * 1024),
                    ),
                ) {
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
            repeat(3) { index ->
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
                if (pagerState.currentPage < 2) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    finish()
                }
            }) {
                Text(
                    stringResource(
                        if (pagerState.currentPage < 2) R.string.onboarding_next
                        else R.string.onboarding_start
                    )
                )
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
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
