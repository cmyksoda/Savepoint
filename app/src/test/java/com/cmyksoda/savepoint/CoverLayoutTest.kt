package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class CoverLayoutTest {
    @Test
    fun zeroMissionHasASquareFrameBelowThePortraitCoverHeight() {
        val size = fitCover(512f / 512f, CoverSize(144f, 176f))
        assertEquals(144f, size.width, .001f)
        assertEquals(144f, size.height, .001f)
    }

    @Test
    fun portraitArtworkKeepsItsWholeImageAndFitsTheShelf() {
        val size = fitCover(556f / 900f, CoverSize(144f, 176f))
        assertEquals(176f, size.height, .001f)
        assertEquals(556f / 900f, size.width / size.height, .001f)
        assertTrue(size.width < 144f)
    }

    @Test
    fun wideArtworkKeepsItsOwnShape() {
        val size = fitCover(800f / 505f, CoverSize(144f, 176f))
        assertEquals(144f, size.width, .001f)
        assertEquals(800f / 505f, size.width / size.height, .001f)
        assertTrue(size.height < size.width)
    }

    @Test
    fun detailThumbnailsAndFocusCoversKeepTheSameProportions() {
        val aspect = 800f / 560f
        for (bounds in listOf(CoverSize(88f, 120f), CoverSize(216f, 244f))) {
            val size = fitCover(aspect, bounds)
            assertEquals(aspect, size.width / size.height, .001f)
            assertTrue(size.width <= bounds.width && size.height <= bounds.height)
        }
    }

    @Test
    fun coversWithoutArtworkUseTheirPlatformsShape() {
        assertEquals(1f, coverAspectRatio("Game Boy Advance"), 0f)
        assertEquals(1.4f, coverAspectRatio("SNES"), 0f)
        assertEquals(100f / 163f, coverAspectRatio("Nintendo Switch"), 0f)
        assertEquals(2f / 3f, coverAspectRatio("PC"), 0f)
    }
}
