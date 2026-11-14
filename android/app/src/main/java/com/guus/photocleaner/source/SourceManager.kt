package com.guus.photocleaner.source

import com.guus.photocleaner.source.nas.NasSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the active photo source: the phone library or the linked NAS (port of iOS
 * `Services/SourceManager.swift`). The choice persists; without a linked NAS the phone
 * library is always active.
 */
class SourceManager(
    val device: PhotoSource,
    val nas: NasSource,
    loadKind: () -> SourceKind,
    private val saveKind: (SourceKind) -> Unit,
) {
    private val _kind = MutableStateFlow(
        if (loadKind() == SourceKind.NAS && nas.config.value != null) SourceKind.NAS else SourceKind.DEVICE,
    )
    val kind: StateFlow<SourceKind> = _kind.asStateFlow()

    fun source(kind: SourceKind): PhotoSource = if (kind == SourceKind.NAS) nas else device

    /** Switches source; the NAS only when one is linked. */
    fun select(kind: SourceKind) {
        if (kind == SourceKind.NAS && nas.config.value == null) return
        _kind.value = kind
        saveKind(kind)
    }

    /** Removes the NAS link and returns to the phone library (specs/android-nas.md AC 23). */
    suspend fun unlinkNas() {
        nas.unlink()
        select(SourceKind.DEVICE)
    }
}
