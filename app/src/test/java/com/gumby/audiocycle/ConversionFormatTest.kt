package com.gumby.audiocycle

import com.gumby.audiocycle.model.ConversionFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversionFormatTest {

    @Test
    fun testConversionFormatProperties() {
        val formats = ConversionFormat.entries
        assertEquals(7, formats.size)

        val mp3 = ConversionFormat.MP3
        assertEquals("mp3", mp3.extension)
        assertEquals("audio/mpeg", mp3.mimeType)
        assertEquals("-c:a libmp3lame", mp3.ffmpegCodecArgs)

        val flac = ConversionFormat.FLAC
        assertEquals("flac", flac.extension)
        assertEquals("audio/flac", flac.mimeType)
        assertEquals("-c:a flac", flac.ffmpegCodecArgs)
    }
}
