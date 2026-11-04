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
