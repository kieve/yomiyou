# Yomiyou

A personal "Tachiyomi for novels" Android app, 5+ years old: it scrapes whole web novels through a WebView (for
anti-bot sites) and reads them offline. The working plan is `plans/rearchitect.md`.

Since 2026-11, new screens take over the app: Shelf, Browse and Settings, built first for yomiage's read-along books
(`kieve/yomiage` `plans/tts-epub-jp.md`, Milestone 5). The webnovel screens live on, untouched, in `YomiActivity`,
reached from Settings → Debug; bringing them into the new screens is planned separately.

## Toolchain

Gradle 9.8.2 (the version all of the user's projects share), AGP 9.4 with built-in Kotlin, KSP, Room 2.8 with the
`androidx.room` plugin, Compose BOM 2026.09 (Material 3), Navigation 3 for the new screens (Navigation 2 for the old ones
in `YomiActivity`), the kotlinx.serialization plugin (Navigation 3 keys), Coil 3, JUnit 6, compileSdk/targetSdk 37,
minSdk 26, Java 17, Kotlin DSL and a version catalog. Material and navigation stay on their newest stable versions.

- Android SDK: `C:/Users/knash/Seafile/Projects/android-sdk` (platform 37.0; 37.1 and 37.2 are QPR betas).
- CLI builds run on the host: the host's JDK 17 launches Gradle, whose daemon runs on the JDK named in
  `gradle-daemon-jvm.properties`. This is yomiyou's exception to the rule that the user's Java projects build in a
  container: Android Studio builds on the host anyway. The user runs the app from Android Studio.
- Verify with `:app:compileDebugKotlin` and `:app:testDebugUnitTest` (`assembleDebug` if an APK is needed).
- Emulators: install the app and drive it through T3's device tools: `device_open`, then the `agent-device` command it
  returns for every install (`install ca.kieve.yomiyou <apk>`), launch, tap and screenshot. The foldable AVD is
  `Pixel_9_Pro_Fold` (Android 15); `agent-device fold closed` and `fold open` switch between its cover and inner
  screens. Never a physical device: the user tests on their Fold 8. Reading state with `adb shell run-as` or `logcat`
  is fine.
- `CopyDownTest` (122 JSON cases) is the only suite. A jsoup upgrade can break it (`wholeText()` versus
  `textContent` semantics); `Node.textContent()` is in `copydown/util/NodeUtils.kt`.

## The new screens

- `home`: `MainActivity` (the launcher), `Home` (the library folder prompt on first start, then the Navigation 3 back
  stack: the tabs at the bottom, the reader over them), `TabsScreen` (Shelf, Browse and Settings in
  `NavigationSuiteScaffold`: a bottom bar on a narrow screen, a rail on a wide one), `Services` (the new screens'
  app-wide objects).
- `shelf`, `browse`, `settings`: a screen each. Settings → Debug starts `YomiActivity`.
- `book`: `ZipIndex` (a zip's entries read by position, zip64 included), `EnrichedEpub` (an enriched EPUB and its
  `yomiage.json`; chapters cut as the web reader's `book.ts` cuts them), `Library` (the folder the user chose, through
  the storage framework: a folder per book, named by `FolderNames`, with its `book.json`), `TestBook`.
- `reader`: `ReaderBook` (what the reader shows, whatever the kind of book), `ReaderScreen` (the book full screen, system
  bars hidden; a tap brings up the controls, the chapter drawer and the reader options), `ReaderPage` (the WebView; its
  page is `assets/reader/`, served with the book's files by a `WebViewAssetLoader`: the whole book as one scroll, five
  chapters loaded at a time around the reader), `ReadingStyle` (app-wide, as CSS variables on the page).
- Debug builds bundle the test book named by `yomiyou.testBook` in `local.properties`: yomiage's output folder for one
  book. Books are copyrighted and the repo is public: never commit one. Settings → Debug → Test book copies it into the
  library and opens it.
- `src/test/resources/epub/kumo-no-ito.yomiage.epub`: 蜘蛛の糸 (public domain) enriched by yomiage's `BuilderTest`; to
  rebuild it, run that test in yomiage with `YOMIAGE_FIXTURE_OUT` set to a folder.
- Icons are Material Symbols vector drawables in `res/drawable` (`ic_<name>.xml`, plus `ic_<name>_filled.xml` for a
  selected tab), copied from `google/material-design-icons` (`symbols/android`), not `material-icons-extended`.

## Decisions (don't re-litigate)

- Chapters are stored as **Markdown files**, readable without tools. Keep CopyDown and compose-markdown; don't propose
  HTML storage, `AnnotatedString.fromHtml` or content in Room.
- The SAF folder picker is an **export feature** (sync through Seafile and the like), not debug code: internal
  `filesDir` is canonical, and the SAF folder is an optional mirror written on chapter download, files named
  `0001 - Title.md`.
- Keep `LogUtil.getTag()` (the stack-walk tag helper); it only needs a `-keepnames` rule if minify is ever enabled.
- The next source is novelphoenix. **Never fetch that site from tools** (it has anti-scraping): parsers are built
  against saved HTML fixtures in `src/test/resources`.
- The LightNovelPub source is dead and will be deleted.

## Working on this project

- Git level RO (`git-level` skill), with `main`, `master` and `modernize` protected. `modernize` is the working
  default branch until it merges into `main`. Claude works on branches of its own from `modernize`, pushes them by name
  (`git push -u origin <branch>`) and opens a Forgejo PR into `modernize` for each piece of work; the user reviews it
  there and merges. Never commit on, merge into or push to a protected branch.
- The GitHub mirror is public and carries only `main` and `modernize` (its branch filter), so work branches stay on
  Forgejo until merged.
- `TODO.txt` is the user's personal list: never edit it, not even to mark something done. Mention addressed items in
  the reply; reading its repro URLs is fine.
- No comments that restate the code; prefer a clearer name (`rawChapterHtml`). Comment only where the code can't
  explain itself. Notes on deliberate simplifications go in `plans/rearchitect.md` or the reply.
