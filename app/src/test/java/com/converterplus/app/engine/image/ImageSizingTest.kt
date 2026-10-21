package com.converterplus.app.engine.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSizingTest {

    private val photo = ImageSize(4000, 3000)

    @Test
    fun `fit keeps proportions and never upscales`() {
        val result = ImageSizing.resolve(photo, ImageSize(1920, 1080), FitMode.FIT)
        assertEquals(1440, result.width)
        assertEquals(1080, result.height)
    }

    @Test
    fun `fit returns original for small photos`() {
        val small = ImageSize(800, 600)
        assertEquals(small, ImageSizing.resolve(small, ImageSize(1920, 1080), FitMode.FIT))
    }

    @Test
    fun `crop fills the frame exactly`() {
        val result = ImageSizing.resolve(photo, ImageSize(1080, 1080), FitMode.CROP)
        assertEquals(1080, result.width)
        assertEquals(1080, result.height)
    }

    @Test
    fun `crop allows upscale to the exact frame`() {
        val small = ImageSize(500, 400)
        assertEquals(
            ImageSize(1080, 1080),
            ImageSizing.resolve(small, ImageSize(1080, 1080), FitMode.CROP),
        )
    }

    @Test
    fun `no target means original`() {
        assertEquals(photo, ImageSizing.resolve(photo, null, FitMode.FIT))
    }

    @Test
    fun `custom size is validated`() {
        assertEquals(ImageSize(800, 600), ImageSizing.normalize(800, 600))
        assertNull(ImageSizing.normalize(null, 600))
        assertNull(ImageSizing.normalize(800, null))
        assertNull(ImageSizing.normalize(0, 600))
        assertNull(ImageSizing.normalize(ImageSizing.MAX_SIDE + 1, 600))
        assertNull(ImageSizing.normalize(ImageSizing.MIN_SIDE - 1, 600))
    }

    @Test
    fun `shrink ratio shows how much smaller the result is`() {
        val result = ImageSizing.resolve(photo, ImageSize(1920, 1080), FitMode.FIT)
        assertEquals(4000.0 / 1440.0, ImageSizing.shrinkRatio(photo, result), 0.0001)
        assertEquals(1.0, ImageSizing.shrinkRatio(photo, photo), 0.0001)
    }

    @Test
    fun `presets have unique ids and valid sizes`() {
        val ids = SizePreset.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        SizePreset.entries.forEach { preset ->
            assertEquals(preset, SizePreset.byId(preset.id))
            assertTrue(preset.width in ImageSizing.MIN_SIDE..ImageSizing.MAX_SIDE)
            assertTrue(preset.height in ImageSizing.MIN_SIDE..ImageSizing.MAX_SIDE)
        }
        assertNull(SizePreset.byId("unknown"))
    }

    @Test
    fun `print presets carry human captions`() {
        assertEquals("A4 · 2480 × 3508", SizePreset.A4_300.label)
        assertEquals("1920 × 1080", SizePreset.FULL_HD.label)
    }
}
