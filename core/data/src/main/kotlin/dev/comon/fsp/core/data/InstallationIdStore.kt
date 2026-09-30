package dev.comon.fsp.core.data

import dev.comon.fsp.core.network.DeviceIdProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Per-installation device ID (UUID). Kept in `noBackupFilesDir`, so it is never restored onto another
 * device and a reinstall produces a new ID, as the attendance and login contracts require.
 */
class InstallationIdStore(private val file: File) : DeviceIdProvider {
    private val id: String by lazy { readValid() ?: create() }

    override fun deviceId(): String = id

    private fun readValid(): String? = file.takeIf { it.isFile }?.readText()?.trim()?.takeIf(::isUuid)

    private fun create(): String {
        val created = UUID.randomUUID().toString()
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(created)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return created
    }

    private fun isUuid(value: String) = value.length == 36 && runCatching { UUID.fromString(value) }.isSuccess
}
