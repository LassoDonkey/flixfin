# FlixFin

An unofficial Jellyfin client for Fire TV and Android TV.

**Not affiliated with, endorsed by, or supported by the Jellyfin project.** This
is a third-party client that connects to a Jellyfin server. For the official
Android TV client, see
[jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) —
please do not raise FlixFin issues there.

---

## What this is

A fork of `jellyfin-androidtv`, with:

- its own name, launcher icon and TV banner
- its own `applicationId`, so it installs **alongside** the official client
  rather than replacing it
- in-app updates: it checks GitHub Releases on startup and can install a newer
  version itself

Everything else — playback, transcoding, subtitles, device profiles, Quick
Connect — is upstream's, and upstream's is very good. Their player carries a
custom FFmpeg decoder for DTS and TrueHD and a libass binding for styled
subtitles, neither of which Media3 does on its own. The parts worth keeping are
exactly the parts this fork does not touch.

## Install

Fire TV needs **Settings → My Fire TV → Developer Options** with **ADB
debugging** and **Apps from Unknown Sources** both on.

```bash
adb connect <fire-tv-ip>:5555      # accept the prompt on the TV
adb install -r flixfin-v1.1.0-release.apk
```

The APK is on the [Releases](../../releases) page.

## Updating

After the first install, updates are in-app: FlixFin checks
`api.github.com/repos/LassoDonkey/flixfin/releases/latest` on startup and offers
to install anything newer. The check has a short timeout and fails silently — an
update check is never worth delaying the home screen for.

The update host is a compile-time constant, not a setting. If it were
configurable, anything able to write config could point the updater at an APK of
its choosing, which turns a convenience feature into a remote code execution
path. Android's own signature check is the backstop: an update installs only if
it is signed with the same key as the installed app.

## Building

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk

./gradlew -Pjellyfin.version=1.1.0 :app:assembleRelease
```

Release builds need signing config in `~/.gradle/gradle.properties`:

```properties
keystore.file=/path/to/your.p12
keystore.password=...
signing.key.alias=...
signing.key.password=...
```

Debug builds need none of that and install alongside release builds — the debug
`applicationId` carries a `.debug` suffix.

See [`FLIXFIN.md`](FLIXFIN.md) for what the fork changes and why.

## Licence

**GPL-2.0-only**, inherited from `jellyfin-androidtv`. No "or later" wording
appears in upstream, so relicensing is not available.

If you were given a FlixFin APK, you are entitled to its corresponding source,
and this repository is it.

The Jellyfin name and logo belong to the Jellyfin project; its brand assets are
CC BY-SA 4.0. FlixFin uses none of them in its own branding, and states in-app
that it is an unofficial client.
