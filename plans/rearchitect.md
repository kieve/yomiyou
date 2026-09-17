# Yomiyou re-architecture plan

Status doc. Tick boxes as work lands; keep "Decisions" and "Constraints" current so a
fresh session can pick this up with zero context.

## Goal

"Tachiyomi, but for novels." Scrape entire webnovels from anti-bot sites through the
Android WebView (a real browser), store every chapter locally as Markdown, read offline.

## Constraints (do not violate)

- **Never fetch novelphoenix (or any live source) from tooling/agents.** It has
  anti-scraping. Develop parsers only against saved HTML fixtures under
  `app/src/test/resources/sources/<source>/` (search page, novel page, chapter-list
  page, chapter page), captured once from a desktop browser or the app's WebView.
- WebView stays the fetch mechanism. No OkHttp/jsoup-connect scraping.
- Chapter content is stored as **Markdown files** — readable without tools. Do not
  propose HTML storage, `AnnotatedString.fromHtml`, or content-in-Room.

## Decisions made (2026-09-17)

| Topic | Decision |
|---|---|
| Content format | Markdown via `copydown/` (turndown port, 122-case test corpus). Rendered by `compose-markdown`. |
| Content store | Internal `filesDir/novels/<novelId>/` is canonical. |
| SAF folder picker | An **export** feature (sync via Seafile etc.), not debug. Optional, set from settings; mirror on chapter write as `<novel title>/0001 - Chapter Title.md` + cover. Never forced at first launch. |
| `LogUtil.getTag()` | Keep. Add `-keepnames` only if `minifyEnabled` is ever turned on. |
| `Crawler.absoluteUrl` | Delete; use jsoup `element.absUrl("href")` (docs are parsed with base URI in `getSoup`). |
| `BaseDao.upsert` / `HasId` | Replace with Room `@Upsert`; `HasId` then has no user and goes. |
| `YomiContext` | Not a standalone deletion; disappears when screens are rewritten (E). |
| LightNovelPub source | Dead (5 years). Delete; Jackson moves to `testImplementation` (only `CopyDownTest` uses it). minSdk can drop back to 24 then, if wanted. |
| UI toolkit | Still Compose Material 2 + Navigation 2 on purpose. M3 migration happens with the UI rewrite (E), not before. |

## Current pipeline (as of the 2026-09 toolchain bump)

```
WebViewScraper.loadPage(url)             scraper/WebViewScraper.kt
  -> Crawler.getSoup / Source.*          crawler/Crawler.kt, crawler/source/en/l/LightNovelPub.kt
  -> Crawler.cleanContents               strip ads / bad tags (keep)
  -> Crawler.extractContents             Python-port flattening (DELETE, see bug 3)
  -> CopyDown().convert(html)            copydown/  HTML -> Markdown
  -> YomiFiles.writeChapter              data/YomiFiles.kt  filesDir/novels/<id>/<chapterIdx>
  -> NovelRepository.onChapterDownloaded data/NovelRepository.kt  in-memory mirror
Room: NovelMeta, ChapterMeta             data/database/
Jobs: NovelScheduler + NovelJob          data/scheduler/  serial WebView queue, active-novel priority
UI:  YomiNavigation -> NovelList / NovelSearch / NovelInfo / Reader   ui/composable/screen/
```

## Bugs (ranked)

- [x] **1. Scheduler dies silently on first exception.** *(fixed 2026-09-17: try/finally on run lock, per-job catch drops the failed job, SupervisorJob scope)* `NovelScheduler.tryRunJobs` has no
  `try/finally`; a throw in any job leaves `runMutex` locked forever, all later
  `schedule()` calls no-op. (`data/scheduler/NovelScheduler.kt`)
- [x] **2. `WebViewScraper.loadPage` can hang forever and completes on the wrong page.** *(fixed 2026-09-17: `loadPage(url, waitFor, timeoutMs)` polls the selector after `onPageFinished`, `withTimeoutOrNull`, Chrome-like UA; `WebViewScreen` hosts the scraper WebView, reached via the FAB on the library screen — auto-opening it on timeout is still TODO)*
  No timeout on `webViewUpdateJob.join()` while holding `webViewMutex` (one stuck load
  deadlocks all scraping). Grabs HTML on the *first* `onPageFinished`, which on
  anti-bot sites is the interstitial, not the content. Fix: poll
  `document.querySelector(<content selector>)` via `evaluateJavascript` until present
  or timeout; on timeout show the (currently never-attached) WebView in an
  `AndroidView` so the user can solve a challenge by hand; strip `; wv` from the UA.
- [ ] **3. `Crawler.extractContents` causes both `TODO.txt` parse bugs.** It flattens
  HTML into `<p>` lines before CopyDown (inline `<i>` becomes its own paragraph;
  `"<%s>%s<%s>"` / `"<p>$s<p>"` never close tags so italics/lists bleed into the next
  paragraph). Verified 2026-09-17: CopyDown alone renders both cases correctly.
  Delete `extractContents` + `internalExtractContents`, keep `cleanContents`, feed
  `element.html()` to CopyDown, apply `blacklistRegexes` to Markdown lines.
- [ ] **4. Reader loads the whole novel into RAM.** `NovelRepository.openNovel` reads
  every chapter file; `ReaderScreen` renders all of them in one `LazyColumn` of
  `MarkdownText`. Reader must load one chapter (± neighbours).
