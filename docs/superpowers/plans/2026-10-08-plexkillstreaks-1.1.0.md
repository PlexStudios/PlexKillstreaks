# PlexKillstreaks 1.1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Native execution is recommended for this small, tightly connected codebase; the user must review the plan and select execution before implementation.

**Goal:** Deliver the complete requested 1.1.0 release candidate without publishing it or breaking existing configurations and data.

**Architecture:** Extend StreakManager, DatabaseManager, KillListener, and StreakCommand. Add only a small validated settings snapshot and an in-memory AntiFarmTracker. Reuse the existing single database executor for ordered persistence and leaderboard queries; retain the exceptional loading fallback subject to the explicit review decision below.

**Tech Stack:** Java 21, Gradle wrapper, Paper API 1.21.4 compileOnly, PlaceholderAPI 2.11.6 compileOnly, SQLite JDBC at test runtime, JUnit 5, Mockito for Bukkit boundaries and controlled async scheduling.

**Spec:** `docs/superpowers/specs/2026-10-08-plexkillstreaks-1.1.0-design.md`, incorporating the user's pasted 1.1.0 specification and approved targeted approach.

## Global Constraints

- Target version is `1.1.0`, with Gradle resource expansion for `plugin.yml`.
- Extend the current architecture rather than replacing it.
- Keep anti-farming in memory only, expiring safely and without unbounded growth.
- Add `last_known_name` only through an additive SQLite migration. Do not recreate or drop the existing table.
- Run leaderboard database queries asynchronously on the existing database executor.
- Preserve ordered saves, but propagate persistence failures instead of swallowing them.
- Later saves must still be able to proceed after an earlier failed save.
- Keep the synchronous fallback loading approach for 1.1.0 rather than redesigning loading completely.
- A failed load must not create writable fake zero-state.
- Prevent delayed async loads from overwriting streak changes that occurred after fallback/gameplay activity.
- Preserve current commands, aliases, permissions, placeholders, existing configs, and SQLite data.
- Existing 1.0.x configs must boot with safe defaults for all new settings.
- Keep leaderboard queries, kill handling, and PlaceholderAPI resolution free from blocking database work, subject to resolving the conflicting fallback requirement below.
- Add reload-time validation without overengineering a new configuration framework.
- No `//` source comments.
- Do not claim tests or builds passed until they are actually run.
- Java 21; Paper 1.21+; PlaceholderAPI optional; no new production dependencies or package renames.
- Do not push, publish, create a release, or alter user data destructively.

## Review decision: synchronous fallback

The present KillListener calls synchronous ensureLoaded. This plan proposes retaining that exceptional query for immediate join/death correctness, while keeping loaded gameplay, placeholders, and leaderboard queries free of blocking database work. This is an exception to the latest absolute wording, not a claim that the fallback is nonblocking. Obtain explicit agreement before executing this plan. Strictly nonblocking early death handling instead requires a revised plan with ordered deferred death processing; deleting the fallback and dropping events is unacceptable.

## Review Focus

1. A stale async load or failure callback arrives after fallback, quit, or reconnect: preserve the current session and gameplay changes (Task 4).
2. A write fails while more saves are queued: report that failure and execute later snapshots in order (Tasks 2 and 4).
3. Malformed or old YAML omits entire new sections: boot/reload safely, warn at validation time, preserve the old file (Task 3).
4. Leaderboard pages overflow, senders lack only one permission, or players disconnect before completion: reject invalid input, enforce independent permissions, and safely deliver callbacks (Task 6).
5. Reward commands throw, return false, or indirectly change player state: execute each captured eligible reward at most once and continue remaining rewards (Task 5).

---

## File map

Paths below use `src/main/java/com/plexdev/plexkillstreaks/` as the Java source root and `src/test/java/com/plexdev/plexkillstreaks/` as the test root.

Create:
- `config/PluginSettings.java`: validated immutable settings and enabled milestone definitions; no framework or duplicate live configuration source.
- `manager/AntiFarmTracker.java`: accepted pair times, expiry, cleanup, capacity, injected clock.
- Test files: `database/DatabaseManagerTest.java`, `manager/AntiFarmTrackerTest.java`, `manager/StreakManagerTest.java`, `config/PluginSettingsTest.java`, `listener/KillListenerTest.java`, `command/StreakCommandTest.java`, `placeholder/KillstreakPlaceholderTest.java`, `PackagingTest.java`.
- `CHANGELOG.md`: verified history and actual 1.1.0 changes, with validation results recorded after execution.

