package dev.comon.fsp.core.data

import dev.comon.fsp.core.network.DeviceIdProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Per-installation device ID (UUID v4). Kept in `noBackupFilesDir`, so it is never restored onto another
 * device and a reinstall produces a new ID, as the attendance and login contracts require.
 */
class InstallationIdStore(private val file: File) : DeviceIdProvider {
    private val id: String by lazy { readUuid(file) ?: UUID.randomUUID().toString().also { writeAtomically(file, it) } }

    override fun deviceId(): String = id
}

/** Idempotency-Key of an unfinished token refresh; see [SessionCredentials.pendingRefreshKey]. */
class RefreshKeyStore(private val file: File) {
    @Synchronized
    fun getOrCreate(): String = readUuid(file) ?: UUID.randomUUID().toString().also { writeAtomically(file, it) }

    @Synchronized
    fun clear() {
        file.delete()
    }
}

internal fun readUuid(file: File): String? = file.takeIf { it.isFile }?.readText()?.trim()?.takeIf {
    it.length == 36 && runCatching { UUID.fromString(it) }.isSuccess
}

internal fun writeAtomically(file: File, text: String) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeText(text)
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
