package com.calmapps.calmmusic.subsonic

import android.util.Log
import com.calmapps.calmmusic.data.CalmMusicSettingsManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class SubsonicClient(
    private val settings: CalmMusicSettingsManager,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    @Volatile
    var lastBrowseTrace: String = ""
        private set

    private val browseLog = StringBuilder()
    fun ping(): String {
        val response = SubsonicJson.parseResponse(execute("ping.view"))
        val version = response.get("version")?.asString
        return if (version.isNullOrBlank()) "Connected" else "Connected (API $version)"
    }

    fun getArtists(): List<SubsonicArtist> {
        val id3 = runCatching { SubsonicJson.parseArtists(execute("getArtists.view")) }.getOrDefault(emptyList())
        if (id3.isNotEmpty()) return id3
        return SubsonicJson.parseArtists(execute("getIndexes.view"))
    }

    fun getArtist(id: String, nameHint: String? = null): SubsonicArtistDetail {
        browseLog.setLength(0)
        note("browse artistId=$id name=${nameHint ?: "-"}")
        return try {
            browseArtist(id, nameHint)
        } finally {
            lastBrowseTrace = browseLog.toString()
        }
    }

    private fun browseArtist(id: String, nameHint: String?): SubsonicArtistDetail {
        val extras = mapOf("id" to id)
        val fromId3 = probe("getArtist.view", extras) { SubsonicJson.parseArtist(it) }
        var merged: SubsonicArtistDetail = fromId3?.stamp(id)
            ?: SubsonicArtistDetail(
                artist = SubsonicArtist(id, nameHint ?: "Unknown artist", null, null),
                albums = emptyList(),
                songs = emptyList(),
            )

        if (merged.albums.isEmpty() && merged.songs.isEmpty()) {
            val fromDir = probe("getMusicDirectory.view", extras) { SubsonicJson.parseArtist(it) }
            merged = mergeArtistDetails(fromId3, fromDir, id) ?: merged
        }

        val artistName = nameHint?.takeIf { it.isNotBlank() } ?: merged.artist.name
        if (merged.albums.isEmpty() && merged.songs.isEmpty() && artistName.isNotBlank()) {
            val search = probe(
                "search3.view",
                mapOf(
                    "query" to artistName,
                    "artistCount" to "5",
                    "albumCount" to "100",
                    "songCount" to "100",
                ),
            ) { SubsonicJson.parseSearch3(it) }
            if (search != null) {
                val albums = search.albums.filter { it.matchesArtist(id, artistName) }
                val songs = search.songs.filter { it.matchesArtist(id, artistName) }
                note("search3 matched albums=${albums.size} songs=${songs.size} rawAlbums=${search.albums.size} rawSongs=${search.songs.size}")
                merged = merged.copy(
                    albums = (merged.albums + albums).distinctBy { it.id },
                    songs = (merged.songs + songs).distinctBy { it.id },
                )
            }

            val topSongs = probe(
                "getTopSongs.view",
                mapOf("artist" to artistName, "count" to "50"),
            ) { SubsonicJson.parseSongBucket(it, "topSongs") }
            if (!topSongs.isNullOrEmpty()) {
                note("getTopSongs songs=${topSongs.size}")
                merged = merged.copy(songs = (merged.songs + topSongs).distinctBy { it.id })
            }
        }

        if (merged.albums.isEmpty()) {
            val fromList = mutableListOf<SubsonicAlbum>()
            var offset = 0
            for (pageIndex in 0 until 4) {
                val page = probe(
                    "getAlbumList2.view",
                    mapOf(
                        "type" to "alphabeticalByName",
                        "size" to "50",
                        "offset" to offset.toString(),
                    ),
                ) { SubsonicJson.parseAlbumList(it) } ?: emptyList()
                if (page.isEmpty()) break
                fromList += page.filter { it.matchesArtist(id, artistName) }
                if (fromList.isNotEmpty()) break
                offset += 50
            }
            if (fromList.isNotEmpty()) {
                note("albumList matched albums=${fromList.size}")
                merged = merged.copy(albums = fromList.distinctBy { it.id })
            }
        }

        if (merged.albums.isNotEmpty() && merged.songs.isEmpty()) {
            val collected = mutableListOf<SubsonicSong>()
            val albumsWithSongs = merged.albums.take(20).map { album ->
                val full = browseAlbum(album.id, album.name)
                collected += full.songs
                album.copy(songs = full.songs)
            }
            merged = merged.copy(
                albums = albumsWithSongs + merged.albums.drop(20),
                songs = collected.distinctBy { it.id },
            )
            note("filled songs from albums count=${merged.songs.size}")
        }

        note("result albums=${merged.albums.size} songs=${merged.songs.size}")
        return merged.stamp(id)
    }

    fun getAlbum(id: String, nameHint: String? = null): SubsonicAlbum {
        browseLog.setLength(0)
        note("browse albumId=$id name=${nameHint ?: "-"}")
        return try {
            browseAlbum(id, nameHint)
        } finally {
            lastBrowseTrace = browseLog.toString()
        }
    }

    private fun browseAlbum(id: String, nameHint: String?): SubsonicAlbum {
        val extras = mapOf("id" to id)
        val fromId3 = probe("getAlbum.view", extras) { SubsonicJson.parseAlbum(it) }
        if (fromId3 != null && fromId3.songs.isNotEmpty()) {
            note("using getAlbum songs=${fromId3.songs.size}")
            return fromId3
        }

        val fromDir = probe("getMusicDirectory.view", extras) { SubsonicJson.parseAlbum(it) }
        val base = fromId3 ?: fromDir ?: SubsonicAlbum(
            id = id,
            name = nameHint ?: "Unknown album",
            artist = null,
            artistId = null,
            coverArt = null,
            songCount = null,
            year = null,
        )
        val songs = (fromId3?.songs.orEmpty() + fromDir?.songs.orEmpty()).distinctBy { it.id }.toMutableList()
        val nestedIds = (fromId3?.nestedAlbumIds.orEmpty() + fromDir?.nestedAlbumIds.orEmpty()).distinct()
        for (nestedId in nestedIds.take(30)) {
            val nested = probe("getMusicDirectory.view", mapOf("id" to nestedId)) { SubsonicJson.parseAlbum(it) }
            if (nested != null) {
                songs += nested.songs
            }
        }

        val albumName = nameHint?.takeIf { it.isNotBlank() } ?: base.name
        if (songs.isEmpty() && albumName.isNotBlank()) {
            val search = probe(
                "search3.view",
                mapOf(
                    "query" to albumName,
                    "artistCount" to "0",
                    "albumCount" to "5",
                    "songCount" to "100",
                ),
            ) { SubsonicJson.parseSearch3(it) }
            if (search != null) {
                val matched = search.songs.filter { song ->
                    song.albumId == id || song.album.equals(albumName, ignoreCase = true)
                }
                note("search3 album matched songs=${matched.size} rawSongs=${search.songs.size}")
                songs += matched
            }
        }

        note("album result songs=${songs.distinctBy { it.id }.size}")
        return base.copy(songs = songs.distinctBy { it.id })
    }

    fun getAlbumList(type: String = "alphabeticalByName", size: Int = 50, offset: Int = 0): List<SubsonicAlbum> {
        val params = mapOf(
            "type" to type,
            "size" to size.toString(),
            "offset" to offset.toString(),
        )
        val id3 = runCatching { SubsonicJson.parseAlbumList(execute("getAlbumList2.view", params)) }.getOrNull()
        if (id3 != null) return id3
        return SubsonicJson.parseAlbumList(execute("getAlbumList.view", params))
    }

    fun search3(query: String, count: Int = 25): SubsonicSearchResult {
        val params = mapOf(
            "query" to query,
            "artistCount" to count.toString(),
            "albumCount" to count.toString(),
            "songCount" to count.toString(),
        )
        val id3 = runCatching { SubsonicJson.parseSearch3(execute("search3.view", params)) }.getOrNull()
        if (id3 != null && (id3.artists.isNotEmpty() || id3.albums.isNotEmpty() || id3.songs.isNotEmpty())) {
            return id3
        }
        return runCatching { SubsonicJson.parseSearch3(execute("search2.view", params)) }
            .getOrDefault(id3 ?: SubsonicSearchResult(emptyList(), emptyList(), emptyList()))
    }

    fun getPlaylists(): List<SubsonicPlaylist> = SubsonicJson.parsePlaylists(execute("getPlaylists.view"))

    fun getPlaylist(id: String): SubsonicPlaylist =
        SubsonicJson.parsePlaylist(execute("getPlaylist.view", mapOf("id" to id)))

    fun streamUrl(songId: String): String {
        val extras = mutableMapOf("id" to songId)
        val bitrate = settings.subsonicMaxBitrateKbps
        if (bitrate != null && bitrate > 0) {
            extras["maxBitRate"] = bitrate.toString()
        }
        return buildUrl("stream.view", extras).toString()
    }

    fun coverArtUrl(coverArtId: String, size: Int = 300): String =
        buildUrl("getCoverArt.view", mapOf("id" to coverArtId, "size" to size.toString())).toString()

    fun isConfigured(): Boolean = settings.hasSubsonicCredentials()

    private fun note(message: String) {
        Log.i("CalmSonic", message)
        if (browseLog.isNotEmpty()) browseLog.append('\n')
        browseLog.append(message)
    }

    private fun <T> probe(
        endpoint: String,
        extras: Map<String, String>,
        parse: (String) -> T,
    ): T? {
        return try {
            val body = execute(endpoint, extras)
            note("$endpoint $extras ${SubsonicJson.summarize(body)}")
            note("  snippet: ${body.replace(Regex("\\s+"), " ").take(500)}")
            try {
                parse(body)
            } catch (e: Exception) {
                note("$endpoint parse FAILED ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        } catch (e: Exception) {
            note("$endpoint FAILED ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun SubsonicAlbum.matchesArtist(id: String, name: String): Boolean {
        if (!artistId.isNullOrBlank() && artistId == id) return true
        return !artist.isNullOrBlank() && artist.equals(name, ignoreCase = true)
    }

    private fun SubsonicSong.matchesArtist(id: String, name: String): Boolean {
        if (!artistId.isNullOrBlank() && artistId == id) return true
        return !artist.isNullOrBlank() && artist.equals(name, ignoreCase = true)
    }

    private fun mergeArtistDetails(
        id3: SubsonicArtistDetail?,
        directory: SubsonicArtistDetail?,
        fallbackId: String,
    ): SubsonicArtistDetail? {
        if (id3 == null && directory == null) return null
        val artist = id3?.artist ?: directory?.artist ?: SubsonicArtist(fallbackId, "Unknown artist", null, null)
        val albums = (id3?.albums.orEmpty() + directory?.albums.orEmpty()).distinctBy { it.id }
        val songs = (id3?.songs.orEmpty() + directory?.songs.orEmpty()).distinctBy { it.id }
        return SubsonicArtistDetail(artist = artist, albums = albums, songs = songs)
    }

    private fun SubsonicArtistDetail.stamp(fallbackArtistId: String) = copy(
        albums = albums.map {
            it.copy(
                artistId = it.artistId ?: fallbackArtistId,
                artist = it.artist ?: artist.name,
            )
        },
        songs = songs.map {
            it.copy(
                artistId = it.artistId ?: fallbackArtistId,
                artist = it.artist ?: artist.name,
            )
        },
    )

    private fun execute(endpoint: String, extras: Map<String, String> = emptyMap()): String {
        val url = buildUrl(endpoint, extras)
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(200)}")
            }
            return body
        }
    }

    private fun buildUrl(endpoint: String, extras: Map<String, String> = emptyMap()): HttpUrl {
        val base = normalizeBaseUrl(settings.subsonicBaseUrl)
        val restRoot = (base.trimEnd('/') + "/rest").toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid Subsonic server URL")
        val salt = SubsonicAuth.newSalt()
        val builder = restRoot.newBuilder()
            .addPathSegment(endpoint)
            .addQueryParameter("u", settings.subsonicUsername)
            .addQueryParameter("t", SubsonicAuth.token(settings.subsonicPassword, salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
        extras.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "CalmSonic"

        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (!url.contains("://")) {
                url = "https://$url"
            }
            if (url.endsWith("/")) url = url.dropLast(1)
            if (url.endsWith("/rest", ignoreCase = true)) {
                url = url.dropLast(5)
            }
            return url
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