- [ ] **5. Chapter identity is a positional index.** `ChapterMeta.id` = crawl position and
  is also the cache filename; a site insert/reorder silently mislabels every cached
  chapter after it. Key by URL/slug; keep a separate `index` for ordering.
- [ ] **6. Startup is O(total chapters) coroutines + O(n²) map copies.** `loadNovels`
  launches a coroutine per chapter; each `setNovel` copies the whole map; missing
  chapters are re-scheduled on every launch with no "already queued" check, and
  `addToLibrary` schedules them all again.
- [ ] **7. `Crawler` is a stateful singleton used concurrently.** `currentSource /
  currentNovelUrl / currentFilter` are mutated by `initSource` while search and cover
  downloads run in parallel with the scheduler. `searchNovels` hard-codes
  `SOURCES[0]`. `lastVisitedUrl` is never assigned.
- [ ] **8. SAF misuse.** Folder picker forced on first launch; 3 files per chapter
  written via `DocumentFile.findFile` (directory listing per call); every file starts
  with `"Hello, world!\n"`. Rework per the SAF export decision above.
- [ ] **9. `Crawler` can't be unit-tested on the JVM** — `android.icu.text.UnicodeSet`
  throws in stub android.jar. Use Kotlin `Regex("[\\p{Cf}\\p{Cc}]")`.

## Structural work

- [ ] **A. Stateless source plugins + fixture tests.** Interface: `search(q)`,
  `novel(url)`, `chapters(url)` (source handles its own pagination — the
  `ChapterListInfo` / `getChapterListPage` / `MultiStepJob` machinery exists only for
  LightNovelPub paging and leaks into the scheduler), `chapterHtml(url)`. Source takes a
  `Scraper`, not the `Crawler`; `HtmlFilter` is a `val` on the source. First source:
  **novelphoenix**, developed against fixtures only.
- [ ] **B. One source of truth for "downloaded".** Files stay canonical; drop the
  in-memory `Novel.chapterFiles` mirror; Room `ChapterMeta` gets a `downloaded`
  flag (or path) written when the file is; UI reads Room `Flow`s. Add
  `read_progress (novel_id, chapter_index, scroll)` and `last_updated`. Chapter files
  named `0001 - Chapter Title.md`.
- [ ] **C. SAF export feature** (see decision). Cache per-novel `DocumentFile` dir;
  "re-export everything" action for folders set after the fact.
- [ ] **D. Scheduler reshape.** Queue of `suspend () -> Unit` jobs, `try/catch` per job
  with the error surfaced to the UI, a `Set` of queued keys for dedup, one fixed
  inter-request delay (`ponytail:` constant, tune per source later). Drop
  `MultiStepJob`. Keep "active novel first" priority.
- [ ] **E. UI rewrite (M3).** Screens take state + callbacks (no `AppContainer` /
  `NavController` in composables). Fix: Scaffold padding not applied,
  `Text("Novel is null") // TODO`, debug FAB, `scrollToItem(chapterId - 1)`. Reading
  progress, next / previous chapter, download-state UI.

## Delete list

- [ ] `YomiViewModel` (empty)
- [x] `NovelRepository.debug()` (FAB now opens `WebViewScreen`)
- [ ] Debug-file code: `YomiFiles.registerDebugFiles/writeDebugFile`, `FileUtil.kt`
  `DocumentFile` helpers, first-launch picker in `YomiActivity` (replaced by C)
- [ ] `BaseDao.upsert` → `@Upsert`; `HasId`
- [ ] `Crawler.absoluteUrl` → `absUrl("href")`
- [ ] `Crawler.extractContents` / `internalExtractContents` (bug 3)
- [ ] `LightNovelPub.kt` + Jackson from `implementation`
- [ ] `Theme.MaterialComponents` XML theme + `material` (views) + `appcompat` deps once
  on `ComponentActivity` (with E)

## Keep as-is

`copydown/` + `CopyDownTest` corpus; WebView-as-browser; serialized job runner with
active-novel priority; `Crawler.cleanContents` tag/CSS strip lists; `LogUtil.getTag()`.

## Order of work

1. [x] Bugs 1 + 2 (scheduler `finally`; scraper timeout + selector-wait + visible-WebView
   fallback). Everything else needs a fetch that can't hang or die silently.
2. [ ] Capture novelphoenix fixtures with a desktop browser → write source + fixture
   tests (A). Delete LightNovelPub.
3. [ ] Bug 3 (delete `extractContents`), bug 9, rest of the delete list.
4. [ ] Re-architecture: B, C, D, E, bug 5 (chapter keying), bug 4 (single-chapter reader),
   bug 6 falls out of B.

## Toolchain notes

- Built 2026-09-17: Gradle 9.7.1, AGP 9.4.0 (built-in Kotlin 2.4.20), KSP 2.3.12,
  Room 2.8.5 + `androidx.room` plugin, Compose BOM 2026.09.00, Coil 3, JUnit 6 via
  `useJUnitPlatform()`, compileSdk/targetSdk 37, minSdk 26 (Jackson needs 26; drops
  to 24 when Jackson leaves `implementation`), Java 17.
- `gradle/gradle-daemon-jvm.properties` pins the daemon to JDK 21.
- SDK: `platforms/android-37.0` is the stable one; 37.1 / 37.2 are QPR betas.
- Only test suite: `CopyDownTest` (122 JSON cases). jsoup upgrades can break it —
  `Node.textContent()` in `copydown/util/NodeUtils.kt` exists because jsoup ≥ 1.15.4
  makes `wholeText()` emit `"\n"` for `<br>`.
