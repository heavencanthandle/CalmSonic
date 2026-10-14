package com.heavencanthandle.calmsonic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CalmMusicSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences = createSecurePrefs(context)

    private val _includeLocalMusic = MutableStateFlow(getIncludeLocalMusicSync())
    val includeLocalMusic: StateFlow<Boolean> = _includeLocalMusic.asStateFlow()

    private val _localMusicFolders = MutableStateFlow(getLocalMusicFoldersSync())
    val localMusicFolders: StateFlow<Set<String>> = _localMusicFolders.asStateFlow()

    private val _streamingProvider = MutableStateFlow(getStreamingProviderSync())
    val streamingProvider: StateFlow<StreamingProvider> = _streamingProvider.asStateFlow()

    private val _completeAlbumsWithYouTube = MutableStateFlow(getCompleteAlbumsWithYouTubeSync())
    val completeAlbumsWithYouTube: StateFlow<Boolean> = _completeAlbumsWithYouTube.asStateFlow()

    private val _subsonicBaseUrl = MutableStateFlow(prefs.getString(KEY_SUBSONIC_BASE_URL, "") ?: "")
    val subsonicBaseUrlFlow: StateFlow<String> = _subsonicBaseUrl.asStateFlow()

    private val _subsonicUsername = MutableStateFlow(prefs.getString(KEY_SUBSONIC_USERNAME, "") ?: "")
    val subsonicUsernameFlow: StateFlow<String> = _subsonicUsername.asStateFlow()

    var subsonicBaseUrl: String
        get() = prefs.getString(KEY_SUBSONIC_BASE_URL, "") ?: ""
        private set(value) {
            prefs.edit { putString(KEY_SUBSONIC_BASE_URL, value) }
            _subsonicBaseUrl.value = value
        }

    var subsonicUsername: String
        get() = prefs.getString(KEY_SUBSONIC_USERNAME, "") ?: ""
        private set(value) {
            prefs.edit { putString(KEY_SUBSONIC_USERNAME, value) }
            _subsonicUsername.value = value
        }

    var subsonicPassword: String
        get() = securePrefs.getString(KEY_SUBSONIC_PASSWORD, "") ?: ""
        private set(value) {
            securePrefs.edit { putString(KEY_SUBSONIC_PASSWORD, value) }
        }

    private val _subsonicConfigured = MutableStateFlow(hasSubsonicCredentials())
    val subsonicConfigured: StateFlow<Boolean> = _subsonicConfigured.asStateFlow()

    val subsonicMaxBitrateKbps: Int?
        get() {
            val stored = prefs.getInt(KEY_SUBSONIC_MAX_BITRATE, 0)
            return stored.takeIf { it > 0 }
        }

    init {
        migratePlaintextPasswordIfNeeded()
        _subsonicConfigured.value = hasSubsonicCredentials()
    }

    fun getLastAppleMusicSyncMillis(): Long {
        return prefs.getLong(KEY_LAST_APPLE_MUSIC_SYNC_MILLIS, 0L)
    }

    fun updateLastAppleMusicSyncMillis(value: Long) {
        prefs.edit { putLong(KEY_LAST_APPLE_MUSIC_SYNC_MILLIS, value) }
    }

    fun getLastLocalLibraryScanMillis(): Long {
        return prefs.getLong(KEY_LAST_LOCAL_LIBRARY_SCAN_MILLIS, 0L)
    }

    fun updateLastLocalLibraryScanMillis(value: Long) {
        prefs.edit { putLong(KEY_LAST_LOCAL_LIBRARY_SCAN_MILLIS, value) }
    }

    fun setIncludeLocalMusic(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INCLUDE_LOCAL_MUSIC, enabled) }
        _includeLocalMusic.value = enabled
    }

    private fun getIncludeLocalMusicSync(): Boolean {
        return prefs.getBoolean(KEY_INCLUDE_LOCAL_MUSIC, false)
    }

    fun addLocalMusicFolder(uri: String) {
        val current = getLocalMusicFoldersSync().toMutableSet()
        if (current.add(uri)) {
            prefs.edit { putStringSet(KEY_LOCAL_MUSIC_FOLDERS, current) }
            _localMusicFolders.value = current
        }
    }

    fun removeLocalMusicFolder(uri: String) {
        val current = getLocalMusicFoldersSync().toMutableSet()
        if (current.remove(uri)) {
            prefs.edit { putStringSet(KEY_LOCAL_MUSIC_FOLDERS, current) }
            _localMusicFolders.value = current
        }
    }

    fun getLocalMusicFoldersSync(): Set<String> {
        return prefs.getStringSet(KEY_LOCAL_MUSIC_FOLDERS, emptySet()) ?: emptySet()
    }

    private fun getStreamingProviderSync(): StreamingProvider {
        val raw = prefs.getString(KEY_STREAMING_PROVIDER, null)
        return StreamingProvider.fromStored(raw)
    }

    fun setStreamingProvider(provider: StreamingProvider) {
        prefs.edit { putString(KEY_STREAMING_PROVIDER, StreamingProvider.toStored(provider)) }
        _streamingProvider.value = provider
    }

    fun getCompleteAlbumsWithYouTubeSync(): Boolean {
        return prefs.getBoolean(KEY_COMPLETE_ALBUMS_WITH_YOUTUBE, false)
    }

    fun setCompleteAlbumsWithYouTube(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_COMPLETE_ALBUMS_WITH_YOUTUBE, enabled) }
        _completeAlbumsWithYouTube.value = enabled
    }

    // Permissions onboarding
    fun hasCompletedPermissionsOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_COMPLETED_PERMISSIONS_ONBOARDING, false)
    }

    fun setHasCompletedPermissionsOnboarding(completed: Boolean) {
        prefs.edit { putBoolean(KEY_HAS_COMPLETED_PERMISSIONS_ONBOARDING, completed) }
    }

    fun hasSubsonicCredentials(): Boolean =
        subsonicBaseUrl.isNotBlank() && subsonicUsername.isNotBlank() && subsonicPassword.isNotBlank()

    fun setSubsonicConnection(baseUrl: String, username: String, password: String) {
        subsonicBaseUrl = baseUrl.trim()
        subsonicUsername = username.trim()
        subsonicPassword = password
        _subsonicConfigured.value = hasSubsonicCredentials()
    }

    fun setSubsonicMaxBitrateKbps(bitrate: Int?) {
        prefs.edit { putInt(KEY_SUBSONIC_MAX_BITRATE, bitrate ?: 0) }
    }

    private fun migratePlaintextPasswordIfNeeded() {
        val leftover = prefs.getString(KEY_SUBSONIC_PASSWORD, null)
        if (leftover.isNullOrEmpty()) return
        if (securePrefs.getString(KEY_SUBSONIC_PASSWORD, "").isNullOrEmpty()) {
            securePrefs.edit { putString(KEY_SUBSONIC_PASSWORD, leftover) }
        }
        prefs.edit { remove(KEY_SUBSONIC_PASSWORD) }
    }

    companion object {
        private const val PREFS_NAME = "calmmusic_settings"
        private const val SECURE_PREFS_NAME = "calmmusic_secure_settings"
        private const val KEY_INCLUDE_LOCAL_MUSIC = "include_local_music"
        private const val KEY_LOCAL_MUSIC_FOLDERS = "local_music_folders"
        private const val KEY_LAST_APPLE_MUSIC_SYNC_MILLIS = "last_apple_music_sync_millis"
        private const val KEY_LAST_LOCAL_LIBRARY_SCAN_MILLIS = "last_local_library_scan_millis"
        private const val KEY_HAS_COMPLETED_PERMISSIONS_ONBOARDING = "has_completed_permissions_onboarding"
        private const val KEY_STREAMING_PROVIDER = "streaming_provider"
        private const val KEY_COMPLETE_ALBUMS_WITH_YOUTUBE = "complete_albums_with_youtube"
        private const val KEY_SUBSONIC_BASE_URL = "subsonic_base_url"
        private const val KEY_SUBSONIC_USERNAME = "subsonic_username"
        private const val KEY_SUBSONIC_PASSWORD = "subsonic_password"
        private const val KEY_SUBSONIC_MAX_BITRATE = "subsonic_max_bitrate"

        private fun createSecurePrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
