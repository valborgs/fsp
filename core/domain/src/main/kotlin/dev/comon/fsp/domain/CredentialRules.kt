package dev.comon.fsp.domain

/** Account input rules of API spec v1.4 §4.1 / §5.13 / §5.16. Passwords are never trimmed or normalized. */
object CredentialRules {
    private val LOGIN_ID = Regex("^[A-Za-z0-9]{4,30}$")
    private val NAME = Regex("^[가-힣A-Za-z]{1,50}$")

    fun isValidLoginId(id: String): Boolean = LOGIN_ID.matches(id)

    /** Stored and searched in lower case by the server; case-insensitively unique. */
    fun normalizeLoginId(id: String): String = id.lowercase()

    fun isValidName(name: String): Boolean = NAME.matches(name)

    /** ASCII 12-64 printable non-space chars with at least one letter, digit and punctuation. */
    fun isValidPassword(password: String): Boolean =
        password.length in 12..64 &&
            password.all { it in '!'..'~' } &&
            password.any { it in 'A'..'Z' || it in 'a'..'z' } &&
            password.any { it in '0'..'9' } &&
            password.any { !it.isLetterOrDigit() }
}
