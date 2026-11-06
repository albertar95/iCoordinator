# Roadmap phase 2 - iCoordinator changes C1-C8 (v1.0, 2026-11-06)

Path: Full. Mode: Auto. No compiler in Claude's workspace: every sprint ends with the user running push.bat and GitHub Actions (unit tests + APK) as the build check (D18).

## Findings that shape the plan
- Repeat already exists: TaskRepository.addTasks makes one row per occurrence (TaskRules.occurrenceDates), so each occurrence is ticked on its own. C5 is mostly a form change plus weekly multi-day. (D23 replaces the completion-table part of D20.)
- Task form already has title, day chips (single), time text HH:mm, repeat, until text, alarm, project.
- DB is version 5 with fallbackToDestructiveMigration (D24: Migration 5->6, fallback removed).

## Locked interfaces (schema v6)
- goals: + sortOrder INTEGER NOT NULL DEFAULT 0 (migration sets sortOrder = id).
- tasks: + goalId INTEGER NULL, FK goals(id) ON DELETE SET NULL, index; + goalContribution INTEGER NULL (0..100, percent of the goal this task advanced when done).
- Goal progress is computed, not stored (D25): 100 if goal.isDone, else min(100, sum goalContribution of done tasks with that goalId). A goal counts as done in the UI when progress = 100.
- Project progress = sum(weightPercent x goalProgress / 100), clamped 0..100 (D19).
- PIN: SharedPreferences, PBKDF2WithHmacSHA256 + random salt (no DB change).

## Sprint 1 - data and logic (1 launch, Sonnet Medium, cost Medium)
| Task | Files | Policy |
|---|---|---|
| T16 Entities + Migration 5->6 | GoalEntity, TaskEntity, data/Migrations.kt (new) | Ask-first (schema, user data) |
| T17 DB wiring + project DAO | AppDatabase, ICoordinatorApp, ProjectDao | Ask-first |
| T18 Task data | TaskDao, TaskRepository, TaskRules | Flag-and-continue |
| T19 Routine + project repos | RoutineDao, RoutineRepository, ProjectRepository | Flag-and-continue |
| T20 Progress logic + tests | ProgressCalculator, ProgressCalculatorTest, TaskRulesTest (new) | Flag-and-continue |
+ independent review of T16/T17 (migration), Sonnet Medium.
End: user pushes; Actions must pass. ViewModels still compile against old repo calls (kept as overloads/defaults).

## Sprint 2 - screens (2 launches, Sonnet Medium, cost Medium)
- Launch A: T21 shared pickers (Shamsi date picker dialog, time picker) + TaskFormDialog + ContributionDialog in ui/components.
- Launch B (after T21 review): T22 project detail (C1, C3c); T23 goal screen + route (C2, C3b); T24 tasks screen form/edit/tick popup (C5, C6, C3a); T25 routines form/edit (C4, C6); T26 calendar daily/weekly views (C7).
End: push, build, user tests on phone.

## Sprint 3 - app lock (1 launch, Sonnet Medium, Ask-first, cost Medium)
- T27 LockManager (PIN hash, 1 min background timeout, erase-all option) + LockScreen + MainActivity integration; manifest allowBackup=false; FLAG_SECURE.
+ independent review (security).

## Approval gates in this plan
- Overwriting existing source files (after backup to handover/backup-phase2-2026-11-06/).
- Migration 5->6 runs on the phone's real data.
- Sprint 3: security change, manifest change, erase-all-data feature.
