# iCoordinator UI redesign v2 spec (neutral pill style, Compose Material 3 1.3)

Style only is taken from the reference screenshots (neutral surfaces, pill cards, big titles, raised centre tab); no content, logo, mascot or brand colour is used. Preview: `mockup.html`, `shots/<id>.png`, `overview.png` (ten screens: s-projects, s-detail, s-weekly, s-routines-empty, s-routines, s-calendar, s-report, s-dialog, s-weekly-dark, s-projects-dark). RTL everywhere, Shamsi dates `1405/08/15`, percent `45٪`. Core icons only (`androidx.compose.material.icons`), outline versions (`Icons.Outlined.*`, `Icons.AutoMirrored.Outlined.*`); no new library. Version 1 stays in `../` unchanged.

## Colors

Use `lightColorScheme` / `darkColorScheme` with these values (M3 roles). Accent = teal; no red brand colour.

| Role | Light | Dark | Use |
|---|---|---|---|
| primary (accent) | #00696B | #4CDADB | selected nav item, primary buttons, progress, selected calendar day |
| onPrimary | #FFFFFF | #003738 | content on accent |
| primaryContainer (tint) | #DCEEEE | #1F3F40 | project letter circle |
| background | #F7F7F8 | #1C1C1E | screen background |
| surface | #FFFFFF | #2A2A2D | pill rows, cards, dialog |
| surfaceContainer (icon circle) | #F0F0F3 | #38383C | 54 dp icon circles, empty-state block, raised-tab idle |
| surfaceContainerHigh (bottom bar) | #FFFFFF | #232326 | custom bottom bar |
| surfaceVariant (progress track) | #E6E6EA | #3A3A3E | progress track, ring track |
| onSurface | #1B1B1D | #F2F2F4 | titles, outline icons |
| onSurfaceVariant | #76767C | #A3A3AA | grey subtitles, unselected labels |
| outlineVariant | #E2E2E6 | #3C3C40 | 1 dp pill outline |
| outline | #1B1B1D | #F2F2F4 | outline (secondary) pill button 1.5 dp border |
| error / onError | #BA1A1A / #FFFFFF | #FFB4AB / #410002 | delete icon, delete button |
| secondary | #52636A | #B9C9D0 | unused by the new screens (kept from Theme.kt) |

Custom constants: weekly hero gradient top `heroTint` = #D3EBEB (dark #1E4546) fading to `background` (vertical, 0% to 100%, also drawn behind the status bar). Score colours (bands unchanged: green above 80, amber 50 to 80, red below 50, grey no data): green #2E7D32 / on #FFFFFF (dark #7BD88F / #00210A); amber #F9A825 / on #1A1A1A (dark #FFCA28 / #1A1A1A); red #C62828 / on #FFFFFF (dark #FF8A80 / #410002); grey #757575 / #FFFFFF (dark #9E9E9E / #000000). Scrim black 32%. No yellow accent is used (dark mode stays teal only).

## Typography

System font. M3 roles (extend `AppTypography`):

| Role | sp / line / weight | Use |
|---|---|---|
| headlineMedium (page title) | 24 / 32 / Bold | screen title at top right |
| titleLarge | 22 / 30 / Bold | dialog title |
| titleMedium | 16 / 24 / SemiBold | row titles |
| bodyMedium | 14 / 22 / Normal | messages, report text |
| bodySmall | 13 / 20 / Normal, `onSurfaceVariant` | one-line grey subtitles |
| labelLarge | 16 / 24 / Bold | pill buttons |
| labelMedium | 12 / 16 / Medium | nav labels, quick-action labels, tags |
| labelLarge 14 Bold | 14 / 20 / Bold | section labels (small bold text above groups) |
| score number | 32 / 36 / Bold | ring centre |

## Spacing and shapes

- Side margin 16 dp (header text 20 dp); gap between rows 12 dp; section label has 8 dp inner start padding and 8 dp above.
- Pill row / card: `RoundedCornerShape(30.dp)`, surface fill, `BorderStroke(1.dp, outlineVariant)`, no elevation, padding 12 dp (start 12, end 8), min height about 80 dp. Selected row: 2 dp primary border.
- Icon circle: 54 dp `CircleShape`, `surfaceContainer`; letter circle same size with `primaryContainer` and 22 sp Bold `primary` letter.
- Buttons: `CircleShape` pills, height 56 dp (52 dp for secondary actions inside forms and dialogs), horizontal padding 28 dp. Text fields: `OutlinedTextField` with `shape = RoundedCornerShape(28.dp)`, 56 dp, border `outlineVariant`, focused `primary`.
- Quick-action circle 64 dp, 1.5 dp `outlineVariant` border, `surface` fill. Header action circle 40 dp with 1.5 dp `onSurface` border and 22 dp icon.
- Big cards (hero, calendar): 30 dp corners, padding 16 dp. Dialog: 32 dp corners, 330 dp wide, padding 24 dp. Empty-state block: 128 dp rounded square, corner 36 dp.
- Icons 24 dp default, 26 to 30 dp in circles; IconButton 48 dp.