Modify:
- `build.gradle.kts`: version, resource expansion, JUnit platform and test dependencies.
- `database/DatabaseManager.java`: additive migration, name-aware saves, failure propagation, paginated leaderboard.
- `manager/StreakManager.java`: name capture, reliable load/save lifecycle, milestone helpers, async leaderboard access.
- `listener/KillListener.java`: world gate, anti-farming, rewards, validated sounds.
- `listener/PlayerDataListener.java`: remove successful-operation log spam while preserving join/quit wiring.
- `command/StreakCommand.java`: top subcommand, independent permissions, usage and completion.
- `command/ReloadCommand.java`: reload validated settings.
- `placeholder/KillstreakPlaceholder.java`: two new memory-only values, branding.
- `PlexKillstreaks.java`: settings initialization, task ownership/cancellation, command completion, clean lifecycle.
- `src/main/resources/config.yml`, `src/main/resources/plugin.yml`, `README.md`.

## Task 1: Establish baseline, test harness, and version packaging

**Interfaces:** Existing Bukkit commands, aliases, permissions, six placeholders, and SQLite schema are the compatibility baseline. PackagingTest reads processed resources through the test classpath.

- [ ] Read the original attached request and design; resolve the loading review decision. Inspect local AGENTS instructions, git status, and 1.0.1 release/tag source using available read-only GitHub access. Record unavailable evidence honestly; do not rewrite the remote or fabricate history. Preserve untracked `.vs/`.
- [ ] Verify a Java 21 toolchain is available through Gradle; the shell currently reports Java 25. Select an installed Java 21 if required without changing machine-wide settings. Run the baseline wrapper build and record actual outcome.
- [ ] Add JUnit 5 BOM/Jupiter, Mockito, and SQLite JDBC with test-only scopes; choose actual available versions after checking repository metadata. Configure useJUnitPlatform. Add PackagingTest.processedDescriptorUsesProjectVersion asserting version 1.1.0, required aliases/permissions, and optional PlaceholderAPI metadata. Run `./gradlew.bat test --tests '*PackagingTest'` and observe the expected old-version failure.
- [ ] Set Gradle version 1.1.0; use `${version}` in plugin.yml, processResources inputs.property and filesMatching expansion limited to plugin.yml. Set author to Plex Studios. Retain packages and dependency scopes. Run the focused test again.
- [ ] Review and commit only this task's files after its checks pass, subject to workspace/git permissions; no push.

## Task 2: Additive database migration and leaderboard queries

**Files:** DatabaseManager.java and DatabaseManagerTest.java.

**Interfaces:** Preserve `DatabaseManager(PlexKillstreaks)`, `connect()`, `loadPlayer(UUID)`, and `savePlayer(UUID,int,int)`; saves now throw SQLException. Add `savePlayer(UUID,int,int,String) throws SQLException`. Add `LeaderboardEntry(String uuid,String player,int highest)` and `LeaderboardPage(List<LeaderboardEntry> entries,int page,int pages,long total)`. Add `getLeaderboard(int page,int pageSize) throws SQLException`; caller provides validated positive inputs. Retain PlayerData(int currentStreak,int highestStreak).

- [ ] Write real temporary-file SQLite tests: legacySchemaMigratesAdditively preserves an existing UUID's current=7/highest=25 and an unrelated column; migrationIsIdempotent reconnects twice with exactly one name column; existingDatabaseStillLoads reads the legacy values; namesUpdateWithoutResettingScores saves a new name; unnamedRowsUseStoredUuid uses a null/blank name and asserts exact stored UUID display.
- [ ] Write leaderboard tests: highestDescendingWithDeterministicTies asserts positive-score order by highest then UUID; paginationReturnsDistinctRows uses page size 2 across three pages; outOfRangeClampsToLastPage; emptyDatabaseHasNoEntries excludes zero and negative highest values. Write saveFailurePropagates for a closed connection and a real SQL constraint/trigger failure; assert SQLException rather than successful return. Write oldSaveOverloadPreservesKnownName.
- [ ] Run `./gradlew.bat test --tests '*DatabaseManagerTest'` and capture missing-interface or behavior failures.
- [ ] Detect last_known_name with PRAGMA table_info and ALTER TABLE only when absent. Use parameterized upsert, retain known names on nameless writes, throw unavailable-connection/SQL errors, and avoid lower-layer duplicate logs. Query count and rows consistently with long offset arithmetic, excluding nonpositive scores and safely restoring transaction state on failure.
- [ ] Run the database test class, inspect actual schema/value assertions, and commit the task after passing checks.

