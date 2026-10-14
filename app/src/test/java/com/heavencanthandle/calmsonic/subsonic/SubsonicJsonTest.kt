package com.heavencanthandle.calmsonic.subsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicJsonTest {
    @Test
    fun parsePingOk() {
        val body = """
            {"subsonic-response":{"status":"ok","version":"1.16.1"}}
        """.trimIndent()
        val response = SubsonicJson.parseResponse(body)
        assertEquals("ok", response.get("status").asString)
        assertEquals("1.16.1", response.get("version").asString)
    }

    @Test
    fun parseFailedPingThrows() {
        val body = """
            {"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}
        """.trimIndent()
        try {
            SubsonicJson.parseResponse(body)
            throw AssertionError("expected SubsonicApiException")
        } catch (e: SubsonicApiException) {
            assertEquals(40, e.code)
            assertEquals("Wrong username or password", e.message)
        }
    }

    @Test
    fun parseSearch3SongsAndSingleArtistObject() {
        val body = """
            {"subsonic-response":{"status":"ok","version":"1.16.1","searchResult3":{
                "artist":{"id":"ar-1","name":"Radiohead","albumCount":9},
                "album":[{"id":"al-1","name":"OK Computer","artist":"Radiohead","artistId":"ar-1","year":1997}],
                "song":[{"id":"s-1","title":"Paranoid Android","artist":"Radiohead","album":"OK Computer","albumId":"al-1","track":2,"duration":383,"coverArt":"al-1"}]
            }}}
        """.trimIndent()
        val result = SubsonicJson.parseSearch3(body)
        assertEquals(1, result.artists.size)
        assertEquals("Radiohead", result.artists[0].name)
        assertEquals(1, result.albums.size)
        assertEquals("OK Computer", result.albums[0].name)
        assertEquals(1, result.songs.size)
        assertEquals("Paranoid Android", result.songs[0].title)
        assertEquals(383, result.songs[0].durationSeconds)
    }

    @Test
    fun parseArtistsIndexes() {
        val body = """
            {"subsonic-response":{"status":"ok","artists":{"index":[
                {"name":"R","artist":[{"id":"ar-1","name":"Radiohead"},{"id":"ar-2","name":"R.E.M."}]}
            ]}}}
        """.trimIndent()
        val artists = SubsonicJson.parseArtists(body)
        assertEquals(2, artists.size)
        assertEquals("ar-2", artists[1].id)
    }

    @Test
    fun parseFolderIndexes() {
        val body = """
            {"subsonic-response":{"status":"ok","indexes":{"index":[
                {"name":"R","artist":{"id":"dir-1","name":"Radiohead"}}
            ]}}}
        """.trimIndent()
        val artists = SubsonicJson.parseArtists(body)
        assertEquals(1, artists.size)
        assertEquals("dir-1", artists[0].id)
        assertEquals("Radiohead", artists[0].name)
    }

    @Test
    fun summarizeJsonArtistWithoutAlbumArray() {
        val body = """
            {"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","artist":{
                "id":"ar-1","name":"Radiohead","albumCount":9
            }}}
        """.trimIndent()
        val summary = SubsonicJson.summarize(body)
        assertTrue(summary.contains("album=0"))
        val detail = SubsonicJson.parseArtist(body)
        assertEquals(0, detail.albums.size)
        assertEquals("Radiohead", detail.artist.name)
    }

    @Test
    fun parseArtistAlbumsWithoutArtistId() {
        val body = """
            {"subsonic-response":{"status":"ok","artist":{
                "id":"ar-1","name":"Radiohead",
                "album":[{"id":"al-1","name":"OK Computer","year":1997}]
            }}}
        """.trimIndent()
        val detail = SubsonicJson.parseArtist(body)
        assertEquals("ar-1", detail.artist.id)
        assertEquals(1, detail.albums.size)
        assertEquals("OK Computer", detail.albums[0].name)
        assertEquals("ar-1", detail.albums[0].artistId)
        assertEquals("Radiohead", detail.albums[0].artist)
    }

    @Test
    fun parseArtistChildDirectoriesLikeDsub() {
        val body = """
            {"subsonic-response":{"status":"ok","directory":{
                "id":"dir-1","name":"Radiohead",
                "child":[
                    {"id":"al-1","title":"OK Computer","isDir":true,"year":1997},
                    {"id":"s-1","title":"Creep","isDir":false,"duration":238,"album":"Pablo Honey"}
                ]
            }}}
        """.trimIndent()
        val detail = SubsonicJson.parseArtist(body)
        assertEquals(1, detail.albums.size)
        assertEquals("OK Computer", detail.albums[0].name)
        assertEquals("dir-1", detail.albums[0].artistId)
        assertEquals(1, detail.songs.size)
        assertEquals("Creep", detail.songs[0].title)
        assertEquals("dir-1", detail.songs[0].artistId)
    }

    @Test
    fun parseNavidromeAlbumWithOpenSubsonicExtras() {
        val body = """
            {"subsonic-response":{"status":"ok","type":"navidrome","openSubsonic":true,"album":{
                "id":"al-1","name":"OK Computer","artist":"Radiohead","artistId":"ar-1","songCount":1,"duration":284,
                "genres":[{"name":"Rock"}],
                "artists":[{"id":"ar-1","name":"Radiohead"}],
                "song":[{
                    "id":"s-1","title":"Airbag","isDir":false,"duration":284,"track":1,
                    "suffix":"mp3","contentType":"audio/mpeg","albumId":"al-1","album":"OK Computer",
                    "replayGain":{"trackGain":-3.5},
                    "artists":[{"id":"ar-1","name":"Radiohead"}]
                }]
            }}}
        """.trimIndent()
        val album = SubsonicJson.parseAlbum(body)
        assertEquals("OK Computer", album.name)
        assertEquals(1, album.songs.size)
        assertEquals("Airbag", album.songs[0].title)
        assertEquals("s-1", album.songs[0].id)
    }

    @Test
    fun parseAlbumSongsFromChildAndTitleFallback() {
        val body = """
            {"subsonic-response":{"status":"ok","album":{
                "id":"al-1","title":"OK Computer","artist":"Radiohead","artistId":"ar-1",
                "song":[{"id":"s-1","name":"Airbag","track":1,"duration":284}]
            }}}
        """.trimIndent()
        val album = SubsonicJson.parseAlbum(body)
        assertEquals("OK Computer", album.name)
        assertEquals(1, album.songs.size)
        assertEquals("Airbag", album.songs[0].title)
        assertEquals("al-1", album.songs[0].albumId)
        assertEquals("ar-1", album.songs[0].artistId)
    }

    @Test
    fun parseMusicDirectoryNestedDiscs() {
        val body = """
            {"subsonic-response":{"status":"ok","directory":{
                "id":"al-1","name":"OK Computer",
                "child":[
                    {"id":"disc-1","title":"Disc 1","isDir":true},
                    {"id":"s-1","title":"Airbag","isDir":false,"duration":284,"track":1}
                ]
            }}}
        """.trimIndent()
        val album = SubsonicJson.parseAlbum(body)
        assertEquals(listOf("disc-1"), album.nestedAlbumIds)
        assertEquals(1, album.songs.size)
        assertEquals("Airbag", album.songs[0].title)
    }

    @Test
    fun parseAlbumListUsesChildWhenAlbumMissing() {
        val body = """
            {"subsonic-response":{"status":"ok","albumList":{"child":[
                {"id":"al-1","title":"Kid A","artist":"Radiohead","artistId":"ar-1"}
            ]}}}
        """.trimIndent()
        val albums = SubsonicJson.parseAlbumList(body)
        assertEquals(1, albums.size)
        assertEquals("Kid A", albums[0].name)
        assertEquals("ar-1", albums[0].artistId)
    }

    @Test
    fun normalizeBaseUrlStripsRestAndAddsScheme() {
        assertEquals("https://music.example.com", SubsonicClient.normalizeBaseUrl("music.example.com/rest/"))
        assertEquals("http://192.168.1.10:4533", SubsonicClient.normalizeBaseUrl("http://192.168.1.10:4533/rest"))
        assertTrue(SubsonicIds.song("abc").startsWith("SUBSONIC:song:"))
        assertEquals("abc", SubsonicIds.rawSong(SubsonicIds.song("abc")))
    }
}
