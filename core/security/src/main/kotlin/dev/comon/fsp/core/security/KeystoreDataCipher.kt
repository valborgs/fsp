package dev.comon.fsp.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable Android Keystore key (available from API 23, so on every
 * supported device). Output: `v1:` + Base64(12-byte IV || ciphertext || 16-byte tag).
 * The key is created on first encryption only; decryption never creates a key, so a lost key
 * surfaces as [CipherException.KeyUnavailable] instead of silently producing garbage.
 */
class KeystoreDataCipher(private val alias: String) : DataCipher {
    private val lock = Any()

    override fun encrypt(plaintext: String, associatedData: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, synchronized(lock) { existingKey() ?: createKey() })
        cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(cipher.iv + sealed)
    }

    override fun decrypt(ciphertext: String, associatedData: String): String {
        if (!ciphertext.startsWith(PREFIX)) throw CipherException.Malformed()
        val bytes = try {
            Base64.getDecoder().decode(ciphertext.substring(PREFIX.length))
        } catch (_: IllegalArgumentException) {
            throw CipherException.Malformed()
        }
        if (bytes.size < IV_BYTES + TAG_BYTES) throw CipherException.Malformed()
        val key = synchronized(lock) { existingKey() } ?: throw CipherException.KeyUnavailable()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, bytes, 0, IV_BYTES))
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw CipherException.KeyUnavailable()
        }
        cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        return try {
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: BadPaddingException) { // includes AEADBadTagException
            throw CipherException.Tampered()
        } catch (_: IllegalBlockSizeException) {
            throw CipherException.Tampered()
        }
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    companion object {
        const val DATA_KEY_ALIAS = "fsp.data.v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "v1:"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}