## Shared components

- **PillRow(leadingIcon, title, subtitle, trailing)**: described under Spacing; leading = 54 dp circle (letter, outline icon or Checkbox), centre column (title `titleMedium`, subtitle `bodySmall` grey, one line), trailing = icon button / score chip. Optional extra line (progress or tags).
- **PageHeader(title, action?, onBack?)**: Row, height 64 dp, padding 20 dp; title `headlineMedium` weight 1 at the start (right); optional end (left) action: 40 dp outline circle with `Icons.Outlined.Add` (Projects, Routines only). Project detail: back `IconButton` 48 dp with `Icons.AutoMirrored.Outlined.ArrowBack` at the start before the title (kept at the start side for usability). Not a `TopAppBar`: a plain Row in the Scaffold content (screen title still present on every screen).
- **DeleteIconButton**: 48 dp `IconButton`, `Icons.Outlined.Delete` 26 dp tint `error`, always opens the one shared Delete dialog.
- **ProgressLine**: `LinearProgressIndicator`, height 6 dp, round cap, `primary`, track `surfaceVariant`.
- **PillButton(text, icon?, outline=false)**: `Button`/`OutlinedButton` with `CircleShape`, height 56 dp, `labelLarge`; primary = `primary` fill, secondary = transparent with 1.5 dp `onSurface` border.
- **EmptyState(icon, lines, button?)**: top padding 88 dp, 128 dp soft grey rounded-square block (`surfaceContainer`, corner 36) with a 64 dp outline icon in `onSurfaceVariant`, 16 dp gap, grey 15 sp text (title line then guidance line), 16 dp, PillButton.
- **ScorePill(percent)**: `Surface` full shape, height 30 dp, 14 dp padding, 13 sp Bold, score colours; null = "بدون داده" grey.
- **ConfirmDeleteDialog**: see Delete dialog.

### Bottom bar

Custom bar, NOT `NavigationBar`: a `Box` (height 96 dp incl. 24 dp gesture area, `containerColor` = surfaceContainerHigh, `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`, 1 dp `outlineVariant` top border) holding a `Row` of five equal-weight items. Order right to left: پروژه‌ها, تقویم, لاگ هفتگی (centre), روتین‌ها, گزارش هفته. Each regular item: `Column` centred, 28 dp `Icons.Outlined.*` icon + `labelMedium` label, 4 dp gap, padding top 14 dp; selected = icon and label in `primary` (label Bold), unselected = `onSurface`; no pill indicator. Centre item: label in the row like the others; icon sits in a raised circle: 64 dp `CircleShape` placed with `Modifier.offset(y = (-38).dp)` / `Box(Alignment.TopCenter)`, drawn over the bar with a 6 dp ring in `background` colour (the notch look) and a 1 dp `outlineVariant` edge when idle. Selected: fill `primary`, icon `onPrimary` 30 dp; idle: fill `surface`, icon `onSurfaceVariant`. Screens reserve 56 dp extra bottom padding for the raised circle. Hidden on Project detail. Implemented with `Row`/`Box` and `clickable` items (navigation logic unchanged from `AppNavHost`), Icons from `Icons.Outlined`. Dark: bar #232326.

## Screens

All screens: `Scaffold(containerColor = background)`, content padding 16 dp, bottom bar as above (except Project detail). `ExactAlarmBanner` is removed from `AppNavHost`; the permission prompt lives in the Weekly log hero.

### Projects

1. PageHeader "پروژه‌ها" with end action circle `Icons.Outlined.Add` (opens the existing create-project dialog; replaces the FAB, whose position collides with the raised tab).
2. `LazyColumn`, 12 dp gap, one clickable PillRow per project: leading letter circle (first letter of the name, 54 dp, `primaryContainer`, `primary` text); title (`titleMedium`); subtitle "پیشرفت: 45٪" (`bodySmall` grey); ProgressLine (6 dp) under it; trailing DeleteIconButton (`Icons.Outlined.Delete`).
3. Empty (no projects): EmptyState with `Icons.Outlined.Home`, "هنوز پروژه‌ای نداری" / "با دکمه‌ی «پروژه جدید» شروع کن", PillButton "پروژه جدید" with `Icons.Outlined.Add`.
4. Bottom bar, پروژه‌ها selected. Dark variant: `s-projects-dark`.

