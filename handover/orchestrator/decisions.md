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
