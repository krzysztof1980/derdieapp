# DerDieDas

Find the gender of any German noun — `der`, `die` or `das` — from a fully offline app.
Type a noun or an inflected form (`Mutter`, `Mueller`, `Kindern`); the answer washes in
the article's colour, with plural forms, senses and a surname lane for ambiguous hits.

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

Prerequisites: JDK 17 on the machine (the Gradle daemon runs on it), Android SDK 35.
`local.properties` must point `sdk.dir` at a local SDK (gitignored).

`gradle.properties` pins `org.gradle.java.home` to an absolute JDK 17 path: the Kotlin
plugin's version parser chokes on JDK 25, so the original author's machine pins its own
daemon JVM. If the path doesn't exist on your box, do **not** delete the pin — override
it per invocation: `./gradlew -Dorg.gradle.java.home=$JAVA_HOME <task>` (CI does exactly
that with the runner's JDK 21).

```sh
./gradlew :core:test :exporter:test :app:testDebugUnitTest   # all tests
./gradlew :app:assembleDebug                                 # debug APK (default, unminified)
./gradlew :app:assembleRelease                               # R8-minified, unsigned
```

### Getting the dictionary asset

The ~93 MiB database is never committed (`.gitignore` keeps `app/src/main/assets/nouns.db*`
out); a build without it throws an `AssetNotFoundException` naming `nouns.db.gzip` on
first launch.

1. In the companion parser repo (`wiktio-peggy-nary`): parse the dump into the local
   Elasticsearch 8.15 index — `./gradlew runParserParallel -PdumpFile=<pages-articles.xml>` —
   then `./gradlew publishToMavenLocal`. `:exporter` consumes the published
   `wiktiopeggynary:wiktiopeggynary:1.0-SNAPSHOT` model jar from mavenLocal, and needs a
   JDK 25 toolchain to read its class files.
2. From this repo's root, run the exporter against that index:

   ```sh
   ./gradlew :exporter:run --args="--out <dir> --dump <index-name> --es-user elastic:<pass>"
   ```
3. The exporter writes a plain `nouns.db` plus a copy of `fold_vectors.txt` beside it.
   **Only the database is an app asset**, gzipped under this exact name:

   ```sh
   gzip -kc nouns.db > ../derdieapp/app/src/main/assets/nouns.db.gzip
   ```

   The name must end `.gzip`, not `.gz`: AGP ≥ 9 silently inflates `.gz` assets at merge
   time, which would hand the app a plain file named `nouns.db`. The fold vectors need no
   copying step at all — they ship as a `:core` jar resource (`fold_vectors.txt`) and the
   exporter stamps their hash into the DB so any drift between the two shipped copies
   fails loudly at first launch of a debug build.

### Launcher icon

Adaptive icon only (minSdk 26 ⇒ a single `mipmap-anydpi-v26` adaptive icon covers every
device, no raster fallbacks): white lowercase `der` on the dark der-blue wash, hand-built
as a vector (`ic_launcher_foreground.xml`). Dump-attributing real font outlines is a
pre-Play TODO.

## Manual smoke checklist (device)

Run `:app:assembleDebug`, install, and verify — the automated subset runs in CI, this
list covers what needs eyes and a thumb:

- Airplane mode: everything works (no network calls exist to fail).
- `booten` → plural note (`das Booten`, plural `—`).
- `Mueller` → folded match on *die Müller* (surname/word lane per the rules).
- `zeit` → answer with the surname footer lane.
- `eltern` → *die Elten*, die + note.
- `virus` → both genders accepted (`das/der`), **no** gender toggle anywhere on screen.
- `xyz` → no-result row quoting the typed text.
- Landscape rotation keeps the query (and, after it settles, the same answer).
- Dark mode: washes stay legible, text contrast comfortable.

## Screenshots

TODO — Play Store screenshots (light + dark, 1080×1920 and 1920×1080) before release.

## Privacy

No data is collected, stored off-device, or transmitted; the app has no permission to
reach the network. A privacy-policy page saying exactly that is still a Play requirement —
open item below.

## Attribution

German word data comes from the [German Wiktionary](https://de.wiktionary.org), used under
the CC BY-SA 4.0 and GFDL licenses; the in-app About dialog names the dump version. Entries
are parsed and exported by `wiktiopeggynary`.

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
:app:assembleDebug` on a Temurin 21 runner (the Gradle wrapper 9.7.1 runs fine on 21; the
local JDK-17 daemon pin is a machine-specific workaround and is overridden in the
workflow). Caveat: `:exporter:test` needs the `wiktiopeggynary:1.0-SNAPSHOT` jar from
mavenLocal — CI only goes green once that snapshot is published somewhere the workflow can
fetch (or the exporter job pre-stages it); see the comment in the workflow.
