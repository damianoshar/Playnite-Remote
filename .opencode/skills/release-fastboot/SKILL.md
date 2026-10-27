---
name: release-fastboot
description: Reproducible Android release publishing playbook + bridge fork release flow. Use when publishing a fork or derivative of an open-source Android client (Playnite Remote / UnifiedGameLibrary), updating README, creating tags, setting GitHub Actions signing, and avoiding the pitfalls hit on first publish.
---

# Release Fastboot (Android + Bridge)

This Skill documents the exact end-to-end workflow we hit while
publishing **Playnite Remote** (Android) and the hardened
**Playnite Bridge** (fork of rollacode's plugin). It assumes a *fork
of an MIT upstream* with byte-identical API preserved (see
REWORK-NOTES.md).

## 1. Pre-flight checks
- **Git installed + identity configured:**
  `git config --global user.name "<name>"`
  `git config --global user.email "<email>"`
- **JDK 17** for CI; locally JDK 21 (ms-21) worked, JDK 23+ beyond AGP support.
- **Gradle**: this project has **no `gradlew` at root**; use
  system `gradle` in Actions (`gradle/gradle-build-action`) or add the
  wrapper first.
- **minSdk**: app ships **26**. Dropping to **24** (Neffos-class devices)
  is possible, but older phones need a signed build; use `minSdk=24` only
  for a dedicated target APK.
- **README format**: UTF-8, no non-ASCII surrogate artifacts from
  Windows terminals; gate mojibake by Python script (see Mistakes).

## 2. Local secrets (never committed)
```
C:\Users\harha\secrets\
  app-release.keystore    (generated locally, kept offline)
  app-release.password.txt (matching password – gitignored)
```
Generation (run once, securely):

```powershell
$bytes = [byte[]]::new(32); [System.Security.Cryptography.RNGCryptoServiceProvider]::new().GetBytes($bytes)
$pass = [Convert]::ToBase64String($bytes)
# simple enough for a 32+ random-alphanumeric password:
$pass = ([guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N"))
```

Store the value in:

```
C:\Users\harha\UnifiedGameLibrary\keystore.properties  (GITIGNORED!)
  storeFile=C:/Users/harha/secrets/app-release.keystore
  keyAlias=release
  keyPassword=<from file>
  storePassword=<from file>
```

Keystore signing must match the zip in `keystore.properties` – e.g.
a mismatch (`xaPHed…`) vs the real stored password causes
`KeytoolException: keystore password was incorrect`.

## 3. GitHub repository setup
1. **Create empty repo** on GitHub (e.g. `Playnite-Remote`).
2. Locally:
   ```powershell
   cd <project>
   git init
   git add .
   git commit -m "Initial release: Updated Game Library + Playnite bridge support"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
   For an additional file rename:
   ```powershell
   git remote set-url origin https://github.com/<you>/<new-repo>.git
   ```
3. **Add LICENSE** (MIT recommended; append original contributors if
   forked).

## 4. GitHub Secrets
Settings → Secrets and variables → Actions → Repository Secrets:

- `ANDROID_KEYSTORE_BASE64` — **base64 blob** of the `.keystore`
- `ANDROID_KEY_ALIAS`        — e.g. `release`
- `ANDROID_KEY_PASSWORD`     — from the same local file
- `ANDROID_STORE_PASSWORD`   — same as above

Generate clipboard copy:

```powershell
$ksb64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\...\app-release.keystore"))
Set-Clipboard -Value $ksb64
```

## 5. Workflow file (`.github/workflows/release.yml`)
Use `gradle/gradle-build-action` instead of `./gradlew`. Decode the
keystore into the **module directory** (AGP resolves paths relative to
`app/`):

```yaml
run: echo "$ANDROID_KEYSTORE_BASE64" | base64 --decode > app/app-release.keystore
keystore.properties path: storeFile=app-release.keystore (module scope)
gradle --no-daemon assembleRelease
```

Use `workflow_dispatch:` so a failed tag run can be re-triggered from
the UI without moving the tag.

### `build.gradle.kts` signing block
```kotlin
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
  keystoreProperties.load(keystorePropertiesFile.inputStream())
}
fun prop(key: String): String? =
  (keystoreProperties.getProperty(key) ?:
    System.getenv(key.uppercase().replace('.', '_')))
    ?.trim()?.takeIf { it.isNotEmpty() }
