package com.guus.photocleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.PhotoSource
import com.guus.photocleaner.ui.deck.EmptyState
import com.guus.photocleaner.ui.deck.Inspector
import com.guus.photocleaner.ui.deck.ReviewDeck
import com.guus.photocleaner.ui.deck.YearLabel
import com.guus.photocleaner.ui.media.Browse
import com.guus.photocleaner.ui.media.CategoryFilter
import com.guus.photocleaner.ui.media.GridReview
import com.guus.photocleaner.ui.media.MediaChips
import com.guus.photocleaner.ui.media.MediaSelection
import com.guus.photocleaner.ui.media.MediaTab
import com.guus.photocleaner.ui.media.MediaTopBar
import com.guus.photocleaner.ui.media.PeriodBar
import com.guus.photocleaner.ui.zoom.MediaInspector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Trash reasons, stored with each discarded item. */
const val REASON_ON_THIS_DAY = "on_this_day"
const val REASON_RANDOM = "random"
const val REASON_VIDEO = "video"

/** Media tab choices that are remembered between app starts (iOS `@AppStorage`). */
data class MediaSettings(
    val tab: MediaTab = MediaTab.PHOTOS,
    val browse: Browse = Browse.DAY,
    val category: CategoryFilter = CategoryFilter.ALL,
    val oldestFirst: Boolean = false,
)

