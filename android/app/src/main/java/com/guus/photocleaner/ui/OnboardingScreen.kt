package com.guus.photocleaner.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R
import com.guus.photocleaner.ui.theme.Accent
import com.guus.photocleaner.ui.theme.DecisionColors
import kotlinx.coroutines.launch

private data class OnboardingPage(
    @param:DrawableRes val icon: Int,
    val tint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val text: Int,
)

/** Three-page introduction shown once on first start. Port of iOS `OnboardingView`. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, darkTheme: Boolean, modifier: Modifier = Modifier) {
    val pages = listOf(
        OnboardingPage(R.drawable.ic_auto_awesome, Accent, R.string.onboarding_1_title, R.string.onboarding_1_text),
        OnboardingPage(
            R.drawable.ic_history,
            if (darkTheme) DecisionColors.keepDark else DecisionColors.keepLight,
            R.string.onboarding_2_title,
            R.string.onboarding_2_text,
        ),
        OnboardingPage(
            R.drawable.ic_lock,
            if (darkTheme) DecisionColors.infoDark else DecisionColors.infoLight,
            R.string.onboarding_3_title,
            R.string.onboarding_3_text,
        ),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            if (!isLast) {
                TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(
                        stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            PageContent(pages[index])
        }

        PageIndicator(count = pages.size, current = pager.currentPage)

        Button(
            onClick = {
                if (isLast) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .height(52.dp),
        ) {
            Text(
                stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(page.icon),
            contentDescription = null,
            tint = page.tint,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(page.text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(count) { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (index == current) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            CircleShape,
                        ),
                )
            }
        }
    }
}
