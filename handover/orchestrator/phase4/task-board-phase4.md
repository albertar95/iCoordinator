# Task board - phase 4 (home menu + accounting)

## Sprint 1 - data and logic (approved by user 2026-11-08 23:19 "مرحله 1 رو شروع کن"; cost label Medium: 1 launch, Sonnet)
| Task | Name | Scores | Level | Policy | Launch | Status |
|---|---|---|---|---|---|---|
| T42 | Accounting entities, DAO, database | R1 S0 C1 V0 I2 = 4, high-stakes (data) | Sonnet Medium | Ask-first (schema) | S1 | Accepted |
| T43 | AccountingMath + MoneyFormat + tests | R1 S0 C1 V0 I2 = 4, high-stakes (money) | Sonnet Medium | Flag-and-continue | S1 | Accepted |
| T44 | Parmis mapper + reader + tests | R1 S0 C1 V0 I2 = 4, high-stakes (user data) | Sonnet Medium | Ask-first | S1 | Accepted (+D61 minor fixes; Q1-Q4 answered D61) |
| T45 | AccountingRepository | R1 S0 C1 V1 I2 = 5, high-stakes (data) | Sonnet Medium | Flag-and-continue | S1 | Accepted (+D61 minor fix) |

Work area (container): /home/claude/work. Outputs go to project at app/src/main/java/com/icoordinator/accounting/ and app/src/test/java/com/icoordinator/accounting/ (new files only) after review.

Launch S1: 136.1k sub-agent tokens, 4.9 min, 19 tool uses. First-try pass 4/4 (validation), review found 3 small issues fixed by orchestrator (D61).
Real-data harness: all D52 numbers PASS. Committed 7 main + 2 test files to app/src/{main,test}/java/com/icoordinator/accounting/ (new files only) 2026-11-08.

## Sprint 2 - home + core accounting screens (approved 2026-11-08 23:35 "پیشنهادات خوبه.مرحله 2 رو شروع کن", incl. gate for MainActivity/ICoordinatorApp; cost Medium: 1 launch Sonnet)
| Task | Name | Level | Policy | Status |
|---|---|---|---|---|
| T54 | AccountingFormat + AmountExpression + tests (pure) | Sonnet Medium | Flag-and-continue | Accepted (first try after Type B fix) |
| T46 | Home menu, RootNavHost, MainActivity/ICoordinatorApp wiring | Sonnet Medium | Flag-and-continue | Accepted (first try after Type B fix) |
| T47 | Accounting shell, dashboard, Parmis import flow | Sonnet Medium | Flag-and-continue | Accepted (first try after Type B fix) |
| T48 | Transaction form, account picker, keypad | Sonnet Medium | Flag-and-continue | Accepted (first try after Type B fix) |
| T49 | Transaction list | Sonnet Medium | Flag-and-continue | Accepted (first try after Type B fix) |
Backups before T46: work/backup-s2/{MainActivity.kt, ICoordinatorApp.kt, AppNavHost.kt} (originals also unchanged on the user's disk until commit).
16 new drawables converted by orchestrator from the user's tabler assets (tooling).
Launch S2: first run BLOCKED on missing inputs (Type B, orchestrator did not copy accepted S1 files into the work copy; 98k tokens), resumed same agent: 200k tokens, 7.3 min. All five validations re-run PASS; write scope PASS (only MainActivity.kt, ICoordinatorApp.kt changed, as approved). Named-argument check of shared components PASS. Compose code NOT compiled here: CI is the compile check.
Committed 2026-11-09: 9 code files, 1 test, 16 drawables, the 2 approved edits; backups at handover/backup-phase4-s2/.

## Sprint 3 - loans, reports, backup, accounts (user 2026-11-09 11:43 "با پیشنهادات خودت همه مراحل رو انجام بده": proceed automatically, gates named in the S2 checkpoint approved; cost Medium/High: 1 launch Sonnet Medium + review Sonnet High)
| Task | Name | Policy | Status |
|---|---|---|---|
| T55 | LoanPlanner + ReportMath + tests (pure) | Flag-and-continue | Accepted |
| T50 | Loans list/detail/form + repo.updateLoan | Flag-and-continue | Accepted |
| T51 | Reports screen + Canvas charts | Flag-and-continue | Accepted |
| T52 | AccountingBackup + InstallmentReminders | Ask-first | Accepted |
| T53 | Accounts management | Flag-and-continue | Accepted |
| T57 | Wiring: nav routes, dashboard menu, ICoordinatorApp start call | Flag-and-continue | Accepted |
Backups: work/backup-s3/ (ICoordinatorApp.kt, AccountingNavHost.kt, DashboardScreen.kt, AccountingRepository.kt).
Launch S3: 262k tokens, 10.7 min, 6/6 first-try PASS.
## Review R3 + fixes
R3A (Sonnet High, targeted money/data paths): 1 High (H1 import group balances double counted, verified), 5 Medium, 8 Low. R3B (Sonnet Medium, compile/API): no build breaker found by reading; 1 Medium (keypad sheet half-height), 3 Low. 284k + 287k tokens.
Fix launch FA-FF (Sonnet Medium, 171k tokens): fixed H1, M1-M5, L1-L6, L8, R3B-1..4; L7 (dangling installmentId after deleteLoan, duplicate loan account names) left open (harmless to balances).
Final checks: 83 unit tests pass (local Kotlin 2.0.21), real-data harness PASS, static check PASS on all accounting/UI files. Version 2.2.0 (20200). Compose code still uncompiled: CI is the build check.
Committed 2026-11-09 with backups in handover/backup-phase4-s3/.
