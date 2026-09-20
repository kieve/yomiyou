# Yomiyou re-architecture plan

Status doc. Tick boxes as work lands; keep "Decisions" and "Constraints" current so a
fresh session can pick this up with zero context.

## Goal

"Tachiyomi, but for novels." Scrape entire webnovels from anti-bot sites through the
Android WebView (a real browser), store every chapter locally as Markdown, read offline.

## Constraints (do not violate)

- Capture fixtures with `tools/capture-fixture.ps1` (clipboard → UTF-8 file + `urls.txt`
  line). `FixtureScraper` fails the test if a fixture is UTF-16. Cause of the UTF-16
  files: Directory Opus "paste clipboard as file" defaults to UTF-16 for non-ASCII text;
  fixed via Preferences → Miscellaneous → Advanced → `clipboard_text_encoding = utf8nobom`.

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
| Content store | Internal `filesDir/novels/<novelId>/<url-slug>.md` is canonical (slug = last URL segment, stable for the chapter's life). Human-readable `0001 - Title.md` names are the export's job (C). |
| SAF folder picker | An **export** feature (sync via Seafile etc.), not debug. Optional, set from settings; mirror on chapter write as `<novel title>/0001 - Chapter Title.md` + cover. Never forced at first launch. |
| `LogUtil.getTag()` | Keep. Add `-keepnames` only if `minifyEnabled` is ever turned on. |
| `Crawler.absoluteUrl` | Delete; use jsoup `element.absUrl("href")` (docs are parsed with base URI in `getSoup`). |
| `BaseDao.upsert` / `HasId` | Replace with Room `@Upsert`; `HasId` then has no user and goes. |
| `YomiContext` | Not a standalone deletion; disappears when screens are rewritten (E). |
| Room schema version | Stays at **1** until a real release; clear app data on test devices between schema changes. No migrations, no `fallbackToDestructiveMigration`. |
| LightNovelPub source | Dead (5 years). Delete; Jackson moves to `testImplementation` (only `CopyDownTest` uses it). minSdk can drop back to 24 then, if wanted. |
| UI toolkit | Compose Material 3 (since 2026-09-18) + Navigation 2. `ComponentActivity` on the platform `android:Theme.Material.NoActionBar`; appcompat / material-views deps gone. Colors: M3 baseline dark scheme with black `background` / `surface`; type and shapes are M3 defaults. |

## Current pipeline (as of the 2026-09 toolchain bump)

```
WebViewScraper.loadPage(url)             scraper/WebViewScraper.kt
  -> NovelSource (NovelPhoenix)          crawler/NovelSource.kt, crawler/source/NovelPhoenix.kt
  -> Crawler.toMarkdown                  cleanContents (strip ads / bad tags) -> CopyDown -> blacklist lines
  -> YomiFiles.writeChapter              data/YomiFiles.kt  filesDir/novels/<id>/<slug>.md
  -> NovelRepository.onChapterDownloaded data/NovelRepository.kt  marks chapter.downloaded
Room: Novel, Chapter (v2)                data/database/  UI collects Flows directly
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
- [x] **3. `Crawler.extractContents` causes both `TODO.txt` parse bugs.** *(fixed 2026-09-17: `Crawler.toMarkdown` = cleanContents → CopyDown → strip non-printables → substitutions → blacklist lines; `CrawlerTest` covers both TODO cases)* It flattens
  HTML into `<p>` lines before CopyDown (inline `<i>` becomes its own paragraph;
  `"<%s>%s<%s>"` / `"<p>$s<p>"` never close tags so italics/lists bleed into the next
  paragraph). Verified 2026-09-17: CopyDown alone renders both cases correctly.
  Delete `extractContents` + `internalExtractContents`, keep `cleanContents`, feed
  `element.html()` to CopyDown, apply `blacklistRegexes` to Markdown lines.
- [x] **4. Reader loads the whole novel into RAM.** *(fixed 2026-09-18: reader shows one chapter from `openChapter(novelId, position)` with prev/next)* `NovelRepository.openNovel` reads
  every chapter file; `ReaderScreen` renders all of them in one `LazyColumn` of
  `MarkdownText`. Reader must load one chapter (± neighbours).
- [x] **5. Chapter identity is a positional index.** *(fixed 2026-09-18: `Chapter` PK is `(novel_id, url)`, `position` is data; `mergeChapters` keeps `downloaded` by URL — `MergeChaptersTest`)* `ChapterMeta.id` = crawl position and
  is also the cache filename; a site insert/reorder silently mislabels every cached
  chapter after it. Key by URL/slug; keep a separate `index` for ordering.
- [x] **6. Startup is O(total chapters) coroutines + O(n²) map copies.** *(fixed 2026-09-18: one `notDownloaded` query per library novel, batch `schedule()`)* `loadNovels`
  launches a coroutine per chapter; each `setNovel` copies the whole map; missing
  chapters are re-scheduled on every launch with no "already queued" check, and
  `addToLibrary` schedules them all again.
- [x] **7. `Crawler` is a stateful singleton used concurrently.** *(fixed 2026-09-17: no state left; sources are stateless and take a `Scraper`)* `currentSource /
  currentNovelUrl / currentFilter` are mutated by `initSource` while search and cover
  downloads run in parallel with the scheduler. `searchNovels` hard-codes
  `SOURCES[0]`. `lastVisitedUrl` is never assigned.
- [ ] **8. SAF misuse.** Folder picker forced on first launch; 3 files per chapter
  written via `DocumentFile.findFile` (directory listing per call); every file starts
  with `"Hello, world!\n"`. Rework per the SAF export decision above.
- [x] **9. `Crawler` can't be unit-tested on the JVM** *(fixed 2026-09-17: Kotlin `Regex`)* — `android.icu.text.UnicodeSet`
  throws in stub android.jar. Use Kotlin `Regex("[\\p{Cf}\\p{Cc}]")`.

## Structural work

- [x] **A0. Scraper page capture (done 2026-09-17).** Replaces the deleted SAF debug
  files: `WebViewScraper.loadPage` writes the HTML it captured (or the page it was stuck
  on at timeout) to `cacheDir/scraper/<timestamp>-<slug>.html`, keeping the last ~20.
  Always on, no permission, bounded. Read via Device Explorer
  (`data/data/ca.kieve.yomiyou/cache/scraper/`) or `adb shell run-as`. Doubles as the
  way to grab fixtures for pages that can't be fetched from a desktop.
- [x] **A. Stateless source plugins + fixture tests.** *(done 2026-09-17: `NovelSource` interface, `NovelPhoenix`, `FixtureScraper` driven by `urls.txt` and validating `waitFor` selectors, `NovelPhoenixTest`)* Interface: `search(q)`,
  `novel(url)`, `chapters(url)` (source handles its own pagination — the
  `ChapterListInfo` / `getChapterListPage` / `MultiStepJob` machinery exists only for
  LightNovelPub paging and leaks into the scheduler), `chapterHtml(url)`. Source takes a
  `Scraper`, not the `Crawler`; `HtmlFilter` is a `val` on the source. First source:
  **novelphoenix**, developed against fixtures only.
- [x] **B. One source of truth for "downloaded".** *(done 2026-09-18: schema stays v1 — clear app data on test devices after schema changes (`novel`, `chapter` with `downloaded`), UI reads Room `Flow`s, in-memory mirror + mutexes gone, covers loaded by Coil straight from `cover_url` (manual cover download deleted). `read_progress` NOT added yet — nothing writes it; add with E when the reader tracks position)* Files stay canonical; drop the
  in-memory `Novel.chapterFiles` mirror; Room `ChapterMeta` gets a `downloaded`
  flag (or path) written when the file is; UI reads Room `Flow`s. Add
  `read_progress (novel_id, chapter_index, scroll)` and `last_updated`. Chapter files
  named `0001 - Chapter Title.md`.
- [ ] **C. SAF export feature** (see decision). Cache per-novel `DocumentFile` dir;
  "re-export everything" action for folders set after the fact.
- [x] **D. Scheduler (done 2026-09-18).** Jobs are `step(): Boolean` (the original
  `MultiStepJob` shape); chapter list = one page per step so the queue is re-sorted between
  page loads. Priority documented at the top of `NovelScheduler.kt`: reading chapter →
  next → previous; open novel (info, list, chapters); background chapters by
  `last_opened`, then lists. Non-library jobs are dropped on navigate-away; the list
  resumes from `Novel.chaptersNextPage`. Key-based dedup. `SchedulerPriorityTest`.
  Still open: errors surfaced to the UI; a fixed inter-request delay if the site ever
  needs one.
- [ ] **E. UI rewrite (M3).** *(2026-09-18: M3 swap done — `material3` dep, `ComponentActivity`,
  Scaffold padding applied, `NovelCard(onClick)`. The rest is still open.)* Screens take state + callbacks (no `AppContainer` /
  `NavController` in composables). Fix: Scaffold padding not applied,
  `Text("Novel is null") // TODO`, debug FAB, `scrollToItem(chapterId - 1)`. Reading
  progress, next / previous chapter, download-state UI.
- [ ] **F. Incremental updates.** *(2026-09-18: a first cut was built and discarded — the
  refresh interval must be a user setting, so F waits for E's settings page. The data
  hook is in place: the list job always re-fetches `Novel.listPage`, so "refresh" is just
  scheduling `DownloadChapterListJob` for a complete list.)* Novels release a few chapters a week; today the chapter
  list is fetched once (when added) and a re-run would reload all pages. Do after bug 5
  (URL-keyed chapters make the merge safe). Pieces: `NovelSource.chapters(novelUrl,
  fromIndex)` so NovelPhoenix starts at page `fromIndex / 100 + 1` (new chapters are on
  the last page) and follows `rel=next` from there; optionally skip the list job when the
  novel page's chapter count / `Updated` time hasn't changed; a `refresh(novelId)`
  action (manual / on opening a library novel) that queues info + list jobs, after which
  the existing "download what's missing" path picks up the new chapters; later a
  WorkManager periodic check (WebView needs the app process + main thread, same as
  Tachiyomi's library update).

## Delete list

- [x] `YomiViewModel` (empty)
- [x] `NovelRepository.debug()` (FAB now opens `WebViewScreen`)
- [x] Debug-file code: `YomiFiles.registerDebugFiles/writeDebugFile`, `FileUtil.kt`
  `DocumentFile` helpers, first-launch picker in `YomiActivity`, `documentfile` dep (C re-adds SAF deliberately)
- [x] `BaseDao.upsert` → `@Upsert`; `HasId`
- [x] `Crawler.absoluteUrl` → `absUrl("href")`
- [x] `Crawler.extractContents` / `internalExtractContents` (bug 3)
- [x] `LightNovelPub.kt` + Jackson from `implementation` (minSdk 26 no longer forced; still set)
- [x] `Theme.MaterialComponents` XML theme + `material` (views) + `appcompat` deps once
  on `ComponentActivity` (2026-09-18)

## Keep as-is

`copydown/` + `CopyDownTest` corpus; WebView-as-browser; serialized job runner with
active-novel priority; `Crawler.cleanContents` tag/CSS strip lists; `LogUtil.getTag()`.

## Order of work

1. [x] Bugs 1 + 2 (scheduler `finally`; scraper timeout + selector-wait + visible-WebView
   fallback). Everything else needs a fetch that can't hang or die silently.
2. [x] Capture novelphoenix fixtures with a desktop browser → write source + fixture
   tests (A). Delete LightNovelPub.
3. [x] Bug 3 (delete `extractContents`), bug 9, rest of the delete list (LightNovelPub + theme XML remain, tied to steps 2 / E).
4. [x] Data layer: B, bug 5, bug 4, bug 6 (2026-09-18).
5. [x] D (scheduler: preemption, reading priority, cancel-on-leave, resume) (2026-09-18).
6. [ ] E (M3 swap done 2026-09-18; screens on state + callbacks, settings page, read progress, errors surfaced), F (needs the settings
   page for its interval), C (SAF export).

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
