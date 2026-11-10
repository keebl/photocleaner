package com.guus.photocleaner.service

import com.guus.photocleaner.asset
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrashStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val start: Instant = Instant.parse("2026-03-01T10:00:00Z")
    private val zone: ZoneId = ZoneOffset.UTC

    private fun file(): File = File(tmp.root, "trash.json")
    private fun store(now: Instant = start) = TrashStore(file(), Clock.fixed(now, zone))

    @Test
    fun mark_addsItemWithCurrentDateAndCountsIt() {
        val store = store()

        store.mark(asset("a"), reason = "deck")

        val item = store.items.value.single()
        assertEquals("a", item.id)
        assertEquals(start.toEpochMilli(), item.markedAtMillis)
        assertEquals("deck", item.reason)
        assertTrue(store.contains("a"))
        assertEquals(setOf("a"), store.trashedIds)
        assertEquals(1, store.totalCleaned.value)
    }

    @Test
    fun markingTwice_isIgnored() {
        val store = store()

        store.mark(asset("a"), "deck")
        store.mark(asset("a"), "deck")

        assertEquals(1, store.items.value.size)
        assertEquals(1, store.totalCleaned.value)
    }

    @Test
    fun markAll_addsSeveralItemsInOneAction() {
        val store = store()

        store.markAll(listOf(asset("a"), asset("b"), asset("a")), "grid")

        assertEquals(listOf("a", "b"), store.items.value.map { it.id })
        assertEquals(2, store.totalCleaned.value)
    }

    @Test
    fun restore_removesItemAndDecrementsCounter() {
        val store = store()
        store.mark(asset("a"), "deck")
        store.mark(asset("b"), "deck")

        store.restore("a")

        assertFalse(store.contains("a"))
        assertEquals(1, store.totalCleaned.value)
    }

    @Test
    fun restoringUnknownId_changesNothing() {
        val store = store()
        store.mark(asset("a"), "deck")

        store.restore("zzz")

        assertEquals(1, store.totalCleaned.value)
        assertEquals(1, store.items.value.size)
    }

    @Test
    fun forget_removesItemsButKeepsCounter() {
        val store = store()
        store.markAll(listOf(asset("a"), asset("b")), "deck")

        store.forget(listOf("a", "b"))

        assertTrue(store.items.value.isEmpty())
        assertEquals(2, store.totalCleaned.value)
    }

    @Test
    fun retention_is30Days() {
        assertEquals(30, TrashStore.RETENTION_DAYS)
    }

    @Test
    fun daysLeft_countsDownAndStopsAtZero() {
        store().mark(asset("a"), "deck")

        val item = store().items.value.single()
        assertEquals(30, store(start).daysLeft(item))
        assertEquals(29, store(start.plusSeconds(1)).daysLeft(item))
        assertEquals(1, store(start.plusSeconds(29L * 86_400)).daysLeft(item))
        assertEquals(0, store(start.plusSeconds(30L * 86_400)).daysLeft(item))
        assertEquals(0, store(start.plusSeconds(45L * 86_400)).daysLeft(item))
    }

    @Test
    fun expiredItems_areThoseOlderThan30Days() {
        store(start).mark(asset("old"), "deck")
        store(start.plusSeconds(10L * 86_400)).mark(asset("new"), "deck")

        val justBefore = store(start.plusSeconds(30L * 86_400 - 1)).expiredItems()
        val atExpiry = store(start.plusSeconds(30L * 86_400)).expiredItems()

        assertTrue(justBefore.isEmpty())
        assertEquals(listOf("old"), atExpiry.map { it.id })
    }

    @Test
    fun itemsAndCounter_survivePersistence() {
        val first = store()
        first.markAll(listOf(asset("a"), asset("b")), "deck")
        first.restore("b")

        val reopened = store()

        assertEquals(listOf("a"), reopened.items.value.map { it.id })
        assertEquals(1, reopened.totalCleaned.value)
    }

    @Test
    fun corruptFile_startsEmpty() {
        file().writeText("{not json")

        val store = store()

        assertTrue(store.items.value.isEmpty())
        assertEquals(0, store.totalCleaned.value)
    }
}
