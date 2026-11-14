# Rounds 3 and 4 - Notes, Record tracker, Password manager (DONE, v2.4.1)
Orchestrator v6.8, Full path. Decisions D73-D81 in decisions-phase4.md.

## Requirements (user, 2026-11-14)
N1 Notes (Samsung Notes like): home menu row; folders; bold, italic, underline, highlight, checklist, bullet, numbered, heading, text size, text colour; images; search; pin; backup. No handwriting.
N2 Record tracker: home menu row; title, target, current (no unit); percent capped at 100; only previous value kept; all "higher is better".
N3 Password manager: home menu row; title, username, password, url (info), note; folders; app PIN/biometric on every entry; no password check, no generator; Keystore encryption, hidden by default, clipboard cleared after 30 s, screenshots blocked, master-password encrypted backup.
Round 4: coloured folder tiles + page per folder (notes and vault); divider under note title; right/centre/left alignment.

## Sprints
S6 data + logic (J1 NoteDocument, J2 RecordMath, J3 VaultCrypto, J4 personal.db, J4b vault.db, J5 Keystore + backups) - 106 tests.
S7 UI + wiring (K1 notes list, K2 editor, K3 records, K4a vault shell, K4b vault entry/session/backup, K6 wiring).
R reviews (R1 security Sonnet High, R2 compile/runtime) -> X1-X6 fixes -> 2.4.0 (CI green first build).
Round 4: F1 align model, F2 FolderGrid, F3 notes folders, F4 editor divider/align, F5 vault folders -> 2.4.1 (CI green).
