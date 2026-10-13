package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class CoverLayoutTest {
    @Test
    fun zeroMissionHasASquareFrameBelowThePortraitCoverHeight() {
        val size = fitCover(coverAspectRatio("Game Boy Advance", 512, 512), CoverSize(144f, 176f))
        assertEquals(144f, size.width, .001f)
        assertEquals(144f, size.height, .001f)
    }

    @Test
    fun portraitArtworkKeepsItsWholeImageAndFitsTheShelf() {
        val size = fitCover(coverAspectRatio("Nintendo Switch", 556, 900), CoverSize(144f, 176f))
        assertEquals(176f, size.height, .001f)
        assertEquals(556f / 900f, size.width / size.height, .001f)
        assertTrue(size.width < 144f)
    }

    @Test
    fun regionalArtworkOverridesTheUsualPlatformShape() {
        val size = fitCover(coverAspectRatio("Game Boy Advance", 800, 505), CoverSize(144f, 176f))
        assertEquals(144f, size.width, .001f)
        assertEquals(800f / 505f, size.width / size.height, .001f)
        assertTrue(size.height < size.width)
    }

    @Test
    fun detailThumbnailsAndFocusCoversKeepTheSameProportions() {
        val aspect = coverAspectRatio("SNES", 800, 560)
        for (bounds in listOf(CoverSize(88f, 120f), CoverSize(216f, 244f))) {
            val size = fitCover(aspect, bounds)
            assertEquals(aspect, size.width / size.height, .001f)
            assertTrue(size.width <= bounds.width && size.height <= bounds.height)
        }
    }
}
