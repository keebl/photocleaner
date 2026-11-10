package com.guus.photocleaner.ui.zoom

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.ui.theme.DecisionColors
import kotlin.math.abs
import kotlinx.coroutines.launch

const val ZOOM_SCREEN_TAG = "zoomScreen"
const val VIDEO_SCREEN_TAG = "videoScreen"

private val SwipeThreshold = 90.dp
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Full-screen view of one item: the zoom view for photos, the player for videos. Deciding
 * here (swipe or buttons) works like a swipe in the deck. Port of iOS `PhotoZoomView` and
 * `VideoPlayerScreen`.
 */
@Composable
fun MediaInspector(
    asset: PhotoAsset,
    imageModel: Any,
    contentUri: Uri?,
    dateText: String?,
    onKeep: (() -> Unit)?,
    onDiscard: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        if (asset.isVideo && contentUri != null) {
            VideoPlayerScreen(asset, contentUri, dateText, onKeep, onDiscard, onClose)
        } else {
            PhotoZoomScreen(asset, imageModel, contentUri, dateText, onKeep, onDiscard, onClose)
        }
    }
}

/** Pinch to zoom (1–5×), drag to pan when zoomed, double-tap to toggle; at 1× swipe to decide. */
@Composable
private fun PhotoZoomScreen(
    asset: PhotoAsset,
    imageModel: Any,
    contentUri: Uri?,
    dateText: String?,
    onKeep: (() -> Unit)?,
    onDiscard: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val canDecide = onKeep != null && onDiscard != null
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var swipeX by remember { mutableFloatStateOf(0f) }
    var committing by remember { mutableStateOf(false) }
    val thresholdPx = with(density) { SwipeThreshold.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag(ZOOM_SCREEN_TAG)) {
        val flyOut = with(density) { maxWidth.toPx() } * 1.5f

        fun settle() {
            if (committing) return
            val kept = when {
                swipeX > thresholdPx -> true
                swipeX < -thresholdPx -> false
                else -> null
            }
            scope.launch {
                if (kept == null) {
                    animate(swipeX, 0f, animationSpec = spring()) { value, _ -> swipeX = value }
                } else {
                    committing = true
                    animate(swipeX, if (kept) flyOut else -flyOut, animationSpec = tween(220)) { value, _ -> swipeX = value }
                    if (kept) onKeep?.invoke() else onDiscard?.invoke()
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var swiped = false
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (event.changes.size > 1 || scale > 1f) {
                                scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (canDecide && !committing && pan != Offset.Zero) {
                                swipeX += pan.x
                                swiped = true
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (swiped) settle()
                    }
                },
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = if (scale > 1f) offset.x else swipeX
                        translationY = if (scale > 1f) offset.y else 0f
                        rotationZ = if (scale > 1f) 0f else swipeX / density.density / 24f
                    },
            )
        }

        if (scale == 1f) DecisionStamp(swipeX, thresholdPx)

        TopBar(
            dateText = dateText,
            shareUri = contentUri,
            isVideo = asset.isVideo,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (scale == 1f && canDecide) Legend(Modifier.align(Alignment.BottomCenter))
    }
}

/** Plays the video; the bar has discard, keep, share and close. */
@Composable
private fun VideoPlayerScreen(
    asset: PhotoAsset,
    uri: Uri,
    dateText: String?,
    onKeep: (() -> Unit)?,
    onDiscard: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag(VIDEO_SCREEN_TAG)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
        TopBar(
            dateText = dateText,
            shareUri = uri,
            isVideo = asset.isVideo,
            onClose = onClose,
            onKeep = onKeep?.let { keep ->
                {
                    player.pause()
                    keep()
                }
            },
            onDiscard = onDiscard?.let { discard ->
                {
                    player.pause()
                    discard()
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun TopBar(
    dateText: String?,
    shareUri: Uri?,
    isVideo: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onKeep: (() -> Unit)? = null,
    onDiscard: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dateText != null) {
            Surface(color = Color.Black.copy(alpha = 0.4f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
                Text(dateText, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (onDiscard != null) {
            RoundIcon(R.drawable.ic_delete, stringResource(R.string.deck_discard), DecisionColors.discardDark, onDiscard)
        }
        if (onKeep != null) {
            RoundIcon(R.drawable.ic_check, stringResource(R.string.deck_keep), DecisionColors.keepDark, onKeep)
        }
        if (shareUri != null) ShareButton(shareUri, isVideo)
        RoundIcon(R.drawable.ic_close, stringResource(R.string.zoom_close), Color.Black.copy(alpha = 0.4f), onClose)
    }
}

@Composable
private fun RoundIcon(icon: Int, description: String, background: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(Modifier.size(36.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = description, tint = Color.White)
        }
    }
}

/** Opens the system share sheet for one photo or video. Port of iOS `ShareButton`. */
@Composable
fun ShareButton(uri: Uri, isVideo: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val title = stringResource(R.string.zoom_share)
    IconButton(
        modifier = modifier,
        onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (isVideo) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, title))
        },
    ) {
        Box(Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_share), contentDescription = title, tint = Color.White)
        }
    }
}

@Composable
private fun DecisionStamp(swipePx: Float, thresholdPx: Float) {
    if (swipePx == 0f) return
    val progress = (abs(swipePx) / thresholdPx).coerceIn(0f, 1f)
    val kept = swipePx > 0
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(96.dp).background((if (kept) DecisionColors.keepDark else DecisionColors.discardDark).copy(alpha = progress), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (kept) R.drawable.ic_check else R.drawable.ic_delete),
                contentDescription = null,
                tint = Color.White.copy(alpha = progress),
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Icon(painterResource(R.drawable.ic_arrow_back), null, tint = DecisionColors.discardDark, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.deck_discard), color = DecisionColors.discardDark, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.deck_keep), color = DecisionColors.keepDark, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))
        Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = DecisionColors.keepDark, modifier = Modifier.size(16.dp))
    }
}
