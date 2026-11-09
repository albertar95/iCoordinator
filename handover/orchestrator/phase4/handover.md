# Handover - iCoordinator phase 4 (home menu + personal accounting)
Prompt version: Orchestrator v6.8 - Last updated: 2026-11-09 13:30 - Mode: Auto - Path: Full

## In one minute
Last time: All 3 sprints + independent review + fixes done and committed (version 2.2.0). Home menu, accounting (dashboard, transactions, loans with reminders, reports, accounts, Parmis import, accounting backup/restore).
Now: sprint 4 (user feedback items 1-6) committed as 2.2.1; waiting for CI and the user's test.
Next: if CI fails, read the error log and fix (Compose code was never compiled; pure code: 83 tests pass locally). Then user acceptance: import .par, check D52 numbers.

## Goal
See ledger-phase4.md (R1-R8).

## Where we are
Stop: delivery.

## Decisions that shape the remaining work
D52-D70 in decisions-phase4.md. Key: D54 separate accounting.db; D58 package com.icoordinator.accounting + ui/accounting; D59 local tooling (tools/ here); D66 separate accounting backup file; D67/D69 reminders; D68/D69 loan creation.

## Waiting on the user
- push.bat -> CI result (send log on failure); install; test checklist in the final message.
- Open Low item L7 (optional).

## Tasks
Counts: T42-T55, T57 accepted (15); fixes FA-FF accepted.

## Metrics so far
Launches: S1 136k; S2 98k (blocked, Type B) + 200k; S3 262k; R3A 284k; R3B 287k; F 171k. All implementation tasks Sonnet Medium, first-try pass 21/21 (after one Type B fix). Questions: 5 + 4 + 1, all on recommended defaults.

## Files (handover/orchestrator/phase4/)
ledger-phase4.md, decisions-phase4.md, roadmap-phase4.md, task-board-phase4.md, handover.md, tools/ (kt.sh, RoomStubs.kt, uicheck.py, check_dispatch.py, return_check.py). Backups: handover/backup-phase4-s2/, handover/backup-phase4-s3/.

## How to resume
Start a new session with the Orchestrator skill (v6.8 or later), connect the project folder (and app\src\main\java\com\icoordinator + app\src\test\java\com\icoordinator as extra folders), attach this file, then say: "Resume from the handover."
