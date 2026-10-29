# Project Progress — Playnite Remote (Android) + PlayniteBridge 1.6.0

_Last updated: Oct 18, 2026_

Two workstreams, one goal: browse and launch a PC Playnite library from Android
without destabilizing Playnite.

| Component | Location | Status |
|---|---|---|
| Android app (client) | `UnifiedGameLibrary/` | Feature-complete, on-device verified |
| Bridge plugin rework | `bridge-rework/` (v1.6.0) | Built, 179/179 tests pass, `.pext` packaged |
| Local build/verify loop | this machine | Working (JDK 17 + portable Gradle 8.7 + adb) |

---

## 1. Android app — features implemented

### Library browsing
- Full library sync (paged `GET /api/games`), client-side search + sort.
- List and grid views with column-count slider (grid), persistent across restarts.
- Pagination: 50 / 100 / 200 / **All** (default), with pager control at the list foot.
- Scrolling-away header controls: search field, sort/show row, active-filter chips
  and grid slider live INSIDE the scrolling list (no dead reserved space).
- Alive cover art via authenticated Coil loader (Bearer token wired into `ImageLoader`).

### Filtering (client-side, works offline)
Every bridge-supported facet, multi-select checkboxes (OR within a facet, AND across):
source, genre, category, tag, feature, platform, completion status — plus toggles:
installed only, favorites only, show hidden, uncategorized, unplayed,
min/max hours. Active-filter chips with one-tap removal; badge count on the funnel
icon. Filtering runs against the cached snapshot so it works fully offline.

### Details & stats
- Full details dialog: developers/publishers, genres/tags, scores, links, release
  info, playtime, completion status, favorite star toggle (writes back to PC).
- Achievements (SuccessStory) and per-game activity sessions when the plugin
  data exists on the PC side.
- Stats tab: totals, hours played, by-source / by-genre / by-completion bars,
  recently played.

### Offline support
- `LibraryCache`: full library + stats snapshot cached on device; offline banner
  with last-synced time; app fully browsable without the PC.
- **Full offline mode** setting: downloads every game's details AND all cover art
  (awaited, with progress UI), keeps a last-full-sync timestamp.
- **Delete offline data** button (wipes cache + downloaded art).

### PC-friendly request behavior (gentleness design)
- Viewport-gated image loading: only items near the screen fire cover requests;
  fast flings don't trigger hundreds of downloads.
- "Keep all images" bypass setting + explicit cover warming instead.
- Artless-ID learning (`MissingArtStore` + runtime 404 capture): zero-art games
  never re-request.
- Circuit breaker: 8 non-404 image failures per minute → 60 s image pause
  (visible "paused" banner); `RetryOnConnectionFailure(false)`.
- Shared OkHttp clients; image lane capped at 2 concurrent requests; 75 ms pacing.

### Themes & UI
- Three themes: **Default / Midnight / Forest**, switchable at runtime, persisted.
- Bottom-nav autohide on scroll (hide on down, show on up; see fix log below).
- Launcher icons for all densities.

### Settings persistence
`UiPrefs` (DataStore): view mode, grid span, page size, theme, keep-all-images,
full-offline flag, last-full-sync. Survive restarts.

---

## 2. Layout & autohide fixes (Oct 16–18 debugging saga)

Three distinct root causes found — all verified with on-device screenshots:

### 2a. Phantom vacant band above the nav bar (104 px, every tab)
**Cause:** nested Scaffolds. Each tab screen had its own inner `Scaffold` inside
AppNav's outer `Scaffold`; all applied default `contentWindowInsets`, so the
navigationBars inset was counted twice (band = exactly system-nav height,
104 px measured). Same at top: inner `TopAppBar` re-padded the status inset
(~93 px dead gap under the clock).
**Fix:** outer `AppNav` Scaffold owns all system insets; inner Scaffolds +
TopAppBars in `LibraryScreen`, `StatsScreen`, `ServerScreen` use
`WindowInsets(0,0,0,0)`.
**Proof:** content now clips exactly at bar top / system-nav top in every state.

### 2b. Bar hidden on cold start (intermittent)
**Cause:** header insertion at index 0 (offline/paused banner appearing ~1.7 s
after launch) landed the viewport on `(index 1, new key)` — indistinguishable
from a down-scroll by index comparison alone. Traced via temporary `AUTOHIDE`
logcat logging.
**Fix:** `snapshotFlow`-based detector with (1) first-visible-key guard (same key
= decide by offset) and (2) progress gate: down-reads only count while
`isScrollInProgress` (insertions happen with no finger down; real scrolls always
have it). Up-reads ungated so page turns still reshow the bar.

### 2c. Bar flickering back mid-scroll / on unrelated recompositions
**Cause:** snapshot state (`upStreak`, last position) written inside a
`derivedStateOf` calculation → self-resubscription; image loads and spinners
flipped the result. Also the old 200 ms time debounce completed between frames
on this janky phone.
**Fix:** no writes in derived calculations; frame-count hysteresis (3 consecutive
up-reads) instead of timers; scroll stops change nothing.

### 2d. Defensive measure
- `onScrollVisibility` has **no default value** — a mismatched caller fails
  compilation loudly instead of silently shipping a bar that never hides.
- Stale-build discipline: same debug key everywhere, full-folder copies,
  Clean before rebuild (earlier confusing bugs traced to mixed file sets).

---

## 3. Bridge plugin rework — playnite-bridge 1.6.0

