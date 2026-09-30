package dev.comon.fsp.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.Base64
import java.util.UUID

/** Real Android Keystore on device. Each test uses its own key alias. */
@RunWith(AndroidJUnit4::class)
class KeystoreDataCipherTest {
    private val alias = "fsp.test.${UUID.randomUUID()}"
    private val cipher = KeystoreDataCipher(alias)
    private val text = "응답 {\"q1\":\"yes\"} 🙂"

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun deleteKey() = keyStore().deleteEntry(alias)

    @After fun cleanUp() = deleteKey()

    private inline fun <reified T : CipherException> expect(block: () -> Unit) {
        try {
            block()
            fail("expected ${T::class.simpleName}")
        } catch (e: CipherException) {
            assertTrue("got ${e::class.simpleName}", e is T)
        }
    }

    @Test fun roundTripAndRandomIv() {
        val a = cipher.encrypt(text, "row-1")
        val b = cipher.encrypt(text, "row-1")
        assertNotEquals(a, b)
        assertFalse(a.contains("\"q1\"")) // quotes never occur in Base64
        assertEquals(text, cipher.decrypt(a, "row-1"))
        assertEquals(text, KeystoreDataCipher(alias).decrypt(b, "row-1")) // same key after "restart"
    }

    @Test fun differentAssociatedDataIsRejected() {
        val sealed = cipher.encrypt(text, "owner:user-1")
        expect<CipherException.Tampered> { cipher.decrypt(sealed, "owner:user-2") }
    }

    @Test fun modifiedCiphertextIsRejected() {
        val sealed = cipher.encrypt(text, "row-1")
        val bytes = Base64.getDecoder().decode(sealed.removePrefix("v1:"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        expect<CipherException.Tampered> { cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(bytes), "row-1") }
    }

    @Test fun foreignOrTruncatedInputIsMalformed() {
        expect<CipherException.Malformed> { cipher.decrypt("plain text", "row-1") }
        expect<CipherException.Malformed> { cipher.decrypt("v1:***", "row-1") }
        expect<CipherException.Malformed> { cipher.decrypt("v1:AAAA", "row-1") }
    }

    @Test fun lostKeyIsReportedAndNeverSilentlyRecreatedOnDecrypt() {
        val sealed = cipher.encrypt(text, "row-1")
        deleteKey()
        expect<CipherException.KeyUnavailable> { cipher.decrypt(sealed, "row-1") }
        assertFalse(keyStore().containsAlias(alias))

        cipher.encrypt("new data", "row-2") // a new key is created only for new encryption
        expect<CipherException.Tampered> { cipher.decrypt(sealed, "row-1") } // old data stays unreadable
    }
}
