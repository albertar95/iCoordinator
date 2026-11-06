# Handover - iCoordinator (Claude work state)

Prompt version: Orchestrator v6.8 - Last updated: 2026-11-06 - Mode: Auto - Path: Full (phase 2)

## In one minute
Last time: Sprints 2 and 3 done and written to the project: shared pickers + task form, project detail (C1, C3c), goal screen (C2, C3b), tasks form/edit/contribution (C5, C6, C3a), routines (C4, C6), calendar day/week (C7), app lock (C8). Independent review found no compile error; its 2 Medium findings were fixed.
Now: waiting for the user to run push.bat, report the GitHub Actions result, then test on the phone with handover/PHASE2-TEST-GUIDE.md.
Next: if Actions fails, fix from the red step's error text (file:line). If it passes, collect phone-test feedback and fix.

## Goal
Changes C1-C8 in changes.md.

## Where we are
Stop: delivery (phase 2 code complete, untested build).

## Decisions that shape the remaining work
D19-D35, F12-F17 in decisions.md. Schema v6 locked.

## Waiting on the user
- push.bat + Actions result; phone test results.
- Open Low items: R1-04 (calendar wide range after paging far), R1-06 (add-goal error position).

## Tools note
- No compiler in Claude's workspace; build = GitHub Actions after push.bat.
- No device shell: files move by staging/committing; Claude rebuilds /home/claude/checks (kt_static.py + orchestrator tools) in a new session.

## Metrics so far (phase 2)
Launches: 5 (S1, S2A, S3, S2B, R). First-try pass: 11/11 at Sonnet Medium. Sub-agent tokens: ~846k. Escalations: 0. Questions asked: 6 (sprint 1 only).

## How to resume
Start a new session with the Orchestrator skill (v6.8 or later).
Attach this file (and the state files, if you have them), then say: "Resume from the handover."
