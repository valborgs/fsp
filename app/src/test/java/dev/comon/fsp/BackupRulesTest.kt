package dev.comon.fsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The database must be excluded from every backup and transfer path the platform offers. */
class BackupRulesTest {
    private val xmlDir = File("src/main/res/xml")

    private fun parse(name: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(xmlDir, name)).documentElement

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.excludesWholeDatabase() =
        children("exclude").any { it.getAttribute("domain") == "database" && it.getAttribute("path") == "." }

    @Test fun legacyAutoBackupExcludesDatabase() {
        val root = parse("backup_rules.xml")
        assertEquals("full-backup-content", root.tagName)
        assertTrue(root.excludesWholeDatabase())
        assertTrue("no include list, which would silently drop the exclusions' context", root.children("include").isEmpty())
    }

    @Test fun cloudBackupAndDeviceTransferExcludeDatabase() {
        val root = parse("data_extraction_rules.xml")
        val sections = listOf("cloud-backup", "device-transfer").map { root.children(it).single() }
        sections.forEach { assertTrue(it.tagName, it.excludesWholeDatabase()) }
    }
}
