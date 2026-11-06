# Change list - iCoordinator phase 2 (after UI)

Collected from the user 2026-11-06 14:22-14:37. User said "این موارد رو انجام بده" at 14:37.

## C1. Project detail screen
- C1a. Manifest section first, collapsed by default; tap opens read-only text with an "ویرایش" button under it.
- C1b. Goals section after manifest, goal count beside its title, collapsible.
- C1c. Long-press on a goal and drag to reorder the list (order is stored).
- C1d. Double-tap a goal opens the goal screen.
- C1e. Under the goal list only an "افزودن" button; it opens the create-goal form as a popup (no static form on the screen).

## C2. Goal screen (new)
- Goal name and percent editable.
- Create a new task that belongs to this goal.

## C3. Goal progress from tasks
- C3a. When a goal-linked task is done, the user enters how many percent of the goal it advanced.
- C3b. Goal progress shown in the goal screen.
- C3c. In the project screen each goal row background fills like a progress bar to its progress.

## C4. New routine form
- Start/end date and time are picked with a (Shamsi) date picker and a time picker, not typed.

## C5. New task form: repeat option (after title)
| Repeat | Day | Repeat end |
|---|---|---|
| None | enabled, single choice | disabled |
| Daily | disabled | enabled |
| Weekly | enabled, multi choice | enabled |

## C6. Edit task and routine (all fields; alarm rescheduled on time change)

## C7. Calendar: add weekly and daily views beside the monthly view

## C8. App lock with a password (personal app)

## Found during planning
- ICoordinatorApp.kt uses fallbackToDestructiveMigration(), and AppDatabase is version 5 (handover said 4). Any schema change (C1c, C2, C3, C5) would erase all data on the phone unless a real Migration 5->6 is written.

# Change list - phase 3 (collected from 2026-11-06 16:41; coding starts only when the user says so)

## C9. Goal screen: task list paging
- Show only the last 10 tasks of the goal, with a "بیشتر" (more) button under the list.
- Open: Q1 order of "last" (newest by date/time vs. newest created); Q2 what "بیشتر" loads (next 10 vs. all).

## C10. Deleting a repeating task
- On delete of a task that belongs to a repeat series, ask: "فقط همین تسک" or "کل سری".
- Done tasks are never deleted by the series delete.
- Found: TaskEntity has no series key (each occurrence is an independent row), so this needs a new column (seriesId) and Migration 6->7 (approval gate: runs on phone data).
- Open: Q3 "کل سری" = all occurrences vs. this one and later only; Q4 tasks already on the phone have no seriesId (treat as single, or guess series by same title+project+time); Q5 can a done task still be deleted alone with "فقط همین تسک".
- Answered (D36): Q1 latest dueAt; Q2 load 10 more. Q3 this and later; Q4 old tasks = single; Q5 done task deletable alone.

## C11. Projects screen: order and drag
- Projects listed by name; long-press and drag to reorder, like goals (order is stored).
- Found: ProjectEntity has no sortOrder -> new column in the same Migration 6->7 (D37).
- Open: Q6 how name order and manual drag order live together.

## C12. Routine alarm
- Routines have no alarm today (RoutineEntity has no alarm field; the scheduler only handles tasks). Add an alarm option to the routine form (new and edit).
- Needs routines.alarmOffsetMinutes in the same Migration 6->7 (D37), and scheduling of the next occurrence, re-armed after each alarm, after boot and after edit/delete.
- Open: Q7 same alarm choices as tasks (none / at time / N minutes before)?; Q8 should an alarm fire on a day already ticked as done?
- Answered (D38): Q6 name order once, then manual order; new project at end. Q7 same choices as tasks; Q8 no alarm if that day is already done.

## C13. Settings screen (new)
User: settings like change PIN, display mode (dark/light), data backup "و ...".
Scope by recommendation (D38; user can change):
- C13a. Change PIN (reuses LockManager; also stays on the lock screen).
- C13b. Theme: System / Light / Dark, stored in SharedPreferences, applied at once.
- C13c. Backup: export all data to a file the user picks (Android file picker, no internet); Restore from such a file, after a confirmation that it replaces all current data; alarms re-armed after restore.
- C13d. Lock timeout choice (immediately / 1 min / 5 min); default stays 1 min.
- C13e. App version shown at the bottom.
- Entry point: a settings icon in the top bar of the main screens (bottom bar unchanged).
- Notes: backup file is not encrypted (personal app; user keeps the file) - flag; allowBackup=false stays, so this export is the only backup path.
- Status 2026-11-06: C9-C13 coded (sprints T28-T41 + fixes F01-F03), written to the project, not built yet. Backups: handover/backup-phase3-2026-11-06/.