### Project detail

1. Header: back IconButton (`Icons.AutoMirrored.Outlined.ArrowBack`) then title = project name (`headlineMedium`). No bottom bar.
2. Progress card (30 dp pill card, padding 14 x 20 dp): Row "پیشرفت کل: 45٪" (`titleMedium`, weight 1) + "45٪" (26 sp Bold `primary`); ProgressLine.
3. Section label "اهداف" (14 sp Bold) with, at the end, grey caption "باقی‌مانده برای تخصیص: 20٪" (`bodySmall`).
4. Goal PillRows: leading 54 dp circle containing a `Checkbox` (toggle); title (strike-through and grey when done); subtitle "45٪ از پروژه"; trailing DeleteIconButton. Empty goals: EmptyState `Icons.Outlined.Check`, "هنوز هدفی اضافه نشده" / "از بخش «هدف جدید» پایین صفحه یک هدف اضافه کن" (no button).
5. Section label "هدف جدید"; pill `OutlinedTextField` "عنوان هدف"; pill `OutlinedTextField` "درصد از کل پروژه" (digits, max 3; error text `bodySmall` `error` below); primary PillButton (52 dp) `Icons.Outlined.Add` + "افزودن هدف".
6. Section label "مانیفست پروژه"; multi-line `OutlinedTextField` (min 5 lines, about 104 dp, 28 dp corners); secondary PillButton (52 dp) "ذخیره مانیفست".
7. Screen scrolls.

### Weekly log

