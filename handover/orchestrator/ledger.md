# Ledger - iCoordinator UI redesign (Orchestrator v6.8, Lite)

## Original goal (verbatim, Persian)
"پوشه handover رو بخون. روی UI برنامه کار کن، خیلی داغونه. یه ورژن با یه UI خوب بده تا کاربر برود تست بگیرد و ببیند چه کار می‌کند و چه کار نمی‌کند. چند مورد را قبلاً تست کرده بوده و نمی‌داند درست شده‌اند یا نه."

## Requirements
- R1 (goal): better UI, one testable version. Source: original goal.
- R2: report what works and what does not (test list). Source: original goal. Needs the user's list of tested items (not yet received).
- R3 (implied): no behaviour change in data, domain, alarm or database. Reason: handover says the code is not yet compiled or tested.

## Constraints
- Project: E:\Dev\projects\iCoordinator (Kotlin, Compose, Room). Source: handover/handover.md.
- UI language Persian, RTL. Source: handover.md.
- Builds are done by the user in Android Studio. This environment cannot compile.
- Only UI files change: ui/theme, ui/components (new), ui/projects, ui/weekly, ui/routines, ui/calendar, ui/report, ui/AppNavHost.kt, ui/ExactAlarmBanner.kt. Not: data, domain, alarm, MainActivity, build files.

## Known issues in scope (source: handover/findings.md section 6 and section 2)
- Navigation bar is text only; no icons.
- Delete buttons have no confirmation.
- Empty states have little guidance.
- Dark theme colours not reviewed.
- `return` inside a composable in ProjectDetailScreen.
- Error message stays after the user retries (clearError is never called from UI).

## Out of scope for this pass
- Shamsi (Persian) dates (findings 5, roadmap phase 2).
- Editing tasks and routines (roadmap phase 2).
- Database migration, backup setting, tests (phases 3 and 5).

## Open questions
- Q1: Which items did the user test, and what happened? (not yet answered)

## Tools check
- Read and write project files: yes (device folder connected, depth limit 7 levels from the root).
- Build or run Gradle: no. The user builds.
- Sub-agents: the staged copies in the container are the work area; sub-agents write to outputs/ and the orchestrator copies results back after a backup.
