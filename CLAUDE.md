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
   playback quality, primarily over the phone's headphone jack (not a USB DAC).

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
playback/      MediaLibraryService, MediaSession, Android Auto browse tree, custom AudioSink
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
- **Hi-res audio plan**: decode losslessly and output at the file's native PCM depth/rate via
  Media3's `DefaultAudioSink` (float output) as the baseline — this helps on every device.  On
  top of that, a best-effort `AAUDIO_SHARING_MODE_EXCLUSIVE` path targeting the phone's
  *internal* output (not USB — the user primarily uses the headphone jack, not an external DAC)
  is planned for `:audio-native`, with **mandatory fallback**: always check
  `AAudioStream_getSharingMode()` after opening a stream and silently fall back to the standard
  Media3 path if exclusive mode wasn't actually granted — many devices downgrade silently.
  Never assume exclusive mode succeeded.

## Status (update this section as phases land)

Phases refer to the original build plan; each was manually verified end-to-end on a real
emulator/device before being committed, not just compiled.

- [x] **Phase 0** — Gradle scaffold, two modules, Hilt/Compose/Media3/Room wired via version
      catalog, `:audio-native` CMake/NDK toolchain validated with a real JNI call.
- [x] **Phase 1** — SAF folder picker → recursive scan → embedded tag/cover/lyrics extraction →
      Room persistence, with WorkManager-driven progress UI. Verified against real tagged
      FLAC/MP3 fixtures (embedded plain lyrics, embedded synced lyrics, embedded cover art all
      confirmed round-tripping correctly into Room).
- [ ] **Phase 2** — Media3 playback service (`MediaLibraryService`), notification/lockscreen
      controls, mini-player.
- [ ] **Phase 3** — Real library browse UI (albums/artists/tracks/search), replacing the
      current placeholder screen.
- [ ] **Phase 4** — `LyricsRepository` fallback chain (embedded → LRCLIB → lyrics.ovh →
      Genius), Room lyrics cache, Retrofit/OkHttp network clients.
- [ ] **Phase 5** — Lyrics screen UI: blurred background, synced line highlight/scroll.
- [ ] **Phase 6** — Queue & playlists (Room-persisted).
- [ ] **Phase 7** — Android Auto (`MediaLibraryService` browse tree).
- [ ] **Phase 8** — AAudio exclusive-mode native sink (see *Hi-res audio plan* above);
      feasibility spike on real hardware before investing in the full native module.
- [ ] **Phase 9** — Polish (icon, empty/error states, unit tests), production signing config,
      first signed release.

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
