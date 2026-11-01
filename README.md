# PlexKillstreaks

PlexKillstreaks 2.0.0 is a lightweight killstreak plugin for Paper. One Java 21 bytecode JAR is used for both older and modern servers. PlaceholderAPI is optional.

It tracks current and lifetime highest streaks, saves them to SQLite, announces configurable milestones, and provides sounds, an action bar, progress indicators, milestone rewards, same-victim anti-farming, and a persisted leaderboard.

## Minecraft and Java compatibility

| Paper version | Server Java | Verification |
|---|---|---|
| 1.21.4 build 232 | 21 | Local startup, console commands, reload, SQLite initialization, shutdown |
| 26.1.1 build 29 | 25 | Local startup, console commands, reload, SQLite initialization, shutdown |
| 26.1.2 build 74 | 25 | Local startup, console commands, reload, SQLite initialization, shutdown |
| 26.2 build 134 | 25 | Local startup, console commands, reload, SQLite initialization, shutdown |
| 26.3 build 203 | 25 | Same checks, plus PlaceholderAPI 2.12.3 registration, reload and all eight null-player placeholders |

These are smoke tests, not complete live PvP certification. Combat, rewards, join/quit races and permissions have automated tests with mocked Bukkit boundaries; no real players were connected. Sound names were resolved during server startup, but audible sounds and rendered action bars were not checked in a client. Other builds, including the remaining 1.21.x releases, are unverified. Folia is not supported.

