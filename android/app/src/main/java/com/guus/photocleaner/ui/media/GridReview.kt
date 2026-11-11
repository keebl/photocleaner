package com.guus.photocleaner.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.ui.deck.Decision
import com.guus.photocleaner.ui.deck.EmptyState
import com.guus.photocleaner.ui.deck.Inspector
import com.guus.photocleaner.ui.deck.NextButton
import com.guus.photocleaner.ui.theme.DecisionColors

private val GridSpacing = 3.dp
private val TileShape = RoundedCornerShape(8.dp)

/**
 * Grid for bulk cleanup: select several items with the circle, then discard or keep them in
 * one action, with undo of the last bulk action. Port of iOS `GridReviewView`.
 */
@Composable
fun GridReview(
    assets: List<PhotoAsset>,
    decidedIds: Set<String>,
    imageModel: (PhotoAsset) -> Any,
    dateText: (PhotoAsset) -> String?,
    itemNoun: String,
    emptyTitle: String,
    emptyMessage: String,
    onDiscard: (List<PhotoAsset>) -> Unit,
    onKeep: (List<PhotoAsset>) -> Unit,
    onUndo: (List<Decision>) -> Unit,
    modifier: Modifier = Modifier,
    nextLabel: String? = null,
    onNext: (() -> Unit)? = null,
    inspector: Inspector? = null,
) {
    val queue = assets.filter { it.id !in decidedIds }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var lastAction by remember { mutableStateOf(emptyList<Decision>()) }
    var inspecting by remember { mutableStateOf<PhotoAsset?>(null) }

    inspecting?.let { asset ->
        inspector?.invoke(
            asset,
            {
                inspecting = null
                selected = selected - asset.id
                onKeep(listOf(asset))
            },
            {
                inspecting = null
                selected = selected - asset.id
                onDiscard(listOf(asset))
            },
            { inspecting = null },
        )
    }

    val undo = {
        onUndo(lastAction)
        lastAction = emptyList()
    }

    if (queue.isEmpty()) {
        EmptyState(emptyTitle, emptyMessage, modifier) {
            NextButton(nextLabel, onNext)
            if (lastAction.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = undo) { Text(stringResource(R.string.grid_undo)) }
            }
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Two rows of two fit on screen; more items scroll.
            val tileHeight = maxOf(120.dp, (maxHeight - GridSpacing * 3) / 2)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(GridSpacing),
                horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                verticalArrangement = Arrangement.spacedBy(GridSpacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(queue, key = { it.id }) { asset ->
                    val isSelected = asset.id in selected
                    Tile(
                        asset = asset,
                        model = imageModel(asset),
                        dateText = dateText(asset),
                        selected = isSelected,
                        height = tileHeight,
                        onToggle = { selected = if (isSelected) selected - asset.id else selected + asset.id },
                        onOpen = { if (inspector != null) inspecting = asset },
                    )
                }
            }
        }
        ActionBar(
            total = queue.size,
            selectedCount = selected.size,
            itemNoun = itemNoun,
            canUndo = lastAction.isNotEmpty(),
            onDiscard = {
                val chosen = queue.filter { it.id in selected }
                onDiscard(chosen)
                lastAction = chosen.map { Decision(it, kept = false) }
                selected = emptySet()
            },
            onKeep = {
                val chosen = queue.filter { it.id in selected }
                onKeep(chosen)
                lastAction = chosen.map { Decision(it, kept = true) }
                selected = emptySet()
            },
            onToggleAll = {
                selected = if (selected.size == queue.size) emptySet() else queue.mapTo(HashSet()) { it.id }
            },
            onUndo = undo,
        )
    }
}

@Composable
private fun Tile(
    asset: PhotoAsset,
    model: Any,
    dateText: String?,
    selected: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val openLabel = stringResource(R.string.grid_open)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics { contentDescription = openLabel }
            .clickable(onClick = onOpen),
    ) {
        AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (selected) {
            Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.25f)).border(3.dp, accent, TileShape))
        }
        if (dateText != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        if (asset.isVideo) {
            Icon(
                painterResource(R.drawable.ic_play_circle),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp),
            )
        }
        // Top right, away from the left screen edge (system back gesture), with a large touch target.
        IconButton(onClick = onToggle, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Box(
                Modifier.size(30.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_radio_button_unchecked),
                    contentDescription = stringResource(if (selected) R.string.grid_deselect else R.string.grid_select),
                    tint = if (selected) accent else Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionBar(
    total: Int,
    selectedCount: Int,
    itemNoun: String,
    canUndo: Boolean,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
    onToggleAll: () -> Unit,
    onUndo: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background == Color.Black
    val red = if (dark) DecisionColors.discardDark else DecisionColors.discardLight
    val green = if (dark) DecisionColors.keepDark else DecisionColors.keepLight
    val hasSelection = selectedCount > 0
    val allSelected = selectedCount == total

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Always present (disabled without a selection) so the grid does not jump.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDiscard,
                    enabled = hasSelection,
                    colors = ButtonDefaults.buttonColors(containerColor = red, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasSelection) stringResource(R.string.grid_discard_n, selectedCount) else stringResource(R.string.grid_discard))
                }
                OutlinedButton(
                    onClick = onKeep,
                    enabled = hasSelection,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = green),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasSelection) stringResource(R.string.grid_keep_n, selectedCount) else stringResource(R.string.grid_keep))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleAll) {
                    Text(stringResource(if (allSelected) R.string.grid_deselect_all else R.string.grid_select_all))
                }
                Spacer(Modifier.weight(1f))
                when {
                    hasSelection -> Text(
                        stringResource(R.string.grid_selected_count, selectedCount, total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    canUndo -> TextButton(onClick = onUndo) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.grid_undo))
                    }
                    else -> Text(
                        stringResource(R.string.grid_count, total, itemNoun),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
