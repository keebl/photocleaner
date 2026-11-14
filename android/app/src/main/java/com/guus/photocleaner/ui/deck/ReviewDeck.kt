package com.guus.photocleaner.ui.deck

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import coil3.compose.AsyncImage
import com.guus.photocleaner.ui.zoom.MediaUri
import com.guus.photocleaner.ui.zoom.ShareButton
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.ui.theme.DecisionColors
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Test tag of the top card in the deck. */
const val DECK_CARD_TAG = "deckCard"

/** Full-screen view of one item with keep/discard; provided by the caller (zoom or video). */
typealias Inspector = @Composable (
    asset: PhotoAsset,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit,
) -> Unit

/** One keep/discard decision, so it can be undone. */
data class Decision(val asset: PhotoAsset, val kept: Boolean)

private val CardShape = RoundedCornerShape(22.dp)
private val SwipeThreshold = 90.dp

/**
 * Swipe deck: one item at a time, right = keep, left = discard, with undo of the last
 * decision. Port of iOS `ReviewDeck`. The caller owns the stores; [decidedIds] are the kept
 * and trashed ids, which never appear in the queue; [keptIds] is the kept part of them.
 */
@Composable
fun ReviewDeck(
    assets: List<PhotoAsset>,
    decidedIds: Set<String>,
    keptIds: Set<String>,
    imageModel: (PhotoAsset) -> Any,
    badge: @Composable (PhotoAsset) -> String?,
    emptyTitle: String,
    emptyMessage: String,
    swipeHintSeen: Boolean,
    onSwipeHintSeen: () -> Unit,
    onKeep: (PhotoAsset) -> Unit,
    onDiscard: (PhotoAsset) -> Unit,
    onUndo: (Decision) -> Unit,
    modifier: Modifier = Modifier,
    nextLabel: String? = null,
    onNext: (() -> Unit)? = null,
    shareUri: (PhotoAsset) -> MediaUri? = { null },
    inspector: Inspector? = null,
) {
    // Keyed on ids, so a reload with the same items keeps the undo history.
    val assetIds = remember(assets) { assets.map { it.id } }
    val history = remember(assetIds) { mutableStateListOf<Decision>() }
    val queue = assets.filter { it.id !in decidedIds }
    val sessionStart = remember(assetIds) { queue.size }
    val current = queue.firstOrNull()
    var inspecting by remember { mutableStateOf<PhotoAsset?>(null) }

    val decide: (PhotoAsset, Boolean) -> Unit = { asset, kept ->
        onSwipeHintSeen()
        if (kept) onKeep(asset) else onDiscard(asset)
        history += Decision(asset, kept)
    }
    val undo: () -> Unit = {
        history.removeLastOrNull()?.let(onUndo)
    }

    Box(modifier.fillMaxSize()) {
        when {
            assets.isEmpty() -> EmptyState(emptyTitle, emptyMessage) { NextButton(nextLabel, onNext) }
            current == null -> DoneState(assets, keptIds, canUndo = history.isNotEmpty(), onUndo = undo) {
                NextButton(nextLabel, onNext)
            }
            else -> {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The queue can grow (undo, new photos); keep the total at least as large.
                    Progress(total = maxOf(sessionStart, queue.size, 1), remaining = queue.size)
                    Spacer(Modifier.height(14.dp))
                    Legend()
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (queue.size > 1) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { scaleX = 0.95f; scaleY = 0.95f }
                                    .offset(y = 16.dp)
                                    .clip(CardShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                        key(current.id) {
                            DeckCard(
                                asset = current,
                                imageModel = imageModel(current),
                                badge = badge(current),
                                shareUri = shareUri(current),
                                onTap = { if (inspector != null) inspecting = current },
                                onDecide = { kept -> decide(current, kept) },
                            )
                        }
                        if (!swipeHintSeen) SwipeHint(onDismiss = onSwipeHintSeen)
                    }
                    history.lastOrNull()?.let { last ->
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = undo) {
                            Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (last.kept) R.string.deck_undo_kept else R.string.deck_undo_discarded))
                        }
                    }
                }
            }
        }
        inspecting?.let { asset ->
            inspector?.invoke(
                asset,
                {
                    inspecting = null
                    decide(asset, true)
                },
                {
                    inspecting = null
                    decide(asset, false)
                },
                { inspecting = null },
            )
        }
    }
}

@Composable
private fun Progress(total: Int, remaining: Int) {
    val decided = (total - remaining).coerceIn(0, total)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.deck_progress, minOf(decided + 1, total), total),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { decided.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun Legend() {
    val discard = discardColor()
    val keep = keepColor()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_arrow_back), null, tint = discard, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.deck_discard), color = discard, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.deck_keep), color = keep, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))
        Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = keep, modifier = Modifier.size(16.dp))
    }
}

