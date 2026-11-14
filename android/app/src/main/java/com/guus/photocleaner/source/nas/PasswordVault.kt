package com.guus.photocleaner.source.nas

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the NAS password encrypted with AES-256-GCM under a non-exportable Android Keystore
 * key (spec "Data and privacy"; the Android counterpart of the iOS Keychain). The ciphertext
 * lives in `noBackupFilesDir`, never in preferences. No androidx.security dependency: its
 * EncryptedSharedPreferences is deprecated.
 */
class PasswordVault(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun save(password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    /** The stored password, "" when none is stored or it cannot be decrypted. */
    fun load(): String {
        if (!file.exists()) return ""
        return try {
            val bytes = file.readBytes()
            val ivSize = bytes[0].toInt()
            val iv = bytes.copyOfRange(1, 1 + ivSize)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return "", GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            ""
        } catch (_: IndexOutOfBoundsException) {
            ""
        }
    }

    /** Deletes the ciphertext and the key. */
    fun clear() {
        file.delete()
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (_: GeneralSecurityException) {
            // nothing stored
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun key(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nas-password"
        const val FILE_NAME = "nas-password.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
