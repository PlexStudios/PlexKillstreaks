# PlexKillstreaks

PlexKillstreaks 1.1.0 is a lightweight killstreak plugin for Paper 1.21+ and Java 21. PlaceholderAPI is optional.

It tracks current and lifetime highest streaks, saves them to SQLite, announces configurable milestones, and provides sounds, an action bar, progress indicators, milestone rewards, same-victim anti-farming, and a persisted leaderboard.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/streak` | `plexkillstreaks.streak` | true |
| `/streaks` | Same as `/streak`; alias supports the same subcommands | true |
| `/streak top [page]` | `plexkillstreaks.top` | true |
| `/streakreload` | `plexkillstreaks.reload` | op |

The no-argument streak command is player-only. Console can use the leaderboard and reload commands. The top permission is independent of the streak permission. Pages start at 1; a positive page beyond the available range displays the last page. Invalid pages return usage text. Tab completion suggests `top`.

## Installation and upgrading

1. Put the plugin JAR in the server's `plugins` directory and start Paper.
2. Customize `plugins/PlexKillstreaks/config.yml`.
3. Use `/streakreload` after editing configuration.

Existing 1.0.x configuration files and SQLite databases remain usable. Do not delete either when upgrading. New settings have safe defaults even if their sections are absent; existing milestones without rewards continue working. The plugin does not overwrite old configuration files to insert new keys. All six original placeholders, commands, alias, and permission names remain supported.

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

## Configuration and persistence

Branding, messages, milestones, announcement toggles, sounds, action bar, and streak-menu settings remain configurable. `/streakreload` validates the new settings, milestone keys, rewards, sounds, disabled-world entries, page size, and progress length. Invalid entries produce concise validation warnings and safe fallbacks. Progress length accepts 1–100, otherwise 10.

The database remains `data.db` with the existing `player_streaks` table. Startup checks its columns and adds nullable `last_known_name TEXT` only if absent. It never drops or recreates the table. Existing streak values and extra columns are preserved. Names update after successful load and on save. Legacy unnamed rows display their stored UUID until the player next loads; there are no network/profile lookups.

Writes remain asynchronous and ordered. Failed writes are logged once and fail their requested futures; subsequent queued saves can continue. Failed loads stay unavailable and cannot overwrite saved data with a fabricated zero state. Delayed async load callbacks cannot overwrite state established by a successful fallback.

Known limitation: a kill/death before player data finishes loading may use the retained synchronous database fallback. Normal loaded gameplay, ordinary persistence, placeholders, leaderboard queries, and `/streak` do not perform blocking database reads. A fully asynchronous early-event loading design is deferred to a later release. Shutdown waits for database work to drain before closing SQLite.

## Building and tests

Use Java 21 and the included Gradle wrapper:

```powershell
.\gradlew.bat clean test build
```

On Linux/macOS use `./gradlew clean test build`. Output is `build/libs/PlexKillstreaks-1.1.0.jar`. Gradle expands the project version into packaged `plugin.yml`. Paper and PlaceholderAPI are compile-only production dependencies. JUnit, Mockito, SQLite JDBC, and test API runtime dependencies are not bundled.

Tests cover the real SQLite migration, pagination and failures, loading races, save recovery, anti-farming, reward dispatch, placeholders, command permissions, and legacy-config lifecycle through mocked Bukkit boundaries. This is not a live Paper-server test. See [CHANGELOG.md](CHANGELOG.md) for recorded release validation.

## Project and license

Maintained by **Plex Studios**. Source and issues: [PlexStudios/PlexKillstreaks](https://github.com/PlexStudios/PlexKillstreaks). Licensed under [MIT](LICENSE).
