# CalmSonic

An e-ink **Subsonic / OpenSubsonic / Navidrome** music client for the [Mudita Kompakt](https://mudita.com/) and similar Android e-ink phones.

If you self-host **Navidrome**, Gonic, Airsonic, or any other Subsonic-compatible server and want a readable player on an e-ink device, this is that app. It is a fork of [CalmMusic](https://github.com/davidraywilson/CalmMusic) restyled with [Mudita Mindful Design](https://github.com/mudita/MMD).

**Keywords:** Navidrome client, Subsonic client, OpenSubsonic, e-ink Android, Mudita Kompakt, eink music player, DSub alternative for e-ink.

## Features

- High-contrast Kompakt UI: large type, jump scrolling, no ripples
- Token auth (`md5(password + salt)`), Subsonic API 1.16.1 JSON
- Browse artists, albums, and playlists from your server
- Search, now playing, queue, shuffle / repeat
- Stream in the background with system media controls
- Optional bitrate cap for mobile transcoding
- Local files still work from the CalmMusic library scanner

## Install (Mudita Kompakt)

1. Download `CalmSonic-1.0.0.apk` from the [latest GitHub Release](https://github.com/heavencanthandle/CalmSonic/releases)
2. On a computer, open **Mudita Center**, connect the Kompakt over USB-C
3. **Your Kompakt → Manage Files → App Installers → Add file**
4. Select the APK, then open it on the phone and accept the install warning

There is no Play Store listing. Sideload only.

To replace an older CalmSonic build over USB debugging:

```powershell
adb install -r CalmSonic-1.0.0.apk
```

## Connect Navidrome / Subsonic

1. Open CalmSonic → **Settings → Streaming**
2. Choose **Subsonic**
3. Enter the server URL (`https://navidrome.example.com` or `http://192.168.1.10:4533`), username, and password
4. Tap **Save and test connection**
5. Optionally cap stream bitrate (useful on cellular)

Tested against **Navidrome 0.64** (OpenSubsonic). Other Subsonic-compatible servers (Gonic, Airsonic, Nextcloud Music, LMS plugins) should work; folder-browse servers fall back to `getMusicDirectory` / `search3` the way DSub does.

## Build from source

- Android Studio (Giraffe / Hedgehog or newer)
- JDK 17
- Android SDK Platform 35+

```
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
```

The signed sideload APK is at `app/build/outputs/apk/release/app-release.apk`.

## License

GPL-3.0. CalmSonic is derived from CalmMusic by David Wilson. See `LICENSE`.
