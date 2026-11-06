# Task board - iCoordinator

## Phase 2 (changes C1-C8, see changes.md, roadmap-phase2.md)
Path: Full. Mode: Auto.

| Task | Name | Level | Launch | Status |
|---|---|---|---|---|
| T16 | Entities + Migration 5->6 | Sonnet Medium | S1 | Accepted; built on Actions (user, 15:11) |
| T17 | DB v6 wiring + ProjectDao | Sonnet Medium | S1 | Accepted |
| T18 | Task data | Sonnet Medium | S1 | Accepted |
| T19 | Routine edit + goal repos | Sonnet Medium | S1 | Accepted |
| T20 | Progress logic + tests | Sonnet Medium | S1 | Accepted |
| T21 | Pickers, TaskFormDialog, ContributionDialog | Sonnet Medium | S2A | Accepted first try (+ minor fix D35; return lacked QUESTIONS key, added) |
| T27 | App lock (LockManager, LockScreen, MainActivity) | Sonnet Medium | S3 (parallel with S2A) | Accepted first try (+ R1-03 fix) |
| T22 | Project detail C1, C3c | Sonnet Medium | S2B | Accepted first try (+ R1-01, R1-05 fixes) |
| T23 | Goal screen + route C2, C3b | Sonnet Medium | S2B | Accepted first try (+ R1-05) |
| T24 | Weekly tasks form/edit/tick C5, C6, C3a | Sonnet Medium | S2B | Accepted first try |
| T25 | Routine form/edit C4, C6 | Sonnet Medium | S2B | Accepted first try |
| T26 | Calendar day/week views C7 | Sonnet Medium | S2B | Accepted first try |
| R1 | Independent compile + security review | Sonnet High | R | Done: 0 High, 2 Medium (fixed), 4 Low (2 fixed) |

## Launches
- S1: T16-T20, 113.5k sub-agent tokens, 1.5 min.
- S2A: T21, 110.4k, 1.9 min. S3: T27, 113.0k, 1.7 min (parallel).
- S2B: T22-T26, 200.5k, 5.1 min.
- R: R1, 309.0k, 12.4 min.

## Status
All 19 changed files written to the project 2026-11-06. Not compiled yet: waiting for push.bat + GitHub Actions, then phone test (handover/PHASE2-TEST-GUIDE.md).

## Backup
- handover/backup-phase2-2026-11-06/ (before sprint 1).
- handover/backup-sprint2-2026-11-06/ (the 12 files sprints 2-3 overwrote, as they were before).
