package com.guus.photocleaner.ui.duplicates

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guus.photocleaner.R
import com.guus.photocleaner.model.DuplicateGroup
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.DuplicateScanner
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.PhotoSource
import com.guus.photocleaner.ui.deck.EmptyState
import com.guus.photocleaner.ui.theme.DecisionColors
import com.guus.photocleaner.ui.zoom.MediaInspector
import com.guus.photocleaner.ui.zoom.mediaUri
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

/** Trash reasons for items discarded from the duplicate groups. */
const val REASON_DUPLICATE = "duplicate"
const val REASON_SIMILAR = "similar"

enum class DuplicateMode { EXACT, SIMILAR }

private const val BATCH_SIZE = 10
private const val CHANGE_DEBOUNCE_MILLIS = 1_500L

/**
 * Dubbelen: exact duplicates (metadata) and similar photos (dHash). Per group the best copy
 * is suggested to keep and the rest can be discarded. Port of iOS `DuplicatesView`.
 */
@OptIn(FlowPreview::class)
@Composable
fun DuplicatesScreen(
    source: PhotoSource,
    trash: TrashStore,
    dateText: (PhotoAsset) -> String?,
    modifier: Modifier = Modifier,
) {
    val scanner = remember(source) { DuplicateScanner(source) }
    var mode by rememberSaveable { mutableStateOf(DuplicateMode.EXACT) }
    var exact by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    var similar by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    var similarStopped by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var scanRound by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var visibleCount by remember(mode) { mutableIntStateOf(BATCH_SIZE) }
    var inspecting by remember { mutableStateOf<PhotoAsset?>(null) }

    // Exact scan: fast, on start and after every rescan or library change.
    LaunchedEffect(scanRound) {
        exact = scanner.exact(trash.trashedIds)
        similar = null
        similarStopped = false
        refreshing = false
    }
    // Similar scan: heavier, only while that mode is shown; cancelled when leaving it.
    val needSimilar = mode == DuplicateMode.SIMILAR && exact != null && similar == null && !similarStopped
    LaunchedEffect(needSimilar, scanRound) {
        if (needSimilar) {
            progress = 0f
            similar = scanner.similar(trash.trashedIds) { progress = it }
        }
    }
    LaunchedEffect(source) {
        source.changes().debounce(CHANGE_DEBOUNCE_MILLIS).collect { scanRound++ }
    }

    val resolve: (DuplicateGroup, Set<String>) -> Unit = { group, keptIds ->
        trash.markAll(
            group.all.filter { it.id !in keptIds },
            if (mode == DuplicateMode.EXACT) REASON_DUPLICATE else REASON_SIMILAR,
        )
        exact = exact?.filterNot { it.id == group.id }
        similar = similar?.filterNot { it.id == group.id }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                DuplicateMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        shape = SegmentedButtonDefaults.itemShape(index, DuplicateMode.entries.size),
                    ) {
                        Text(stringResource(if (entry == DuplicateMode.EXACT) R.string.dup_exact else R.string.dup_similar), maxLines = 1)
                    }
                }
            }
            IconButton(onClick = {
                refreshing = true
                source.invalidateCache()
                scanRound++
            }) {
                Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.dup_rescan))
            }
        }

        val groups = if (mode == DuplicateMode.EXACT) exact else similar
        when {
            exact == null || refreshing -> Loading(stringResource(if (refreshing) R.string.dup_rescanning else R.string.dup_scanning))
            needSimilar -> SimilarProgress(progress, onStop = { similarStopped = true })
            groups.isNullOrEmpty() -> EmptyState(
                stringResource(if (mode == DuplicateMode.EXACT) R.string.dup_empty_exact_title else R.string.dup_empty_similar_title),
                stringResource(if (mode == DuplicateMode.EXACT) R.string.dup_empty_exact_message else R.string.dup_empty_similar_message),
            )
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Text(
                        stringResource(R.string.dup_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(groups.take(visibleCount), key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        imageModel = source::imageModel,
                        onOpen = { inspecting = it },
                        onResolve = { kept -> resolve(group, kept) },
                    )
                }
                if (groups.size > visibleCount) {
                    item {
                        OutlinedButton(onClick = { visibleCount += BATCH_SIZE }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.dup_show_more, groups.size - visibleCount))
                        }
                    }
                }
            }
        }
    }

    inspecting?.let { asset ->
        MediaInspector(
            asset = asset,
            imageModel = source.imageModel(asset),
            contentUri = mediaUri(source, asset),
            dateText = dateText(asset),
            onKeep = null,
            onDiscard = null,
            onClose = { inspecting = null },
        )
    }
}

@Composable
private fun Loading(text: String) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            seconds++
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (seconds > 0) "${seconds}s" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SimilarProgress(progress: Float, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.dup_similar_scanning))
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onStop) { Text(stringResource(R.string.dup_stop)) }
    }
}

/** One group: tap an item to enlarge, tick the ones to keep (several allowed), discard the rest. */
@Composable
private fun GroupCard(
    group: DuplicateGroup,
    imageModel: (PhotoAsset) -> Any,
    onOpen: (PhotoAsset) -> Unit,
    onResolve: (keptIds: Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var keptIds by remember(group.id) { mutableStateOf(setOf(group.keep.id)) }
    val discard = group.all.filter { it.id !in keptIds }
    val reclaimable = discard.sumOf { it.byteSize }
    val dark = MaterialTheme.colorScheme.background == Color.Black
    val red = if (dark) DecisionColors.discardDark else DecisionColors.discardLight
    val green = if (dark) DecisionColors.keepDark else DecisionColors.keepLight

    Column {
        Text(
            stringResource(R.string.dup_header, group.count, keptIds.size, Formatter.formatShortFileSize(context, reclaimable)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(group.all, key = { it.id }) { asset ->
                        val kept = asset.id in keptIds
                        Candidate(
                            asset = asset,
                            model = imageModel(asset),
                            kept = kept,
                            borderColor = if (kept) green else red.copy(alpha = 0.7f),
                            onToggle = { keptIds = if (kept) keptIds - asset.id else keptIds + asset.id },
                            onOpen = { onOpen(asset) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onResolve(keptIds) },
                    enabled = discard.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = red, contentColor = Color.White),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dup_discard_n, discard.size))
                }
            }
        }
    }
}

@Composable
private fun Candidate(
    asset: PhotoAsset,
    model: Any,
    kept: Boolean,
    borderColor: Color,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val openLabel = stringResource(R.string.grid_open)
    Column(Modifier.width(132.dp)) {
        Box(
            Modifier
                .size(132.dp)
                .clip(shape)
                .border(if (kept) 3.dp else 1.5.dp, borderColor, shape)
                .semantics { contentDescription = openLabel }
                .clickable(onClick = onOpen),
        ) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (asset.isVideo) {
                Icon(
                    painterResource(R.drawable.ic_play_circle),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp),
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.align(Alignment.TopStart)) {
                Box(Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (kept) R.drawable.ic_check_circle else R.drawable.ic_radio_button_unchecked),
                        contentDescription = stringResource(if (kept) R.string.dup_kept_toggle else R.string.dup_discard_toggle),
                        tint = if (kept) borderColor else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        asset.folder?.let { folder ->
            Spacer(Modifier.height(3.dp))
            Text(
                folder,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}
