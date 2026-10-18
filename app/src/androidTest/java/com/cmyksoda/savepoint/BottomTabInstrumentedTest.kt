package com.cmyksoda.savepoint

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomTabInstrumentedTest {
    @Test
    fun tabsRenderBeforeTheFirstSwitchAndSlidesReuseTheirCompositionAndLayout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val route = mutableStateOf("home")
        val ready = CountDownLatch(bottomTabRoutes.size)
        val tokens = ConcurrentHashMap<String, Any>()
        val creations = AtomicInteger()
        val disposals = AtomicInteger()
        val measurements = AtomicInteger()
        val draws = bottomTabRoutes.associateWith { AtomicInteger() }
        ActivityScenario.launch(SavepointActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        ScreenContentHost(route.value) { page ->
                            if (page !in bottomTabRoutes) {
                                Text(page)
                                return@ScreenContentHost
                            }
                            val token = remember { Any() }
                            SideEffect { tokens[page] = token }
                            DisposableEffect(Unit) {
                                creations.incrementAndGet()
                                onDispose { disposals.incrementAndGet() }
                            }
                            Box(
                                Modifier.fillMaxSize()
                                    .layout { measurable, constraints ->
                                        measurements.incrementAndGet()
                                        val child = measurable.measure(constraints)
                                        layout(child.width, child.height) { child.place(0, 0) }
                                    }
                                    .drawWithContent {
                                        if (draws.getValue(page).incrementAndGet() == 1)
                                            ready.countDown()
                                        drawContent()
                                    }
                            ) {
                                Text(page)
                            }
                        }
                    }
                }
            }
            assertTrue("All tab viewports should be prepared", ready.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            val firstTokens = tokens.toMap()
            val firstMeasurements = measurements.get()
            assertEquals(4, creations.get())
            assertTrue(draws.getValue("home").get() > 0)
            assertTrue(
                "Hidden tab drawing must be prepared",
                bottomTabRoutes.all { draws.getValue(it).get() > 0 },
            )
            for (page in listOf("log", "home", "backlog", "paused", "log", "home")) {
                instrumentation.runOnMainSync { route.value = page }
                SystemClock.sleep(350)
                instrumentation.waitForIdleSync()
                assertTrue("The selected tab should be drawn", draws.getValue(page).get() > 0)
                assertEquals("Switching tabs must retain compositions", 4, creations.get())
                assertEquals(0, disposals.get())
                assertEquals(
                    "Sliding must not remeasure pages",
                    firstMeasurements,
                    measurements.get(),
                )
                bottomTabRoutes.forEach { assertSame(firstTokens[it], tokens[it]) }
            }
            for (page in listOf("log", "home", "paused", "backlog", "home")) {
                instrumentation.runOnMainSync { route.value = page }
                SystemClock.sleep(40)
            }
            SystemClock.sleep(350)
            instrumentation.waitForIdleSync()
            assertEquals(4, creations.get())
            assertEquals(0, disposals.get())
            assertEquals(firstMeasurements, measurements.get())
            bottomTabRoutes.forEach { assertSame(firstTokens[it], tokens[it]) }
            val firstDraws = draws.mapValues { it.value.get() }
            instrumentation.runOnMainSync { route.value = "details" }
            SystemClock.sleep(250)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { route.value = "home" }
            SystemClock.sleep(350)
            instrumentation.waitForIdleSync()
            assertEquals(8, creations.get())
            assertEquals(4, disposals.get())
            bottomTabRoutes.drop(1).forEach {
                assertEquals(
                    "Returning from details must not repeat startup drawing",
                    firstDraws[it],
                    draws.getValue(it).get(),
                )
            }
        }
    }
}