## Task 3: Validated settings, milestones, and anti-farming tracker

**Files:** PluginSettings.java, AntiFarmTracker.java, their tests, config.yml, PlexKillstreaks.java and ReloadCommand.java.

**Interfaces:** `PluginSettings.load(FileConfiguration,Logger)` returns an immutable snapshot; `isWorldDisabled(String)`, `nextMilestone(int)`, `killsToNext(int)`, `lastMilestone(int)`, `milestone(int)` provide memory-only access. `Milestone(int streak,String message,List<Reward> rewards)` and `Reward(boolean console,String command)` are nested records. Expose validated settings through named accessors. `AntiFarmTracker(long cooldownNanos,LongSupplier clock)` exposes `boolean tryCount(UUID killer,UUID victim)`, `int cleanup()`, `int size()`, and `void clear()`. Add plugin `getSettings()` and `reloadSettings()`.

- [ ] Write PluginSettingsTest.oldConfigUsesSafeDefaults loading the actual old YAML fixture: rewards true, worlds empty, cooldown 300, notifications true, page size 10, original milestones preserved. Test malformed numbers, nonpositive milestones, scalar lists, nonstring worlds/rewards, unknown reward type, blank commands, invalid sounds, negative/overflow cooldown, invalid page size, and progress length. Assert safe values and warnings only when loading/reloading settings. Test reloadReplacesSnapshotWithoutRewritingYaml.
- [ ] Test next/remaining/last results for milestones 3,5,10,25 at currents 0,3,7,25; assert next(7)=10, remaining(7)=3, last(7)=5, no-next remaining=0, and disabled milestones excluded. Test bare command console routing and all four replacement tokens being retained for execution.
- [ ] Write AntiFarmTrackerTest.firstCounts, repeatBlocked, blockedAttemptDoesNotExtendExpiry, countsAtExpiry, differentVictimCounts, differentKillerCounts, cleanupRemovesExpiredOnly, zeroCooldownDoesNotRetainPairs. Use an injected clock: accept t=0, reject t=120/240 seconds, accept t=300 seconds. Test capacityRejectsNewPairsUntilExpired with the internal capacity and no eviction of valid pairs.
- [ ] Run both focused test classes before implementation and record failures.
- [ ] Implement the snapshot and tracker with the spec's defaults, bounds, monotonic timing, immutable rewards, sorted enabled milestones, and Locale.ROOT matching. Own a periodic cleanup task in the plugin, clearing history when disabling/changing cooldown and cancelling at shutdown. Log a throttled capacity warning only on saturation. Reload validates and replaces the snapshot synchronously without resetting streaks.
- [ ] Update default config with original visual style, all requested keys/messages, empty rewards on unchanged milestones and vanilla golden_apple reward at 5. Add configurable leaderboard error and usage messages for actual failure paths. Run focused tests and commit after passing checks.

## Task 4: Reliable asynchronous saves and guarded loading

**Files:** StreakManager.java, StreakManagerTest.java, PlayerDataListener.java, PlexKillstreaks.java.

**Interfaces:** Preserve existing streak and loading methods. Add `CompletableFuture<Void> savePlayer(Player)` and `CompletableFuture<DatabaseManager.LeaderboardPage> getLeaderboard(int page,int pageSize)` using the existing executor. Make internal queueSave return its individual future and include a name captured on the server thread. Add `getKillsToNext(Player)` and `getLastMilestone(Player)` delegating to settings; retain getNextMilestone's -1 sentinel. Lifecycle state remains owned by StreakManager.

