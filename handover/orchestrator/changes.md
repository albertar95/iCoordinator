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
