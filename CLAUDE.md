# LumioMusic

A local audio player for Android 12+ (min/target SDK reasoning below), built for one person's
own Bandcamp-purchased FLAC/MP3 library stored on an SD card. Kotlin + Jetpack Compose,
Media3/ExoPlayer playback, Room persistence, Hilt DI. The two features that make this more
than a generic player:

1. **Synced lyrics screen** styled like a Spotify/Apple-Music-style now-playing view: current
   line highlighted, neighboring lines dimmed, background = the track's own embedded cover art,
   heavily gaussian-blurred (native `RenderEffect`, available unconditionally since minSdk is
   31) with a dark scrim over it.
2. **Best-effort hi-res / bit-perfect audio path**, since the user cares about lossless
   playback quality. The target setup is a **OnePlus 15 (CPH2747, Android 16) → USB-C DAC →
   in-ear monitors** (no headphone jack); the dev phone is a Galaxy S10 (Android 12), which has
   no bit-perfect support, so everything USB-specific is verified by unit tests and by
   exercising the same code on the S10's speaker, never on a real DAC (see *Hi-res audio plan*).

The project language is English throughout: code, comments, commit messages, UI strings, this
file. (The person's own conversation with their assistant may be in French — that's unrelated
to what goes in the repo.)

## Non-negotiable product decisions (do not re-litigate without asking)

- **Storage Access Framework only** — no `MediaStore`, no broad storage permissions. The user
  picks exactly one root folder once (`ACTION_OPEN_DOCUMENT_TREE`), we take a persistable URI
  permission, and recursively walk that tree. Multiple libraries / multiple roots are
  explicitly out of scope.
