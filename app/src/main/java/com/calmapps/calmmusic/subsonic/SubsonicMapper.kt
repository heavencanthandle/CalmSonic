package com.calmapps.calmmusic.subsonic

import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.data.ArtistEntity
import com.calmapps.calmmusic.data.SongEntity
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.SongUiModel
import com.calmapps.calmmusic.formatDurationMillis

fun SubsonicArtist.toArtistEntity() = ArtistEntity(
    id = SubsonicIds.artist(id),
    name = name,
    sourceType = SubsonicIds.SOURCE,
)

fun SubsonicAlbum.toAlbumEntity() = AlbumEntity(
    id = SubsonicIds.album(id),
    name = name,
    artist = artist,
    sourceType = SubsonicIds.SOURCE,
    artistId = artistId?.let { SubsonicIds.artist(it) },
)

fun SubsonicSong.toSongEntity() = SongEntity(
    id = SubsonicIds.song(id),
    title = title,
    artist = artist ?: "Unknown artist",
    album = album,
    albumId = albumId?.let { SubsonicIds.album(it) },
    discNumber = discNumber,
    trackNumber = track,
    durationMillis = durationSeconds?.toLong()?.times(1000L),
    sourceType = SubsonicIds.SOURCE,
    audioUri = id,
    artistId = artistId?.let { SubsonicIds.artist(it) },
    releaseYear = year,
    coverArtId = coverArt,
)

fun SubsonicArtist.toArtistUiModel() = ArtistUiModel(
    id = SubsonicIds.artist(id),
    name = name,
    songCount = 0,
    albumCount = albumCount ?: 0,
)

fun SubsonicAlbum.toAlbumUiModel() = AlbumUiModel(
    id = SubsonicIds.album(id),
    title = name,
    artist = artist,
    sourceType = SubsonicIds.SOURCE,
    releaseYear = year,
)

fun SubsonicSong.toSongUiModel(client: SubsonicClient) = SongUiModel(
    id = SubsonicIds.song(id),
    title = title,
    artist = artist ?: "Unknown artist",
    durationText = formatDurationMillis(durationSeconds?.toLong()?.times(1000L)),
    durationMillis = durationSeconds?.toLong()?.times(1000L),
    discNumber = discNumber,
    trackNumber = track,
    sourceType = SubsonicIds.SOURCE,
    audioUri = client.streamUrl(id),
    album = album,
    artworkUri = coverArt?.let { client.coverArtUrl(it) },
)

fun SubsonicPlaylist.toPlaylistUiModel() = PlaylistUiModel(
    id = SubsonicIds.playlist(id),
    name = name,
    description = comment,
    songCount = songCount,
)

fun SongEntity.toSongUiModel(client: SubsonicClient?): SongUiModel {
    val isSubsonic = sourceType == SubsonicIds.SOURCE
    val streamUri = if (isSubsonic && client != null) {
        client.streamUrl(SubsonicIds.rawSong(id))
    } else {
        audioUri
    }
    val art = if (isSubsonic && client != null && !coverArtId.isNullOrBlank()) {
        client.coverArtUrl(coverArtId)
    } else {
        null
    }
    return SongUiModel(
        id = id,
        title = title,
        artist = artist,
        durationText = formatDurationMillis(durationMillis),
        durationMillis = durationMillis,
        discNumber = discNumber,
        trackNumber = trackNumber,
        sourceType = sourceType,
        audioUri = streamUri,
        album = album,
        artworkUri = art,
    )
}

fun isDirectPlaybackSource(sourceType: String): Boolean =
    sourceType == "LOCAL_FILE" || sourceType == "YOUTUBE_DOWNLOAD" || sourceType == SubsonicIds.SOURCE
