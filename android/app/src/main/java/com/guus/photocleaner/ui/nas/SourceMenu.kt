package com.guus.photocleaner.ui.nas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R
import com.guus.photocleaner.source.SourceKind

const val SOURCE_MENU_TAG = "sourceMenu"

/**
 * The photo source menu in the media header (port of the iOS `MediaView` source menu):
 * Telefoon, the linked NAS, and "NAS koppelen…" or "NAS-koppeling wijzigen…".
 */
@Composable
fun SourceMenu(
    kind: SourceKind,
    /** Display name of the linked NAS; null when none is linked. */
    nasName: String?,
    onSelect: (SourceKind) -> Unit,
    onConnectNas: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = if (kind == SourceKind.NAS) nasName ?: stringResource(R.string.source_nas_default) else stringResource(R.string.source_device)
    val description = stringResource(R.string.source_menu)
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.testTag(SOURCE_MENU_TAG).semantics { contentDescription = "$description: $label" },
        ) {
            Icon(
                painterResource(if (kind == SourceKind.NAS) R.drawable.ic_hard_drive else R.drawable.ic_photo_library),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_device)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_photo_library), null) },
                trailingIcon = if (kind == SourceKind.DEVICE) ({ Icon(painterResource(R.drawable.ic_check), null) }) else null,
                onClick = {
                    open = false
                    onSelect(SourceKind.DEVICE)
                },
            )
            if (nasName != null) {
                DropdownMenuItem(
                    text = { Text(nasName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_hard_drive), null) },
                    trailingIcon = if (kind == SourceKind.NAS) ({ Icon(painterResource(R.drawable.ic_check), null) }) else null,
                    onClick = {
                        open = false
                        onSelect(SourceKind.NAS)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(if (nasName != null) R.string.source_change_nas else R.string.source_link_nas)) },
                leadingIcon = { Icon(painterResource(if (nasName != null) R.drawable.ic_settings else R.drawable.ic_hard_drive), null) },
                onClick = {
                    open = false
                    onConnectNas()
                },
            )
        }
    }
}
