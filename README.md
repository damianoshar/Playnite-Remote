# Playnite Android Client

Remote client for your PC game library managed by Playnite. Playnite itself handles every store login, so this app is a thin REST API client for the [`playnite-bridge`](https://github.com/rollacode/playnite-bridge) plugin (MIT).

## Architecture

PC runs Playnite, the bridge plugin (`127.0.0.1:19821`) and your library. Phone speaks the REST API over LAN / Tailscale.

```
PC:   Playnite + library plugins + playnite-bridge :19821  (Bearer token)
           - -  LAN / Tailscale - -
Phone: this app (library, search, covers, details, launch-on-PC)
```

## Screenshot (current emulator build)

![Empty/offline state screen from the current emulator build](docs/screenshots/Screenshot-main-app-empty-offline-state.png)

The screenshot above shows the first-run “No PC server configured” screen. With a PC configured, the screen shows your full unread/un-filtered library.

**Four captured emulator states:**

| Library (offline first-run) | Stats (server-connected) |
|---|---|
| ![](docs/screenshots/05_actual_offline.png) | ![](docs/screenshots/02_stats.png) |
| ![](docs/screenshots/03_dashboard_listing.png) | ![](docs/screenshots/04_filters.png) |


1. Install Playnite and link every store; let it import the library.
2. Install `PlayniteBridge_vX.X.X.pext` from the playnite-bridge GitHub releases (drag onto Playnite -> restart).
3. Playnite -> Settings -> Plugins -> Playnite Bridge -> copy the bearer token.
4. Allow TCP 19821 through Windows Firewall. Get your LAN IP via `ipconfig`.
5. For remote access, use Tailscale on both devices.

## App setup

1. Open this folder in Android Studio, sync Gradle, run the app (minSdk 26).
2. Open the **Server** tab, enter `http://<PC-IP>:19821` + token, tap **Save & Connect**.
3. The **Library** tab loads everything. Sources are fetched live, so new stores appear automatically.

## Features

- List and grid views with adjustable column slider (2-5).
- Paged display: 50 / 100 / 200 / All, with a pager.
- Filters mirror the bridge API: text, source, genre, category, tag, feature, platform, completion status, installed, favorite, hidden, uncategorized, unplayed, min/max hours.
- Sorting: name, playtime, last played, score, source.
- Game details dialog: description, developers/publishers, score, release date, links, playtime, completion, and favorite toggle (writes back to PC). Optional achievements and activity views.
- Stats tab: totals, hours, by source/genre/status bars, recently played.
- Themes: Default (system), Midnight, Forest; preferences persist.

## API mapping

| Endpoint | Used for |
|---|---|
| `GET /api/games?limit=&offset=` (paged) | Full library, search/sort client-side |
| `GET /api/sources` | Store filter chips |
| `GET /api/stats` | Library header count + Stats tab |
| `GET /api/games/{id}` | Details, scoring, links |
| `GET /api/games/{id}/cover?type=cover` or `background` | Artwork (authenticated Coil loader) |
| `POST /api/games/{id}/launch` | "Launch on PC" button |
| `PUT /api/games/{id}` | Favorite star toggle |
| `GET /api/games/{id}/achievements` | SuccessStory progress + list (when plugin data exists) |
| `GET /api/games/{id}/activity` | GameActivity sessions (when plugin data exists) |

## Offline support

Every successful sync snapshots the library (`games`, sources, stats) and covers into internal storage (Coil disk cache).

- App opens instantly from the snapshot, then refreshes in the background.
- PC unreachable? An amber "Offline - library from ..." banner appears; browsing/search/sort still work.
- Game details are cached lazily when opened; uncached details need the PC.
- Launch-on-PC always needs a live connection.
- Disconnecting keeps the snapshot browsable; reconnecting refreshes it.

## Full offline mode

The **Full offline mode** switch downloads everything once (game info plus covers) into a larger disk cache. Progress is shown per phase. Backgrounds stay on-demand but cache whenever viewed. Re-run to refresh.

Use **Delete offline data** afterward to reclaim storage (with confirmation).

## PC-friendly loading

Requests are intentionally gentle:

- Image loading is viewport-gated (only near-visible rows fetch).
- Games without art (404s) are learned and never re-requested.
- A separate 2-at-a-time image lane and ~75 ms pacing prevent flooding.
- A circuit breaker pauses image loading after repeated failures and automatically resumes shortly.

## Security

The token has full control over the PC (launch, install/uninstall, delete, remote eval). Treat it like a password. Use only on a trusted LAN or Tailscale. Never port-forward 19821 to the internet. Rotate the token in plugin settings if it ever leaks.

Cleartext HTTP is enabled because playnite-bridge serves plain HTTP on LAN. If you reverse-proxy it behind HTTPS, change the address to `https://...`.

## Credits

- [Playnite](https://github.com/JosefNemec/Playnite) (MIT)
- [playnite-bridge](https://github.com/rollacode/playnite-bridge) (MIT)

This app is public on GitHub (repo `Playnite-Android-Client`).