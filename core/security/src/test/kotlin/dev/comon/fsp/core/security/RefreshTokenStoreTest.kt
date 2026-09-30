package dev.comon.fsp.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class RefreshTokenStoreTest {
    /** Reversible stand-in that still enforces associated-data binding. */
    private class FakeCipher : DataCipher {
        var failWith: CipherException? = null
        override fun encrypt(plaintext: String, associatedData: String) =
            "fake:" + Base64.getEncoder().encodeToString("$associatedData\u0000$plaintext".toByteArray())

        override fun decrypt(ciphertext: String, associatedData: String): String {
            failWith?.let { throw it }
            val (aad, plain) = String(Base64.getDecoder().decode(ciphertext.removePrefix("fake:"))).split('\u0000', limit = 2)
            if (aad != associatedData) throw CipherException.Tampered()
            return plain
        }
    }

    @get:Rule val folder = TemporaryFolder()
    private val cipher = FakeCipher()
    private val file by lazy { File(folder.root, "session/refresh-token") }
    private val store by lazy { RefreshTokenStore(file, cipher) }

    @Test fun roundTripForOwnerOnly() {
        store.save("user-1", "rt-secret")
        assertFalse(file.readText().contains("rt-secret"))
        assertEquals("rt-secret", store.read("user-1"))
        assertNull(store.read("user-2"))
        assertEquals("rt-secret", store.read("user-1")) // another account's read does not destroy it
    }

    @Test fun newSignInReplacesPreviousAccountToken() {
        store.save("user-1", "rt-1")
        store.save("user-2", "rt-2")
        assertNull(store.read("user-1"))
        assertEquals("rt-2", store.read("user-2"))
    }

    @Test fun undecryptableTokenIsDiscardedSoUserSignsInAgain() {
        store.save("user-1", "rt-1")
        cipher.failWith = CipherException.KeyUnavailable()
        assertNull(store.read("user-1"))
        assertFalse(file.exists())
    }

    @Test fun unknownFileFormatIsDiscarded() {
        file.parentFile!!.mkdirs()
        file.writeText("garbage")
        assertNull(store.read("user-1"))
        assertFalse(file.exists())
    }

    @Test fun missingFileMeansNoToken() {
        assertNull(store.read("user-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUserIdThatWouldBreakTheFileFormat() {
        store.save("user\n1", "rt")
    }
}
