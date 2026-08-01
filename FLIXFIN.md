# FlixFin

**Unofficial third-party Jellyfin client for Fire TV and Android TV.**

> FlixFin is not affiliated with, endorsed by, or supported by the Jellyfin project.
> Do not report FlixFin bugs to Jellyfin.

FlixFin is a fork of [jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv),
licensed **GPL-2.0**. The upstream `LICENSE` applies unchanged, and every
copyright notice in the original source is intact.

## What's changed from upstream

| Area | Change |
|---|---|
| Identity | App name **FlixFin**, own launcher icon and TV banner |
| `applicationId` | `com.leooperations.flixfin` — installs *alongside* official Jellyfin, does not replace it |
| Self-update | Checks a Gitea release feed on startup, downloads and installs over itself |

The Kotlin `namespace` is still `org.jellyfin.androidtv`. That is deliberate:
keeping upstream's package names means ~1500 source files are untouched and
rebasing on upstream stays a merge rather than a rewrite. Only `applicationId`
differs, which is what Android actually identifies an app by.

## Building

Needs JDK 17+ and the Android SDK (compileSdk 36).

```bash
# Debug — unsigned-ish, installs as com.leooperations.flixfin.debug
./gradlew :app:assembleDebug

# Release — needs signing config, see below
./gradlew -Pjellyfin.version=1.0.0 :app:assembleRelease
```

Output lands in `app/build/outputs/apk/<type>/flixfin-v<version>-<type>.apk`.

### Signing

The build reads four properties, falling back to equivalent env vars
(`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`):

```properties
keystore.file=/path/to/flixfin-release.p12
keystore.password=...
signing.key.alias=flixfin
signing.key.password=...
```

Put them in `~/.gradle/gradle.properties`, **never** in the repo.

> **The keystore is irreplaceable.** Android only installs an update over an
> existing app when both are signed with the same key. Lose it and every TV must
> uninstall FlixFin — losing its login — and install fresh. Back it up off-machine.

## Installing on a Fire TV

1. Fire TV → Settings → My Fire TV → Developer Options → **Install unknown apps**
   (and **ADB debugging** if installing over the network).
2. `adb connect <fire-tv-ip>:5555`
3. `adb install -r app/build/outputs/apk/release/flixfin-v1.0.0-release.apk`

Or sideload with the Downloader app pointed at the release URL.

## Releasing

Self-update reads the **latest** release of `LassoDonkey/flixfin` on Gitea and
compares its tag against the installed `versionCode`.

1. Tag as `vMAJOR.MINOR.PATCH` — the app parses the tag, so the format matters.
2. Build with a matching `-Pjellyfin.version=`.
3. Attach the release APK as an asset whose filename ends in `.apk`.

Version codes are `MAJOR*1000000 + MINOR*10000 + PATCH*100 + prerelease`, with
prerelease defaulting to 99. `1.0.0` → `1000099`. A later tag must produce a
higher number or devices will not offer the update.

## Licence

GPL-2.0, inherited from jellyfin-androidtv. See `LICENSE`.

The Jellyfin name and logo belong to the Jellyfin project. FlixFin uses the
Jellyfin logo only on its connection/boot screen, to identify the server it
talks to — never as its own launcher icon. Jellyfin branding assets are
CC BY-SA 4.0.
