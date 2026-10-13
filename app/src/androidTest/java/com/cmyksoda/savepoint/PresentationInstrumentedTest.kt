package com.cmyksoda.savepoint

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun spriteCroppingRemovesTransparentBorderAndKeepsOriginalFile() = runBlocking {
        val artwork = Artwork(context)
        val name = artwork.filename("sprite-cropping-test")
        val file = java.io.File(artwork.directory, name)
        try {
            val original = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            for (y in 40 until 52) for (x in 42 until 50) original.setPixel(
                x,
                y,
                android.graphics.Color.GREEN,
            )
            file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val bytes = file.readBytes()
            val sprite = artwork.spriteBitmap("local:$name")!!
            assertEquals(8, sprite.width)
            assertEquals(12, sprite.height)
            assertSame(sprite, artwork.memoryBitmap("local:$name", true))
            assertEquals(96, artwork.cachedBitmap("local:$name")!!.width)
            assertArrayEquals(bytes, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun bothLauncherForegroundsFitInsideAdaptiveIconSafeCircle() {
        for (id in listOf(R.drawable.icon_handheld_launcher, R.drawable.icon_handheld_mono)) {
            val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
            val icon = ContextCompat.getDrawable(context, id)!!
            icon.setBounds(0, 0, 108, 108)
            icon.draw(Canvas(bitmap))
            for (y in 0 until 108) for (x in 0 until 108) {
                if ((bitmap.getPixel(x, y) ushr 24) != 0) {
                    assertTrue(
                        "Clipped launcher pixel at $x,$y",
                        (x - 54) * (x - 54) + (y - 54) * (y - 54) <= 33 * 33,
                    )
                }
            }
        }
    }

    @Test
    fun invitationActionsOpenLaterPickerAndSuppressFurtherFollowups() {
        val first = Reminders.invitationNotification(context, 60000, allowLater = true)
        val followup = Reminders.invitationNotification(context, 60000, allowLater = false)
        assertEquals(3, first.actions.size)
        assertTrue(first.actions.first().actionIntent.isActivity)
        assertTrue(first.actions.drop(1).all { it.actionIntent.isBroadcast })
        assertEquals(2, followup.actions.size)
        assertTrue(followup.actions.all { it.actionIntent.isBroadcast })
        assertEquals(first.actions[1].actionIntent, followup.actions[0].actionIntent)
        assertEquals(first.actions[2].actionIntent, followup.actions[1].actionIntent)
        assertEquals(60000L, first.timeoutAfter)
        if (InstrumentationRegistry.getArguments().getString("notificationPreview") == "true") {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    Reminders.CHANNEL,
                    "Time for yourself",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun command(value: String) {
                automation.executeShellCommand(value).use { descriptor ->
                    java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
            }
            try {
                manager.notify(Reminders.NOTIFICATION, first)
                command("cmd statusbar expand-notifications")
                android.os.SystemClock.sleep(3000)
                assertTrue(manager.activeNotifications.any { it.id == Reminders.NOTIFICATION })
                val screenshot = automation.takeScreenshot()!!
                java.io.File(context.cacheDir, "notification-preview.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                manager.cancel(Reminders.NOTIFICATION)
                command("cmd statusbar collapse")
            }
        }
    }
}