- [ ] Use controlled executor/scheduler callbacks and latches, never timing sleeps, to test delayedLoadDoesNotOverwriteFallbackGameplay: async load captures 2/8, fallback loads successfully, addKill reaches 3, delayed callback leaves 3/8. Test delayedLoadFailureDoesNotClearFallback, staleSessionCannotOverwriteReconnect, quitBeforeLoadDoesNotWriteZero, and failedFallbackAndAsyncLoadRemainUnavailable with no queued writes/rewards. Test retryAfterLoadFailure succeeds without fabricated state.
- [ ] Test requestedSaveFutureIsExceptional and laterSaveRecoversAfterFailure: make first write fail, queue the next snapshot, assert first future fails and second succeeds with latest values; verify invocation order and a single log for the failed write. Test completedOlderChainCannotRemoveNewerChain and shutdownDrainsPendingWritesBeforeClose. Assert no main-thread leaderboard query and capture names before executor work.
- [ ] Run `./gradlew.bat test --tests '*StreakManagerTest'` and record failures.
- [ ] Apply load results only for the current session when no successful fallback already loaded it. On load error leave unavailable; never insert/write fake zero data. Recover each predecessor with handle before the next thenRunAsync; wrap checked SQL failures in CompletionException and expose the individual future. Keep ordering without holding a manager monitor while waiting. Remove successful join/load debug-style logs.
- [ ] Implement orderly shutdown that queues loaded state before stopping submissions, drains database tasks including loads/leaderboards, suppresses stale scheduled callbacks, and closes the connection only when no worker can use it. Test configured shutdown behavior without a live server.
- [ ] Run focused tests plus DatabaseManagerTest and commit after passing checks.

## Task 5: Death handling, rewards, disabled worlds, and placeholders

**Files:** KillListener.java, KillListenerTest.java, KillstreakPlaceholder.java, KillstreakPlaceholderTest.java.

**Interfaces:** Keep onPlayerDeath(PlayerDeathEvent). Use settings and AntiFarmTracker from the plugin; preserve existing listener constructor. Rewards dispatch through Bukkit on the event/server thread after successful addKill. Existing placeholder identifiers remain unchanged; add kills_to_next and last_milestone.

- [ ] Write listener tests countedExactMilestoneExecutesEachRewardOnce, nonExactDoesNotReward, disabledMilestoneDoesNotReward, rewardsToggleKeepsAnnouncements, announcementsToggleKeepsRewards, and replacementUsesPlayerUuidStreakHighest. Assert correct console/player sender and unprefixed console command.
- [ ] Write invalidRewardDoesNotStopLaterRewards for validation rejection, false dispatch, and throwing command. Test ignoredFarmKillStillResetsVictim and assert no increment/highest update, normal action bar, milestone, reward, or expiry refresh. Test disabledWorldLeavesBothPlayersUntouched with mixed case and no anti-farm interaction. Test unavailableKillerCannotRewardButLoadedVictimResets and self/environment deaths retain victim reset behavior. Test reentrantRewardCannotReplayCapturedMilestoneRewards.
- [ ] Test every existing placeholder with loaded/unloaded/null player and unsupported names; assert no load/query calls. Verify kills_to_next=3 and last_milestone=5 at current 7, zero when none, and new author branding only.
- [ ] Run the focused listener/placeholder tests and observe failures before implementing.
- [ ] Apply the early world guard, availability and pair gates, independent victim reset, and exact-transition reward execution. Capture eligible command replacements before dispatch so a command cannot corrupt remaining replacements. Catch individual command failures and continue. Use cached validated sounds; no ordinary gameplay config-warning spam. Preserve announcements and streak-ended threshold.
- [ ] Add the placeholder cases and supported-name list. Run both test classes plus manager tests, then commit after passing checks.

## Task 6: Asynchronous leaderboard command and permission compatibility

**Files:** StreakCommand.java, StreakCommandTest.java, plugin.yml, PlexKillstreaks.java.

**Interfaces:** StreakCommand implements TabExecutor; preserve constructor signature and no-argument workflow. Dispatch top before player-only checks, check plexkillstreaks.top explicitly, and use StreakManager.getLeaderboard. Server-thread callbacks render configured messages with rank/player/highest/page/pages and prefix replacements.

