package com.calmapps.calmmusic.data

enum class StreamingProvider {
    APPLE_MUSIC,
    YOUTUBE,
    SUBSONIC,
    ;

    companion object {
        fun fromStored(value: String?): StreamingProvider = when (value) {
            "APPLE_MUSIC" -> APPLE_MUSIC
            "YOUTUBE" -> YOUTUBE
            else -> SUBSONIC
        }

        fun toStored(provider: StreamingProvider): String = when (provider) {
            APPLE_MUSIC -> "APPLE_MUSIC"
            YOUTUBE -> "YOUTUBE"
            SUBSONIC -> "SUBSONIC"
        }
    }
}