- **Lyrics priority order, always in this sequence, first hit wins:**
  1. Lyrics embedded in the audio file itself (ID3 `USLT`/`SYLT` for MP3, Vorbis comment
     `LYRICS` for FLAC/Ogg).
  2. [LRCLIB](https://lrclib.net) — free, no API key, synced LRC.
  3. [lyrics.ovh](https://api.lyrics.ovh) — free, no API key, plain text only.
  4. Genius.com — last resort, unofficial page scrape (no free official lyrics-text API
     exists). This is a ToS gray area: **personal use only, this app must never be published to
     the Play Store because of it.** Keep it behind a feature flag / easily disableable
     `LyricsProvider` implementation.
  Fetched (non-embedded) lyrics are cached in Room so the network is only hit once per track.
  Lyrics are **never** written back into the user's audio files — those are purchased files,
  don't touch them.
- **No release keystore ever committed.** It lives outside the repo (see *Release signing*
  below) and `.gitignore` covers every variant of the filename.
- **Repo name note**: `github.com/LinuxKernel44/Musix` is a *different, unrelated* older
  project (a Last.fm/Spotify discovery app) on the same GitHub account. Don't confuse the two.
  This project is `github.com/LinuxKernel44/LumioMusic`.

## Architecture

Two Gradle modules:

- **`:app`** — everything: UI, ViewModels, data layer, Room, Media3 playback service, DI.
- **`:audio-native`** — Android library module, JNI/CMake, isolated because it's the only part
  needing the NDK and is inherently best-effort/device-dependent (see *Hi-res audio* below).
  Exposes a small pure-Kotlin API (no Media3 dependency); the Media3 `AudioSink` adapter that
  *uses* it lives in `:app/playback`.

Package layout under `app/src/main/java/com/davidpallier/lumiomusic/`:

```
di/            Hilt modules (AppModule, DatabaseModule, PlaybackModule, AudioSinkModule, WorkerModule)
data/model/    Domain/Room-shared models
data/db/       Room: AppDatabase, entities, DAOs
data/library/  SAF file walking, URI permission persistence, library scanning (WorkManager)
data/tags/     Embedded tag/cover/lyrics extraction (built on Media3's MetadataRetriever)
data/lyrics/   LyricsProvider chain, LRC parsing, lyrics Room cache (Phase 4+)
data/network/  Retrofit/OkHttp clients for LRCLIB / lyrics.ovh (Phase 4+)
data/playlist/ Playlist/queue persistence (Phase 6+)
playback/      MediaLibraryService, MediaSession, Android Auto browse tree
playback/audio/        Hi-res path: BitPerfectAudioSink, RoutingAudioSink, BitPerfectOutputManager,
                       UsbDacMonitor, BitPerfectPlanner/PcmConverter (pure, unit-tested), status/prefs
playback/audio/flac/   Pure-Kotlin FLAC decoder + Media3 renderer (integer PCM at native depth)
ui/            theme/, navigation/, library/, nowplaying/, lyrics/, playlists/, setup/, settings/, common/

audio-native/src/main/cpp/                                   CMakeLists.txt, JNI bridge, AAudio sink (Phase 8+)
audio-native/src/main/java/.../audionative/                  Kotlin side of the native bridge
```

**DI**: Hilt, not Koin — chosen specifically because this project has three places where a
runtime-only DI failure would be painful to debug (a `MediaLibraryService`, a `WorkManager`
`HiltWorker`, Compose Navigation ViewModels), and Hilt catches wiring mistakes at compile time.

## Environment / building

The Android SDK is **not vendored** in this repo. `local.properties` (gitignored) must point
`sdk.dir` at a local SDK install. Required components, matching what `compileSdk`/`minSdk`
below need:

- **compileSdk 37**, **targetSdk 36**, **minSdk 31** (Android 12+ only — this is deliberate,
  see below). You need SDK platform 37 (or newer — see the *AGP / API level* gotcha) installed.
- **NDK 28.2.13676358** (pinned in `audio-native/build.gradle.kts`) + **CMake 3.22.1**, for the
  `:audio-native` module.
- **Gradle 9.6.1** (via the committed wrapper — just run `./gradlew`, no local Gradle install
  needed).
- **JDK 17+** (compileOptions target 17; developed against JDK 21).

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK — needs keystore.properties, see below
```

Test on a real device or an emulator with an **API 31+, x86_64 or arm64 system image**
(`abiFilters` in `app/build.gradle.kts` / `audio-native/build.gradle.kts` currently restrict
native builds to `arm64-v8a` + `x86_64` — add another ABI there if you test on something else).
There is no CI configured; every phase so far has been manually verified by installing on a
real emulator/device and checking actual behavior (see *Status* below for what's been verified
this way).

### Why minSdk 31 (Android 12) specifically

Two reasons, both load-bearing for the architecture, not arbitrary:
1. `RenderEffect.createBlurEffect` (the lyrics-screen background blur) is API 31+, unconditional
   — no compat/fallback blur library needed anywhere in the codebase.
2. The user's own phone is Android 12+; there's no requirement to support older devices, so we
   didn't try to.

### AGP / API level gotchas (verified empirically on this project, not just from docs)

- **AGP 9.x has built-in Kotlin support and *errors* if you also apply the
  `org.jetbrains.kotlin.android` plugin** (or use the `kotlinOptions {}` DSL block that comes
  from it) in any module's `build.gradle.kts`. Don't add it back. `org.jetbrains.kotlin.plugin.compose`
  and `org.jetbrains.kotlin.plugin.serialization` are still applied normally — only
  `kotlin.android` is banned by AGP 9's built-in-Kotlin mode.
- Android's API numbering moved to a **major.minor scheme** around the time this project
  started (installed platform is literally `android-37.0`). Several current AndroidX libraries
  (Compose runtime 1.12.x, lifecycle 2.11.x, core-ktx 1.19.x, activity 1.13.x, okhttp-android
  5.5.x) require `compileSdk 37` at minimum — `compileSdk 35` fails at dependency-resolution
  time with an explicit "requires... compile against version 37" error. If dependency bumps
  hit this again, bump `compileSdk`, don't downgrade the library.
- Reading a `keystore.properties` file *inside* an `android { signingConfigs { create(...) {
  ... } } }` lambda in `build.gradle.kts` mysteriously fails to resolve even `java.util.Properties`
  (an AGP-9-era Kotlin-DSL script-accessor quirk with nested receivers). Workaround already
  applied in `app/build.gradle.kts`: read the properties file into a top-level `val` *before*
  the `android {}` block, then reference that val from inside `signingConfigs`/`buildTypes`.

### Media3 is pinned to 1.10.1, not the newer 1.11.x

`MetadataRetriever` — which the entire embedded-tag-reading pipeline (`data/tags/TagReaderFactory.kt`)
is built on — **was removed outright in Media3 1.11.1**. This was confirmed by inspecting the
actual AAR contents (the class exists in 1.10.1, is absent in 1.11.1), not from changelogs.
Do not bump Media3 past 1.10.x without first checking whether `MetadataRetriever` (or a
replacement) exists in the target version — if it doesn't, `TagReaderFactory` needs a rewrite
around whatever replaced it (possibly a real `ExoPlayer` instance + `Player.Listener`, which is
heavier and worth thinking through before switching).

### Media3's Id3Decoder does not parse ID3 USLT/SYLT lyrics frames

Verified empirically: embedded real `USLT`/`SYLT` frames into a fixture MP3 (via Python's
`mutagen`) and inspected Media3's parsed `Metadata` output on-device. They come through only as
a raw `BinaryFrame(id, data)` — Media3 has dedicated decoders for `APIC`, `COMM`, `GEOB`,
`MLLT`, `PRIV`, text/URL frames, but not lyrics frames. `data/tags/Id3UsltSyltDecoder.kt`
decodes the frame *body* from those already-extracted bytes — no need to re-parse the ID3v2
header, Media3 already handled unsynchronization and frame boundaries.

FLAC's Vorbis `LYRICS` comment and cover art (both FLAC `PICTURE` and ID3 `APIC`) come through
from Media3 natively, no custom parsing needed.

## Key design decisions worth knowing before touching related code

- **`TagReaderFactory`** uses `MetadataRetriever.Builder(context, mediaItem).build()` (the
  non-static instance API), not the deprecated static `retrieveMetadata()` — needed because
  only the instance API also exposes `retrieveDurationUs()`. Always `.close()` it (see the
  `finally` block) — it holds a worker thread.
- **`SafFileWalker`** uses raw `ContentResolver.query()` against
  `DocumentsContract.buildChildDocumentsUriUsingTree`, *not*
  `androidx.documentfile.provider.DocumentFile.listFiles()`. `DocumentFile` issues one extra
  content-provider round trip per child per property and is too slow for a real library
  (hundreds to thousands of files). Keep it that way if this code is touched.
- **`LibraryScanner`** does incremental rescans: a track is only re-read (re-tagged) if its
  `lastModified`/size changed since the last scan; tracks no longer present in the SAF walk are
  deleted from Room at the end of each scan. Cover art is extracted once per track and written
  to `context.cacheDir/art/<sha256(uriString)>.jpg` — Room stores only the file path, not a
  blob, and Coil loads directly from that path.
- **Room migrations**: pre-release, `fallbackToDestructiveMigration(true)` is used deliberately
  (see `di/DatabaseModule.kt`) so the schema can keep evolving phase to phase without
  hand-written migrations. Switch to real migrations before this app has any real user data
  worth preserving across upgrades.
- **Hi-res audio path** (OnePlus 15 + USB DAC; the old AAudio-exclusive/headphone-jack plan is
  superseded — see Phase 8/10 below):
  1. **Decoding**: the platform FLAC codec is *not* used. It narrows 24-bit to 16-bit, and
     asking it for float output is unreliable (on the S10/Android 12 it ignores the request while
     Media3 assumes float → playback at 2× speed). `FlacAudioRenderer` (registered *before*
     `MediaCodecAudioRenderer` in `PlaybackModule`) decodes FLAC with the pure-Kotlin
     `FlacFrameDecoder` to integer PCM 16/24/32-bit at the file's native depth. It is verified
     bit-exact against the MD5 the reference `flac` encoder stores in STREAMINFO
     (`FlacFrameDecoderTest`, fixtures in `app/src/test/resources/flac/`). MP3/AAC etc. still use
     MediaCodec (16-bit).
  2. **Baseline output** (every device, no DAC): `DefaultAudioSink` with float output, so 24-bit
     stays lossless into Android's mixer (which then resamples to its fixed rate).
     `RoutingAudioSink.getFormatSupport` reports float PCM as "needs transcoding" on purpose, so
     `MediaCodecAudioRenderer` never asks platform codecs for float (see above).
  3. **USB bit-perfect** (Android 14+ only): when a USB DAC is attached, the file is lossless FLAC
     (no gapless-trim info, i.e. not MP3/AAC), and the DAC's
     `AudioManager.getSupportedMixerAttributes()` lists a `MIXER_BEHAVIOR_BIT_PERFECT` format with
     the **exact** sample rate/channel count and an integer width ≥ the source, `RoutingAudioSink`
     switches to `BitPerfectAudioSink`: it registers those attributes with
     `setPreferredMixerAttributes`, opens an `AudioTrack` in exactly that format, and converts PCM
     exactly (`PcmConverter`; float32 that came from ≤24-bit audio round-trips to the identical
     integers). Never resamples; an unsupported rate falls back to the Android mixer.
  4. **Mandatory fallback**: any refusal/exception while bringing the bit-perfect path up makes
     the router fall back to `DefaultAudioSink` for the rest of the process (`DAC_REJECTED`)
     instead of failing playback. Switching sinks mid-queue drains the old sink first. The user
     can also switch it off (Audio output panel on the lyrics screen, `HiResPreferences`).
  5. **Visibility**: `AudioOutputStatusStore` + the chip on the lyrics screen show
     "USB DAC · Bit-perfect · 24-bit / 96 kHz" or why the Android mixer is used.
  - Caution that is *not* solvable in code: with bit-perfect Android bypasses its software volume;
    DACs without hardware volume may ignore the volume keys (the sink applies `setVolume` in
    software only when ≠ 1). The UI warns about this for in-ear monitors.
  - Diagnostics on the phone: `adb logcat -s LumioHiRes`, and
    `adb shell dumpsys audio | grep -i -B2 -A8 "mixer"` /
    `adb shell dumpsys media.audio_flinger` to see whether the track is bit-perfect.

## Version

Current version: **2.1.0** (`versionCode 3`, set in `app/build.gradle.kts`). `v0.1.0` was the
first signed release; 2.0.0 is the first release that treats the phase 0-9 feature set as the
baseline. Bump `versionCode` on every release; release tags are `vX.Y.Z`.

### 2.0.0 device pass (Galaxy S10 SM-G973F, Android 12, adb)

The 2.0.0 release was verified on the real dev phone (library in `/sdcard/Music/Bandcamp/...`,
picked via SAF — note Android refuses the storage root and `Download`; pick `Music/Bandcamp`).
Fixed in that pass:
- Nested `Scaffold`s double-applied window insets (big empty bands top/bottom). The outer
  scaffold in `NavGraph.kt` now has zero insets; screens own their insets.
- Mini player was hidden under the system nav bar / tab bar. It now sits **above** the tab bar
  inside `LibraryHostScreen`, and is hosted by the outer scaffold (with nav-bar padding) elsewhere.
- Lyrics screen: transport controls were cut off by the nav bar; status/nav bar icons were dark
  on the always-dark screen; added elapsed/total time labels.
- Album/playlist detail: last rows were covered by the extended FAB (`withFabClearance`).
- Playlists can now be renamed/deleted (⋮ menu on the playlist screen).
- Search field auto-focuses, system Back closes search; count labels use plurals.

Findings worth knowing: **AAudio EXCLUSIVE is never granted on the S10** (`LumioAAudioFeasibility`
logs a silent downgrade to shared on every launch), so the full custom exclusive `AudioSink` is
not worth building for this device. Some Bandcamp FLACs carry a truncated `LYRICS` tag (e.g.
*Panorama*: 5 lines); embedded lyrics still win per the priority rule.

## Status (update this section as phases land)

Phases refer to the original build plan; each was manually verified end-to-end on a real
emulator/device before being committed, not just compiled.

- [x] **Phase 0** — Gradle scaffold, two modules, Hilt/Compose/Media3/Room wired via version
      catalog, `:audio-native` CMake/NDK toolchain validated with a real JNI call.
- [x] **Phase 1** — SAF folder picker → recursive scan → embedded tag/cover/lyrics extraction →
      Room persistence, with WorkManager-driven progress UI. Verified against real tagged
      FLAC/MP3 fixtures (embedded plain lyrics, embedded synced lyrics, embedded cover art all
      confirmed round-tripping correctly into Room).
- [x] **Phase 2** — Media3 playback service (`MediaLibraryService`), notification/lockscreen
      controls, mini-player. Verified end-to-end on a real emulator: playback, queue
      auto-advance, and lockscreen/notification transport controls all confirmed working with
      real tagged MP3 fixtures.
- [x] **Phase 3** — Real library browse UI (albums/artists/tracks/search), replacing the
      Phase 1 placeholder screen. Verified on-device: SAF scan → Tracks/Albums/Artists tabs,
      album/artist detail screens, search all confirmed working.
- [x] **Phase 4** — `LyricsRepository` fallback chain (embedded → LRCLIB → lyrics.ovh →
      Genius, Genius gated off by default behind `LyricsFeatureFlags.GENIUS_ENABLED`), Room
      lyrics cache (`lyrics_cache` table, DB version bumped to 2), Retrofit/OkHttp network
      clients. Verified: embedded-lyrics shortcut and the network chain (falling through to "not
      found" for a fictional test track) both exercised on-device.
- [x] **Phase 5** — Lyrics screen UI: blurred cover-art background (`RenderEffect`), dark scrim,
      synced line highlight/auto-scroll, playback controls. Verified on-device with a real
      embedded ID3 SYLT fixture — highlighting and centering confirmed visually correct.
- [x] **Phase 6** — Queue & playlists (Room-persisted: `playlists` + `playlist_tracks` tables,
      DB version bumped to 3), plus queue-state persistence (`QueueStateStore` via DataStore) so
      `PlaybackService` can answer `MediaSession.Callback.onPlaybackResumption`. Verified
      on-device: create/add/remove/delete playlist and playlist playback all confirmed working;
      the resumption code path itself is implemented against the real Media3 API but wasn't
      exercised by an actual system resumption request in this session.
- [x] **Phase 7** — Android Auto (`MediaLibraryService` browse tree: root → Albums/Artists/All
      tracks → tracks; `automotive_app_desc.xml` declares the `media` capability). Compiles
      against the real Media3 API and the app's own `MediaController` connects to the service
      without issue, but the browse tree itself has not been walked by an actual Android Auto
      head unit (e.g. Desktop Head Unit) — that's still open.
- [x] **Phase 8** — AAudio exclusive-mode native sink, scoped deliberately as the feasibility
      spike this phase is meant to be (see *Hi-res audio plan*): `:audio-native` now has a real
      JNI/AAudio bridge (`aaudio_sink.cpp`, `AudioNativeSink.kt`) that requests exclusive mode,
      falls back to shared on failure, and always re-checks `AAudioStream_getSharingMode()`
      after opening. `LumioApplication` runs it once at startup and logs the result to
      `LumioAAudioFeasibility` (`adb logcat -s LumioAAudioFeasibility`). On the development
      emulator it correctly detected and logged a silent downgrade to shared mode — check this
      log on the real phone before deciding whether the full custom `AudioSink` is worth
      building. That full `AudioSink` adapter itself is *not* implemented — this phase
      intentionally stops at the spike. **Superseded**: exclusive mode is never granted on the
      S10, and the real target is a USB DAC, handled by Phase 10 (Android's bit-perfect mixer).
      `:audio-native` and the startup feasibility log are kept but no longer drive anything.
- [~] **Phase 9** — Polish: empty/error states audited and filled in across all library tabs and
      detail screens; unit tests added for `LrcParser` and `Id3UsltSyltDecoder` (13 tests, all
      passing — `./gradlew :app:testDebugUnitTest`). Production signing config was already wired
      in Phase 0/1 (`keystore.properties`, see *Release signing* below) and still works
      unmodified. Not done: a redesigned app icon (still the Phase 0 placeholder) and the first
      actual signed release — both left for the person to decide on/do, since a release keystore
      and a published GitHub Release are exactly the kind of machine-specific, externally-visible
      steps this file says to keep off the assistant's plate.

- [x] **Phase 10** — Hi-res USB path (see *Hi-res audio path*): pure-Kotlin FLAC decoder
      (bit-exact vs reference MD5s, 6 fixtures), `RoutingAudioSink` + `BitPerfectAudioSink`,
      USB DAC monitor, Android 14 bit-perfect mixer registration, status chip + settings panel.
      Verified on the S10: normal playback speed/position/seek/pause, 16/44.1 and 24/96 FLAC,
      MP3, format switches mid-queue (including through `BitPerfectAudioSink`, exercised on the
      speaker with a temporary fake plan that was removed again). **Not verified**: the real
      USB-DAC part (`getSupportedMixerAttributes`/`setPreferredMixerAttributes` on a OnePlus 15
      with a DAC) — nobody had that hardware here. If it misbehaves, check `LumioHiRes` logs first.
- Also in 2.1.0: add-to-playlist button on album tracks.

## Release signing

The release keystore is generated and kept **only on the machine doing the release build**,
outside this repo:

```
~/.android-keystores/lumiomusic-release.jks
```

`app/build.gradle.kts` reads signing config from a `keystore.properties` file at the repo root
(gitignored) with this shape:

```properties
storeFile=/home/<you>/.android-keystores/lumiomusic-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

If `keystore.properties` doesn't exist, the release build type simply isn't signed (debug
builds are unaffected). Signed release APKs are distributed as **GitHub Release assets**
(`gh release create ... path/to/app-release.apk`), never committed into the git tree, and the
keystore/credentials are never pushed to the public repo.

## Working on this from a different machine

This repo has no machine-specific state committed (`local.properties`, any keystore material,
and build outputs are all gitignored). To pick up work elsewhere:

```bash
git clone https://github.com/LinuxKernel44/LumioMusic.git
cd LumioMusic
echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties
```

Then install whatever SDK/NDK/CMake components are listed under *Environment / building* above
if they're not already present, and build as usual. If you're picking this up with a fresh
Claude Code session, this file plus a `git log` for recent detail is the intended full context
— there's no other hidden state to reconstruct.
