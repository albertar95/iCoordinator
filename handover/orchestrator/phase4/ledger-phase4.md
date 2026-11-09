# Ledger - iCoordinator phase 4: home menu + personal accounting (Orchestrator v6.8, Full, Auto)

## Original goal (verbatim, Persian)
1) "من از برنامه حسابداری شخصی پارمیس استفاده میکنم.این فایل بک اپشه ببین میتونی بخونیش.اگه بتونی بخونیم یه بخش حسابداری شخصی به icoordinator اضافه کنیم"
2) "پس اگه اینجوریه اول باید صفحه اول برنامه رو تغییر بدیم.صفحه اول برنامه اینجور باشه که دو تا دکمه به صورت لیستی با ui برنامه نشون بده.اولیش مدیریت کار ها باشه با کلیک روش همین برنامه الان که روش کار کردیم رو بیاره و دومین دکمه حسابداری شخصی که وارد بخش حسابداری میشه.
بخش حسابداری ثبت تراکنش،نمایش وضعیت فعلی صندوق ها،گزارش اجمالی هزینه و درامد این ماه،بخش وام ها و اقساط و گزارش های اماری متنوع.اگه بخوای میتونم یکسری اسکرین شات از اپ پارمیس بهت بدم"

## Requirements
- R1: new home screen, two list buttons in app UI style: "مدیریت کارها" (opens current app) and "حسابداری شخصی". Source: goal 2.
- R2: record transactions. Source: goal 2 "ثبت تراکنش".
- R3: current state of funds (صندوق ها). Source: goal 2.
- R4: summary of this month's expense and income. Source: goal 2.
- R5: loans and installments. Source: goal 2.
- R6: various statistical reports. Source: goal 2.
- R7: read Parmis backup (.par). Source: goal 1. Status: verified readable.
- R8 (implied): existing task-management features unchanged. Reason: goal 2 "همین برنامه الان".

## Parmis backup facts (from inspection, 2026-11-08)
- .par = SQLite 3 (user_version 13). Tables with data: Activity 451 (transactions, Persian dates 1403/04/30..1405/08/15, Act_amount, PayPath/RecivePath "root,sub,leaf"), accounts 140 (tree; roots: 1 هزینه ها, 2 درآمد ها, 3 صندوق ها, 4 بانک ها, 5 بدهکاران, 6 بستانکاران, 7 دارایی ها, 8 بدهی ها, 10 غیره, 161 اشخاص, 162 سرمایه), Installment 18 + InstDetails 314 (loans), Budget 36, Pattern 11, smsTable 74, FiscalYear 1, UnregisteredBankSms 11.
- Balance rule settled and verified against Parmis screenshots (D52).
- One path uses "8-167-208" form instead of commas.

## Constraints (from earlier phases)
- Kotlin, Compose, Room, Persian RTL. Schema v7 (D41). Build = push + GitHub Actions (D26). Never remove app/signing/debug.keystore (D50). Bump versionName/versionCode (D51). Backup/restore exists (BackupManager).
- No device shell: work on staged copies; commit back after backup.

## Tools check
- Read .par: yes (python sqlite3). Project folder: stage/commit only. Sub-agents: yes. Compiler: no (GitHub Actions).

## Open questions (asked 2026-11-08)
Q1-Q5 not answered -> recommendations taken per D38 (D53). Screenshots received 2026-11-08 (9: home, income/expense panels, funds, transactions list, add sheet, payment form, calculator keypad, loans list, account picker).