signingConfigs.create("release") { … (if storeFile present) signingConfig }
debug { applicationIdSuffix = ".debug" }
```

## 6. Publish sequence
1. Test locally: `gradle :app:assembleRelease` with env vars.
2. Verify: `apksigner verify -v …`.
3. Push tags:
   - `git tag v1.0`
   - `git push origin main --tags`
4. Monitor the Actions run; if it fails, inspect `signing` and `assembleRelease`.
5. Optionally produce a separate release APK for SDK 24 devices by
   making a minSdk variant.

## 7. Common mistakes (keep them in mind)
1. **`./gradlew: not found`** — no wrapper committed. Use system
   `gradle` in CI or add wrapper scripts & jars.
2. **Keystore path wrong** — Gradle resolves `file(storeFile)` inside
   the *module*; do **not** hide it in the repo root. Decode into
   `app/app-release.keystore`.
3. **Keystore + password mismatch** — a local file content (`xaPHed…`)
   shipped as temp; overwrite to the correct random value and verify
   with `keytool -list -v`.
4. **Key not read** — `storePassword` had a trailing newline from a
   naive `Set-Content`. Trim and compare with
   `Get-Content -Raw).Trim()`.
5. **Windows line-ending conversion (`CRLF` / `LF` warnings)** — Git
   complains but does not fail; 21 tracked files may remain
   un-indexable if run from PowerShell with odd quoting.
6. **README encoding / mojibake** — always write UTF-8; the terminal
   turns Greek letters due alternate decoding. Fix back to em dash
   programmatically (Python script replacements).
7. **Image input unavailable to model** — screenshot lines take
   manual user inserts; ensure the exact path exists and use
   `adb exec-out screencap -p` to keep binary intact.
8. **Kanso tool temporarily disappeared** — treat as flaky and re-run.
9. **CRLF resets build dirs under memory pressure** — eliminate
   *unused* processes (browsers) to recover pagefile (~3 GB after browser close).
10. **Unauthorised adb** — ensure device asks and user taps “Allow”
    with “Always allow from this computer”.
11. **Git rebase conflict & tag tying** — use `git config user.*` or
    `-c` flags locally. Move tags as needed.
12. **Bridge LICENSE + author attribution** — preserve upstream rollacode
    notice; add your copyright above it.

## 8. Bridge fork specifics
- **Clone, modify, push upstream**: use a real fork URL
  `https://github.com/<you>/playnite-bridge.git`.
- **Tag / release**: `git tag -a v1.6.0 -m …` + force push if needed;
  attach `.pext` and `SHA256SUMS.txt`, plus rework docs.
- **Don’t embed binaries** in git (use `.gitignore` and GitHub releases).
- **Attribution**: keep `LICENSE` + upstream repo link + differences
  in `REWORK-NOTES.md`.
- **Checksum** `sha256sum PlayniteBridge_v1.6.0.pext` in description, too.

## 9. Fastest support checklist
- **Bridge no longer reachable?** Show offline banner (app already
  handles).
- **PC unreachable?** Same; badge count and circuit breaker revisit.
- If **Restarting Playnite still happens**, stress-test fast-scroll during
  PC import, capture `extensions.log`, then check for
  `ERROR ... HttpApiServer`.

## 10. Deliverables you carry from start to end
1. `README.md` with download links.
2. `LICENSE` (MIT minimum)
3. `.github/workflows/release.yml`
4. `.gitignore` (`.keystore`, `.jks`, build dirs)
5. `app/build.gradle.kts` signing helper + `keystore.properties` template
6. New Kanso board? Useful status for parallel Playnite bridge on the
   PC side.
7. Screenshots, all in a clean 1×4 row (current app).
8. Signing key stored in a 2nd location (password manager + encrypted
   offline).

Cut any step once the user reviews and says “go”.