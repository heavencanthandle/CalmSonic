package com.heavencanthandle.calmsonic.subsonic

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class SubsonicApiException(
    val code: Int,
    override val message: String,
) : Exception(message)

data class SubsonicArtist(
    val id: String,
    val name: String,
    val albumCount: Int?,
    val coverArt: String?,
)

data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val year: Int?,
    val songs: List<SubsonicSong> = emptyList(),
    val nestedAlbumIds: List<String> = emptyList(),
)

data class SubsonicSong(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val track: Int?,
    val discNumber: Int?,
    val durationSeconds: Int?,
    val year: Int?,
    val coverArt: String?,
)

data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val comment: String?,
    val songCount: Int?,
    val songs: List<SubsonicSong> = emptyList(),
)

data class SubsonicSearchResult(
    val artists: List<SubsonicArtist>,
    val albums: List<SubsonicAlbum>,
    val songs: List<SubsonicSong>,
)

data class SubsonicArtistDetail(
    val artist: SubsonicArtist,
    val albums: List<SubsonicAlbum>,
    val songs: List<SubsonicSong> = emptyList(),
)

object SubsonicJson {
    fun parseResponse(body: String): JsonObject {
        val root = JsonParser.parseString(body).asJsonObject
        val response = root.getAsJsonObject("subsonic-response")
            ?: throw SubsonicApiException(-1, "Missing subsonic-response")
        if (response.get("status")?.asString == "failed") {
            val error = response.getAsJsonObject("error")
            throw SubsonicApiException(
                code = error?.get("code")?.asInt ?: -1,
                message = error?.get("message")?.asString ?: "Request failed",
            )
        }
        return response
    }

    fun parseArtists(body: String): List<SubsonicArtist> {
        val response = parseResponse(body)
        val artists = response.getAsJsonObject("artists")
            ?: response.getAsJsonObject("indexes")
            ?: return emptyList()
        return artists.asObjectList("index").flatMap { index ->
            index.asObjectList("artist").map { it.toArtist() }
        }
    }

    fun looksLikeXml(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("<") || trimmed.startsWith("\uFEFF<")
    }

    fun summarize(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "empty body"
        if (looksLikeXml(body)) {
            val tags = Regex("<([A-Za-z0-9:_-]+)")
                .findAll(trimmed.take(2500))
                .map { it.groupValues[1] }
                .filter { it != "?xml" }
                .distinct()
                .take(15)
                .joinToString()
            return "XML bytes=${body.length} tags=[$tags]"
        }
        return try {
            val response = parseResponse(body)
            val keys = response.keySet().joinToString()
            val container = response.firstObject(
                "artist", "directory", "album", "albumList2", "albumList",
                "albumListID3", "searchResult3", "searchResult2", "topSongs",
                "artists", "indexes",
            )
            val inner = container?.keySet()?.joinToString() ?: "-"
            val albums = container?.asObjectList("album")?.size ?: 0
            val songs = container?.asObjectList("song")?.size ?: 0
            val children = container?.asObjectList("child")?.size ?: 0
            "JSON keys=[$keys] inner=[$inner] album=$albums song=$songs child=$children"
        } catch (e: Exception) {
            "parse-fail ${e.javaClass.simpleName}: ${e.message} bytes=${body.length}"
        }
    }

    fun parseArtist(body: String): SubsonicArtistDetail {
        if (looksLikeXml(body)) return parseArtistXml(body)
        val response = parseResponse(body)
        val container = response.firstObject("artist", "directory")
            ?: throw SubsonicApiException(-1, "Missing artist")
        val artist = container.toArtist()
        val children = container.parseChildren(
            fallbackArtistId = artist.id,
            fallbackArtistName = artist.name,
            fallbackAlbumId = null,
            fallbackAlbumName = null,
        )
        val albums = children.albums.map { album ->
            album.copy(
                artistId = album.artistId ?: artist.id,
                artist = album.artist ?: artist.name,
            )
        }
        val songs = children.songs.map { song ->
            song.copy(
                artistId = song.artistId ?: artist.id,
                artist = song.artist ?: artist.name,
            )
        }
        return SubsonicArtistDetail(artist = artist, albums = albums, songs = songs)
    }

