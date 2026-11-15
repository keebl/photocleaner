package com.guus.photocleaner.ui.pro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R

/** A small lock badge on a control that needs Pro; not shown for Pro users (AC 17). */
@Composable
fun LockBadge(modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = modifier.size(16.dp)) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = stringResource(R.string.pro_locked),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(3.dp),
        )
    }
}

/** [content] with a [LockBadge] in its top-end corner when [locked]. */
@Composable
fun Lockable(locked: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        if (locked) LockBadge(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
    }
}
