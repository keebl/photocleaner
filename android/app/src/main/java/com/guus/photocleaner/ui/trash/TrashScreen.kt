package com.guus.photocleaner.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.TrashItem
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.PhotoSource
import com.guus.photocleaner.ui.deck.EmptyState
import kotlinx.coroutines.launch

/**
 * The in-app trash with 30-day retention: restore, empty expired items or everything now.
 * Port of iOS `TrashView`; removal goes to Android's own trash via [systemTrash].
 */
@Composable
fun TrashScreen(
    source: PhotoSource,
    trash: TrashStore,
    systemTrash: SystemTrash,
    modifier: Modifier = Modifier,
) {
    val allItems by trash.items.collectAsState()
    // Only the active source's items (specs/android-nas.md AC 21).
    val items = remember(allItems, source) { allItems.filter { source.owns(it.id) } }
    var assets by remember { mutableStateOf<Map<String, PhotoAsset>>(emptyMap()) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(items, source) {
        assets = source.assets(items.map { it.id }).associateBy { it.id }
    }
    val remove: (List<TrashItem>) -> Unit = { chosen ->
        scope.launch { moveToSystemTrash(chosen, source, trash, systemTrash) }
    }
    val expired = trash.expiredItems().filter { source.owns(it.id) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tab_trash),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = items.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_more_horiz), stringResource(R.string.trash_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trash_empty_expired, expired.size)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                        enabled = expired.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            remove(expired)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trash_delete_all)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                        onClick = {
                            menuOpen = false
                            confirmAll = true
                        },
                    )
                }
            }
        }

        if (items.isEmpty()) {
            EmptyState(
                stringResource(R.string.trash_empty_title),
                stringResource(R.string.trash_empty_message, TrashStore.RETENTION_DAYS.toInt()),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                        Column {
                            items.forEach { item ->
                                TrashRow(item, assets[item.id], source, trash) { trash.restore(item.id) }
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.trash_footer, TrashStore.RETENTION_DAYS.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(pluralStringResource(R.plurals.trash_delete_all_title, items.size, items.size)) },
            text = { Text(stringResource(R.string.trash_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    remove(items)
                }) { Text(stringResource(R.string.trash_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.media_cancel)) } },
        )
    }
}

@Composable
private fun TrashRow(item: TrashItem, asset: PhotoAsset?, source: PhotoSource, trash: TrashStore, onRestore: () -> Unit) {
    val days = trash.daysLeft(item)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (asset != null) {
                AsyncImage(model = source.imageModel(asset), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(reasonLabel(item.reason)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                if (days == 0) stringResource(R.string.trash_expires_today) else pluralStringResource(R.plurals.trash_days_left, days, days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onRestore) { Text(stringResource(R.string.trash_restore)) }
    }
}

/** Localised label for a trash reason code (see the REASON_* constants). */
fun reasonLabel(reason: String): Int = when (reason) {
    "on_this_day" -> R.string.reason_on_this_day
    "random" -> R.string.reason_random
    "video" -> R.string.reason_video
    "duplicate" -> R.string.reason_duplicate
    "similar" -> R.string.reason_similar
    else -> R.string.reason_other
}

/**
 * Asked once when the app opens and items are older than the retention period (spec AC 14):
 * confirming opens Android's trash dialog for those items; "Later" leaves them for next time.
 */
@Composable
fun ExpiredTrashPrompt(source: PhotoSource, trash: TrashStore, systemTrash: SystemTrash) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val items by trash.items.collectAsState()
    val expired = remember(items, source) { trash.expiredItems().filter { source.owns(it.id) } }
    val scope = rememberCoroutineScope()
    if (dismissed || expired.isEmpty()) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(pluralStringResource(R.plurals.trash_expired_title, expired.size, expired.size)) },
        text = { Text(stringResource(R.string.trash_expired_message, TrashStore.RETENTION_DAYS.toInt())) },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                scope.launch { moveToSystemTrash(expired, source, trash, systemTrash) }
            }) { Text(stringResource(R.string.trash_expired_confirm)) }
        },
        dismissButton = { TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.trash_expired_later)) } },
    )
}
