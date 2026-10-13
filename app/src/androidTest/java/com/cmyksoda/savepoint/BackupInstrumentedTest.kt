package com.cmyksoda.savepoint

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun backupRoundTripPreservesBothNotesAndSelectedArtworkAndDisablesReminders() = runBlocking {
        val artwork = Artwork(context)
        val name = artwork.filename(UUID.randomUUID().toString())
        val image = File(artwork.directory, name)
        val archive = File(context.cacheDir, "test-${UUID.randomUUID()}.zip")
        try {
            image.outputStream().use {
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val game =
                Game(
                    title = "Test game",
                    notes = "general notes",
                    pauseNote = "pause note",
                    place = Place.PAUSED,
                    cover = "local:$name",
                )
            Backup.export(
                context,
                Uri.fromFile(archive),
                listOf(game),
                Settings(reminders = true),
                artwork,
            )
            val restored = Backup.inspect(context, Uri.fromFile(archive))
            try {
                assertEquals(listOf(game), restored.games)
                assertFalse(restored.settings.reminders)
                assertEquals(0, restored.settings.laterAt)
                assertArrayEquals(
                    image.readBytes(),
                    File(restored.artwork, "artwork/$name").readBytes(),
                )
            } finally {
                restored.artwork.deleteRecursively()
            }
        } finally {
            image.delete()
            archive.delete()
        }
    }

    @Test
    fun traversalArchiveIsRejectedWithoutWritingOutsideStaging() = runBlocking {
        val archive = File(context.cacheDir, "test-${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../escape.txt"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
            try {
                Backup.inspect(context, Uri.fromFile(archive))
                fail("Traversal archive accepted")
            } catch (expected: Exception) {
                assertTrue(
                    expected is java.util.zip.ZipException || expected is IllegalArgumentException
                )
            }
            assertFalse(File(context.cacheDir, "escape.txt").exists())
        } finally {
            archive.delete()
        }
    }

    @Test
    fun unsupportedBackupVersionIsRejected() = runBlocking {
        val archive = File(context.cacheDir, "test-${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("library.json"))
                zip.write("{\"version\":999}".toByteArray())
                zip.closeEntry()
            }
            try {
                Backup.inspect(context, Uri.fromFile(archive))
                fail("Future backup accepted")
            } catch (expected: IllegalArgumentException) {
                assertEquals("Unsupported backup version", expected.message)
            }
        } finally {
            archive.delete()
        }
    }
}
