# CalmSonic

CalmSonic is [CalmMusic](https://github.com/davidraywilson/CalmMusic) with a **Subsonic / OpenSubsonic / Navidrome** streaming layer added. Same e-ink player for the [Mudita Kompakt](https://mudita.com/), pointed at a self-hosted music server instead of (or in addition to) local files.

Almost all of the UI, playback, and library code is David Wilson’s CalmMusic, restyled with [Mudita Mindful Design](https://github.com/mudita/MMD). This repo keeps that app working on Kompakt and teaches it to talk to Navidrome and other Subsonic-compatible servers.

**Keywords:** CalmMusic fork, Navidrome client, Subsonic client, OpenSubsonic, e-ink Android, Mudita Kompakt, eink music player.

## What changed vs CalmMusic

- Streaming provider: Subsonic (token auth, API 1.16.1 JSON)
- Browse artists, albums, and server playlists from Navidrome / Gonic / Airsonic / similar
- Search and background streaming, with an optional bitrate cap for transcoding
- Local files and the rest of CalmMusic still work

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

Tested against **Navidrome 0.64** (OpenSubsonic). Other Subsonic-compatible servers should work; folder-browse servers fall back to `getMusicDirectory` / `search3`.

## Tracking CalmMusic

There is no automatic “when CalmMusic updates, CalmSonic updates” hook. This tree started from CalmMusic and then diverged (new application id, Subsonic client, Kompakt streaming settings), so a blind merge will often conflict.

Practical options:

1. **Occasionally catch up by hand** (recommended). Open this project in Cursor and ask to merge the latest [CalmMusic](https://github.com/davidraywilson/CalmMusic) into CalmSonic. That is the least painful path.
2. **Merge upstream yourself** if you are comfortable with git:

```
git remote add upstream https://github.com/davidraywilson/CalmMusic.git
git fetch upstream
git merge upstream/main
```

Use whatever default branch CalmMusic actually uses (`main` or `master`). Expect conflicts in Gradle files, the app id, settings, and anything under `subsonic/`.

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

GPL-3.0. CalmSonic is a derivative of CalmMusic by David Wilson. See `LICENSE`.
