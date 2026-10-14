package com.calmapps.calmmusic.subsonic

import com.calmapps.calmmusic.data.AlbumDao
import com.calmapps.calmmusic.data.ArtistDao
import com.calmapps.calmmusic.data.PlaylistDao
import com.calmapps.calmmusic.data.PlaylistEntity
import com.calmapps.calmmusic.data.PlaylistTrackEntity
import com.calmapps.calmmusic.data.SongDao
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.SongUiModel

class SubsonicLibrarySync(
    private val client: SubsonicClient,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
) {
    suspend fun syncArtistsAndAlbums() {
        if (!client.isConfigured()) return

        val artists = client.getArtists()
        artistDao.deleteBySourceType(SubsonicIds.SOURCE)
        if (artists.isNotEmpty()) {
            artistDao.upsertAll(artists.map { it.toArtistEntity() })
        }

        val albums = mutableListOf<SubsonicAlbum>()
        var offset = 0
        val pageSize = 50
        try {
            while (true) {
                val page = client.getAlbumList(size = pageSize, offset = offset)
                if (page.isEmpty()) break
                albums += page
                if (page.size < pageSize || albums.size >= 2000) break
                offset += pageSize
            }
        } catch (_: Exception) {
            // Older servers may lack getAlbumList2; artist details still fill albums.
        }
        if (albums.isNotEmpty()) {
            albumDao.deleteBySourceType(SubsonicIds.SOURCE)
            albumDao.upsertAll(albums.map { it.toAlbumEntity() })
        }
    }

    suspend fun loadArtistAlbums(artistId: String): SubsonicArtistDetail {
        if (!client.isConfigured()) {
            return SubsonicArtistDetail(
                artist = SubsonicArtist(SubsonicIds.rawArtist(artistId), "Unknown artist", null, null),
                albums = emptyList(),
                songs = emptyList(),
            )
        }
        val rawId = SubsonicIds.rawArtist(artistId)
        val nameHint = artistDao.getAllArtists().firstOrNull { it.id == artistId }?.name
        val detail = client.getArtist(rawId, nameHint)
        if (detail.albums.isNotEmpty()) {
            albumDao.upsertAll(detail.albums.map { it.toAlbumEntity() })
        }
        if (detail.songs.isNotEmpty()) {
            songDao.upsertAll(detail.songs.map { it.toSongEntity() })
        }
        return detail
    }

    suspend fun loadAlbumSongs(albumId: String, albumName: String? = null): List<SongUiModel> {
        if (!client.isConfigured()) return emptyList()
        val album = client.getAlbum(SubsonicIds.rawAlbum(albumId), albumName)
        albumDao.upsertAll(listOf(album.toAlbumEntity()))
        val songs = album.songs
        if (songs.isNotEmpty()) {
            songDao.upsertAll(songs.map { it.toSongEntity() })
        }
        return songs.map { it.toSongUiModel(client) }
            .sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: 0 }))
    }

    suspend fun remotePlaylists(): List<PlaylistUiModel> {
        if (!client.isConfigured()) return emptyList()
        return client.getPlaylists().map { it.toPlaylistUiModel() }
    }

    suspend fun loadPlaylistSongs(playlistId: String): List<SongUiModel> {
        if (!client.isConfigured()) return emptyList()
        val playlist = client.getPlaylist(SubsonicIds.rawPlaylist(playlistId))
        val playlistEntity = PlaylistEntity(
            id = SubsonicIds.playlist(playlist.id),
            name = playlist.name,
            description = playlist.comment,
        )
        playlistDao.upsertPlaylist(playlistEntity)
        val songs = playlist.songs
        if (songs.isNotEmpty()) {
            songDao.upsertAll(songs.map { it.toSongEntity() })
            playlistDao.deleteTracksForPlaylist(playlistEntity.id)
            playlistDao.upsertTracks(
                songs.mapIndexed { index, song ->
                    PlaylistTrackEntity(
                        playlistId = playlistEntity.id,
                        songId = SubsonicIds.song(song.id),
                        position = index,
                    )
                },
            )
        }
        return songs.map { it.toSongUiModel(client) }
    }
}
