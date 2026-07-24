package com.tonnet.browser.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyDataMigratorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `marker is committed only after every deletion and preference clear succeeds`() {
        val root = temporaryFolder.newFolder("success")
        val legacy = root.resolve("legacy").apply {
            mkdirs()
            resolve("private.txt").writeText("private")
        }
        val marker = root.resolve("no-backup/migration-complete")

        val result = LegacyDataMigrator.migrate(
            marker = marker,
            legacyDirectories = listOf(legacy),
            clearLegacyPreferences = { true },
        )

        assertTrue(result.isSuccess)
        assertFalse(legacy.exists())
        assertTrue(marker.exists())
    }

    @Test
    fun `failed deletion leaves marker absent so migration retries`() {
        val root = temporaryFolder.newFolder("delete-failure")
        val legacy = root.resolve("legacy").apply { mkdirs() }
        val marker = root.resolve("no-backup/migration-complete")

        val result = LegacyDataMigrator.migrate(
            marker = marker,
            legacyDirectories = listOf(legacy),
            clearLegacyPreferences = { true },
            deleteDirectory = { false },
        )

        assertTrue(result.isFailure)
        assertFalse(marker.exists())
    }

    @Test
    fun `failed preference clear leaves marker absent so migration retries`() {
        val root = temporaryFolder.newFolder("preferences-failure")
        val marker = root.resolve("no-backup/migration-complete")

        val result = LegacyDataMigrator.migrate(
            marker = marker,
            legacyDirectories = emptyList(),
            clearLegacyPreferences = { false },
        )

        assertTrue(result.isFailure)
        assertFalse(marker.exists())
    }

    @Test
    fun `outdated marker is not trusted and is replaced after a verified retry`() {
        val root = temporaryFolder.newFolder("outdated-marker")
        val marker = root.resolve("no-backup/migration-complete").apply {
            parentFile?.mkdirs()
            writeText("2.0.0-alpha.1\n")
        }
        var preferencesCleared = false

        val result = LegacyDataMigrator.migrate(
            marker = marker,
            legacyDirectories = emptyList(),
            clearLegacyPreferences = {
                preferencesCleared = true
                true
            },
        )

        assertTrue(result.isSuccess)
        assertTrue(preferencesCleared)
        assertTrue(marker.readText().contains("2.0.0-beta"))
    }
}
