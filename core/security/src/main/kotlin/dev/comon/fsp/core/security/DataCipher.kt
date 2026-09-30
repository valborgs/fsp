package dev.comon.fsp.core.security

/**
 * Authenticated encryption for data at rest. [associatedData] binds a ciphertext to its context
 * (row ID, owner, purpose): decrypting with different associated data fails as [CipherException.Tampered].
 */
interface DataCipher {
    fun encrypt(plaintext: String, associatedData: String): String

    /** @throws CipherException when the value cannot be authenticated or decrypted. */
    fun decrypt(ciphertext: String, associatedData: String): String
}

sealed class CipherException(message: String) : Exception(message) {
    /** Ciphertext or associated data changed after encryption. */
    class Tampered : CipherException("Ciphertext failed authentication")

    /** The key is gone (e.g. app data or keystore reset); the data cannot be recovered on this device. */
    class KeyUnavailable : CipherException("Encryption key is not available")

    /** Not produced by this cipher or truncated. */
    class Malformed : CipherException("Ciphertext has an unknown format")
}