Drop-in replacement for 1.5.3. **API surface byte-identical** (routes, params,
JSON, status codes, auth) so the phone app works unchanged. Only internals changed,
to stop the flood-crash cycle (fast phone scrolling → hundreds of concurrent
requests in the Playnite process → *"Extension caused an unrecoverable error —
Restart Playnite"* dialog).

### Root causes fixed
1. **Unhandled escape in the error path (the crasher):** client disconnect →
   catch block wrote a 500 → threw again → escaped the ThreadPool to
   AppDomain-unhandled. Now: disconnects are Debug noise; the 500 writer is
   nested-guarded and cannot throw out.
2. **Unbounded concurrency:** `RequestGate` — semaphore (12) + single-flight
   for identical concurrent GETs (500 ms retention).
3. **Thread-unsafe DB enumeration:** `SdkGate` — single global lock for list reads.
4. **Repeated full-file cover reads:** `CoverService` — 256 MB in-memory cache,
   validated against last-write ticks on every hit (art changes apply live).
5. **Oversized payloads:** `GET /api/games/{id}/cover?width=256` (clamped 32–1024,
   JPEG q80; GIF/ICO/TGA always original).
6. **Repeat downloads:** strong `ETag` on covers + `304 Not Modified` handling.

### Verification
- `dotnet build` 0 warnings; **179/179 tests pass** (156 existing + new gate
  fixture that caught and fixed a real single-flight race).
- `PlayniteBridge_v1.6.0.pext` packaged correctly (2 entries: `extension.yaml` +
  `PlayniteBridge.dll`; verified against upstream v1.5.3 layout — bundling
  `Playnite.SDK.dll` breaks installation).
- Docs: `REWORK-NOTES.md`, `docs/api.md`, `skill.md`, `build-release.ps1`.

---

## 4. Build & debug tooling (this machine)

- Android build loop: Microsoft JDK 17 + portable Gradle 8.7 +
  `C:\Users\harha\AppData\Local\Temp\opencode\gradle-8.7`; ~30 s incremental
  builds; install + screenshot verification over adb.
- Pixel forensics toolkit (temp scripts): PNG band analyzer (stdlib decoder, no
  PIL), bottom/top crop tool, UI hierarchy dump parser.
- Detector tracing pattern: temporary `Log.d("AUTOHIDE", "idx/off/key/up/prog")`
  per collector emission, verified with `adb logcat -d -s AUTOHIDE`.
- **Skill:** `.opencode/skills/autohide-debug/SKILL.md` — generic runbook for
  scroll autohide debugging (bug catalog, forensics loop, verification matrix).

---

## 5. Verified on device (screenshot evidence)

- Cold start → bar visible, no band, no top gap (repeated runs).
- Scroll down → bar hides, content runs to the system buttons.
- Scroll up → bar returns, content flush against it, zero gap.
- Cold start offline (banner insert case) → bar stays visible.
- Stats + Server tabs → flush, no band, no gap.
- Covers loading row after row; breaker behavior visible in paused state.

---

## 6. Open items / follow-ups

1. **Post-1.6.0 crash check on the PC:** install the new `.pext`, scroll hard,
   check `extensions.log` for `ERROR ... HttpApiServer` (expect none;
   disconnects at most Debug). If the restart dialog still appears, grab
   `log.zip` + exact dialog text.
2. **Phone cover optimization (documented follow-up):** point cover URLs at
   `?width=512` and send `If-None-Match` from cached ETags → re-scrolls cost
   zero bytes even before disk cache is warm.
3. **Sync screen files to the other build machine** (or keep building here):
   `LibraryScreen.kt`, `StatsScreen.kt`, `ServerScreen.kt` carry the inset fixes.

---

## 7. Planned extras (backlog)

User-facing suggestions that fit the constraints (no heavy deps, PC-friendly,
client-side where possible, API-driven). Priority: quick wins first.

### Quick wins (reuse existing fields)
- **Cover ETag / 304 support** (already backlog): point cover URLs at `?width=512`
  and send `If-None-Match`; re-scrolls cost ~0 bytes.
- **Share game**: copy/share a one-line summary (title, source, playtime, score).
- **Favorite toggle from list**: star icon on each row that PUTs favorite
  (already wired in details).
- **Score-range filter**: min/max `userScore` slider in the filter sheet.
- **Sort by install size / play count**: fields already present in DTOs.
- **Haptic feedback** on favorite/launch success.

### Medium (small new screens / data)
- **Recent / "now playing" row**: horizontal list of games with `lastActivity`
  within N days.
- **Collections (client-side)**: named sets of game IDs in DataStore/UiPrefs,
  browsable offline, no server changes.
- **Game notes**: short per-game note stored locally (offline, sync-free).
- **Activity timeline**: group `GameActivity` sessions by day/week with totals.
- **Achievements progress bar** in details dialog (SuccessStory data already
  fetched).
- **Export/import settings**: JSON dump of UiPrefs (theme, filters, view) for
  backup/new device.
- **App widget**: home-screen widget showing last-played + playtime or
  library count.

### Larger / niche
- **Android TV / controller navigation**: focusable rows, D-pad scrolling.
- **Search within details**: find text in description/notes (client-side).
- **Multi-account / multi-server**: save more than one ServerConfig and switch
  quickly.
- **Adaptive icon / splash branding**: refresh launcher assets.

### Probably skip (conflicts with constraints)
- Push notifications, background sync daemon, queueing server, cloud backup.
