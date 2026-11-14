package com.guus.photocleaner.ui.trash

import com.guus.photocleaner.asset
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.Deletion
import com.guus.photocleaner.source.FakePhotoSource
import com.guus.photocleaner.source.SourceKind
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** specs/android-nas.md AC 19–21: permanent NAS deletion after the in-app trash. */
class MoveToSystemTrashTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val nasA = asset("nas://nas:445/photo/a.jpg")
    private val nasB = asset("nas://nas:445/photo/b.jpg")
    private val phone = asset("42")

    private fun nas(vararg items: com.guus.photocleaner.model.PhotoAsset) = FakePhotoSource(
        items.toList(),
        kind = SourceKind.NAS,
        deletion = Deletion.PERMANENT,
        ownsId = { it.startsWith("nas://") },
    )

    /** Mirrors rememberSystemTrash for a PERMANENT source, with a scripted answer. */
    private fun permanent(source: FakePhotoSource, confirmed: Boolean) = SystemTrash { assets ->
        if (!confirmed) return@SystemTrash false
        try {
            source.deletePermanently(assets)
            true
        } catch (_: IOException) {
            false
        }
    }

    @Test
    fun confirmed_deletesOnTheNas_andLeavesTheTrash_otherSourceUntouched() = runBlocking {
        val source = nas(nasA, nasB)
        val trash = TrashStore(File(tmp.root, "trash.json"))
        trash.markAll(listOf(nasA, nasB, phone), "deck")

        moveToSystemTrash(trash.items.value, source, trash, permanent(source, confirmed = true))

        assertEquals(listOf(nasA.id, nasB.id), source.trashedIds)
        assertEquals(setOf(phone.id), trash.trashedIds)
    }

    @Test
    fun cancelled_keepsEverythingInTheTrash() = runBlocking {
        val source = nas(nasA)
        val trash = TrashStore(File(tmp.root, "trash.json"))
        trash.markAll(listOf(nasA), "deck")

        moveToSystemTrash(trash.items.value, source, trash, permanent(source, confirmed = false))

        assertEquals(emptyList<String>(), source.trashedIds)
        assertEquals(setOf(nasA.id), trash.trashedIds)
    }

    @Test
    fun unreachableNas_keepsEverythingInTheTrash() = runBlocking {
        val source = nas(nasA).apply { deleteFailure = IOException("unreachable") }
        val trash = TrashStore(File(tmp.root, "trash.json"))
        trash.markAll(listOf(nasA), "deck")

        moveToSystemTrash(trash.items.value, source, trash, permanent(source, confirmed = true))

        assertEquals(setOf(nasA.id), trash.trashedIds)
    }
}
