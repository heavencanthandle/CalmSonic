package com.calmapps.calmmusic.subsonic

object SubsonicIds {
    const val SOURCE = "SUBSONIC"

    fun artist(id: String) = "SUBSONIC:artist:$id"
    fun album(id: String) = "SUBSONIC:album:$id"
    fun song(id: String) = "SUBSONIC:song:$id"
    fun playlist(id: String) = "SUBSONIC:playlist:$id"

    fun rawArtist(id: String) = id.removePrefix("SUBSONIC:artist:")
    fun rawAlbum(id: String) = id.removePrefix("SUBSONIC:album:")
    fun rawSong(id: String) = id.removePrefix("SUBSONIC:song:")
    fun rawPlaylist(id: String) = id.removePrefix("SUBSONIC:playlist:")

    fun isSubsonic(id: String) = id.startsWith("SUBSONIC:")
    fun isPlaylist(id: String) = id.startsWith("SUBSONIC:playlist:")
}
