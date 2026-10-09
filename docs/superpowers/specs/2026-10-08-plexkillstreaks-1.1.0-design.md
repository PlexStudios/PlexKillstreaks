# PlexKillstreaks 1.1.0 targeted design

Status: implementation approach approved in chat; this document and its implementation plan await review. No implementation or validation results are claimed.

## Purpose and boundaries

Deliver the requested 1.1.0 rewards, anti-farming, leaderboard, disabled worlds, placeholders, validation, persistence fixes, tests, and documentation. Extend the current plugin, managers, listeners, and commands. Keep Java 21, Paper 1.21+, optional PlaceholderAPI, existing package names, SQLite data, command aliases, permissions, and placeholder behavior. No release, push, network profile lookup, production test doubles, unnecessary runtime dependencies, or added Java line comments.

## Loading requirement requiring resolution

The approved targeted approach retains `ensureLoaded` and its synchronous fallback for 1.1.0. The current death listener invokes it for a killer and victim whose data is unavailable. The latest requirement also says kill handling must be free of blocking database work. These cannot both remain true with the existing call flow.

The proposed plan retains this exceptional synchronous fallback, explicitly documents it, and introduces no additional synchronous gameplay queries. This follows the original specification's allowance to defer a loading redesign. This exception needs explicit confirmation before implementation. If all death handling must instead be strictly nonblocking, revise the design to defer ordered death operations until loading finishes; do not silently discard early kills/deaths. That alternative is outside this targeted plan.

## Configuration and milestones

Use one small validated settings snapshot, rebuilt at startup and reload, without a configuration framework. Parse positive enabled milestones into a sorted map. Retain original announcement strings and sound behavior. Invalid milestone keys, reward definitions, sounds, scalar/list types, cooldowns, page sizes, and progress lengths produce concise validation warnings, not repeated gameplay warnings. Old configs use explicit defaults; never replace their files.

Defaults: rewards enabled; disabled worlds empty; anti-farming enabled with 300 seconds and notifications enabled; leaderboard page size 10. Negative or malformed cooldowns fall back to 300, zero disables pair blocking. Page size accepts 1–100, otherwise 10. Progress length accepts 1–100, otherwise 10. These upper bounds limit pathological output allocation. Disabled worlds use case-insensitive matching with Locale.ROOT; invalid entries are ignored. Sound names are resolved once per reload.

Milestones without rewards remain valid. Next milestone preserves the internal -1 sentinel; new kills-to-next and last-milestone helpers return zero when appropriate. Enabled milestones alone participate. Rewards are independent of the announcement toggle, run only on an exact counted transition, and dispatch on the server thread. Commands support console, player, and unprefixed console dispatch, with player, UUID, streak, and highest replacements. Invalid types, empty commands, false dispatch results, and exceptions do not stop later rewards. Never replay rewards from load or save callbacks.

## Death and anti-farming flow

Return before any tracking operation when the victim's death world is disabled. Otherwise, validate killer data availability, apply same-pair protection, and increment a valid killer exactly once. Only that successful increment produces action bar, milestone, or rewards. Always process the victim's normal reset independently of rejected killer credit. Preserve the existing streak-ended announcement threshold.

Use a dedicated in-memory AntiFarmTracker with an injected monotonic clock for deterministic tests. Store last accepted pair times. Ignored attempts never change them. Expired records are pruned periodically, the repeating task is cancelled at shutdown, and the tracker is cleared when disabled or its cooldown changes. To bound active records without evicting protected pairs, use an internal capacity of 100,000 pairs; after pruning, reject an untracked pair if full and log a throttled warning. This exceptional fail-closed behavior preserves protection rather than silently allowing farming. Existing pairs still expire normally. No persistence or quit-time clearing of valid pair history.

## SQLite and save lifecycle

Keep the existing connection and single database executor. Add nullable `last_known_name TEXT` only after inspecting PRAGMA table_info. Never drop or recreate player_streaks. Capture player names on the server thread and include them in saves, preserving a known name when an older save overload lacks one. Populate names after successful load even if no streak changes. Legacy unnamed leaderboard rows display their stored UUID, without profile lookup. Exclude highest_streak <= 0; order by highest_streak DESC, uuid ASC.

DatabaseManager throws SQL failures, including unavailable connection errors. StreakManager exposes the requested save's exceptional future and logs each failed write once. Recover the previous chain before scheduling the next write, so one failed write does not poison future writes. Preserve per-player ordering and remove only the matching completed chain. Shutdown flushes loaded online state, stops new work, drains executor work, and closes SQLite after executor termination; avoid closing a connection still in use.

Failed loads leave the player unavailable with no writable synthetic state. Async results verify session identity and whether a fallback already loaded that session before applying. A failed delayed load must likewise not clear successful fallback state. Stale reconnect results are ignored. Catch expected SQL/executor failures rather than treating every Throwable as recoverable. Keep synchronization away from future waits.

## Leaderboard and commands

Extend `/streak` and `/streaks` with `top [page]`, tab completion, independent plexkillstreaks.top permission (default true), and console support. Preserve player-only no-argument behavior and plexkillstreaks.streak checks. Move the root plugin.yml permission gate into the executor so top permission works independently of streak permission.

Validate positive integer pages before scheduling. Query count and page on the existing executor, preferably within one read transaction for consistent pagination. Clamp a positive out-of-range page to the last available page. Return loading, empty, formatted success, or a concise failure message on the server thread; failures must not masquerade as empty results. Use long arithmetic for ranks/offsets. Ignore disconnected player recipients and callbacks after shutdown. No database work in placeholders.

## Packaging, documentation, and verification

Gradle owns version 1.1.0 and expands only plugin.yml. Retain compileOnly Paper/PlaceholderAPI; add only test dependencies needed for JUnit, Mockito, and real SQLite integration. Inspect the public 1.0.1 release before asserting its fixes are preserved; the local checkout currently has only a 1.0.0 tag and an old remote URL. Do not invent unavailable release history.

Update README, default config, plugin metadata with Plex Studios branding, and CHANGELOG. Preserve any release history actually found. Verify old config startup and reload, migrations, lifecycle races, error recovery, reward routing, and command compatibility using focused tests. Run the full Gradle clean test build, aggregate exact XML counts, inspect packaged resources and dependency contents, run git diff --check, and inspect added Java for line comments and debug output. Report live Paper testing only if actually performed.
