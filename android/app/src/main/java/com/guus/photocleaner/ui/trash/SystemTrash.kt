package com.guus.photocleaner.ui.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.TrashItem
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.PhotoSource
import kotlinx.coroutines.CompletableDeferred

/** Moves items to Android's system trash; true when done, false when the user cancelled. */
fun interface SystemTrash {
    suspend fun moveToSystemTrash(assets: List<PhotoAsset>): Boolean
}

/**
 * [SystemTrash] backed by MediaStore's trash request: Android shows its own confirmation
 * dialog, and the call suspends until the user confirms or cancels it.
 */
@Composable
fun rememberSystemTrash(source: PhotoSource): SystemTrash {
    val pending = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending[0]?.complete(result.resultCode == Activity.RESULT_OK)
        pending[0] = null
    }
    return remember(source, launcher) {
        SystemTrash { assets ->
            val request = source.createTrashRequest(assets) ?: return@SystemTrash true
            val answer = CompletableDeferred<Boolean>()
            pending[0] = answer
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            answer.await()
        }
    }
}

/**
 * Removes trash entries from the device: existing items go through [systemTrash] and are
 * forgotten once confirmed; entries whose item no longer exists are forgotten right away.
 * Cancelling leaves everything in the in-app trash (spec AC 15).
 */
suspend fun moveToSystemTrash(
    items: List<TrashItem>,
    source: PhotoSource,
    trash: TrashStore,
    systemTrash: SystemTrash,
) {
    if (items.isEmpty()) return
    val ids = items.map { it.id }
    val assets = source.assets(ids)
    val existing = assets.mapTo(HashSet()) { it.id }
    trash.forget(ids.filter { it !in existing })
    if (assets.isNotEmpty() && systemTrash.moveToSystemTrash(assets)) {
        trash.forget(existing)
    }
}
