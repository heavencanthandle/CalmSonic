package com.heavencanthandle.calmsonic.subsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {
    @Test
    fun tokenMatchesSubsonicApiExample() {
        // From the Subsonic REST API docs: password "sesame", salt "c19b2d"
        assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicAuth.token("sesame", "c19b2d"))
    }

    @Test
    fun newSaltIsAlphanumericAndRequestedLength() {
        val salt = SubsonicAuth.newSalt(16)
        assertEquals(16, salt.length)
        assertTrue(salt.all { it.isLetterOrDigit() })
    }
}
