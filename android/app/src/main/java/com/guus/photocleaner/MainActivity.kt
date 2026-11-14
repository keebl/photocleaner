package com.guus.photocleaner

import android.Manifest
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.guus.photocleaner.service.AppPreferences
import com.guus.photocleaner.service.AppTheme
import com.guus.photocleaner.service.MediaAccess
import com.guus.photocleaner.service.ReminderScheduler
import com.guus.photocleaner.service.StoreReconciler
import com.guus.photocleaner.ui.AppShell
import com.guus.photocleaner.ui.MediaScreen
import com.guus.photocleaner.ui.ShellState
import com.guus.photocleaner.ui.media.Browse
import com.guus.photocleaner.ui.media.MediaTab
import com.guus.photocleaner.ui.settings.SettingsScreen
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import com.guus.photocleaner.ui.trash.ExpiredTrashPrompt
import com.guus.photocleaner.ui.trash.TrashScreen
import com.guus.photocleaner.ui.trash.rememberSystemTrash
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private val prefs: AppPreferences get() = container.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as PhotoCleanerApplication).container
        // Idempotent: keeps the daily alarm in step with the saved preference.
        ReminderScheduler.apply(this, prefs.reminder)
        val openOnThisDay = intent.getBooleanExtra(ReminderScheduler.EXTRA_OPEN_ON_THIS_DAY, false)
        setContent {
            var theme by remember { mutableStateOf(prefs.theme) }
            val dark = when (theme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            PhotoCleanerTheme(darkTheme = dark) {
                PhotoCleanerApp(
                    darkTheme = dark,
                    theme = theme,
                    onTheme = {
                        theme = it
                        prefs.theme = it
                    },
                    openOnThisDay = openOnThisDay,
                )
            }
        }
    }

    @Composable
    private fun PhotoCleanerApp(darkTheme: Boolean, theme: AppTheme, onTheme: (AppTheme) -> Unit, openOnThisDay: Boolean) {
        var state by remember { mutableStateOf(currentState()) }
        var swipeHintSeen by remember { mutableStateOf(prefs.didSeeSwipeHint) }
        var reminder by remember { mutableStateOf(prefs.reminder) }
        val systemTrash = rememberSystemTrash(container.source)
        val permissions = remember { MediaAccess.permissionsToRequest(Build.VERSION.SDK_INT) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            prefs.requestedMediaAccess = true
            state = currentState()
        }
        val pendingNotificationAnswer = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
        val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingNotificationAnswer[0]?.complete(ReminderScheduler.canNotify(this))
            pendingNotificationAnswer[0] = null
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
                    initialSettings = remember {
                        // The reminder opens the "Op deze dag" deck for today.
                        val saved = prefs.mediaSettings
                        if (openOnThisDay) saved.copy(tab = MediaTab.PHOTOS, browse = Browse.DAY) else saved
                    },
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
            trashContent = {
                TrashScreen(source = container.source, trash = container.trash, systemTrash = systemTrash)
            },
            settingsContent = {
                SettingsScreen(
                    theme = theme,
                    onTheme = onTheme,
                    reminder = reminder,
                    onReminder = {
                        reminder = it
                        prefs.reminder = it
                        ReminderScheduler.apply(this, it)
                    },
                    requestNotifications = {
                        if (ReminderScheduler.canNotify(this)) {
                            true
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val answer = CompletableDeferred<Boolean>()
                            pendingNotificationAnswer[0] = answer
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            answer.await()
                        } else {
                            false // notifications blocked in system settings
                        }
                    },
                    keep = container.keep,
                    trash = container.trash,
                    versionText = versionText(),
                )
            },
            startupContent = {
                // Drop items deleted outside the app (AC 16), only with full access: with
                // "selected photos only" every unselected item would look deleted.
                LaunchedEffect(state.access) {
                    if (state.access == MediaAccess.FULL) {
                        StoreReconciler(container.source, container.trash, container.keep).dropMissing()
                    }
                }
                ExpiredTrashPrompt(source = container.source, trash = container.trash, systemTrash = systemTrash)
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

    private fun versionText(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return "${info.versionName} (${info.longVersionCode})"
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}
