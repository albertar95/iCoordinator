# Task board - rounds 3 and 4 (all Sonnet Medium unless noted)
| Launch | Tasks | Tokens | Duration | Result |
|---|---|---|---|---|
| S6 | J1 J2 J3 J4 J4b J5 | 189.9k | 8.1 min | all accepted, 106 tests |
| S7a | K1 K2 K3 | 215.3k | 8.1 min | accepted |
| S7b | K4a K4b K6 | 213.2k | 7.4 min | accepted; K4a F4 fixed by orchestrator (rotation re-lock) |
| R1 (Sonnet High) | vault security + restore paths | 200.0k | 4.5 min (+ rate-limit pause) | 1 High, 3 Medium, 11 Low |
| R2 | compile/runtime broad pass | 292.2k | 8.0 min | 1 High (same as R1-01), 1 Medium, 5 Low; no compile error |
| Fix | X1-X6 | 158.4k | 3.1 min | accepted, 117 tests; R1-01 and PBKDF2 600k fixed by orchestrator |
| R4 | F1-F5 | 188.1k | 4.8 min | accepted, 38 NoteDocument tests |
First-try pass: 17/17 tasks. Escalations: 0. CI: green on first push for 2.4.0 and 2.4.1.
