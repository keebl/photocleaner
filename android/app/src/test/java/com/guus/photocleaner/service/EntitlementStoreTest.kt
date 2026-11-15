package com.guus.photocleaner.service

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Cached entitlement (specs/pro-unlock.md AC 16): JSON, atomic, fail closed to FREE. */
class EntitlementStoreTest {

    private lateinit var dir: File
    private lateinit var file: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("entitlement").toFile()
        file = File(dir, "entitlement.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun missingFile_isFree() {
        val store = EntitlementStore(file)
        assertEquals(ProState.FREE, store.state)
        assertNull(store.purchaseToken)
    }

    @Test
    fun savedPro_survivesARestart() {
        EntitlementStore(file).save(ProState.PRO, "token-1")

        val reopened = EntitlementStore(file)
        assertEquals(ProState.PRO, reopened.state)
        assertEquals("token-1", reopened.purchaseToken)
    }

    @Test
    fun savedPending_staysPending() {
        EntitlementStore(file).save(ProState.PENDING, null)
        assertEquals(ProState.PENDING, EntitlementStore(file).state)
    }

    @Test
    fun refundAfterPro_savesFree() {
        val store = EntitlementStore(file)
        store.save(ProState.PRO, "token-1")
        store.save(ProState.FREE, null)

        val reopened = EntitlementStore(file)
        assertEquals(ProState.FREE, reopened.state)
        assertNull(reopened.purchaseToken)
    }

    @Test
    fun corruptFile_isFree() {
        file.writeText("{ not json")
        assertEquals(ProState.FREE, EntitlementStore(file).state)
    }

    @Test
    fun unknownState_isFree() {
        file.writeText("""{"state":"LIFETIME_GOLD","purchaseToken":"x"}""")
        assertEquals(ProState.FREE, EntitlementStore(file).state)
    }

    @Test
    fun write_leavesNoTempFile() {
        EntitlementStore(file).save(ProState.PRO, "token-1")
        assertFalse(File(dir, "entitlement.json.tmp").exists())
    }
}
