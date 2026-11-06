# Roadmap phase 3 - changes C9-C13 (v1.0, 2026-11-06)
Path: Full. Mode: Auto. No compiler here: build check = push.bat + GitHub Actions (D26). Decisions D36-D46.

## Sprint 1 - data (1 launch, Sonnet Medium)
| Task | Files | 
|---|---|
| T28 Entities v7 | TaskEntity, ProjectEntity, RoutineEntity |
| T29 Persian sort | domain/PersianSort.kt (new), test/domain/PersianSortTest.kt (new) |
| T30 Migration 6->7 + wiring | Migrations.kt, AppDatabase.kt, ICoordinatorApp.kt |
| T31 Task series delete | TaskDao, TaskRepository |
| T32 Project order | ProjectDao, ProjectRepository |
| T33 Routine alarm scheduling | RoutineUtils, RoutineUtilsTest, RoutineRepository |

## Sprint 2 - alarms, settings infra, screens (1 launch, Sonnet Medium)
T34 alarm delivery (TaskAlarmScheduler, AlarmReceiver, AlarmRescheduler); T35 AppSettings + BackupManager (new) + LockManager; T36 DeleteTaskDialog (new) + GoalScreen + GoalViewModel; T37 WeeklyLogScreen + VM; T38 ProjectsScreen + VM; T39 RoutinesScreen + VM.

## Sprint 3 - settings screen + review
T40 SettingsScreen (new) + AppNavHost + MainActivity; R2 independent review (Sonnet High).