/**
 * Media tab: kind, browse mode, category and period in the header; the swipe deck or the
 * grid below. Port of iOS `MediaView`. Starts on today ("Op deze dag"); the grid is a
 * per-session choice, like on iOS.
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
    onOpenTrash: () -> Unit = {},
    initialSettings: MediaSettings = MediaSettings(),
    onSettingsChange: (MediaSettings) -> Unit = {},
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var settings by remember { mutableStateOf(initialSettings) }
    var dateEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    var randomSeed by rememberSaveable { mutableLongStateOf(System.nanoTime()) }
    var library by remember { mutableStateOf<List<PhotoAsset>?>(null) }
    var reloads by remember { mutableIntStateOf(0) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val keptIds by keep.ids.collectAsState()
    val trashItems by trash.items.collectAsState()
    val cleaned by trash.totalCleaned.collectAsState()
    val decidedIds = remember(keptIds, trashItems) { keptIds + trashItems.map { it.id } }

    fun update(change: MediaSettings) {
        if (change.browse == Browse.RANDOM && settings.browse != Browse.RANDOM) randomSeed = System.nanoTime()
        settings = change
        onSettingsChange(change)
    }

    LaunchedEffect(source, reloads) { library = source.fetchAll() }
    // Large libraries send bursts of change notifications; reload once they settle.
    LaunchedEffect(source) { source.changes().debounce(CHANGE_DEBOUNCE_MILLIS).collect { reloads++ } }

    val locale = LocalConfiguration.current.locales[0]
    val showPeriod = settings.tab != MediaTab.DUPLICATES && settings.browse != Browse.RANDOM

    Column(modifier.fillMaxSize()) {
        MediaTopBar(
            showGridToggle = settings.tab != MediaTab.DUPLICATES,
            gridMode = gridMode,
            onToggleGrid = { gridMode = !gridMode },
            cleanedCount = cleaned,
            onOpenTrash = onOpenTrash,
        )
        MediaChips(
            tab = settings.tab,
            browse = settings.browse,
            category = settings.category,
            onTab = { update(settings.copy(tab = it)) },
            onBrowse = { update(settings.copy(browse = it)) },
            onCategory = { update(settings.copy(category = it)) },
        )
        Spacer(Modifier.height(4.dp))
        val title = periodTitle(date, settings.browse, locale)
        if (showPeriod) {
            PeriodBar(
                title = title,
                date = date,
                showBackToNow = !MediaSelection.isCurrentPeriod(date, settings.browse, today),
                backToNowLabel = when (settings.browse) {
                    Browse.MONTH -> R.string.media_this_month
                    Browse.YEAR -> R.string.media_this_year
                    else -> R.string.media_today
                },
                oldestFirst = settings.oldestFirst,
                onShift = { dateEpochDay = MediaSelection.shift(date, settings.browse, it).toEpochDay() },
                onPickDate = { dateEpochDay = it.toEpochDay() },
                onBackToNow = { dateEpochDay = today.toEpochDay() },
                onOldestFirst = { update(settings.copy(oldestFirst = it)) },
            )
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val all = library
            when {
                all == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                settings.tab == MediaTab.DUPLICATES ->
                    EmptyState(stringResource(R.string.media_tab_duplicates), stringResource(R.string.media_duplicates_placeholder))
                else -> ReviewContent(
                    all = all,
                    settings = settings,
                    date = date,
                    title = title,
                    gridMode = gridMode,
                    randomSeed = randomSeed,
                    decidedIds = decidedIds,
                    source = source,
                    trash = trash,
                    keep = keep,
                    zone = zone,
                    locale = locale,
                    swipeHintSeen = swipeHintSeen,
                    onSwipeHintSeen = onSwipeHintSeen,
                    onGoTo = { dateEpochDay = it.toEpochDay() },
                )
            }
        }
    }
}

@Composable
private fun ReviewContent(
    all: List<PhotoAsset>,
    settings: MediaSettings,
    date: LocalDate,
    title: String,
    gridMode: Boolean,
    randomSeed: Long,
    decidedIds: Set<String>,
    source: PhotoSource,
    trash: TrashStore,
    keep: KeepStore,
    zone: ZoneId,
    locale: Locale,
    swipeHintSeen: Boolean,
    onSwipeHintSeen: () -> Unit,
    onGoTo: (LocalDate) -> Unit,
) {
    val (tab, browse, category, oldestFirst) = settings
    val items = remember(all, settings, date, randomSeed, zone) {
        MediaSelection.select(all, tab, browse, category, date, zone, oldestFirst, randomSeed)
    }
    val next = remember(all, settings, date, decidedIds, zone) {
        MediaSelection.nextPeriod(MediaSelection.base(all, tab, category), decidedIds, browse, date, zone)
    }
    val reason = when {
        tab == MediaTab.VIDEOS -> REASON_VIDEO
        browse == Browse.RANDOM -> REASON_RANDOM
        else -> REASON_ON_THIS_DAY
    }
    val noun = stringResource(if (tab == MediaTab.VIDEOS) R.string.media_noun_videos else R.string.media_noun_photos)
    val emptyTitle = stringResource(
        when {
            tab == MediaTab.VIDEOS -> R.string.media_empty_title_videos
            browse == Browse.RANDOM -> R.string.media_empty_title_photos
            else -> R.string.media_empty_title
        },
    )
    val emptyMessage = if (browse == Browse.RANDOM) {
        stringResource(R.string.media_empty_message_random, noun)
    } else {
        stringResource(R.string.media_empty_message, noun, title)
    }
    val nextLabel = when (browse) {
        Browse.DAY -> stringResource(R.string.media_next_day, noun)
        Browse.MONTH -> stringResource(R.string.media_next_month, noun)
        Browse.YEAR -> stringResource(R.string.media_next_year, noun)
        Browse.RANDOM -> null
    }
    val onNext = next?.let { target -> { onGoTo(target) } }
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    val dateText: (PhotoAsset) -> String? = { asset ->
        asset.browseDateMillis?.let { Instant.ofEpochMilli(it).atZone(zone).format(dateFormat) }
    }
    val inspector: Inspector = { asset, onKeep, onDiscard, onClose ->
        MediaInspector(
            asset = asset,
            imageModel = source.imageModel(asset),
            contentUri = source.contentUri(asset),
            dateText = dateText(asset),
            onKeep = onKeep,
            onDiscard = onDiscard,
            onClose = onClose,
        )
    }

    // A fresh deck or grid for every kind/mode/category/period/order (iOS `.id(...)`).
    key(tab, browse, category, if (browse == Browse.RANDOM) randomSeed else date, oldestFirst, gridMode) {
        if (gridMode) {
            GridReview(
                assets = items,
                decidedIds = decidedIds,
                imageModel = source::imageModel,
                dateText = dateText,
                itemNoun = noun,
                emptyTitle = emptyTitle,
                emptyMessage = emptyMessage,
                onDiscard = { trash.markAll(it, reason) },
                onKeep = { chosen -> chosen.forEach { keep.keep(it.id) } },
                onUndo = { decisions ->
                    decisions.forEach { if (it.kept) keep.unkeep(it.asset.id) else trash.restore(it.asset.id) }
                },
                nextLabel = nextLabel,
                onNext = onNext,
                inspector = inspector,
            )
        } else {
            ReviewDeck(
                assets = items,
                decidedIds = decidedIds,
                imageModel = source::imageModel,
                badge = { asset ->
                    if (browse == Browse.RANDOM || browse == Browse.YEAR) {
                        dateText(asset)
                    } else {
                        yearBadge(asset, date.year, zone)
                    }
                },
                emptyTitle = emptyTitle,
                emptyMessage = emptyMessage,
                swipeHintSeen = swipeHintSeen,
                onSwipeHintSeen = onSwipeHintSeen,
                onKeep = { keep.keep(it.id) },
                onDiscard = { trash.mark(it, reason) },
                onUndo = { if (it.kept) keep.unkeep(it.asset.id) else trash.restore(it.asset.id) },
                nextLabel = nextLabel,
                onNext = onNext,
                shareUri = source::contentUri,
                inspector = inspector,
            )
        }
    }
}

@Composable
private fun yearBadge(asset: PhotoAsset, referenceYear: Int, zone: ZoneId): String? =
    when (val label = YearLabel.of(asset.browseDateMillis, referenceYear, zone)) {
        null -> null
        YearLabel.ThisYear -> stringResource(R.string.deck_this_year)
        is YearLabel.YearsAgo -> pluralStringResource(R.plurals.deck_years_ago, label.years, label.years, label.year)
        is YearLabel.Year -> label.year.toString()
    }

/** "10 november", "November" or "2026" for the period bar and empty-state message. */
private fun periodTitle(date: LocalDate, browse: Browse, locale: Locale): String = when (browse) {
    Browse.DAY, Browse.RANDOM -> date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
    Browse.MONTH -> date.format(DateTimeFormatter.ofPattern("LLLL", locale)).replaceFirstChar { it.titlecase(locale) }
    Browse.YEAR -> date.year.toString()
}

private const val CHANGE_DEBOUNCE_MILLIS = 1_500L
