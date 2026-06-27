# Khaata — Android (Kotlin + Compose + Firebase)

A native Android rebuild of the Khaata savings tracker: monthly income, day-to-day
kharcha (expenses), and savings goals (bike, laptop, whatever) — all backed by
Firebase Firestore so it persists and could later sync across devices.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3) — single-activity app, no XML layouts
- **Firebase Firestore** — the database
- **Firebase Anonymous Auth** — gives every install a private, stable user ID without
  needing a login screen
- **MVVM**: `FinanceRepository` (Firestore reads/writes) → `FinanceViewModel`
  (`StateFlow`s) → Composable screens

## Project layout

```
app/src/main/java/com/khaata/app/
  MainActivity.kt              App shell: auth bootstrap, top bar with month nav, bottom nav
  data/model/Models.kt         Expense, MonthSummary, Goal, Contribution, GoalStats + date helpers
  data/repository/FinanceRepository.kt   All Firestore reads (as Flows) and writes
  viewmodel/FinanceViewModel.kt          Exposes StateFlows the UI collects
  util/Formatters.kt           ₹ formatting, expense category list/colors
  ui/theme/Theme.kt             Khaata's ledger/passbook color palette
  ui/components/Components.kt  SummaryCard, ProgressStamp (progress ring), StatusBadge,
                                CategoryBarRow, DatePickerField
  ui/screens/
    DashboardScreen.kt   Income/Kharcha/Net Savings cards, category breakdown, goals snapshot
    AddEntryScreen.kt    Set income, log an expense, see/delete this month's entries
    GoalsScreen.kt       Add goals, log contributions, see pace/status per goal
    HistoryScreen.kt     Every past month, tap to jump the Dashboard there
```

## Firestore data model

```
users/{uid}/months/{yyyy-MM}                 { income, totalExpenses }
users/{uid}/months/{yyyy-MM}/expenses/{id}    { category, amount, note, date }
users/{uid}/goals/{id}                        { name, targetAmount, targetDate,
                                                 createdAt, savedAmount, monthlyContributions }
users/{uid}/goals/{id}/contributions/{id}     { amount, date }
```

`totalExpenses` and `savedAmount` are kept as running totals on the parent document
(updated atomically with `FieldValue.increment`) so the Dashboard and History screens
never need to fan out and sum a subcollection just to show a number. `monthlyContributions`
is a map keyed by month (`"2026-06": 3000`) stored right on the goal doc, which is how a
goal's card knows "have I put enough in *this* month" without a second listener.

## Setting it up

### 1. Create a Firebase project
Go to the [Firebase console](https://console.firebase.google.com), create a project,
then **Add app → Android**, using package name `com.khaata.app`.

### 2. Turn on what the app needs
- **Build → Authentication → Get started → Sign-in method → Anonymous → Enable**
- **Build → Firestore Database → Create database** (start in production mode)
- Once created, go to the **Rules** tab and paste in `firestore.rules` from this repo
  (it restricts every user to their own `users/{uid}/...` data).

### 3. Replace the placeholder config
Download the real `google-services.json` from your Firebase project (Project settings
→ your Android app), and **overwrite** `app/google-services.json` in this project —
the one checked in here is a non-functional placeholder so the project compiles before
you've connected your own Firebase project.

### 4. Open and run
Open the `KhaataApp` folder in Android Studio (Ladybug or newer), let Gradle sync,
plug in a device or start an emulator (API 26+), and run. First launch signs the
device in anonymously — that's the "no login screen" you'll see skipped automatically.

> Android Studio's Image Asset tool can regenerate a sharper/adaptive launcher icon
> any time — a basic one is already included so the project builds as-is.

## Design notes

- **Palette**: navy ink, warm paper, ledger green / gold / rust — meant to feel like a
  physical passbook rather than a generic Material demo. It's intentionally light-only.
- **Responsiveness**: forms and the summary/goal cards use `FlowRow`, so on a phone
  they stack one-per-line and on a tablet or in landscape they naturally wrap into
  two or three per row — no hard-coded breakpoints.
- **Dates**: every date field opens the Material 3 `DatePicker` rather than a raw text
  field, to keep `yyyy-MM-dd` formatting always valid.

## Known simplifications / good next steps

- **Anonymous auth is per-install.** Uninstalling the app or switching devices starts
  a fresh, empty ledger. The natural upgrade is `FirebaseAuth.linkWithCredential` to
  let someone turn their anonymous account into a real Google/email account later
  without losing data — wire that up if you want the same ledger on multiple phones.
- **No multi-currency.** Amounts are stored as plain `Double`, formatted as ₹.
- **Deleting a goal** batch-deletes its `contributions` subcollection first, since
  Firestore doesn't cascade-delete subcollections automatically.
- Library versions in the Gradle files were current as of early 2025/2026 — Android
  Studio will flag newer patch versions if any exist; bumping them is safe.
