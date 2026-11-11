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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun savedLibraryCoversAreDecodedBeforeTheFirstShelfWithoutNetwork() = runBlocking {
        val saved = Artwork(context)
        val name = saved.filename(UUID.randomUUID().toString())
        val file = File(saved.directory, name)
        try {
            file.outputStream().use {
                Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888)
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val url = "local:$name"
            val reopened = Artwork(context)
            assertNull(reopened.memoryBitmap(url))
            reopened.prepareLibraryArtwork(
                listOf(Game(title = "Saved cover", cover = url, place = Place.ACTIVE))
            )
            val bitmap = reopened.memoryBitmap(url)
            assertNotNull(bitmap)
            assertEquals(80, bitmap!!.width)
            assertEquals(1.6f, reopened.cachedAspectRatio(url)!!, .001f)
            assertSame(bitmap, reopened.cachedCoverBitmap(url))
            assertEquals(File(context.filesDir, "artwork"), reopened.directory)
        } finally {
            file.delete()
        }
    }

    @Test
    fun originalFormatBackupLoadsWithoutInventingPlaythroughs() = runBlocking {
        val archive = File(context.cacheDir, "test-${UUID.randomUUID()}.zip")
        try {
            val legacy =
                JsonCodec.game(
                    Game(title = "Older game", place = Place.FINISHED, notes = "keep these notes")
                )
            legacy.remove("playthroughs")
            legacy.remove("hasPlayed")
            legacy.remove("replayCount")
            legacy.remove("dailyPlays")
            val manifest =
                JSONObject()
                    .put("version", 1)
                    .put("games", JSONArray().put(legacy))
                    .put("settings", JsonCodec.settings(Settings()))
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("library.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
            }
            val restored = Backup.inspect(context, Uri.fromFile(archive))
            try {
                assertTrue(restored.games.single().inPlayLog)
                assertTrue(restored.games.single().playthroughs.isEmpty())
                assertTrue(restored.games.single().dailyPlays.isEmpty())
                assertEquals("keep these notes", restored.games.single().notes)
            } finally {
                restored.artwork.deleteRecursively()
            }
        } finally {
            archive.delete()
        }
    }

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
                    dailyPlays = listOf(DailyPlay("2026-10-12"), DailyPlay("2026-10-13", 75)),
                    notes = "general notes",
                    pauseNote = "pause note",
                    place = Place.PAUSED,
                    cover = "local:$name",
                    coverProvider = "Selected image",
                    collection = "favorites",
                    hasPlayed = true,
                    replayCount = 1,
                    playthroughs =
                        listOf(
                            Playthrough(
                                feeling = Feeling.LOVED,
                                thoughts = "first playthrough",
                                hours = 2.5,
                            ),
                            Playthrough(feeling = Feeling.LIKED, thoughts = "replay thoughts"),
                        ),
                )
            Backup.export(
                context,
                Uri.fromFile(archive),
                listOf(game),
                Settings(
                    reminders = true,
                    celebratedMilestones = setOf("streak:3"),
                    eveningCheckIn = true,
                    collectionNames = setOf("favorites", "empty collection"),
                ),
                artwork,
            )
            val restored = Backup.inspect(context, Uri.fromFile(archive))
            try {
                assertEquals(listOf(game), restored.games)
                assertFalse(restored.settings.reminders)
                assertFalse(restored.settings.eveningCheckIn)
                assertEquals(setOf("streak:3"), restored.settings.celebratedMilestones)
                assertEquals(
                    setOf("favorites", "empty collection"),
                    restored.settings.collectionNames,
                )
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
    fun remoteCoverBackupKeepsBothQualityAndOfflineFallback() = runBlocking {
        val artwork = Artwork(context)
        val url = "https://art.gametdb.com/switch/cover/US/TESTA.jpg"
        val preferred = preferredCoverUrl(url)
        assertEquals("https://art.gametdb.com/switch/coverHQ/US/TESTA.jpg", preferred)
        assertEquals("local:cover", preferredCoverUrl("local:cover"))
        val fallback = File(artwork.directory, artwork.filename(url))
        val quality = File(artwork.directory, artwork.filename(preferred))
        val archive = File(context.cacheDir, "test-${UUID.randomUUID()}.zip")
        try {
            for ((file, width) in listOf(fallback to 2, quality to 8)) {
                file.outputStream().use {
                    Bitmap.createBitmap(width, 2, Bitmap.Config.ARGB_8888)
                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            assertEquals(2, artwork.cachedBitmap(url)!!.width)
            assertEquals(8, artwork.bitmap(preferred)!!.width)
            Backup.export(
                context,
                Uri.fromFile(archive),
                listOf(Game(title = "Test game", cover = url)),
                Settings(),
                artwork,
            )
            val restored = Backup.inspect(context, Uri.fromFile(archive))
            try {
                assertArrayEquals(
                    fallback.readBytes(),
                    File(restored.artwork, "artwork/${fallback.name}").readBytes(),
                )
                assertArrayEquals(
                    quality.readBytes(),
                    File(restored.artwork, "artwork/${quality.name}").readBytes(),
                )
                assertEquals(url, restored.games.single().cover)
            } finally {
                restored.artwork.deleteRecursively()
            }
        } finally {
            fallback.delete()
            quality.delete()
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
