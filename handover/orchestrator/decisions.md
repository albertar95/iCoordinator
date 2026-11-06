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
