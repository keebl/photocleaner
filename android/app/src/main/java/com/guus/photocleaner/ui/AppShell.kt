package com.guus.photocleaner.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R
import com.guus.photocleaner.service.MediaAccess

/** Everything the shell needs to decide which screen to show. */
data class ShellState(
    val didOnboard: Boolean,
    val access: MediaAccess,
    /** False once Android stops showing the permission dialog (denied twice). */
    val canAskAgain: Boolean,
)

enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    MEDIA(R.string.tab_media, R.drawable.ic_photo_library),
    TRASH(R.string.tab_trash, R.drawable.ic_delete),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}

/**
 * Onboarding, permission gate and the three tabs. Port of iOS `RootView`; stateless so it
 * can be tested with any [ShellState].
 */
@Composable
fun AppShell(
    state: ShellState,
    darkTheme: Boolean,
    onFinishOnboarding: () -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    mediaContent: @Composable () -> Unit = { Placeholder(R.string.placeholder_media) },
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !state.didOnboard -> OnboardingScreen(onDone = onFinishOnboarding, darkTheme = darkTheme)
            !state.access.canReadPhotos -> PermissionGateScreen(
                canAskAgain = state.canAskAgain,
                onRequestAccess = onRequestAccess,
                onOpenSettings = onOpenSettings,
            )
            else -> MainTabs(
                partialAccess = state.access == MediaAccess.PARTIAL,
                onRequestAccess = onRequestAccess,
                mediaContent = mediaContent,
            )
        }
    }
}

@Composable
private fun MainTabs(partialAccess: Boolean, onRequestAccess: () -> Unit, mediaContent: @Composable () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Tab.MEDIA) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (partialAccess) PartialAccessBanner(onRequestAccess)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (selected) {
                    Tab.MEDIA -> mediaContent()
                    Tab.TRASH -> Placeholder(R.string.placeholder_trash)
                    Tab.SETTINGS -> Placeholder(R.string.placeholder_settings)
                }
            }
        }
    }
}

/** Android 14+ "selected photos only": works on those, offers full access (spec AC 3). */
@Composable
private fun PartialAccessBanner(onRequestAccess: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                stringResource(R.string.partial_access_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.partial_access_action)) }
        }
    }
}

/** Temporary tab content until the screens are built (spec steps 6–11). */
@Composable
private fun Placeholder(@StringRes text: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