    fun parseAlbum(body: String): SubsonicAlbum {
        if (looksLikeXml(body)) return parseAlbumXml(body)
        val response = parseResponse(body)
        val albumObj = response.firstObject("album", "directory")
            ?: throw SubsonicApiException(-1, "Missing album")
        val album = albumObj.toAlbum()
        val children = albumObj.parseChildren(
            fallbackArtistId = album.artistId,
            fallbackArtistName = album.artist,
            fallbackAlbumId = album.id,
            fallbackAlbumName = album.name,
        )
        return album.copy(
            songs = children.songs.map { song ->
                song.copy(
                    albumId = song.albumId ?: album.id,
                    album = song.album ?: album.name,
                    artistId = song.artistId ?: album.artistId,
                    artist = song.artist ?: album.artist,
                )
            },
            nestedAlbumIds = children.albums.map { it.id },
        )
    }

    fun parseAlbumList(body: String): List<SubsonicAlbum> {
        val response = parseResponse(body)
        val list = response.getAsJsonObject("albumList2")
            ?: response.getAsJsonObject("albumList")
            ?: response.getAsJsonObject("albumListID3")
            ?: return emptyList()
        val items = list.asObjectList("album").ifEmpty { list.asObjectList("child") }
        return items.map { it.toAlbum() }
    }

    fun parseSongBucket(body: String, bucket: String): List<SubsonicSong> {
        if (looksLikeXml(body)) {
            return parseArtistXml(body).songs
        }
        val response = parseResponse(body)
        val obj = response.firstObject(bucket, "directory", "album") ?: return emptyList()
        return obj.asObjectList("song").ifEmpty { obj.asObjectList("child") }.mapNotNull {
            runCatching { it.toSong() }.getOrNull()
        }
    }

    fun parseSearch3(body: String): SubsonicSearchResult {
        val result = parseResponse(body).getAsJsonObject("searchResult3")
            ?: parseResponse(body).getAsJsonObject("searchResult2")
            ?: return SubsonicSearchResult(emptyList(), emptyList(), emptyList())
        return SubsonicSearchResult(
            artists = result.asObjectList("artist").map { it.toArtist() },
            albums = result.asObjectList("album").map { it.toAlbum() },
            songs = result.asObjectList("song").map { it.toSong() },
        )
    }

    fun parsePlaylists(body: String): List<SubsonicPlaylist> {
        val playlists = parseResponse(body).getAsJsonObject("playlists") ?: return emptyList()
        return playlists.asObjectList("playlist").map { it.toPlaylist() }
    }

    fun parsePlaylist(body: String): SubsonicPlaylist {
        val playlist = parseResponse(body).getAsJsonObject("playlist")
            ?: throw SubsonicApiException(-1, "Missing playlist")
        return playlist.toPlaylist().copy(songs = playlist.asObjectList("entry").map { it.toSong() })
    }

    private data class DirectoryChildren(
        val albums: List<SubsonicAlbum>,
        val songs: List<SubsonicSong>,
    )

    private fun JsonObject.parseChildren(
        fallbackArtistId: String?,
        fallbackArtistName: String?,
        fallbackAlbumId: String?,
        fallbackAlbumName: String?,
    ): DirectoryChildren {
        val albums = mutableListOf<SubsonicAlbum>()
        val songs = mutableListOf<SubsonicSong>()

        // DSub MusicDirectoryParser: <album> children are always directories.
        asObjectList("album").forEach { child ->
            runCatching { child.toAlbum().withFallbacks(fallbackArtistId, fallbackArtistName) }
                .getOrNull()
                ?.let { albums += it }
        }
        asObjectList("entry").forEach { child ->
            if (child.isDirectory()) {
                runCatching { child.toAlbum().withFallbacks(fallbackArtistId, fallbackArtistName) }
                    .getOrNull()
                    ?.let { albums += it }
            } else {
                runCatching {
                    child.toSong().withFallbacks(
                        fallbackArtistId,
                        fallbackArtistName,
                        fallbackAlbumId,
                        fallbackAlbumName,
                    )
                }.getOrNull()?.let { songs += it }
            }
        }
        // <song> children are always tracks.
        asObjectList("song").forEach { child ->
            runCatching {
                child.toSong().withFallbacks(
                    fallbackArtistId,
                    fallbackArtistName,
                    fallbackAlbumId,
                    fallbackAlbumName,
                )
            }.getOrNull()?.let { songs += it }
        }
        // <child> uses isDir like getMusicDirectory.
        asObjectList("child").forEach { child ->
            if (child.isDirectory()) {
                runCatching { child.toAlbum().withFallbacks(fallbackArtistId, fallbackArtistName) }
                    .getOrNull()
                    ?.let { albums += it }
            } else {
                runCatching {
                    child.toSong().withFallbacks(
                        fallbackArtistId,
                        fallbackArtistName,
                        fallbackAlbumId,
                        fallbackAlbumName,
                    )
                }.getOrNull()?.let { songs += it }
            }
        }
        return DirectoryChildren(albums = albums.distinctBy { it.id }, songs = songs.distinctBy { it.id })
    }

