# DerDieDas

[![CI](https://github.com/krzysztof1980/derdieapp/actions/workflows/ci.yml/badge.svg)](https://github.com/krzysztof1980/derdieapp/actions/workflows/ci.yml)

Find the gender of any German noun — `der`, `die` or `das` — from a fully offline app.
Type a noun or an inflected form (`Mutter`, `Mueller`, `Kindern`); the answer washes in
the article's colour, with plural forms and senses.

The dictionary ships inside the APK as a gzipped SQLite asset. **The app declares no
permissions at all** — no INTERNET, no data access — and a test
(`ManifestContractTest`) fails the build if the merged manifest ever grows one beyond the
single androidx-injected dynamic-receiver permission.

## Modules

| Module | What it is |
|---|---|
| `:app` | Compose UI + `android.database.sqlite` reader; offline by contract. |
| `:core` | Shared folding (`foldForm`), lookup engine, DB schema, `fold_vectors.txt`. |
| `:exporter` | One-shot pipeline: Elasticsearch index → `nouns.db` asset. |

## Building

Prerequisites: any JDK 17–25, Android SDK 35, and `local.properties` pointing `sdk.dir`
at that SDK (gitignored). Point `JAVA_HOME` at your JDK first — this machine has no java
on `PATH`, and Gradle needs a JVM just to start:

```sh
export JAVA_HOME=/path/to/your/jdk-21    # pick yours; CI runs Temurin 21
```

Everything below then runs as written from this repo's root:

```sh
./gradlew :core:test :exporter:test :app:testDebugUnitTest   # all tests
./gradlew :app:assembleDebug                                 # debug APK (default, unminified)
./gradlew :app:assembleRelease                               # R8-minified, unsigned
```

`gradle.properties` carries no `org.gradle.java.home`: an absolute JDK path is
machine-specific and must not be tracked, and none is needed — the build runs on daemons
from 17 to 25 (both ends verified). If something on your box insists on pinning anyway,
see Troubleshooting.

### Getting the dictionary asset

The ~93 MiB database is never committed (`.gitignore` keeps `app/src/main/assets/nouns.db*`
out); a build without it throws an `AssetNotFoundException` naming `nouns.db.gzip` on
first launch. Steps name their repo; all relative paths are from that repo's root.

1. Parser repo (`../wiktio-peggy-nary`) — parse the dump into the local Elasticsearch
   8.15 index, then publish the model jar:

   ```sh
   # wiktio-peggy-nary/
   ./gradlew runParserParallel -PdumpFile=/path/to/pages-articles.xml
   ./gradlew publishToMavenLocal
   ```

   `:exporter` consumes the published `wiktiopeggynary:wiktiopeggynary:1.0-SNAPSHOT` jar
   from mavenLocal and declares a JDK 25 toolchain to read its class files.
2. This repo's root — export the index into a plain database under a pinned dir:

   ```sh
   # derdieapp/
   ./gradlew :exporter:run --args="--out build/export --dump dewiktionary-20260901-pages-articles-multistream.xml --es-user elastic:<pass>"
   ```

   `--dump` takes the dump FILE name; it lands verbatim in `meta.built_from` and is what
   the app's About dialog attributes. The run task's working dir is the exporter project,
   so a relative `--out build/export` lands in `exporter/build/export/`.

3. Still `derdieapp/` root — gzip the one file that is an app asset:

   ```sh
   # derdieapp/  (exporter wrote exporter/build/export/nouns.db)
   mkdir -p app/src/main/assets
   gzip -kc exporter/build/export/nouns.db > app/src/main/assets/nouns.db.gzip
   ```

   The name must end `.gzip`, not `.gz`: AGP ≥ 9 silently inflates `.gz` assets at merge
   time, which would hand the app a plain file named `nouns.db`. The exporter also writes
   `fold_vectors.txt` beside the database; it needs no copying step — the app's vectors
   ship as the `:core` jar resource they were hashed from at export time. The app does not
   verify that hash on launch: the `fold_vectors_sha256` stamp is kept for offline
   forensics, matching a shipped asset to a shipped jar. Actual fold drift is caught in
   debug builds, where the app scans the shipped `forms` corpus with its own fold function
   and fails loudly on the first drifted form.

### Launcher icon

Adaptive icon only (minSdk 26 ⇒ a single `mipmap-anydpi-v26` adaptive icon covers every
device, no raster fallbacks): white lowercase `der` on the dark der-blue wash, hand-built
as a vector (`ic_launcher_foreground.xml`). Dump-attributing real font outlines is a
pre-Play TODO.

## Manual smoke checklist (device)

Install the debug APK and verify — the automated subset runs in CI, this list covers what
needs eyes and a thumb:

- Airplane mode: everything works (no network calls exist to fail).
- `booten` → plural note (`das Booten`, plural `—`).
- `Mueller` → folded match on *die Müller*: the `Mueller`/`Müller` → `mueller` key
  convergence is pinned by `FoldTest`; the device step exercises the answer rules on top.
- `zeit` → *die Zeit* (surnames intentionally not shown).
- `eltern` → *die Eltern*, die + note.
- `virus` → both genders accepted (`das/der`), **no** gender toggle anywhere on screen.
- `xyz` → no-result row quoting the typed text.
- Landscape rotation keeps the query (and, after it settles, the same answer — the
  saveable round-trip is pinned by `SearchScreenRecreationTest`).
- System dark mode: app appearance stays identical (single-appearance design; the
  `NightModeInvarianceTest` pins this).

## Troubleshooting

- **Kotlin plugin ≤ 2.0.x crashes on a JDK 25 daemon** (its version parser chokes on
  strings like `25.0.4.1`). This repo's Kotlin (2.4.21 via the catalog) does not crash,
  so prefer just running on JDK 17–21. If you are pinned to an old plugin, add
  `org.gradle.java.home=/path/to/jdk-17` to `~/.gradle/gradle.properties` — host-level on
  purpose, so no repo tracks a machine path; CI needs no pin at all because setup-java
  pins JDK 21. Careful: the Gradle user home outranks every project's
  `org.gradle.java.home`, so a host-wide JDK 17 also re-jails sibling builds that need a
  newer daemon (the parser repo needs ≥ 25 — verified to break under a host pin).
- **`:exporter` dependency `wiktiopeggynary:1.0-SNAPSHOT` not found** — run
  `./gradlew publishToMavenLocal` in the parser repo first.

## Privacy

No data is collected, stored off-device, or transmitted; the app has no permission to
reach the network. A privacy-policy page saying exactly that is still a Play requirement —
open item below.

## Attribution

German word data comes from the [German Wiktionary](https://de.wiktionary.org), used under
the CC BY-SA 4.0 and GFDL licenses; the in-app About dialog names the dump version. Entries
are parsed and exported by `wiktiopeggynary`.

## Screenshots

TODO — Play Store screenshots (light + dark, 1080×1920 and 1920×1080) before release.

## Before Play (open items)

- [ ] Choose and apply an open-source license (repo currently has none).
- [ ] Publish a privacy policy page ("no data collected") and link it in the listing.
- [ ] Dump-attribute the launcher icon (real font outlines instead of hand-built paths) —
      and attribute the font if a different one is chosen.
- [ ] Decide the repo/app rename (display name is `DerDieDas`; the git repo is still
      `derdieapp`).
- [ ] Fill `signingConfig` on the release build type (currently unsigned on purpose).

## CI

`.github/workflows/ci.yml` runs `:core:test :exporter:test :app:testDebugUnitTest
:app:assembleDebug` on Temurin 21 (Gradle wrapper 9.7.1 runs fine on 21; JDK 17 is
installed alongside for the `jvmToolchain(17)` modules). `:exporter:test` needs
`wiktiopeggynary:1.0-SNAPSHOT` in mavenLocal, so the workflow first clones
`krzysztof1980/wiktionary-dump-browser` and `krzysztof1980/wiktio-peggy-nary` (both
public) and publishes them to mavenLocal on JDK 25 — the same staging the quick-start
does locally. If either dependency repo goes private, the staging step needs a token.
