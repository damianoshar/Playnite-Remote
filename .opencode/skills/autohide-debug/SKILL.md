---
name: autohide-debug
description: Diagnose and fix scroll-driven auto-hide UI bugs (bottom/top bar show/hide on scroll, phantom inset bands) in any Jetpack Compose app using adb-driven screenshot forensics, logcat tracing, and pixel-band analysis. Use when a navigation bar won't hide, hides on launch, reappears uninvited, or a blank strip sits above it.
---

# Scroll Auto-Hide Debugging (Jetpack Compose)

## Target behavior
- Scroll down → bar hides on the first downward read.
- Scroll up → bar shows after N consecutive upward reads (N=3 filters single-frame
  index-rollover noise during flings).
- Scroll stops change nothing; the bar reacts only to direction.
- Cold start → bar visible. Content flush against the bar or system nav, no phantom bands.

## Reference pattern
A `LaunchedEffect(scrollState, ...)` collecting
`snapshotFlow { Triple(firstVisibleItemIndex, firstVisibleItemScrollOffset, visibleItemsInfo.firstOrNull()?.key) }`
with `.distinctUntilChanged()`. Exactly ONE `Scaffold` in the hierarchy owns system
insets (usually the outermost); every nested `Scaffold` uses
`contentWindowInsets = WindowInsets(0,0,0,0)` and nested `TopAppBar`s use
`windowInsets = WindowInsets(0,0,0,0)`.

## Bug catalog (check in this order)

### 1. Phantom band above the bar — nested Scaffold double inset
Any screen-level `Scaffold` nested inside an outer `Scaffold`, all with default
`contentWindowInsets`, stacks the navigationBars inset once per level. Signature: a
blank strip exactly nav-bar sized (~system nav height), present on every tab, in both
bar-visible and bar-hidden states. Confirm by pixel measurement (see forensics below):
content clip = screen bottom − system nav − bar (if visible) − K×104px, where each
extra 104px is one redundant inset level. Same double-count happens at the top when a
`TopAppBar` re-pads the status inset (dead gap under the clock). Fix: zero insets on
all inner Scaffolds and their app bars; outer Scaffold keeps defaults.

### 2. Bar hidden at launch / hides without touch — content shift misread as scroll
Header items inserting at index 0 (banners, spinners, loading placeholders) land the
viewport on `(higher index, new key)` with no finger down. Pure index comparison reads
this as scrolling down. Two guards:
- **Key guard**: same first-visible key as last read → not a scroll, decide by offset.
- **Progress gate**: down-reads only count while `isScrollInProgress` is true.
  Insertions land idle; real scrolls always have it. Keep up-reads ungated so
  programmatic `scrollToItem(0)` (e.g. page turns) still reshows the bar.
- On any ignore path, still absorb the new baseline (update last index/offset/key).

### 3. Bar reappears uninvited / flickers — snapshot writes inside `derivedStateOf`
Writing streak/last-position state inside a `derivedStateOf` calculation resubscribes
it to itself: unrelated recompositions (image loads, timers) re-run the calc and flip
the result with zero movement. Rule: direction bookkeeping lives in `snapshotFlow`
collector locals, never in derived calculations. Also avoid time-based debounce for
hide/show on slow devices — a millisecond delay can complete between frames and pop
the bar back mid-scroll; use frame-count hysteresis instead.

### 4. Stale-build false positives
If on-device behavior contradicts freshly read source, suspect the APK, not the code:
same debug key on all build machines, full-project copies (never single files),
`Build → Clean` after copies. A signature-level tripwire helps: give the visibility
callback no default value so a mismatched caller fails compilation instead of silently
never hiding.

## Forensics loop (adb + pixel analysis)
```powershell
# Build (example: portable Gradle + JDK 17; adapt JAVA_HOME/AGP to the project)
gradle assembleDebug --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop <package>
adb shell am start -n <package>/.MainActivity
Start-Sleep -Seconds 5
adb shell screencap -p /sdcard/x.png; adb pull /sdcard/x.png <local>
```
- Mid-scroll capture: start `adb shell input swipe ...` in a background job,
  screenshot ~1.2s in, then wait for the job.
- Band analysis: decode the PNG (stdlib `struct`/`zlib` suffices, no PIL needed) and
  print uniform-color row bands for the bottom ~600px. Typical light-theme markers:
  app surface background = phantom band; distinct container color = nav bar;
  near-white + icons = system nav. Expected: content ends exactly where the bar
  (~80dp) or system nav begins — any extra surface-colored strip is a redundant inset.
- Detector tracing: temporary `Log.d(TAG, ...)` per collector emission
  (`idx/off/key/up/inProgress`), `adb logcat -d -s TAG`. Launch must show only
  `up=true` emissions; a `down` with `inProgress=false` is the phantom class from
  bug 2. Remove logging before the final build.
- Window sanity: `adb shell dumpsys window windows` → app frame must be full-screen
  (edge-to-edge); `adb shell wm density` gives the px↔dp scale for measurements.

## Verification matrix (screenshot each)
1. Cold start → bar visible, no band, no top gap.
2. Scroll down → bar hidden, content flush to system nav.
3. Scroll up → bar visible, content flush against it.
4. Every tab/screen with its own Scaffold → no band.
