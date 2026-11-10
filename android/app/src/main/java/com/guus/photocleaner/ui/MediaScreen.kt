package com.guus.photocleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.PhotoSource
import com.guus.photocleaner.ui.deck.Decision
import com.guus.photocleaner.ui.deck.ReviewDeck
import com.guus.photocleaner.ui.deck.YearLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Reason stored with items discarded from the "on this day" deck. */
const val REASON_ON_THIS_DAY = "on_this_day"

/**
 * Media tab: the "Op deze dag" deck for [today]. Modes, filters and the grid follow in spec
 * step 7. Shows items taken on today's month/day in earlier years (spec AC 4).
 */
@OptIn(FlowPreview::class)
@Composable
fun MediaScreen(
    source: PhotoSource,
    trash: TrashStore,
    keep: KeepStore,
    swipeHintSeen: Boolean,
    onSwipeHintSeen: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var items by remember { mutableStateOf<List<PhotoAsset>?>(null) }
    var reloads by remember { mutableIntStateOf(0) }
    val keptIds by keep.ids.collectAsState()
    val trashItems by trash.items.collectAsState()
    val decidedIds = remember(keptIds, trashItems) { keptIds + trashItems.map { it.id } }

    LaunchedEffect(source, today, reloads) {
        items = source.fetchOnThisDay(today.monthValue, today.dayOfMonth).filter { asset ->
            val millis = asset.browseDateMillis ?: return@filter false
            Instant.ofEpochMilli(millis).atZone(zone).year < today.year
        }
    }
    // Large libraries send bursts of change notifications; reload once they settle.
    LaunchedEffect(source) {
        source.changes().debounce(CHANGE_DEBOUNCE_MILLIS).collect { reloads++ }
    }

    val locale = LocalConfiguration.current.locales[0]
    val title = remember(today, locale) { today.format(DateTimeFormatter.ofPattern("d MMMM", locale)) }

    Column(modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val loaded = items
            if (loaded == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                ReviewDeck(
                    assets = loaded,
                    decidedIds = decidedIds,
                    imageModel = source::imageModel,
                    badge = { asset ->
                        when (val label = YearLabel.of(asset.browseDateMillis, today.year, zone)) {
                            null -> null
                            YearLabel.ThisYear -> stringResource(R.string.deck_this_year)
                            is YearLabel.YearsAgo ->
                                pluralStringResource(R.plurals.deck_years_ago, label.years, label.years, label.year)
                            is YearLabel.Year -> label.year.toString()
                        }
                    },
                    emptyTitle = stringResource(R.string.media_empty_title),
                    emptyMessage = stringResource(R.string.media_empty_message, title),
                    swipeHintSeen = swipeHintSeen,
                    onSwipeHintSeen = onSwipeHintSeen,
                    onKeep = { keep.keep(it.id) },
                    onDiscard = { trash.mark(it, REASON_ON_THIS_DAY) },
                    onUndo = { decision: Decision ->
                        if (decision.kept) keep.unkeep(decision.asset.id) else trash.restore(decision.asset.id)
                    },
                )
            }
        }
    }
}

private const val CHANGE_DEBOUNCE_MILLIS = 1_500L
