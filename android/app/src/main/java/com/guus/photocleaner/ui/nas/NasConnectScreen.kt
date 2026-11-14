package com.guus.photocleaner.ui.nas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.guus.photocleaner.R
import com.guus.photocleaner.source.nas.NasBrowser
import com.guus.photocleaner.source.nas.NasConfig
import com.guus.photocleaner.source.nas.NasError
import com.guus.photocleaner.source.nas.NasErrors
import com.guus.photocleaner.source.nas.NasPaths
import com.guus.photocleaner.ui.settings.Footer
import com.guus.photocleaner.ui.settings.Group
import com.guus.photocleaner.ui.settings.SectionHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

const val NAS_SERVER_FIELD_TAG = "nasServerField"
const val NAS_USER_FIELD_TAG = "nasUserField"
const val NAS_PASSWORD_FIELD_TAG = "nasPasswordField"
const val NAS_CONNECT_BUTTON_TAG = "nasConnectButton"

/** One screen of the browsing step. */
private sealed interface BrowseNode {
    data object Shares : BrowseNode
    data class Folder(val share: String, val path: String, val title: String) : BrowseNode
}

/**
 * Links a NAS in two steps (port of iOS `SMBConnectView`, specs/android-nas.md AC 1–8):
 * 1. server address and login; "Verbinden en shares tonen" logs in and lists the shares;
 * 2. pick a share, browse its subfolders and choose one. The choice and everything below it
 *    becomes the NAS source.
 */
@Composable
fun NasConnectScreen(
    current: NasConfig?,
    /** Opens a session for browsing; [NasBrowser.shareNames] validates the login. */
    openBrowser: (host: String, port: Int, username: String, password: String) -> NasBrowser,
    /** Checks and stores the link; throws on failure. */
    onLink: suspend (NasConfig, password: String) -> Unit,
    onUnlink: suspend () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(current?.let { if (it.port == NasConfig.DEFAULT_PORT) it.host else "${it.host}:${it.port}" }.orEmpty()) }
    var username by remember { mutableStateOf(current?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmUnlink by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<NasBrowser?>(null) }
    val stack = remember { mutableStateListOf<BrowseNode>() }

    DisposableEffect(Unit) { onDispose { browser?.close() } }
    fun message(t: Throwable) = nasErrorMessage(context, NasErrors.wrap(t).error)
    val busy = connecting || linking

    fun login() {
        val parsed = NasPaths.parseServer(server)
        if (parsed == null) {
            error = nasErrorMessage(context, NasError.BAD_SERVER)
            return
        }
        connecting = true
        scope.launch {
            val candidate = openBrowser(parsed.first, parsed.second, username.trim(), password)
            try {
                candidate.shareNames() // logs in: validates server and credentials
                browser?.close()
                browser = candidate
                stack.clear()
                stack += BrowseNode.Shares
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                candidate.close()
                error = message(e)
            } finally {
                connecting = false
            }
        }
    }

    fun finalize(share: String, folder: String) {
        val (host, port) = NasPaths.parseServer(server) ?: return
        linking = true
        scope.launch {
            try {
                onLink(NasConfig(host = host, port = port, share = share, folder = folder, username = username.trim()), password)
                onClose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = message(e)
            } finally {
                linking = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false),
    ) {
        BackHandler(enabled = !busy) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else onClose() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                val node = stack.lastOrNull()
                val title = when (node) {
                    null -> stringResource(R.string.nas_connect_title)
                    BrowseNode.Shares -> stringResource(R.string.nas_shares_title)
                    is BrowseNode.Folder -> node.title
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (node == null) {
                        TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.media_cancel)) }
                    } else {
                        IconButton(onClick = { stack.removeAt(stack.lastIndex) }, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.nas_back))
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                }
                Box(Modifier.weight(1f)) {
                    val activeBrowser = browser
                    when {
                        node == null -> LoginForm(
                            server = server,
                            onServer = { server = it },
                            username = username,
                            onUsername = { username = it },
                            password = password,
                            onPassword = { password = it },
                            connecting = connecting,
                            canUnlink = current != null,
                            onConnect = ::login,
                            onUnlink = { confirmUnlink = true },
                        )
                        activeBrowser == null -> Unit
                        node == BrowseNode.Shares -> ShareList(activeBrowser) { share ->
                            stack += BrowseNode.Folder(share, "", share)
                        }
                        node is BrowseNode.Folder -> FolderList(
                            browser = activeBrowser,
                            node = node,
                            onOpen = { name -> stack += BrowseNode.Folder(node.share, NasPaths.join(node.path, name), name) },
                            onChoose = { finalize(node.share, node.path) },
                        )
                    }
                    if (linking) LinkingOverlay()
                }
            }
        }

        error?.let { text ->
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text(stringResource(R.string.nas_error_title)) },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.media_ok)) } },
            )
        }
        if (confirmUnlink) {
            AlertDialog(
                onDismissRequest = { confirmUnlink = false },
                title = { Text(stringResource(R.string.nas_unlink_title)) },
                text = { Text(stringResource(R.string.nas_unlink_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmUnlink = false
                        scope.launch {
                            onUnlink()
                            onClose()
                        }
                    }) { Text(stringResource(R.string.nas_unlink_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text(stringResource(R.string.media_cancel)) } },
            )
        }
    }
}

