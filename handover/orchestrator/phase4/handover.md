# Handover - iCoordinator phase 4 (home menu + personal accounting)
Prompt version: Orchestrator v6.8 - Last updated: 2026-11-09 00:10 - Mode: Auto - Path: Full

## In one minute
Last time: Sprint 2 accepted and committed: home menu (RootNavHost), accounting shell + dashboard + Parmis import, transaction form (picker, keypad), transaction list, AccountingFormat; MainActivity/ICoordinatorApp edited (backups handover/backup-phase4-s2). Compose code not compiled yet.
Now: sprint 2 checkpoint; waiting for the user's CI build (push.bat) and phone test (import the .par, check fund numbers = D52).
Next: fix CI compile errors if any (send the error log); then sprint 3 plan: T50 loans (+alarm hook, gate), T51 reports, T52 backup incl. accounting.db (gate: BackupManager), T53 accounts management, R3 review, F fixes + version bump (gate: build.gradle.kts).

## Goal
New home screen with two rows (مدیریت کارها / حسابداری شخصی) + accounting section: transactions, funds, this month summary, loans/installments, statistical reports; import Parmis backup. See ledger-phase4.md.

## Where we are
Stop: sprint 2 checkpoint.

## Decisions that shape the remaining work
D52 balance rule + check values; D53 defaults for Q1-Q5; D54 separate DB (approved); D58 package com.icoordinator.accounting; D59 local tooling; D60; D61 review fixes; F23 deferred to T50; D55 app theme; D56 Long toman + Persian date strings; D57 home/nav. Earlier: D26 build via Actions, D38, D50 keystore, D51 version bump.

## Waiting on the user
- Run push.bat, report CI result; install and test: home menu, import Parmis file, dashboard numbers, add/edit/delete transactions.
- Confirm S2 flags (F24-F45, recommended accept) and approve sprint 3 incl. gates (alarm files, BackupManager, build.gradle.kts version bump).

## Tasks
Counts: 9 accepted (T42-T49, T54) / 4 pending (T50-T53) + R3 + fixes.

## Metrics so far
Questions asked: 5 (defaults) + 4 (T44, D61) + 1 icon question (A). Launches: 2 (S1 136k; S2 98k blocked + 200k). First-try pass 9/9 Sonnet Medium (S2 after Type B fix).

## Files (handover/orchestrator/phase4/)
ledger-phase4.md, decisions-phase4.md, roadmap-phase4.md, task-board-phase4.md, handover.md. Container work area /home/claude/work (lost with the session; tools/kt.sh, tools/uicheck.py, harness/) - rebuild per D59 if needed. Parmis backup: user attachment (re-attach in a new session) - the importer reads it on the phone.

## How to resume
Start a new session with the Orchestrator skill (v6.8 or later).
Attach this file (and the state files, if you have them), then say: "Resume from the handover."
