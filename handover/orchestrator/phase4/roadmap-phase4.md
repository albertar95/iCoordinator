# Roadmap phase 4 - home menu + personal accounting (v1.0, 2026-11-08)
Path: Full. Mode: Auto. No compiler here: build = push.bat + GitHub Actions (D26). All tasks Sonnet Medium (no runnable check here, V=2; 14/14 first-try history).

## Sprint 1 - data and logic (1 launch, 4 tasks)
| Task | Goal | Files | Policy |
|---|---|---|---|
| T42 | Accounting entities, DAOs, AccountingDatabase (D54, D56) | data/accounting/* (new) | Ask-first (schema) |
| T43 | Balance + monthly summary + report math, with tests using D52 numbers | domain/accounting/* + tests (new) | Flag-and-continue |
| T44 | Parmis importer: pure mapper + thin SQLite reader, test with fixture rows | data/accounting/ParmisImporter*.kt + test (new) | Ask-first (user data) |
| T45 | AccountingRepository (transactions CRUD, balances, loans, installment pay) | data/accounting/AccountingRepository.kt (new) | Flag-and-continue |

## Sprint 2 - navigation and core screens (1 launch, 4 tasks)
| T46 | Home menu screen + nav restructure (D57) | ui/home (new), AppNavHost, MainActivity | Flag-and-continue (backup first) |
| T47 | Accounting shell + dashboard: this month income/expense, funds with balances, expense/income panels | ui/accounting/dashboard (new) | Flag-and-continue |
| T48 | Transaction form: pay / receive / transfer, account picker tree with search, calculator keypad, Persian date-time | ui/accounting/tx (new) | Flag-and-continue |
| T49 | Transaction list: tabs all/pay/receive/transfer, grouped by day with day total, edit/delete | ui/accounting/list (new) | Flag-and-continue |

## Sprint 3 - loans, reports, import, review (2-3 launches)
| T50 | Loans list + detail: remaining amount/count, next due, pay installment (creates transaction), reminders via alarms | ui/accounting/loans (new), alarm hook | Flag-and-continue |
| T51 | Reports: expense by category (pie), 12-month income vs expense (bars), fund balances, top categories, date range | ui/accounting/reports (new) | Flag-and-continue |
| T52 | Settings: import .par (file picker, preview counts, confirm replace) + backup/restore includes accounting.db | BackupManager, SettingsScreen | Ask-first (backup/restore) |
| T53 | Account management: add/edit/disable accounts in tree | ui/accounting/accounts (new) | Flag-and-continue |
| R3 | Independent review, targeted on balance/import/backup paths + one broad pass | - | Sonnet High |
| F | Review fixes, versionName/versionCode bump (D51) | - | Sonnet Medium |

Acceptance for the user (phone): after import, funds and loan numbers equal the D52 check values.
