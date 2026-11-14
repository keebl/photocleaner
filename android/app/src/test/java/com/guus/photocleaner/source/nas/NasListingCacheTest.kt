package com.guus.photocleaner.source.nas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec AC 11 and AC 22: the listing is cached per linked folder and can be cleared. */
class NasListingCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val config = NasConfig(host = "nas", share = "photo", folder = "2022")

    @Test
    fun roundTrip_keepsEveryField() {
        val cache = NasListingCache(tmp.root)
        val items = listOf(
            nasAsset("${config.idPrefix}2022/a.jpg", false, 1_000L, 10L, "a.jpg", "2022"),
            nasAsset("${config.idPrefix}2022/b.mp4", true, null, 20L, "b.mp4", null),
        )
        cache.save(config, items)

        assertEquals(items, cache.load(config))
    }

    @Test
    fun keyedByHostShareAndFolder() {
        val cache = NasListingCache(tmp.root)
        cache.save(config, listOf(nasAsset("x", false, 1L, 1L, "x", null)))

        assertNull(cache.load(config.copy(folder = "")))
        assertNull(cache.load(config.copy(host = "other")))
        assertNull(cache.load(config.copy(port = 1445)))
    }

    @Test
    fun emptyScan_isNotSaved_andClearRemoves() {
        val cache = NasListingCache(tmp.root)
        cache.save(config, emptyList())
        assertNull(cache.load(config))

        cache.save(config, listOf(nasAsset("x", false, 1L, 1L, "x", null)))
        cache.clear(config)
        assertNull(cache.load(config))

        cache.save(config, listOf(nasAsset("x", false, 1L, 1L, "x", null)))
        cache.clearAll()
        assertNull(cache.load(config))
    }
}