- [ ] Test consoleTopWorks, aliasTopWorks, noArgConsoleStillRejected, streakPermissionDoesNotGrantTop, topPermissionWorksWithoutStreakPermission, and noArgPlayerBehaviorPreserved. Test topTabCompletion and unknownSubcommandUsage.
- [ ] Test invalidPageNeverQueries for zero, negative, malformed, overflow and extra args. Test loadingBeforeAsyncQuery, successReturnsOnServerThread, emptyMessageDistinctFromQueryFailure, disconnectedRecipientIgnored, and callbackAfterShutdownIgnored. Verify all configured formatting tokens, final-page clamping, and permission denial.
- [ ] Run `./gradlew.bat test --tests '*StreakCommandTest'` before implementing and record failures.
- [ ] Add parsing and tab completion; submit via the existing executor and marshal completion with the Bukkit scheduler. Check plugin lifecycle and player online state on delivery. Retain existing command/alias metadata, add top permission default true, remove only the root permission gate that would prevent independent top access, and keep explicit no-argument streak checks.
- [ ] Run command, packaging, and database test classes, then commit after passing checks.

## Task 7: Documentation and complete release verification

**Files:** README.md, CHANGELOG.md, PackagingTest.java, final corrections limited to verified defects.

**Interfaces:** Published command/config/placeholder documentation matches the final implementation. No release publishing.

- [ ] Update README for 1.1.0, reward examples using vanilla commands, anti-farming, disabled worlds, `/streak top [page]`, permission defaults, both new placeholders, safe legacy UUID display, reload defaults, migration compatibility, and the confirmed loading exception. Correct existing permission table against actual metadata. Describe anti-farm capacity behavior and leaderboard failure behavior without claiming live tests.
- [ ] Create CHANGELOG.md with `## 1.1.0`, actual Added/Changed/Fixed/Technical entries, and only verified older history if obtainable. Do not invent 1.0.1 fixes or dates. Record actual validation after running it.
- [ ] Run `./gradlew.bat clean test build`. Aggregate tests/failures/errors/skipped from every `build/test-results/test/TEST-*.xml`; distinguish parameterized executions from source-method counts. Report exact totals and exit result, including failures or infrastructure blockers.
- [ ] Inspect the generated JAR directly: packaged plugin.yml version exactly 1.1.0; original commands, aliases, permissions and softdepend preserved; default YAML loads. Verify no org/bukkit, io/papermc, me/clip/placeholderapi, test, Mockito, JUnit or SQLite driver classes bundled. Inspect Gradle runtime/test dependency reports to confirm test-only additions and unchanged production packaging.
- [ ] Run `git diff --check`; inspect diff and new files for debug output, secrets, unused imports/dependencies, fake data, unfinished handlers, and added Java line comments (distinguish syntax/URL strings). Check all eight placeholder cases and both existing command names plus streaks alias. Run an old-config startup/reload lifecycle test with optional PlaceholderAPI absent and settings defaults present.
- [ ] If any validation exposes a defect, add a focused regression, observe failure, fix the defect, rerun the focused checks and then rerun the full clean test build for the final artifact. Do not broaden testing without a reason.
- [ ] Update CHANGELOG validation with actual numbers and remaining limitations. Report any inability to compare the public 1.0.1 artifact; do not assert it was verified without evidence. No live Paper claim unless a live server was actually run.
- [ ] Review final git diff/status, commit verified release-candidate changes if permitted, and give the requested final summary: exact created/modified files, commands, permissions, placeholders, config keys, migration details, backwards compatibility, test/build totals, limitations, and full actual Change Log. Do not push or publish.

## Planning self-review and handoff

The tasks cover each feature and each specifically requested loading/persistence regression. Interfaces above retain old entry points and make new async boundaries explicit. Bounds, fallback display, independent permissions, failure messages, and saturated anti-farming behavior are intentional decisions for review, not measured results.

The unresolved loading conflict is explicitly identified and blocks implementation until the user selects the exception or requests the deferred-event design. All other task behavior is specified. Recommended execution: native implementation in this session with test-first task cycles and an independent final review if selected/authorized. Plan review must precede product-code changes.

Planning change log: added this implementation plan and the approved-approach design record. Product code, dependencies, version, and runtime data have not been changed by planning. No tests or builds have been run during planning.
