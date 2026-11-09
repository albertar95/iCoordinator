# Decisions - iCoordinator UI redesign

## Project rules
- D1: Only UI files change (see ledger). Data, domain, alarm, DAOs, ViewModels' public API and build files are frozen.
- D2: No new Gradle dependencies. Icons are not added (F1).
- D3: Public signatures of screen functions and ICoordinatorTheme stay exactly as they are (checked by kt_check.py).
- D4: Persian text is kept unless the brief says otherwise.

## Flags (F-IDs, defaults chosen; confirmed by the user's "پیشنهادات خوبه")
- F1: Bottom navigation uses text labels with a clear selected indicator, no icons. If wrong: add icons later after a dependency decision.
- F2: Dates stay Gregorian. Shamsi is phase 2. If wrong: separate task.
- F3: Every delete (project, goal, routine, task) asks for confirmation through one shared dialog. If wrong: remove the dialog per screen.
- F4: Visual style: Material 3, one teal primary palette, light and dark schemes, 16 dp screen margin, cards with 12 dp corners, score colours green/amber/red (above 80, 50 to 80, below 50, the thresholds in handover.md). If wrong: change the palette in Theme.kt only.
- F5: Empty states get one short Persian guidance line each.
- F6: clearError() is called when the user edits the goal input (UI only; no ViewModel change).

## Decision on routing
- D5: Routing: T01 (shared components) and T02-T05 (screens) are design tasks, V = 2 (judgment), so Sonnet Medium (route.py row 1).
- D6: Two launches, not one: T02-T05 import the components from T01, and T01 has no runnable validation (no compiler here), so T02-T05 wait for T01 to be reviewed.

## Calibration log
- none yet (first sprint).