[Paper requires Java 25 for 26.1+](https://docs.papermc.io/paper/getting-started/). Java 21 remains sufficient for the plugin bytecode and the tested 1.21.4 server. The descriptor keeps `api-version: '1.21'`; no Java 25 variant is needed. Existing Bukkit sound names remain accepted; their deprecated lookup method is still available in the tested APIs.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/streak` | `plexkillstreaks.streak` | true |
| `/streaks` | Same as `/streak`; alias supports the same subcommands | true |
| `/streak top [page]` | `plexkillstreaks.top` | true |
| `/streakreload` | `plexkillstreaks.reload` | op |

The no-argument streak command is player-only. Console can use the leaderboard and reload commands. The top permission is independent of the streak permission. Pages start at 1; a positive page beyond the available range displays the last page. Invalid pages return usage text. Tab completion suggests `top`.

## Installation and upgrading

1. Stop the server and back up `plugins/PlexKillstreaks/`, including `data.db` and `config.yml`.
2. Install the appropriate server Java version, replace the old plugin JAR with `PlexKillstreaks-2.0.0.jar`, and start Paper. Keep only one PlexKillstreaks JAR installed.
3. Keep your existing database and configuration. Customize `plugins/PlexKillstreaks/config.yml` as needed and use `/streakreload` after editing it.

Existing 1.0.x and 1.1.0 configuration files and SQLite databases remain usable. Do not delete either when upgrading. No new settings are required. Existing milestones without rewards continue working, and configuration files are not overwritten to insert keys. All eight placeholders, commands, alias, and permission names remain supported. No gameplay or data-format breaking change is intended.

## Milestone rewards

```yaml
settings:
  milestone-rewards:
    enabled: true

milestones:
  "5":
    enabled: true
    message: "<prefix> <white>{player}</white> <gray>is on a <red>5 kill streak</red>!"
    rewards:
      - "console:give {player} golden_apple 1"
      - "player:help"
```

Use `console:<command>` or `player:<command>`. A command without a type prefix runs as console. Use an explicit `console:` prefix for namespaced commands. Commands may contain `{player}`, `{uuid}`, `{streak}`, and `{highest}`. No economy plugin is required or assumed.

Rewards execute on the server thread only when a counted kill reaches that exact enabled milestone. They are independent of the milestone-announcements toggle. Turning rewards off keeps normal announcements enabled. Invalid definitions are skipped during validation; a failed command does not stop later rewards. Ignored farming kills, excluded worlds, and unavailable streak data cannot earn rewards. Existing sound and announcement settings remain available.

## Anti-farming

```yaml
anti-farming:
  enabled: true
  window-seconds: 180
  max-counted-kills-per-victim: 2
  notify-killer: true

messages:
  anti-farming: "<prefix> <gray>This kill did not count because you have already killed this player too many times recently.</gray>"
```

With the default settings, a player can receive streak credit for killing the same victim twice within three minutes. Further kills against that victim are ignored until the original three-minute window expires. Ignored kills do not extend the window. Each directional killer/victim pair is independent. Allowed kills can trigger normal milestones and rewards; rejected kills do not increase the killer's current/highest streak or trigger an action-bar increase, milestone, or reward. The victim's streak still resets normally.

History exists only in memory, survives individual quits, expires automatically, and clears when protection is disabled or the window/threshold changes on reload. Zero window permits repeated kills without retaining pairs. Window duration accepts 0–31,536,000 seconds (one year); negative, malformed, or larger values use 180 seconds. The counted-kill limit must be a positive integer; missing or invalid limits use 2. A limit of 1 permits the first kill and rejects repeats until expiry. An internal 100,000-pair capacity bounds memory; when full, new pairs are temporarily rejected until entries expire, with a throttled console warning. Valid protected pairs are never evicted to admit new ones.

## Leaderboard

```yaml
leaderboard:
  page-size: 10

messages:
  leaderboard:
    header: "<dark_gray>━━━━━━━━ <red><bold>TOP KILLSTREAKS</bold></red> <dark_gray>━━━━━━━━</dark_gray>"
    entry: "<red>#{rank}</red> <white>{player}</white> <dark_gray>»</dark_gray> <gold>{highest}</gold>"
    footer: "<gray>Page {page}/{pages}</gray>"
    empty: "<prefix> <gray>No leaderboard entries were found.</gray>"
    loading: "<prefix> <gray>Loading leaderboard...</gray>"
    pending: "<prefix> <gray>Your leaderboard is already loading.</gray>"
    busy: "<prefix> <gray>The leaderboard is busy. Please try again shortly.</gray>"
    error: "<prefix> <red>Unable to load the leaderboard. Please try again.</red>"
```

`/streak top [page]` ranks persisted lifetime highest streaks, including offline players. Queries use the existing asynchronous database executor; messages return on the server thread. Positive scores sort by highest descending and UUID ascending for ties. Page size accepts 1–100 and otherwise falls back to 10. Entry/header/footer templates accept `{rank}`, `{player}`, `{highest}`, `{page}`, `{pages}`, and `<prefix>`; player-specific fields are empty in header/footer. Database errors display the error message rather than the empty message. At most 16 database queries can be outstanding, with one request awaiting delivery per sender; duplicate or saturated requests receive pending/busy feedback instead of growing the database queue.

## Disabled worlds

```yaml
settings:
  disabled-worlds:
    - lobby
    - afk
```

Matching is case-insensitive. A death in an excluded world leaves both players' streaks untouched, does not record anti-farming history, and sends no tracking messages or rewards. The default empty list enables all worlds.

## PlaceholderAPI

| Placeholder | Value |
|---|---|
| `%plexkillstreaks_current%` | Current streak |
| `%plexkillstreaks_highest%` | Lifetime highest streak |
| `%plexkillstreaks_next_milestone%` | Next enabled milestone, or 0 |
| `%plexkillstreaks_progress%` | Configured progress bar |
| `%plexkillstreaks_is_active%` | Whether current streak is positive |
| `%plexkillstreaks_loaded%` | Whether player streak data is available |
| `%plexkillstreaks_kills_to_next%` | Kills remaining until the next enabled milestone, or 0 |
| `%plexkillstreaks_last_milestone%` | Highest enabled milestone already reached, or 0 |

Resolution uses memory and validated configuration only. Unknown identifiers return null. For null or unavailable players, boolean placeholders return false and the others return 0, preserving 1.0.1 behavior. There is no database-backed rank placeholder.

PlaceholderAPI 2.12.3 was tested on Paper 26.3. The internal expansion survives `/papi reload` and unregisters when PlexKillstreaks shuts down. Without PlaceholderAPI installed, the plugin loads normally. No economy plugin or other mandatory plugin dependency is introduced.

## Configuration and persistence

Branding, messages, milestones, announcement toggles, sounds, action bar, and streak-menu settings remain configurable. `/streakreload` validates the new settings, milestone keys, rewards, sounds, disabled-world entries, page size, and progress length. Invalid entries produce concise validation warnings and safe fallbacks. Progress length accepts 1–100, otherwise 10.

Malformed YAML or an unreadable configuration file rejects a reload and keeps the active configuration and settings. Correct the file and run `/streakreload` again.

The database remains `data.db` with the existing `player_streaks` table. Startup checks its columns and adds nullable `last_known_name TEXT` only if absent. It never drops or recreates the table. Existing streak values and extra columns are preserved. Names update after successful load and on save. Legacy unnamed rows display their stored UUID until the player next loads; there are no network/profile lookups.

Schema initialization and validation are transactional. Incompatible tables roll back without partial migration; malformed database files are not deleted. NULL, noninteger, negative, out-of-range or inconsistent player streaks fail their load and remain unavailable instead of being converted to zero and saved back. Copy-based migration tests cover the prior three-column and named-player schemas, including extra columns.

Writes remain asynchronous and ordered. Failed writes are logged once and fail their requested futures; subsequent queued saves can continue. Failed loads stay unavailable and cannot overwrite saved data with a fabricated zero state. Delayed async load callbacks cannot overwrite state established by a successful fallback.

Known limitation: a kill/death before player data finishes loading may use the retained synchronous database fallback. Normal loaded gameplay, ordinary persistence, placeholders, leaderboard queries, and `/streak` do not perform blocking database reads. A fully asynchronous early-event loading design is deferred to a later release. Shutdown waits for database work to drain before closing SQLite.

## Building and tests

Use the included Gradle 9.7.1 wrapper, with Java 21 installed as the compilation/test toolchain. Gradle itself can run on Java 21 or 25; compilation explicitly targets Java 21:

```powershell
.\gradlew.bat clean test build
```

On Linux/macOS use `./gradlew clean test build`. Output is `build/libs/PlexKillstreaks-2.0.0.jar`. Gradle expands the project version into packaged `plugin.yml`. Paper API is pinned to `1.21.4-R0.1-20250925.065901-231`, and PlaceholderAPI to `2.12.3`; both are compile-only production dependencies. The older API keeps the Java 21 compilation baseline without forcing Java 25 bytecode. The wrapper distribution has SHA-256 verification. JUnit, Mockito, SQLite JDBC, and test API runtime dependencies are not bundled; Paper supplies the runtime SQLite driver.

Development validation reported 90 automated tests covering real SQLite migration copies, malformed stored values, pagination and failures, loading races, save recovery, anti-farming, reward dispatch, placeholders, command permissions, malformed reloads and legacy-config lifecycle. **The test source files are not currently included in the public repository, so this checkout cannot reproduce that reported suite.** Server smoke tests are listed separately above. No performance benchmark or real-player PvP test was run.

## Project and license

**Plex Studios**, maintained by **ApplexDev**. Source and issues: [PlexStudios/PlexKillstreaks](https://github.com/PlexStudios/PlexKillstreaks). Licensed under [MIT](LICENSE).
