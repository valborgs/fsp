package dev.comon.fsp.core.data

import dev.comon.fsp.core.security.CipherException
import dev.comon.fsp.core.security.DataCipher
import java.util.Base64

/** Reversible stand-in for the Keystore cipher that still enforces associated-data binding. */
class FakeCipher : DataCipher {
    override fun encrypt(plaintext: String, associatedData: String): String =
        "fake:" + Base64.getEncoder().encodeToString("$associatedData\u0000$plaintext".toByteArray())

    override fun decrypt(ciphertext: String, associatedData: String): String {
        if (!ciphertext.startsWith("fake:")) throw CipherException.Malformed()
        val (aad, plain) = String(Base64.getDecoder().decode(ciphertext.removePrefix("fake:"))).split('\u0000', limit = 2)
        if (aad != associatedData) throw CipherException.Tampered()
        return plain
    }
}
