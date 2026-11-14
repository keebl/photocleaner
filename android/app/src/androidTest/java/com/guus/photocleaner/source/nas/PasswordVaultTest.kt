package com.guus.photocleaner.source.nas

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Spec AC 8–9 and AC 23: the password is stored encrypted and survives a new instance. */
@RunWith(AndroidJUnit4::class)
class PasswordVaultTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        PasswordVault(context).clear()
        NasConfigStore(context).clear()
    }

    @Test
    fun password_roundTrips_isNotStoredInPlainText_andClears() {
        val secret = "s3cret-wachtwoord-äöü"
        PasswordVault(context).save(secret)

        assertEquals(secret, PasswordVault(context).load()) // a fresh instance, as after a restart
        val raw = File(context.noBackupFilesDir, "nas-password.bin").readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("s3cret"))
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
        assertFalse(prefs.any { it.readText().contains("s3cret") })

        PasswordVault(context).clear()
        assertEquals("", PasswordVault(context).load())
    }

    @Test
    fun config_roundTrips_withoutPassword() {
        val config = NasConfig(host = "192.168.1.10", port = 1445, share = "photo", folder = "2022", username = "guest")
        NasConfigStore(context).save(config)

        assertEquals(config, NasConfigStore(context).load())
        NasConfigStore(context).clear()
        assertNull(NasConfigStore(context).load())
    }
}
