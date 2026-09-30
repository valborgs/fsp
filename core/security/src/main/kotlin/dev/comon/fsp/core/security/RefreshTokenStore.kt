package dev.comon.fsp.core.security

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Keeps the signed-in account's refresh token encrypted in `noBackupFilesDir`. The owner ID is the
 * associated data, so a token cannot be read as another account's. Passwords are never stored.
 * Undecryptable tokens are discarded: the user signs in again, which is the only safe recovery.
 */
class RefreshTokenStore(private val file: File, private val cipher: DataCipher) {
    @Synchronized
    fun save(userId: String, refreshToken: String) {
        require(userId.isNotBlank() && '\n' !in userId) { "Invalid user ID" }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(listOf(FORMAT, userId, cipher.encrypt(refreshToken, aad(userId))).joinToString("\n"))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Token for [userId], or null when none is stored for that account or it can no longer be decrypted. */
    @Synchronized
    fun read(userId: String): String? {
        if (!file.isFile) return null
        val lines = file.readLines()
        if (lines.size != 3 || lines[0] != FORMAT) return clearAndNull()
        if (lines[1] != userId) return null
        return try {
            cipher.decrypt(lines[2], aad(userId))
        } catch (_: CipherException) {
            clearAndNull()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun clearAndNull(): String? {
        file.delete()
        return null
    }

    private fun aad(userId: String) = "refresh-token:$FORMAT:$userId"

    private companion object {
        const val FORMAT = "v1"
    }
}