    private fun SubsonicAlbum.withFallbacks(artistId: String?, artistName: String?) = copy(
        artistId = this.artistId ?: artistId,
        artist = this.artist ?: artistName,
    )

    private fun SubsonicSong.withFallbacks(
        artistId: String?,
        artistName: String?,
        albumId: String?,
        albumName: String?,
    ) = copy(
        artistId = this.artistId ?: artistId,
        artist = this.artist ?: artistName,
        albumId = this.albumId ?: albumId,
        album = this.album ?: albumName,
    )

    private fun JsonObject.toArtist() = SubsonicArtist(
        id = requiredString("id"),
        name = optionalString("name") ?: optionalString("title") ?: "Unknown artist",
        albumCount = optionalInt("albumCount"),
        coverArt = optionalString("coverArt"),
    )

    private fun JsonObject.toAlbum() = SubsonicAlbum(
        id = requiredString("id"),
        name = optionalString("name") ?: optionalString("title") ?: optionalString("album") ?: "Unknown album",
        artist = optionalString("artist"),
        artistId = optionalString("artistId"),
        coverArt = optionalString("coverArt"),
        songCount = optionalInt("songCount"),
        year = optionalInt("year"),
    )

    private fun JsonObject.toSong() = SubsonicSong(
        id = requiredString("id"),
        title = optionalString("title") ?: optionalString("name") ?: "Unknown title",
        artist = optionalString("artist"),
        album = optionalString("album"),
        albumId = optionalString("albumId"),
        artistId = optionalString("artistId"),
        track = optionalInt("track"),
        discNumber = optionalInt("discNumber"),
        durationSeconds = optionalInt("duration"),
        year = optionalInt("year"),
        coverArt = optionalString("coverArt"),
    )

    private fun JsonObject.toPlaylist() = SubsonicPlaylist(
        id = requiredString("id"),
        name = optionalString("name") ?: "Playlist",
        comment = optionalString("comment"),
        songCount = optionalInt("songCount"),
    )

    private fun JsonObject.isDirectory(): Boolean {
        optionalBoolean("isDir")?.let { return it }
        optionalBoolean("isDirectory")?.let { return it }
        if (optionalString("suffix") != null || optionalString("contentType") != null) return false
        if (optionalInt("duration") != null) return false
        return optionalInt("songCount") != null || optionalInt("albumCount") != null
    }

    private fun JsonObject.requiredString(key: String): String =
        optionalString(key) ?: throw SubsonicApiException(-1, "Missing $key")

    private fun JsonObject.optionalString(key: String): String? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return try {
            when {
                value.isJsonPrimitive -> value.asString
                else -> null
            }?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.optionalInt(key: String): Int? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return try {
            value.asInt
        } catch (_: Exception) {
            value.asString.toIntOrNull()
        }
    }

