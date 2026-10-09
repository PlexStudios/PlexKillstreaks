# Execution ledger — plan: 2026-10-08-plexkillstreaks-1.1.0.md

User approved native execution and the exceptional synchronous death-loading fallback.

Ruling: work in the existing workspace on local codex/1.1.0 — keeps changes in the user's writable checkout and isolates the main branch; no new worktree requested.
Ruling: keep the execution ledger alongside the plan rather than running shell-specific skill helpers — Windows PowerShell and restricted metadata access; the same progress evidence is retained.
Pre-flight: DatabaseManager name-aware saves and leaderboard records feed StreakManager; settings feed listener/manager/commands; command permissions require removing the root descriptor permission gate. No unresolved interface conflict.
Baseline: Gradle clean build succeeded, test NO-SOURCE; default sandbox cache access failed, escalated build succeeded. No product tests existed.
Task 1: in progress; version regression written before changing version.

Task 1: complete — baseline build passed; PackagingTest failed on 1.0.0 before expansion, then passed on 1.1.0. Java 21 toolchain found at C:/Program Files/Java/jdk-21.0.11.
Task 2: complete — 6 real SQLite tests passed, including additive/idempotent migration, preservation, pagination and actual rejected writes. New interfaces first produced compilation failures.
Task 3: complete — 5 settings and 8 anti-farming tests passed. Paper sound registry required a test-only service provider and static Bukkit unsafe boundary; no runtime dependency added.
Ruling: tracker clock measures monotonic seconds rather than nanoseconds — matches configured units and avoids conversion overflow; resolution is one second, within the seconds-based contract.
Task 4: complete — 9 manager lifecycle tests passed. Pending snapshots bridge reconnect fallback while writes are queued; background loads wait behind each player's pending chain.
Ruling: rewrite the touched manager/database control flow compactly while retaining public entry points — the previous deeply expanded methods obscured shared lifecycle guards; behavior changes remain limited to requested fixes.
Task 5: complete — 11 listener and 2 placeholder tests passed; baseline listener tests demonstrated missing rewards/world/farming guards before changes.
Task 6: complete — 11 command tests plus packaging/database tests passed. Top permission is independent; no-arg command no longer invokes fallback.
Ruling: retain fixed usage text rather than add a usage configuration key — no user requirement for configurable usage; avoid an unnecessary setting.
Task 7: in progress — README/default config updated. Upstream CHANGELOG existed despite being absent locally; preserved its entire 1.0.0 section and added 1.0.1 from the actual public GitHub release API response. Full suite passed before documentation; final review/build pending.

Final review: independent read-only reviewer found two important issues, reward/death reentrancy and unbounded leaderboard submission. Both received failing regressions and passing fixes.
Final: Ruling: treat saturated anti-farm repeated full scans as important — repeated main-thread work at capacity violates the lightweight gameplay requirement; an expiry-aware scan guard removes it without changing cooldown results.
Final: Ruling: treat wrapper-only leaderboard logging as important — legitimate SQL failures need useful diagnosis; log the underlying cause without exposing it to players.
Final: fixed nested reward death — rewardCausedDeathCannotUseOrEraseVictimsEndedStreak RED to GREEN; reset original victim before external commands.
Final: fixed unbounded requests — leaderboardQueueIsBoundedAndAdmissionRecovers and duplicateTopRequestsAreBoundedAndReleasedAfterDelivery RED to GREEN; 16 outstanding query cap and per-sender admission with pending/busy messages.
Final: fixed repeated cleanup — saturatedRejectionsDoNotRepeatCleanupBeforeExpiry RED to GREEN.
Final: fixed old-config inherited rewards — oldMilestonesDoNotInheritNewDefaultRewards RED to GREEN; explicit absent rewards remain empty even with bundled defaults.
Final: no deferred minor findings. Reviewer declined early async loading redesign (authorized exception), live Paper/performance verification (not run), provenance/packaging (verified separately by coordinator), and unchanged PlaceholderAPI persistence behavior (preserved).
Ruling: preserve all final work in one local release-candidate commit rather than intermediate task commits — the changed source is one tested unit on codex/1.1.0; it costs less granular history, with test-cycle evidence retained here.
Task 7: complete — final clean test build BUILD SUCCESSFUL; 62 tests, 0 failures, 0 errors, 0 skipped. Final JAR has 30 entries, version 1.1.0, class major 65, and no external or test classes/services. Production runtimeClasspath has no dependencies. Java source has zero double-slash matches. git diff --check passed before final staging.
Final limitations: retained synchronous early-death fallback, no live Paper server or benchmark; Paper legacy Sound/test Registry API and Gradle/Mockito warnings remain non-failing tooling warnings. No push, merge, release or publication.

2026-10-09 anti-farming revision: user authorized pushing all project changes except CHANGELOG.md. Keep version1.1.0; use fixed180-second windows allowing2 counted kills per directional pair. Allowed/blocked repeats preserve original window start. Config accepts window0..31536000 and positive integer threshold, with180/2 fallbacks and optional explicit old RC-key fallback. Reload clears history on enabled/window/threshold changes. Tracker remains bounded and memory-only.
Revision validation: focused tracker/listener/config/lifecycle tests passed, full test suite passed, then clean test build BUILD SUCCESSFUL. 81 tests,0 failures,0 errors,0 skipped. Packaged version1.1.0; no production dependency changes or bundled test/external libraries; no Java double-slash comments; git diff --check passed. No live Paper test.
Push preparation: upstream main added support/contribution documents and CI; preserve these. Create outbound branch from upstream main and copy tested source/build/README/planning changes only, so outgoing commit/history never modifies CHANGELOG.md. Preserve revised changelog locally and previous local branch history. User explicitly authorizes the branch push, not a release.
Revision independent review: no important regressions. The optional RC-key fallback remains intentionally undocumented in README/default config as the user requested. Final outbound branch codex/1.1.0-rc is based on current upstream main, preserves upstream support/CI files, and excludes CHANGELOG.md changes from all outgoing commits.
