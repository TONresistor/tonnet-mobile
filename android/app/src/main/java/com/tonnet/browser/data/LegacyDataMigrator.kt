package com.tonnet.browser.data

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.io.FileOutputStream

object LegacyDataMigrator {
    @SuppressLint("ApplySharedPref", "UseKtx")
    fun runOnce(context: Context): Result<Unit> {
        val marker = File(context.noBackupFilesDir, "native-v2-data-reset-complete")
        if (isCurrentMarker(marker)) return Result.success(Unit)

        val appRoot = context.dataDir
        val legacyDirectories = listOf(
            File(appRoot, "app_webview"),
            File(appRoot, "databases"),
            File(appRoot, "cache"),
            File(appRoot, "code_cache"),
            File(appRoot, "files/proxy"),
        )
        return migrate(
            marker = marker,
            legacyDirectories = legacyDirectories,
            clearLegacyPreferences = {
                context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            },
        )
    }

    internal fun migrate(
        marker: File,
        legacyDirectories: List<File>,
        clearLegacyPreferences: () -> Boolean,
        deleteDirectory: (File) -> Boolean = File::deleteRecursively,
    ): Result<Unit> = runCatching {
        if (isCurrentMarker(marker)) return@runCatching

        legacyDirectories.forEach { directory ->
            if (directory.exists()) {
                check(deleteDirectory(directory) && !directory.exists()) {
                    "Could not remove legacy application data"
                }
            }
        }
        check(clearLegacyPreferences()) {
            "Could not clear legacy preferences"
        }

        val parent = requireNotNull(marker.parentFile)
        check(parent.exists() || parent.mkdirs()) {
            "Could not prepare migration marker directory"
        }
        val temporaryMarker = File(parent, "${marker.name}.tmp")
        if (temporaryMarker.exists()) check(temporaryMarker.delete())
        FileOutputStream(temporaryMarker).use { output ->
            output.write(MARKER_CONTENT.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (marker.exists()) check(marker.delete()) {
            "Could not replace outdated migration marker"
        }
        check(temporaryMarker.renameTo(marker)) {
            temporaryMarker.delete()
            "Could not commit migration marker"
        }
    }

    private fun isCurrentMarker(marker: File): Boolean =
        runCatching { marker.isFile && marker.readText() == MARKER_CONTENT }.getOrDefault(false)

    private const val MARKER_CONTENT = "2.0.0-beta\n"
}
