package com.guus.photocleaner.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R
import com.guus.photocleaner.service.AppTheme
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.ReminderSettings
import com.guus.photocleaner.service.TrashStore
import kotlinx.coroutines.launch

const val REMINDER_SWITCH_TAG = "reminderSwitch"

/**
 * Settings: theme, daily reminder, keep reset and version. Port of iOS `SettingsView`
 * (without the NAS source, which is out of scope for Android v1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: AppTheme,
    onTheme: (AppTheme) -> Unit,
    reminder: ReminderSettings,
    onReminder: (ReminderSettings) -> Unit,
    /** Asks for notification permission if needed; true when notifications may be posted. */
    requestNotifications: suspend () -> Boolean,
    keep: KeepStore,
    versionText: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keptIds by keep.ids.collectAsState()
    var denied by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        SectionHeader(R.string.settings_appearance)
        Group {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                val options = listOf(
                    AppTheme.SYSTEM to R.string.settings_theme_system,
                    AppTheme.LIGHT to R.string.settings_theme_light,
                    AppTheme.DARK to R.string.settings_theme_dark,
                )
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = theme == value,
                        onClick = { onTheme(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(stringResource(label)) }
                }
            }
        }

        SectionHeader(R.string.settings_on_this_day)
        Group {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_reminder), Modifier.weight(1f))
                Switch(
                    checked = reminder.enabled,
                    modifier = Modifier.testTag(REMINDER_SWITCH_TAG),
                    onCheckedChange = { on ->
                        if (!on) {
                            onReminder(reminder.copy(enabled = false))
                        } else {
                            scope.launch {
                                // Permission refused: the switch stays off and a hint explains it (AC 18).
                                val allowed = requestNotifications()
                                denied = !allowed
                                if (allowed) onReminder(reminder.copy(enabled = true))
                            }
                        }
                    },
                )
            }
            if (reminder.enabled) {
                HorizontalDivider(Modifier.padding(start = 16.dp))
                ValueRow(
                    label = stringResource(R.string.settings_reminder_time),
                    value = "%02d:%02d".format(reminder.hour, reminder.minute),
                    onClick = { pickingTime = true },
                )
            }
        }
        Footer(stringResource(R.string.settings_reminder_footer))
        if (denied) {
            Text(
                stringResource(R.string.settings_reminder_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text(stringResource(R.string.settings_open_system)) }
        }

        SectionHeader(R.string.settings_cleanup)
        Group {
            ValueRow(stringResource(R.string.settings_kept_count), keptIds.size.toString())
            HorizontalDivider(Modifier.padding(start = 16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = keptIds.isNotEmpty()) { confirmReset = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_undo),
                    contentDescription = null,
                    tint = if (keptIds.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.settings_reset_kept),
                    color = if (keptIds.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        Footer(stringResource(R.string.settings_reset_footer))

        SectionHeader(R.string.settings_about)
        Group {
            ValueRow(
                stringResource(R.string.settings_retention),
                stringResource(R.string.settings_retention_value, TrashStore.RETENTION_DAYS.toInt()),
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ValueRow(stringResource(R.string.settings_version), versionText)
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = reminder.hour, initialMinute = reminder.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    pickingTime = false
                    onReminder(reminder.copy(hour = state.hour, minute = state.minute))
                }) { Text(stringResource(R.string.media_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.media_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(pluralStringResource(R.plurals.settings_reset_title, keptIds.size, keptIds.size)) },
            text = { Text(stringResource(R.string.settings_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    keep.reset()
                }) { Text(stringResource(R.string.settings_reset_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.media_cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
        Column { content() }
    }
}

@Composable
private fun Footer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
