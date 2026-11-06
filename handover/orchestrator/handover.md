# Handover - iCoordinator (Claude work state)

Prompt version: Orchestrator v6.8 - Last updated: 2026-11-06 15:00 - Mode: Auto - Path: Full (phase 2)

## In one minute
Last time: Sprint 1 of phase 2 done: DB v6 with Migration 5->6 (destructive fallback removed), goal order, task-goal link with contribution, task/routine/goal edit in repositories, progress functions + tests. Written to the project; not yet built.
Now: waiting for the user to run push.bat and report the GitHub Actions result.
Next: if Actions passes, sprint 2 (screens T21-T26) per roadmap-phase2.md. If it fails, fix from the red step's error text.

## Goal
Changes C1-C8 in changes.md (project screen, goal screen, goal progress from tasks, pickers, task repeat form, edit task/routine, calendar daily/weekly, app lock).

## Where we are
Stop: sprint 1 checkpoint. Current sprint: 2 not started.

## Decisions that shape the remaining work
D19-D27, F12-F15 in decisions.md. Schema v6 locked (roadmap-phase2.md "Locked interfaces").

## Waiting on the user
- Push + Actions result for sprint 1.
- Sprint 3 (app lock) needs its own approval before start.

## Tools note
- No compiler in Claude's workspace; build = GitHub Actions after push.bat.
- Folder app\src\main\java\com\icoordinator is connected separately (depth limit 7).
- Checks: Claude rebuilds /home/claude/checks scripts if the session is new (check_migration.py logic described in D27).

## Metrics so far (phase 2)
Launches: 1 (S1). First-try pass: 5/5 Sonnet Medium. Sub-agent tokens: 113.5k. Questions asked: 6. Flags: 4 minor accepted.

## How to resume
Start a new session with the Orchestrator skill (v6.8 or later).
Attach this file (and the state files, if you have them), then say: "Resume from the handover."
