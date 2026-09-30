package dev.comon.fsp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRulesTest {
    @Test fun loginIdIsAsciiAlphanumeric4To30() {
        assertTrue(CredentialRules.isValidLoginId("worker001"))
        assertTrue(CredentialRules.isValidLoginId("A".repeat(30)))
        assertFalse(CredentialRules.isValidLoginId("abc"))
        assertFalse(CredentialRules.isValidLoginId("a".repeat(31)))
        assertFalse(CredentialRules.isValidLoginId("work er1"))
        assertFalse(CredentialRules.isValidLoginId("홍길동1234"))
        assertEquals("worker001", CredentialRules.normalizeLoginId("WoRkEr001"))
    }

    @Test fun passwordNeedsLetterDigitPunctuationWithinAsciiLimits() {
        assertTrue(CredentialRules.isValidPassword("Example!1234A"))
        assertTrue(CredentialRules.isValidPassword("a1!" + "b".repeat(61)))
        assertFalse("too short", CredentialRules.isValidPassword("Exam!123"))
        assertFalse("too long", CredentialRules.isValidPassword("a1!" + "b".repeat(62)))
        assertFalse("no digit", CredentialRules.isValidPassword("Example!abcdA"))
        assertFalse("no letter", CredentialRules.isValidPassword("123456!78901"))
        assertFalse("no punctuation", CredentialRules.isValidPassword("Example12345"))
        assertFalse("no spaces", CredentialRules.isValidPassword("Example !1234"))
        assertFalse("ASCII only", CredentialRules.isValidPassword("Example!1234가"))
    }

    @Test fun nameIsHangulOrLatinWithoutSpaces() {
        assertTrue(CredentialRules.isValidName("홍길동"))
        assertTrue(CredentialRules.isValidName("Hong"))
        assertFalse(CredentialRules.isValidName("홍 길동"))
        assertFalse(CredentialRules.isValidName(""))
        assertFalse(CredentialRules.isValidName("김1"))
    }
}
