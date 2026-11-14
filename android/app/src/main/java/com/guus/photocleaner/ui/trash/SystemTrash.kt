package com.guus.photocleaner.ui.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.TrashItem
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.Deletion
import com.guus.photocleaner.source.PhotoSource
import com.guus.photocleaner.source.nas.NasErrors
import com.guus.photocleaner.ui.nas.nasErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Removes items from the device for good, after the in-app trash: through Android's system
 * trash, or for a NAS permanently (no system trash there, ADR-0003). True when done, false
 * when the user cancelled or it failed.
 */
fun interface SystemTrash {
    suspend fun moveToSystemTrash(assets: List<PhotoAsset>): Boolean
}

/**
 * [SystemTrash] for [source]. MediaStore: Android shows its own confirmation dialog, and the
 * call suspends until the user confirms or cancels it. NAS (specs/android-nas.md AC 19–20):
 * the app asks itself whether to delete permanently, then deletes on the NAS; a failure is
 * shown and leaves the items in the in-app trash.
 */
@Composable
fun rememberSystemTrash(source: PhotoSource): SystemTrash {
    val pending = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending[0]?.complete(result.resultCode == Activity.RESULT_OK)
        pending[0] = null
    }
    var confirm by remember { mutableStateOf<Pair<Int, CompletableDeferred<Boolean>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorText = nasErrorMessageProvider()

    confirm?.let { (count, answer) ->
        AlertDialog(
            onDismissRequest = {
                confirm = null
                answer.complete(false)
            },
            title = { Text(stringResource(R.string.nas_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.nas_delete_message, count, count)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    answer.complete(true)
                }) { Text(stringResource(R.string.trash_expired_confirm_nas), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirm = null
                    answer.complete(false)
                }) { Text(stringResource(R.string.media_cancel)) }
            },
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(stringResource(R.string.nas_error_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.media_ok)) } },
        )
    }

    return remember(source, launcher) {
        if (source.deletion == Deletion.PERMANENT) {
            SystemTrash { assets ->
                val answer = CompletableDeferred<Boolean>()
                confirm = assets.size to answer
                if (!answer.await()) return@SystemTrash false
                try {
                    source.deletePermanently(assets)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = errorText(NasErrors.wrap(e).error)
                    false
                }
            }
        } else {
            SystemTrash { assets ->
                val request = source.createTrashRequest(assets) ?: return@SystemTrash true
                val answer = CompletableDeferred<Boolean>()
                pending[0] = answer
                launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                answer.await()
            }
        }
    }
}

@Composable
private fun nasErrorMessageProvider(): (com.guus.photocleaner.source.nas.NasError) -> String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return { nasErrorMessage(context, it) }
}

/**
 * Removes trash entries of [source] from the device: existing items go through [systemTrash]
 * and are forgotten once done; entries whose item no longer exists are forgotten right away.
 * Entries of another source are left alone (spec AC 21). Cancelling leaves everything in the
 * in-app trash (spec AC 15).
 */
suspend fun moveToSystemTrash(
    items: List<TrashItem>,
    source: PhotoSource,
    trash: TrashStore,
    systemTrash: SystemTrash,
) {
    val ids = items.map { it.id }.filter(source::owns)
    if (ids.isEmpty()) return
    val assets = source.assets(ids)
    val existing = assets.mapTo(HashSet()) { it.id }
    trash.forget(ids.filter { it !in existing })
    if (assets.isNotEmpty() && systemTrash.moveToSystemTrash(assets)) {
        source.invalidateCache()
        trash.forget(existing)
    }
}
