# Playnite Remote (Android client for playnite-bridge)

Browse your entire PC game library -- Steam, Epic, GOG, Ubisoft, EA, and everything else Playnite tracks -- from your Android phone.

**Architecture:** Playnite runs on your PC and already handles every store login (verified against its own plugin source). The open-source [`playnite-bridge`](https://github.com/rollacode/playnite-bridge) plugin (MIT) exposes the library as a REST API on port 19821. This app is a thin client for that API: one server address + one token, no per-store logins.

```
PC:  Playnite + library plugins + playnite-bridge :19821 (Bearer token)
            +-- LAN / Tailscale --+
Phone: this app (library, search, covers, details, launch-on-PC)
```


## Quick downloads

- **Android client APK** (release v1.0.3): https://github.com/damianoshar/Playnite-Remote/releases/download/v1.0.3/app-release.apk
- **Hardened bridge plugin (recommended)**: https://github.com/damianoshar/playnite-bridge/releases/download/v1.6.0/PlayniteBridge_v1.6.0.pext
- **Upstream bridge plugin (fallback)**: https://github.com/rollacode/playnite-bridge/releases/download/v1.5.3/PlayniteBridge_v1.5.3.pext

## PC setup

1. Install Playnite, link your stores, let it import your library.
2. Download `PlayniteBridge_vX.X.X.pext` from the playnite-bridge GitHub releases, drag it onto Playnite, restart. The API starts on port **19821**.
3. In Playnite: **Settings -> Plugins -> Playnite Bridge** -> copy the bearer token.
4. Allow **TCP 19821** through Windows Firewall. Find your PC's LAN IP with `ipconfig`.
5. For access outside home: use Tailscale on both devices, then use the Tailscale IP.



## App setup

1. Install the APK from the [Releases](https://github.com/damianoshar/Playnite-Remote/releases) page.
2. Open the **Server** tab -> enter `http://<PC-IP>:19821` + token -> **Save & Connect**.
3. The **Library** tab loads everything. Sources come live from `GET /api/sources`, so new stores appear automatically.



## Screenshots

**Four emulator captures in a single row:**

| Dashboard Grid | Dashboard List | Stats | Filters |
|---|---|---|---|
| ![Dashboard listing](docs/screenshots/01-grid-dashboard.png) | ![Actual offline](docs/screenshots/03_dashboard_listing.png) | ![Settings connection](docs/screenshots/02_stats.png) | ![Filters view](docs/screenshots/04_filters.png) |


### Connection screenshots (side-by-side)

| Empty/offline | Settings (connection form) | Connection steps |
|---|---|---|
| ![Empty/offline state](docs/screenshots/05_actual_offline.png) | ![Settings connection form](docs/screenshots/06_settings_connection.png) | 1. Install Playnite on the PC. 2. Install the PlayniteBridge `.pext` and copy its token. 3. On phone: Server tab `http://<PC-IP>:19821` + token -> **Save & Connect**. |



## Offline support

Every successful sync snapshots the library (`games`, sources, stats) to internal storage, and covers are kept in a 250 MB Coil disk cache. Behavior:

- App opens instantly from the snapshot, then refreshes in the background.
- PC unreachable + snapshot present -> amber "Offline -- library from "" banner, full browsing/search/sort still works.
- Game details are cached lazily when opened; uncached details need the PC.
- Launch-on-PC always needs a live connection.
- Disconnecting keeps the snapshot browsable; reconnecting refreshes it.



## Full offline mode (Server tab)

For flights and dead zones: the **Full offline mode** switch downloads
everything up front -- full info for every game, then all cover art into a
1 GB disk cache. Progress shows per phase (`Game info n/total`, `Covers n/total`)
and only finishes when every cover is actually downloaded; cancel keeps
partial progress, and re-running refills the gaps. Backgrounds
stay on-demand (they're too big to prefetch for thousands of games) but are
cached whenever viewed. Re-run **Refresh offline data** after big library
changes.

Games with no art on the PC are learned (`hasCover=false` in full details,
plus any live 404 while scrolling) and never requested again -- neither while
scrolling nor during cover prefetch. Those repeat 404s were the flood behind
the bridge errors. Image requests are also viewport-gated: only rows near
the visible screen may load; anything scrolled past never fires.

**Delete offline data** (same card, red button, with confirmation) wipes the
snapshot, cached details, downloaded covers and the artless set, reporting
how much space was freed. The server connection is kept.

Requests are deliberately gentle, in layers: viewport-gated image loading
(only near-visible rows fetch), artless games never requested, a separate
2-at-a-time image lane that can't crowd out API calls, no silent retries,
~75 ms pacing on bulk downloads -- and a circuit breaker: 8 failing (non-404)
image requests in a minute pauses all image loading for a minute.



## Security

The token grants **full control**: launch, install, uninstall, delete, and remote C# eval (`/api/eval`). Treat it like a password. Use only on trusted LAN or Tailscale, and rotate it in plugin settings if it ever leaks. Never port-forward 19821 to the internet.

Cleartext HTTP is enabled (`usesCleartextTraffic="true"`) because the bridge serves plain HTTP on LAN. If you reverse-proxy it behind HTTPS, switch the address to `https://`.



## Hardened bridge (optional)

If you want the Playnite-side stability improvements, install the drop-in replacement bridge server:

- Release: https://github.com/damianoshar/playnite-bridge/releases/tag/v1.6.0
- Features: request gate (max 12 concurrent + single-flight), global SDK gate, 256MB cover cache, strong ETags, 304 handling, hardened HTTP error path.
- API byte-compatible with upstream 1.5.3 (same routes/params/status code), so this client works with either.




## Credits

- [Playnite](https://github.com/JosefNemec/Playnite) (MIT) -- library aggregation on PC.
- [playnite-bridge](https://github.com/rollacode/playnite-bridge) (MIT) -- REST API plugin this client consumes.


