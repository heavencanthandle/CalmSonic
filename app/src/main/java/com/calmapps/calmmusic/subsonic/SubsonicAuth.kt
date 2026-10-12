package com.calmapps.calmmusic.subsonic

import java.security.MessageDigest
import java.security.SecureRandom

object SubsonicAuth {
    private val random = SecureRandom()
    private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun newSalt(length: Int = 12): String {
        val chars = CharArray(length) { alphabet[random.nextInt(alphabet.length)] }
        return String(chars)
    }

    fun token(password: String, salt: String): String = md5Hex(password + salt)

    fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
