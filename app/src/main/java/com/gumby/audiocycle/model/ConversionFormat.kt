package com.gumby.audiocycle.model

enum class ConversionFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val ffmpegCodecArgs: String
) {
    MP3("MP3 (MPEG Audio Layer 3)", "mp3", "audio/mpeg", "-c:a libmp3lame"),
    AAC("M4A / AAC (Advanced Audio Coding)", "m4a", "audio/mp4", "-c:a aac"),
    WAV("WAV (Uncompressed PCM Wave)", "wav", "audio/wav", "-c:a pcm_s16le"),
    FLAC("FLAC (Free Lossless Audio Codec)", "flac", "audio/flac", "-c:a flac"),
    OGG("OGG Vorbis", "ogg", "audio/ogg", "-c:a libvorbis"),
    OPUS("Opus", "opus", "audio/opus", "-c:a libopus"),
    WMA("WMA (Windows Media Audio)", "wma", "audio/x-ms-wma", "-c:a wmav2")
}