    private fun JsonObject.optionalBoolean(key: String): Boolean? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return try {
            val primitive = value.asJsonPrimitive
            when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> primitive.asInt != 0
                else -> primitive.asString.equals("true", ignoreCase = true)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.firstObject(vararg keys: String): JsonObject? {
        for (key in keys) {
            val element = get(key) ?: continue
            when {
                element.isJsonObject -> return element.asJsonObject
                element.isJsonArray -> {
                    val first = element.asJsonArray.firstOrNull { it.isJsonObject }
                    if (first != null) return first.asJsonObject
                }
            }
        }
        return null
    }

    private fun parseArtistXml(body: String): SubsonicArtistDetail {
        val parsed = parseDirectoryXml(body)
        val artist = SubsonicArtist(
            id = parsed.id ?: throw SubsonicApiException(-1, "Missing artist"),
            name = parsed.name ?: "Unknown artist",
            albumCount = parsed.albums.size,
            coverArt = parsed.coverArt,
        )
        return SubsonicArtistDetail(
            artist = artist,
            albums = parsed.albums.map {
                it.copy(artistId = it.artistId ?: artist.id, artist = it.artist ?: artist.name)
            },
            songs = parsed.songs.map {
                it.copy(artistId = it.artistId ?: artist.id, artist = it.artist ?: artist.name)
            },
        )
    }

    private fun parseAlbumXml(body: String): SubsonicAlbum {
        val parsed = parseDirectoryXml(body)
        val id = parsed.id ?: throw SubsonicApiException(-1, "Missing album")
        return SubsonicAlbum(
            id = id,
            name = parsed.name ?: "Unknown album",
            artist = parsed.artist,
            artistId = parsed.artistId,
            coverArt = parsed.coverArt,
            songCount = parsed.songs.size,
            year = parsed.year,
            songs = parsed.songs.map {
                it.copy(albumId = it.albumId ?: id, album = it.album ?: parsed.name)
            },
            nestedAlbumIds = parsed.albums.map { it.id },
        )
    }

    private data class ParsedXmlDirectory(
        val id: String?,
        val name: String?,
        val artist: String?,
        val artistId: String?,
        val coverArt: String?,
        val year: Int?,
        val albums: List<SubsonicAlbum>,
        val songs: List<SubsonicSong>,
    )

    private fun parseDirectoryXml(body: String): ParsedXmlDirectory {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(body.byteInputStream(Charsets.UTF_8), "UTF-8")
        var id: String? = null
        var name: String? = null
        var artist: String? = null
        var artistId: String? = null
        var coverArt: String? = null
        var year: Int? = null
        var sawRoot = false
        val albums = mutableListOf<SubsonicAlbum>()
        val songs = mutableListOf<SubsonicSong>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "error" -> throw SubsonicApiException(
                        parser.attr("code")?.toIntOrNull() ?: -1,
                        parser.attr("message") ?: "Request failed",
                    )
                    "artist", "directory", "album" -> {
                        if (!sawRoot) {
                            id = parser.attr("id")
                            name = parser.attr("name") ?: parser.attr("title") ?: parser.attr("album")
                            artist = parser.attr("artist")
                            artistId = parser.attr("artistId")
                            coverArt = parser.attr("coverArt")
                            year = parser.attr("year")?.toIntOrNull()
                            sawRoot = true
                        } else if (parser.name == "album" || parser.attr("isDir") == "true") {
                            xmlEntryToAlbum(parser, artistId ?: id, artist ?: name)?.let { albums += it }
                        } else {
                            xmlEntryToSong(parser, artistId ?: id, artist ?: name, id, name)?.let { songs += it }
                        }
                    }
                    "child", "song", "entry", "video" -> {
                        val isDir = parser.attr("isDir") == "true" || parser.name == "album"
                        if (isDir) {
                            xmlEntryToAlbum(parser, artistId ?: id, artist ?: name)?.let { albums += it }
                        } else {
                            xmlEntryToSong(parser, artistId ?: id, artist ?: name, id, name)?.let { songs += it }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return ParsedXmlDirectory(id, name, artist, artistId, coverArt, year, albums, songs)
    }

    private fun xmlEntryToAlbum(
        parser: XmlPullParser,
        fallbackArtistId: String?,
        fallbackArtistName: String?,
    ): SubsonicAlbum? {
        val id = parser.attr("id") ?: return null
        return SubsonicAlbum(
            id = id,
            name = parser.attr("name") ?: parser.attr("title") ?: parser.attr("album") ?: "Unknown album",
            artist = parser.attr("artist") ?: fallbackArtistName,
            artistId = parser.attr("artistId") ?: fallbackArtistId,
            coverArt = parser.attr("coverArt"),
            songCount = parser.attr("songCount")?.toIntOrNull(),
            year = parser.attr("year")?.toIntOrNull(),
        )
    }

    private fun xmlEntryToSong(
        parser: XmlPullParser,
        fallbackArtistId: String?,
        fallbackArtistName: String?,
        fallbackAlbumId: String?,
        fallbackAlbumName: String?,
    ): SubsonicSong? {
        val id = parser.attr("id") ?: return null
        return SubsonicSong(
            id = id,
            title = parser.attr("title") ?: parser.attr("name") ?: "Unknown title",
            artist = parser.attr("artist") ?: fallbackArtistName,
            album = parser.attr("album") ?: fallbackAlbumName,
            albumId = parser.attr("albumId") ?: fallbackAlbumId,
            artistId = parser.attr("artistId") ?: fallbackArtistId,
            track = parser.attr("track")?.toIntOrNull(),
            discNumber = parser.attr("discNumber")?.toIntOrNull(),
            durationSeconds = parser.attr("duration")?.toIntOrNull(),
            year = parser.attr("year")?.toIntOrNull(),
            coverArt = parser.attr("coverArt"),
        )
    }

    private fun XmlPullParser.attr(name: String): String? =
        getAttributeValue(null, name)?.takeIf { it.isNotBlank() }

    private fun JsonObject.asObjectList(key: String): List<JsonObject> {
        val element = get(key) ?: return emptyList()
        return element.asObjectList()
    }

    private fun JsonElement.asObjectList(): List<JsonObject> = when {
        isJsonArray -> asJsonArray.mapNotNull { it.asObjectOrNull() }
        isJsonObject -> listOf(asJsonObject)
        else -> emptyList()
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null
}