Main screen, hero header (no TopAppBar):
1. Hero band: vertical gradient `heroTint` to `background` behind status bar and header. Row 1: "لاگ هفتگی" (`headlineMedium`). Row 2: greeting "سلام 👋" (16 sp Medium, no name). Row 3: week range "1405/08/09 تا 1405/08/15" (15 sp Bold, weight 1) and "انجام‌شده: 0 از 2" (13 sp grey) at the end. ProgressLine (6 dp, track `surface`) of done/total, 20 dp side padding.
2. Quick actions: Row `SpaceBetween`, 16 dp above, 20 dp side padding; each = 64 dp circle (white/`surface`, 1.5 dp `outlineVariant`, 28 to 30 dp outline icon, `onSurface`) + 6 dp + `labelMedium` label (centre, up to two lines, width 76 dp). Right to left: "تسک جدید" (`Icons.Outlined.Add`, accent border and icon `primary`; opens AddTaskDialog), "هفته قبل" (`Icons.AutoMirrored.Outlined.KeyboardArrowLeft`, shows pointing right), "هفته بعد" (`Icons.AutoMirrored.Outlined.KeyboardArrowRight`, pointing left), "فعال‌سازی آلارم" (`Icons.Outlined.Notifications`; shown ONLY when exact-alarm permission is missing; opens the existing settings intent; replaces the old banner; when hidden the other three keep their positions).
3. Section label "تسک‌ها".
4. Task PillRows: leading 54 dp circle with Checkbox; title (`titleMedium`, strike-through + grey when done); subtitle "شنبه 1405/08/09 - 16:32" (`bodySmall` grey); tag row (12 sp): project name in grey (only if any) and "آلارم" with 14 dp `Icons.Outlined.Notifications` in `primary` (only if alarm set); trailing DeleteIconButton.
5. Empty: EmptyState `Icons.AutoMirrored.Outlined.List`, "برای این هفته تسکی نداری" / "با دکمه‌ی «تسک جدید» اولین تسک را اضافه کن", PillButton "تسک جدید".
6. Bottom bar, لاگ هفتگی (raised centre) selected. Dark: `s-weekly-dark` (hero gradient #1E4546 to #1C1C1E, accent #4CDADB).

### Routines

1. PageHeader "روتین‌ها"; end action circle `Icons.Outlined.Add` (opens AddRoutineDialog) shown only when the list is not empty.
2. Empty (`s-routines-empty`): EmptyState block with `Icons.Outlined.Refresh`, grey text "هنوز روتینی نداری" + "با دکمه‌ی «روتین جدید» یک عادت تکرارشونده بساز", primary PillButton `Icons.Outlined.Add` "روتین جدید".
3. List (`s-routines`): PillRow per routine: leading 54 dp circle with `Icons.Outlined.Refresh` (26 dp); title; subtitle "شنبه، دوشنبه، چهارشنبه - 07:00"; second grey 12 sp line "از 1405/08/01 (بدون پایان)" or "از 1405/08/01 تا 1405/09/30"; trailing DeleteIconButton.
4. Bottom bar, روتین‌ها selected.

### Calendar

1. PageHeader "تقویم".
2. Calendar big card (30 dp, outline, padding 8 dp): header Row: IconButton `Icons.AutoMirrored.Outlined.KeyboardArrowLeft` (previous month, right side, points right), month title "آبان 1405" (18 sp Bold centred), IconButton `Icons.AutoMirrored.Outlined.KeyboardArrowRight` (next month). Weekday header ش ی د س چ پ ج (12 sp SemiBold grey; Saturday at the right). 7-column grid, cell 44 dp, corner 14 dp: today = 2 dp `primary` border + `primary` Bold text; selected = `primary` fill, `onPrimary` Bold text; 5 dp dot under days with items (`primary`; `onPrimary` when selected).
3. Section label "برنامه‌ی 1405/08/10 (یکشنبه)".
4. Entry PillRows: leading circle with Checkbox; title; subtitle "تسک - 09:00" or "روتین - 07:00"; trailing 24 dp grey icon `Icons.AutoMirrored.Outlined.List` (task) or `Icons.Outlined.Refresh` (routine).
5. Empty day: EmptyState `Icons.Outlined.DateRange`, "چیزی برای این روز نیست" / "از «لاگ هفتگی» یا «روتین‌ها» برای این روز برنامه اضافه کن" (no button).
6. Bottom bar, تقویم selected.

### Report

1. PageHeader "گزارش هفته".
2. Primary PillButton full width (56 dp) `Icons.Outlined.Add` + "ساخت گزارش هفته‌ی جاری".
3. Hero big card (selected report): Row gap 16: score ring 128 dp (`CircularProgressIndicator`, stroke 12 dp, round cap, colour by band, track `surfaceVariant`) with centre "32٪" (32 sp Bold) and "نمره" (12 sp grey); Column: range "1405/08/09 تا 1405/08/15" (`bodySmall`), 8 dp, ScorePill-style label "نمره: بد" (height 34 dp, band colour), 8 dp, report text (`bodyMedium`).
4. Section label "گزارش‌های ذخیره‌شده".
5. History PillRows (clickable select; selected = 2 dp `primary` border): leading circle `Icons.Outlined.Star`; title = range (15 sp); subtitle = report text (12 sp, one line); trailing ScorePill "32٪" / "85٪" / "62٪".
6. Empty: EmptyState `Icons.Outlined.Star`, "هنوز گزارشی ساخته نشده" / "با دکمه‌ی بالا گزارش هفته‌ی جاری را بساز" (no extra button).
7. Bottom bar, گزارش هفته selected.

### Delete dialog

Shared `ConfirmDeleteDialog(itemName, onConfirm, onDismiss)` for project, goal, task and routine deletes; scrim 32%. Built with `Dialog` + `Surface` (not the default AlertDialog look): `surface`, 32 dp corners, width 330 dp, padding 24 dp, centred content: 64 dp circle (`surfaceContainer`) with `Icons.Outlined.Delete` 32 dp tint `error`; 16 dp; title "حذف «alarm»؟" (`titleLarge`, centred); 8 dp; text "این کار قابل برگشت نیست." (`bodyMedium` grey, centred); 24 dp; Row of two 52 dp pill buttons with 12 dp gap, equal weight: secondary "انصراف" (outline, right in RTL) and "حذف" (filled `error`, `onError` text, left).

## Icons used

Outline variants; all in the core set.

- Icons.Outlined.Home: bottom bar, پروژه‌ها; Projects empty state
- Icons.Outlined.DateRange: bottom bar, تقویم; Calendar empty state
- Icons.AutoMirrored.Outlined.List: bottom bar raised centre, لاگ هفتگی; Weekly empty state; Calendar task entry icon
- Icons.Outlined.Refresh: bottom bar, روتین‌ها; routine row icon; Routines empty state; Calendar routine entry icon
- Icons.Outlined.Star: bottom bar, گزارش هفته; Report history rows; Report empty state
- Icons.Outlined.Add: Projects and Routines header action, "تسک جدید" quick action, empty-state and form buttons, report generate button
- Icons.Outlined.Delete: DeleteIconButton on every row; Delete dialog icon
- Icons.Outlined.Notifications: "فعال‌سازی آلارم" quick action; alarm tag on task rows
- Icons.Outlined.Check: Project detail goals empty state; checked state of the Checkbox is the standard M3 one
- Icons.AutoMirrored.Outlined.ArrowBack: Project detail back button
- Icons.AutoMirrored.Outlined.KeyboardArrowLeft: "هفته قبل" quick action, "ماه قبل" calendar button
- Icons.AutoMirrored.Outlined.KeyboardArrowRight: "هفته بعد" quick action, "ماه بعد" calendar button
