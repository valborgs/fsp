package dev.comon.fsp.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class InstallationIdStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun idFile() = File(folder.root, "no_backup/installation-id")

    @Test fun createsUuidOnceAndReusesItAcrossInstances() {
        val first = InstallationIdStore(idFile()).deviceId()
        UUID.fromString(first)
        assertEquals(first, InstallationIdStore(idFile()).deviceId())
        assertEquals(first, idFile().readText())
    }

    @Test fun corruptedFileIsReplacedWithValidId() {
        idFile().parentFile!!.mkdirs()
        idFile().writeText("garbage")
        val id = InstallationIdStore(idFile()).deviceId()
        UUID.fromString(id)
        assertEquals(id, idFile().readText())
    }

    @Test fun missingFileMeansNewInstallationId() {
        val first = InstallationIdStore(idFile()).deviceId()
        assertTrue(idFile().delete()) // models a reinstall
        assertNotEquals(first, InstallationIdStore(idFile()).deviceId())
    }
}