@Composable
private fun LoginForm(
    server: String,
    onServer: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    connecting: Boolean,
    canUnlink: Boolean,
    onConnect: () -> Unit,
    onUnlink: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionHeader(R.string.nas_server_section)
        Group {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = server,
                    onValueChange = onServer,
                    label = { Text(stringResource(R.string.nas_server)) },
                    placeholder = { Text(stringResource(R.string.nas_server_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag(NAS_SERVER_FIELD_TAG),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsername,
                    label = { Text(stringResource(R.string.nas_username)) },
                    placeholder = { Text(stringResource(R.string.nas_username_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag(NAS_USER_FIELD_TAG),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPassword,
                    label = { Text(stringResource(R.string.nas_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag(NAS_PASSWORD_FIELD_TAG),
                )
            }
        }
        Footer(stringResource(R.string.nas_server_footer))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConnect,
            enabled = server.isNotBlank() && !connecting,
            modifier = Modifier.fillMaxWidth().testTag(NAS_CONNECT_BUTTON_TAG),
        ) {
            if (connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nas_connecting))
            } else {
                Text(stringResource(R.string.nas_connect_button))
            }
        }
        if (canUnlink) {
            Spacer(Modifier.height(24.dp))
            Group {
                ActionRow(R.drawable.ic_delete, stringResource(R.string.nas_unlink), MaterialTheme.colorScheme.error, onUnlink)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShareList(browser: NasBrowser, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var shares by remember(browser) { mutableStateOf<List<String>?>(null) }
    var error by remember(browser) { mutableStateOf<String?>(null) }
    LaunchedEffect(browser) {
        try {
            shares = browser.shareNames()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = nasErrorMessage(context, NasErrors.wrap(e).error)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionHeader(R.string.nas_choose_share)
        Group {
            val list = shares
            when {
                error != null -> MessageRow(error.orEmpty(), MaterialTheme.colorScheme.error)
                list == null -> LoadingRow()
                list.isEmpty() -> MessageRow(stringResource(R.string.nas_no_shares))
                else -> list.forEachIndexed { i, share ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                    NavigationRow(R.drawable.ic_hard_drive, share) { onSelect(share) }
                }
            }
        }
    }
}

@Composable
private fun FolderList(browser: NasBrowser, node: BrowseNode.Folder, onOpen: (String) -> Unit, onChoose: () -> Unit) {
    val context = LocalContext.current
    var folders by remember(node) { mutableStateOf<List<String>?>(null) }
    var error by remember(node) { mutableStateOf<String?>(null) }
    LaunchedEffect(node) {
        try {
            folders = browser.folders(node.share, node.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = nasErrorMessage(context, NasErrors.wrap(e).error)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Group {
            ActionRow(
                R.drawable.ic_check_circle,
                stringResource(if (node.path.isEmpty()) R.string.nas_use_share else R.string.nas_use_folder),
                Color(0xFF34C759),
                onChoose,
            )
        }
        Footer(stringResource(R.string.nas_folder_footer))
        SectionHeader(R.string.nas_subfolders)
        Group {
            val list = folders
            when {
                error != null -> MessageRow(error.orEmpty(), MaterialTheme.colorScheme.error)
                list == null -> LoadingRow()
                list.isEmpty() -> MessageRow(stringResource(R.string.nas_no_subfolders))
                else -> list.forEachIndexed { i, folder ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                    NavigationRow(R.drawable.ic_folder, folder) { onOpen(folder) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NavigationRow(icon: Int, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f).padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ActionRow(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = color)
        Text(label, color = color, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun LoadingRow() {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.nas_loading), Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageRow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
}

@Composable
private fun LinkingOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.nas_linking))
            }
        }
    }
}
