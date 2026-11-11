package com.guus.photocleaner.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeepStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(): File = File(tmp.root, "kept.json")
    private fun store() = KeepStore(file())

    @Test
    fun keep_storesId() {
        val store = store()

        store.keep("a")

        assertTrue(store.contains("a"))
        assertEquals(setOf("a"), store.ids.value)
    }

    @Test
    fun keptIds_surviveRestart() {
        store().apply {
            keep("a")
            keep("b")
        }

        assertEquals(setOf("a", "b"), store().ids.value)
    }

    @Test
    fun unkeep_removesId() {
        val store = store()
        store.keep("a")

        store.unkeep("a")

        assertFalse(store.contains("a"))
        assertTrue(store().ids.value.isEmpty())
    }

    @Test
    fun reset_clearsAll() {
        val store = store()
        store.keep("a")
        store.keep("b")

        store.reset()

        assertTrue(store.ids.value.isEmpty())
        assertTrue(store().ids.value.isEmpty())
    }

    @Test
    fun forget_dropsIdsDeletedOutsideTheApp() {
        val store = store()
        store.keep("a")
        store.keep("b")

        store.forget(listOf("b", "unknown"))

        assertEquals(setOf("a"), store().ids.value)
    }

    @Test
    fun missingOrCorruptFile_startsEmpty() {
        assertTrue(store().ids.value.isEmpty())

        file().writeText("[1, 2")

        assertTrue(store().ids.value.isEmpty())
    }
}
