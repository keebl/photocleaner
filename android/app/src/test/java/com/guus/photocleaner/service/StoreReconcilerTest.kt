package com.guus.photocleaner.service

import com.guus.photocleaner.asset
import com.guus.photocleaner.source.FakePhotoSource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec AC 16: items deleted outside the app are silently dropped from trash and keep lists. */
class StoreReconcilerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun missingItems_areDroppedFromBothLists_presentOnesStay() = runBlocking {
        val present = asset("present")
        val trashedGone = asset("trashedGone")
        val keptGone = "keptGone"
        val source = FakePhotoSource(listOf(present))
        val trash = TrashStore(File(tmp.root, "trash.json"))
        val keep = KeepStore(File(tmp.root, "kept.json"))
        trash.markAll(listOf(present, trashedGone), "deck")
        keep.keep(keptGone)
        keep.keep("present")

        StoreReconciler(source, trash, keep).dropMissing()

        assertEquals(setOf("present"), trash.trashedIds)
        assertEquals(setOf("present"), keep.ids.value)
        assertEquals(2, trash.totalCleaned.value) // the lifetime counter is not reduced
    }

    @Test
    fun emptyLists_needNoQuery() = runBlocking {
        val trash = TrashStore(File(tmp.root, "trash.json"))
        val keep = KeepStore(File(tmp.root, "kept.json"))

        StoreReconciler(FakePhotoSource(), trash, keep).dropMissing()

        assertEquals(emptySet<String>(), trash.trashedIds)
    }

    @Test
    fun otherSourcesEntries_areNeitherQueriedNorDropped() = runBlocking {
        // specs/android-nas.md AC 21: the phone library must not drop NAS entries, and back.
        val nasId = "nas://nas:445/photo/a.jpg"
        val device = FakePhotoSource(listOf(asset("1")), ownsId = { it.toLongOrNull() != null })
        val trash = TrashStore(File(tmp.root, "trash.json"))
        val keep = KeepStore(File(tmp.root, "kept.json"))
        trash.markAll(listOf(asset("1"), asset(nasId), asset("2")), "deck")
        keep.keep(nasId)

        StoreReconciler(device, trash, keep).dropMissing()

        assertEquals(setOf("1", nasId), trash.trashedIds)
        assertEquals(setOf(nasId), keep.ids.value)
    }
}