/** The top card: drag horizontally past the threshold to decide, otherwise it springs back. */
@Composable
private fun DeckCard(
    asset: PhotoAsset,
    imageModel: Any,
    badge: String?,
    shareUri: MediaUri?,
    onTap: () -> Unit,
    onDecide: (kept: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(0f) }
    var committing by remember { mutableStateOf(false) }
    val keepLabel = stringResource(R.string.deck_keep)
    val discardLabel = stringResource(R.string.deck_discard)
    val description = stringResource(
        if (asset.isVideo) R.string.deck_card_description_video else R.string.deck_card_description,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val thresholdPx = with(density) { SwipeThreshold.toPx() }
        val flyOutPx = with(density) { maxWidth.toPx() } * 1.5f

        fun commit(kept: Boolean) {
            if (committing) return
            committing = true
            scope.launch {
                offset.animateTo(if (kept) flyOutPx else -flyOutPx, tween(durationMillis = 220))
                onDecide(kept)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .testTag(DECK_CARD_TAG)
                .semantics {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction(keepLabel) { commit(true); true },
                        CustomAccessibilityAction(discardLabel) { commit(false); true },
                    )
                }
                .graphicsLayer {
                    translationX = offset.value
                    rotationZ = offset.value / density.density / 22f
                }
                .clickable(onClick = onTap)
                .pointerInput(asset.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offset.value > thresholdPx -> commit(true)
                                offset.value < -thresholdPx -> commit(false)
                                else -> scope.launch { offset.animateTo(0f, spring()) }
                            }
                        },
                        onDragCancel = { if (!committing) scope.launch { offset.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!committing) {
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + dragAmount) }
                            }
                        },
                    )
                }
                .clip(CardShape)
                .background(Color.Black.copy(alpha = 0.9f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CardShape),
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (badge != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            if (offset.value == 0f) {
                if (asset.isVideo) {
                    Icon(
                        painterResource(R.drawable.ic_play_circle),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(64.dp),
                    )
                } else {
                    // Subtle hint that the photo can be enlarged.
                    Icon(
                        painterResource(R.drawable.ic_open_in_full),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                            .padding(6.dp)
                            .size(20.dp),
                    )
                }
                if (shareUri != null) {
                    ShareButton(shareUri, asset.isVideo, Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
            DecisionStamp(offsetPx = offset.value, thresholdPx = thresholdPx)
        }
    }
}

/** Green check (keep) or red bin (discard) that fades in while dragging. */
@Composable
private fun DecisionStamp(offsetPx: Float, thresholdPx: Float) {
    if (offsetPx == 0f) return
    val progress = (abs(offsetPx) / thresholdPx).coerceIn(0f, 1f)
    val kept = offsetPx > 0
    val color = if (kept) keepColor() else discardColor()
    Box(
        Modifier.fillMaxSize().background(color.copy(alpha = progress * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (kept) R.drawable.ic_check_circle else R.drawable.ic_delete),
            contentDescription = null,
            tint = Color.White.copy(alpha = progress),
            modifier = Modifier.size(84.dp),
        )
    }
}

/** One-time explanation over the first card. */
@Composable
private fun SwipeHint(onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(CardShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            HintArrow(R.drawable.ic_arrow_back, R.string.deck_discard, discardColor())
            HintArrow(R.drawable.ic_arrow_forward, R.string.deck_keep, keepColor())
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_open_in_full), null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.deck_hint_tap), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.deck_hint_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDismiss) { Text(stringResource(R.string.deck_hint_ok)) }
    }
}

@Composable
private fun HintArrow(icon: Int, label: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(label), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** "Next day/month/year with photos" button for the empty and done states. */
@Composable
fun NextButton(label: String?, onNext: (() -> Unit)?) {
    if (label == null || onNext == null) return
    Spacer(Modifier.height(16.dp))
    Button(onClick = onNext) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null)
    }
}

/**
 * Summary of every item in this period, all decided: counted from the stores rather than this
 * session, so a day finished earlier does not read "0 kept · 0 discarded". Items already moved
 * to Android's trash are no longer in [assets] and so not counted.
 */
@Composable
private fun DoneState(
    assets: List<PhotoAsset>,
    keptIds: Set<String>,
    canUndo: Boolean,
    onUndo: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val kept = assets.count { it.id in keptIds }
    val discarded = assets.filterNot { it.id in keptIds }
    val freed = discarded.sumOf { it.byteSize }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = keepColor(),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.deck_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.deck_done_summary, kept, discarded.size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (freed > 0) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_hard_drive), null, tint = keepColor(), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.deck_freed, Formatter.formatShortFileSize(context, freed)),
                    color = keepColor(),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        actions()
        if (canUndo) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onUndo) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.deck_undo_last))
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_auto_awesome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        actions()
    }
}

@Composable
private fun keepColor(): Color =
    if (MaterialTheme.colorScheme.background == Color.Black) DecisionColors.keepDark else DecisionColors.keepLight

@Composable
private fun discardColor(): Color =
    if (MaterialTheme.colorScheme.background == Color.Black) DecisionColors.discardDark else DecisionColors.discardLight
