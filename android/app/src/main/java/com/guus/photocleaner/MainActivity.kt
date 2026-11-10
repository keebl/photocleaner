package com.guus.photocleaner

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.guus.photocleaner.service.AppPreferences
import com.guus.photocleaner.service.MediaAccess
import com.guus.photocleaner.ui.AppShell
import com.guus.photocleaner.ui.MediaScreen
import com.guus.photocleaner.ui.ShellState
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private val prefs: AppPreferences get() = container.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as PhotoCleanerApplication).container
        setContent {
            val dark = isSystemInDarkTheme()
            PhotoCleanerTheme(darkTheme = dark) { PhotoCleanerApp(dark) }
        }
    }

    @Composable
    private fun PhotoCleanerApp(darkTheme: Boolean) {
        var state by remember { mutableStateOf(currentState()) }
        var swipeHintSeen by remember { mutableStateOf(prefs.didSeeSwipeHint) }
        val permissions = remember { MediaAccess.permissionsToRequest(Build.VERSION.SDK_INT) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            prefs.requestedMediaAccess = true
            state = currentState()
        }

        // Re-check on every resume: access can be changed or revoked in system settings.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = currentState() }

        AppShell(
            state = state,
            darkTheme = darkTheme,
            onFinishOnboarding = {
                prefs.didOnboard = true
                state = currentState()
                if (!state.access.canReadPhotos) launcher.launch(permissions.toTypedArray())
            },
            onRequestAccess = { launcher.launch(permissions.toTypedArray()) },
            onOpenSettings = ::openAppSettings,
            mediaContent = { openTrash ->
                MediaScreen(
                    source = container.source,
                    trash = container.trash,
                    keep = container.keep,
                    onOpenTrash = openTrash,
                    initialSettings = remember { prefs.mediaSettings },
                    onSettingsChange = { prefs.mediaSettings = it },
                    swipeHintSeen = swipeHintSeen,
                    onSwipeHintSeen = {
                        if (!swipeHintSeen) {
                            swipeHintSeen = true
                            prefs.didSeeSwipeHint = true
                        }
                    },
                )
            },
        )
    }

    private fun currentState(): ShellState {
        val sdk = Build.VERSION.SDK_INT
        val access = MediaAccess.resolve(sdk) {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val primary = MediaAccess.permissionsToRequest(sdk).first()
        val canAskAgain = !prefs.requestedMediaAccess || shouldShowRequestPermissionRationale(primary)
        return ShellState(didOnboard = prefs.didOnboard, access = access, canAskAgain = canAskAgain)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}