## Session 2026-11-06 (UI redesign, round 2)
- D7: Icons allowed, core set only (material-icons-core comes with material3 1.3 via compose-bom 2024.09.00; no new dependency). Nav: Home, List, Refresh, DateRange, Star. Replaces F1. Reason: user said the UI "has almost no UI elements" (2026-11-06).
- D8: Exact-alarm banner becomes a compact card on the weekly log screen only (shown only when permission missing). Pending user approval via mockup.
- D9: Every screen gets a TopAppBar with title; project detail has a back arrow. Pending user approval via mockup.
- D10: Flow: HTML mockup + screenshots first (T06), user approves, then Compose rewrite. Source: user "قبل از پابلیش یه اسکرین شات نشونم بده".
- Note: phone screenshots show blue/lavender colours, not the teal in Theme.kt; open question Q2 whether the phone build is older than the 2026-11-03 code.
- D11 (2026-11-06): User sent a reference app's screenshots: "یه همچین چیزی میتونی بسازی؟ فقط رابط کاربریشو در نظر بگیر نه context رو". T06 revision 1 = v2 mockup in that style (outlined pill rows, round icon circles, big titles, gradient hero on weekly log, raised centre nav item = weekly log, outline icons). Accent stays teal; no branding of the reference app copied. Nav order change and teal accent are flags for the user.
- D12 (2026-11-06): Font Vazirmatn v33.003 (OFL), Farsi-Digits (FD) variant so numbers show as Persian digits (user answer 2A). Files in inputs/fonts; will be bundled in res/font.
- D13 (2026-11-06): Icons Tabler 3.50.0 outline (MIT), imported per icon as vector drawables (no library). Replaces D7's core-icon limit.
- D14 (2026-11-06): Bottom bar right-to-left: پروژه‌ها, روتین‌ها, تقویم (raised centre), تسک‌ها, گزارش هفته. "لاگ هفتگی" renamed "تسک‌ها". Source: user 13:22. User approved v3 design, plan, and overwrite of ui/ files after backup (13:23 "با هر دو موافقم").
- D15: Icon drawables and font copies (T07) made by the orchestrator with a conversion script (mechanical, cheaper than a launch); 15 drawables + 4 fonts counted as one asset output.
- D16: User asked for a more minimal launcher logo (13:23). T15 makes 3 options; user picks before it is applied.
- D17 (2026-11-06 13:35): User picked logo option 1 (open ring + dot, white on #00696B). Apply ic_launcher_foreground.xml + colors.xml from outputs/T15/option1 (overwrite after backup).
- D18 (2026-11-06 14:12): User installed the build (GitHub Actions) and is satisfied with the UI. UI phase closed. Builds are done only by GitHub Actions on push (push.bat at repo root); no local builds.

## Phase 2 (changes after UI), session 2026-11-06 14:37
Change list: changes.md (C1-C8). User "go" at 14:41 accepted the recommendations below.
- D19: Project progress = sum(goal weight x goal progress / 100); a goal auto-ticks at 100% progress. (Q2 A)
- D20: Routines stay. Recurring tasks are separate, may link to a goal; each occurrence is ticked separately (completion table like routines). (Q3 A)
- D21: App lock = 4-digit PIN, hashed; lock on app open and after 1 min in background; FLAG_SECURE hides content in recents; allowBackup=false. No biometric (no new dependency). (Q4 A)
- D22: Forgot PIN = "erase all data and start over" option. (Q5 A)
- F7: Edit task/routine opens on tap of the item.
- F8: Date/time pickers used in the task form too, not only routines.
- F9: Ticking a goal-linked task opens a popup for percent; dismiss = 0%.
- F10: Goal drag-reorder implemented without a new library.
- F11: Calendar daily view = items by time, tickable; weekly view = per-day summary.
- OPEN (approval gate, needs explicit answer): Q1 data migration. Recommended A: write Migration 5->6 and remove fallbackToDestructiveMigration(). Not covered by "go".
- BLOCKER: folder app\src\main\java\com\icoordinator must be connected separately (depth limit 7).
- D24 (14:42): Q1 = 1A. Write Migration 5->6, remove fallbackToDestructiveMigration(). Source: user "1a".
- D23: Recurring tasks already exist as one row per occurrence (TaskRules.occurrenceDates); keep that model. No completion table for tasks. Replaces the completion-table part of D20.
- D25: Goal progress computed, not stored: 100 if isDone else min(100, sum goalContribution of done tasks linked to the goal).
- F12: Editing a repeated task changes only that occurrence. If wrong: add "this / all in series" choice (needs a seriesId column).
- F13: Weekly repeat with several days creates one row per selected day per week until the end date (cap MAX_OCCURRENCES).
- F14: Deleting a goal sets its tasks' goalId to null (tasks kept), same as project delete.
- F15: Date picker is a custom Shamsi month grid (Material DatePicker is Gregorian only); time picker is Material3 TimePicker, 24h.
- D26: No compiler in Claude's workspace; validations are static scripts; the build check is GitHub Actions after the user runs push.bat.

## Phase 2, sprints 2-3 (2026-11-06, 15:13-)
- D28 (15:13): User: "do all sprints, ask nothing, go with your recommendations; tell me when to push and test." Ask-first tasks (T27) ran as Flag-and-continue with the defaults below; this message also approved overwriting the screen files (backups in handover/backup-sprint2-2026-11-06/) and the sprint 3 security/manifest changes.
- D29: PIN hash = PBKDF2WithHmacSHA256, 16-byte salt, 60000 iterations, 256-bit; prefs "app_lock"; MessageDigest.isEqual.
- D30: 5 wrong PINs -> 30 s lockout.
- D31: Forgot PIN = ActivityManager.clearApplicationUserData() after a confirmation dialog (wipes DB, prefs, alarms; app closes).
- D32: First launch with no PIN shows mandatory PIN setup; change PIN from the lock screen ("تغییر رمز").
- D33: Lock state is process-wide (rotation does not re-lock); background timeout measured with SystemClock.elapsedRealtime (review R1-03).
- D34: manifest allowBackup=false (orchestrator edit).
- D35: New non-repeating task preselects today when today is in the shown week (orchestrator minor fix to T21 F1).
- F16: Tasks created from the goal screen use the current week (weekStart of today).
- F17: Calendar view switch Month/Week/Day chips; week = Saturday-start week of the selected day; day = selected day.
- Accepted flags: T21 F2-F5; T27 F1-F6 (LTR keypad, cancel in change-PIN, rotation resets PIN entry); T22 F1-F4 (progress card stays above manifest); T23 F1-F4 (delete on goal task rows); T24 F1-F3; T25 F1-F3 (routine default time 08:00; "بدون پایان" chip); T26 F1-F5.
- Review R1 (Sonnet, reading review, no compiler): no compile error found. Fixed: R1-01 drag reorder used stale state (could undo a just-ticked goal), R1-02 contribution input dropped Persian digits, R1-03 clock, R1-05 "not found" flicker. Open (Low): R1-04 calendar loads a wide range after paging many months; R1-06 add-goal blank-title error shows under the percent field.

## Phase 3 change collection (2026-11-06, 16:41-)
- D36 (17:26): User: "پیشنهادات خودت خوبه" - accepted the recommended answers:
  - C9 Q1: "last 10" = the 10 tasks with the latest dueAt. Q2: "بیشتر" loads 10 more.
  - C10 Q3: "کل سری" = this occurrence and later ones. Q4: existing tasks without seriesId are treated as single tasks. Q5: a done task can be deleted alone with "فقط همین تسک"; the series delete never deletes done tasks.
- D37: C10 needs tasks.seriesId and C11 needs projects.sortOrder -> one Migration 6->7 (schema v7). Approval gate before it runs on phone data.
- D38 (17:40): Standing rule from the user: every open question takes Claude's recommendation unless the user says otherwise; silence = recommendation. Applied: C11 Q6 = name order once (migration), then manual drag order kept, new project at end. C12 Q7 = same alarm choices as tasks; Q8 = no alarm on a day already ticked done.

## Phase 3 build (2026-11-06 17:42-)
- D39 (17:42): User: "شروع کن، تمام اسپرینت ها رو انجام بده و تموم شد بهم خبر بده". Read like D28: run all sprints without stopping; questions take recommendations (D38); this message approves overwriting source files after backup (handover/backup-phase3-2026-11-06/) and Migration 6->7.
- D40: Correction to C12: routines already get an alarm at their time (RoutineRepository.scheduleAlarms always schedules; no on/off). C12 = add the alarm choice (none / at time / N min before) to routines, default for existing routines = at time (keeps today's behavior), and skip the notification when that day is already ticked (Q8). If the user saw no routine alarm on the phone, that is a separate bug to check in testing.
- D41: Schema v7 (locked): tasks + seriesId INTEGER NULL (+ index index_tasks_seriesId); projects + sortOrder INTEGER NOT NULL DEFAULT 0; routines + alarmOffsetMinutes INTEGER NULL DEFAULT 0. Migration sets projects.sortOrder by Persian alphabetical title order (PersianSort, ties by id).
- D42: seriesId of a repeat series = id of its first inserted occurrence; a single (non-repeating or one-date) task keeps seriesId = null.
- D43: C9 is UI only: the goal screen sorts the goal's tasks by dueAt descending, shows the first 10, "بیشتر" adds 10. Goal progress keeps using all tasks.
- D44: Backup = copy of the SQLite DB file (after WAL checkpoint) to a user-picked file (SAF CreateDocument), name iCoordinator-backup-<yyyyMMdd-HHmm>.db. Restore = validate the file (SQLite header, opens read-only, has tables tasks/projects/routines, user_version 5..7), cancel current alarms, keep the current DB as icoordinator.db.before-restore, replace, set a "reschedule alarms" flag, restart the app.
- D45: Settings stored in SharedPreferences "app_settings": theme_mode (system/light/dark, default system), lock_timeout_ms (0 / 60000 / 300000, default 60000).
- D46: Orchestrator rule check (3 files per task) splits the work into 3 sprints: S1 data (T28-T33), S2 alarms + settings infra + screens (T34-T39), S3 settings screen (T40) + independent review R2.
- D47 (sprint 2 review): orchestrator minor fixes: RoutineRepository passes alarmOffset to scheduleRoutine (T34 F1/T39 F3); BackupManager restores the previous DB file if the final copy fails and exposes needsRestart so the settings screen restarts the app whenever the DB was closed (T35 F4). MainActivity follow-ups (T35 F1, F2) moved into T41. Accepted flags: T34 F2-F4, T35 F3 F5-F8, T36 F1-F4, T37 F1, T38 F1-F3, T39 F1-F2; T29 F1-F2, T30 F1, T31 F1, T32 F1-F2, T33 F1-F3.
- D48: Agents write LF line endings; files that were CRLF on disk are converted back to CRLF before writing to the project.
- D49 (review R2, Sonnet High, reading only): no compile error, no Room v6->v7 mismatch; 0 High, 2 Medium, 6 Low. Fixed: R2-01 (restore validation: quick_check + all tables), R2-02 (swap via .new + rename with fallback; no startup recovery), R2-03 (Back blocked while busy), R2-05 (skipNextLock reset in picker callbacks), R2-07 (more Persian normalizations), R2-08 (CRLF restored at write). Open Low: R2-04 export consistency under concurrent writes; R2-06 score colours follow the phone theme, not the app setting. Icons.Default.Settings dependency (material-icons-core via material3 1.3) not verifiable offline.
- D50 (18:24): "App not installed" on update. Cause: GitHub Actions makes a new debug keystore on every run, so each APK has a different signature and Android refuses the update. Fix: fixed keystore app/signing/debug.keystore (password android) + signingConfigs debug in app/build.gradle.kts (backup in handover/backup-signing-2026-11-06/). The old app's key is lost, so this one update needs: rescue-data.bat (adb run-as copy of the DB, phone unchanged) -> Claude merges WAL and checks the data -> user uninstalls old app -> installs new APK -> Settings > restore from the rescued file. Never uninstall before the rescue is verified.
- D51 (19:14): User confirmed: new APK installed, restore from the rescued file worked, "همه چیز خوبه". This build is release 2.1.2: versionName "2.1.2", versionCode 20102 (rule major*10000 + minor*100 + patch) set in app/build.gradle.kts. Future builds bump these.
