package com.guus.photocleaner.ui.media

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guus.photocleaner.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One option in a header dropdown. */
data class MenuOption<T>(val value: T, @param:StringRes val label: Int, @param:DrawableRes val icon: Int)

val TabOptions = listOf(
    MenuOption(MediaTab.PHOTOS, R.string.media_tab_photos, R.drawable.ic_image),
    MenuOption(MediaTab.VIDEOS, R.string.media_tab_videos, R.drawable.ic_videocam),
    MenuOption(MediaTab.DUPLICATES, R.string.media_tab_duplicates, R.drawable.ic_filter_none),
)

val BrowseOptions = listOf(
    MenuOption(Browse.RANDOM, R.string.media_browse_random, R.drawable.ic_shuffle),
    MenuOption(Browse.DAY, R.string.media_browse_day, R.drawable.ic_calendar_today),
    MenuOption(Browse.MONTH, R.string.media_browse_month, R.drawable.ic_calendar_today),
    MenuOption(Browse.YEAR, R.string.media_browse_year, R.drawable.ic_calendar_today),
)

val CategoryOptions = listOf(
    MenuOption(CategoryFilter.ALL, R.string.media_category_all, R.drawable.ic_grid_view),
    MenuOption(CategoryFilter.SCREENSHOTS, R.string.media_category_screenshots, R.drawable.ic_screenshot),
    MenuOption(CategoryFilter.FAVORITES, R.string.media_category_favorites, R.drawable.ic_favorite),
    MenuOption(CategoryFilter.PANORAMAS, R.string.media_category_panoramas, R.drawable.ic_panorama),
)

/** Top row: grid/deck toggle and the number of items in the trash, which opens it. */
@Composable
fun MediaTopBar(
    showGridToggle: Boolean,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    trashCount: Int,
    onOpenTrash: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        if (showGridToggle) {
            IconButton(onClick = onToggleGrid) {
                Icon(
                    painterResource(if (gridMode) R.drawable.ic_crop_portrait else R.drawable.ic_grid_view),
                    contentDescription = stringResource(if (gridMode) R.string.media_show_deck else R.string.media_show_grid),
                )
            }
        }
        if (trashCount > 0) {
            val description = stringResource(R.string.media_open_trash, trashCount)
            TextButton(onClick = onOpenTrash, modifier = Modifier.semantics { contentDescription = description }) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(trashCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Row of dropdown chips: kind, browse mode and (photos only) category. Scrolls horizontally. */
@Composable
fun MediaChips(
    tab: MediaTab,
    browse: Browse,
    category: CategoryFilter,
    onTab: (MediaTab) -> Unit,
    onBrowse: (Browse) -> Unit,
    onCategory: (CategoryFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DropdownChip(TabOptions, tab, onTab)
        if (tab != MediaTab.DUPLICATES) DropdownChip(BrowseOptions, browse, onBrowse)
        if (tab == MediaTab.PHOTOS) DropdownChip(CategoryOptions, category, onCategory)
    }
}

@Composable
private fun <T> DropdownChip(options: List<MenuOption<T>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = options.first { it.value == selected }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(current.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(current.label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { Icon(painterResource(option.icon), contentDescription = null) },
                    trailingIcon = {
                        if (option.value == selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    },
                    onClick = {
                        open = false
                        onSelect(option.value)
                    },
                )
            }
        }
    }
}

/** Previous/next period, the period title (opens a date picker), back to now and sort order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodBar(
    title: String,
    date: LocalDate,
    showBackToNow: Boolean,
    @StringRes backToNowLabel: Int,
    oldestFirst: Boolean,
    onShift: (Int) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onBackToNow: () -> Unit,
    onOldestFirst: (Boolean) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.media_previous), tint = MaterialTheme.colorScheme.primary)
        }
        TextButton(
            onClick = { picking = true },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Icon(painterResource(R.drawable.ic_calendar_today), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            // Long titles ("12 november") next to the back-to-now button shrink instead of wrapping.
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp, stepSize = 1.sp),
            )
        }
        IconButton(onClick = { onShift(1) }) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.media_next), tint = MaterialTheme.colorScheme.primary)
        }
        if (showBackToNow) {
            OutlinedButton(onClick = onBackToNow, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(backToNowLabel), maxLines = 1)
            }
        }
        Box {
            FilledTonalIconButton(onClick = { sortMenu = true }) {
                Icon(painterResource(R.drawable.ic_swap_vert), stringResource(R.string.media_sort))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                listOf(false to R.string.media_sort_newest, true to R.string.media_sort_oldest).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        trailingIcon = { if (value == oldestFirst) Icon(painterResource(R.drawable.ic_check), null) },
                        onClick = {
                            sortMenu = false
                            onOldestFirst(value)
                        },
                    )
                }
            }
        }
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    state.selectedDateMillis?.let {
                        onPickDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }) { Text(stringResource(R.string.media_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.media_cancel)) } },
        ) {
            Column { DatePicker(state = state, title = { Text(stringResource(R.string.media_pick_date), Modifier.padding(16.dp)) }) }
        }
    }
}
