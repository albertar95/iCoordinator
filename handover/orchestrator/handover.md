# Handover - iCoordinator (Claude work state)

Prompt version: Orchestrator v6.8 - Last updated: 2026-11-06 19:14 - Mode: Auto - Path: Full

## In one minute
Last time: Phase 3 (C9-C13) built and installed as version 2.1.2 together with phase 2 (C1-C8). Install was blocked by changing signing keys; fixed with a fixed keystore (D50); user data rescued by adb and restored through the new Settings > restore. User confirmed everything is good.
Now: nothing in progress.
Next: collect new changes from C14 in changes.md; code only when the user says start (as this session did).

## Goal
iCoordinator personal planning app; changes C1-C13 delivered (changes.md).

## Where we are
Stop: delivery done (2.1.2 installed and confirmed).

## Decisions that shape the remaining work
D19-D51, F12-F17 in decisions.md. Schema v7 (D41). D38: unanswered questions take Claude's recommendation. D50: never remove app/signing/debug.keystore. D51: bump versionName/versionCode each release.

## Waiting on the user
- Nothing blocking. Open Low items: R1-04, R1-06, R2-04, R2-06. Full alarm test (PHASE2/PHASE3 guides) not reported yet.
- Question still open (D50): is the GitHub repo public? If yes, move the keystore to GitHub Secrets.

## Tools note
- No Kotlin compiler / network in Claude's workspace; build = GitHub Actions after push.bat.
- No device shell: files move by staging/committing. adb exists on the user's PC (project folder platform-tools/).

## Metrics (phase 3)
Launches: 5 (S1, S2, S3, R, F). First-try pass: 14/14 at Sonnet Medium. Sub-agent tokens: ~746k. Escalations: 0. Questions asked: 8 (answered by recommendation).

## How to resume
Start a new session with the Orchestrator skill (v6.8 or later).
Attach this file (and the state files, if you have them), then say: "Resume from the handover."
