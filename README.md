# Khaata — Android (Kotlin + Compose + Firebase)

A native Android rebuild of the Khaata savings tracker: monthly income, day-to-day
kharcha (expenses), and savings goals (bike, laptop, whatever) — all backed by
Firebase Firestore so it persists and could later sync across devices.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3) — single-activity app, no XML layouts
- **Firebase Firestore** — the database
- **Firebase Authentication (Email/Password)** — sign up and sign in with email +
  password, with inline validation, a password-visibility toggle, and a
  "Forgot password?" reset flow. `MainActivity` listens for Firebase's auth state
  via an `AuthStateListener` and swaps between `AuthScreen` and the main app
  automatically — no manual navigation calls needed after sign-in/sign-up.
- **MVVM**: `FinanceRepository` (Firestore reads/writes) → `FinanceViewModel`
  (`StateFlow`s) → Composable screens

## Project layout

```
app/src/main/java/com/khaata/app/
  MainActivity.kt              App shell: auth-state listener, top bar (month nav + sign out), bottom nav
  data/model/Models.kt         Expense, MonthSummary, Goal, Contribution, GoalStats + date helpers
  data/repository/FinanceRepository.kt   All Firestore reads (as Flows) and writes
  viewmodel/FinanceViewModel.kt          Exposes StateFlows the UI collects
  util/Formatters.kt           ₹ formatting, expense category list/colors
  ui/theme/Theme.kt             Khaata's ledger/passbook color palette
  ui/components/Components.kt  SummaryCard, ProgressStamp (progress ring), StatusBadge,
                                CategoryBarRow, DatePickerField
  ui/screens/
    AuthScreen.kt        Email/password sign-in, sign-up, and password reset
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

`uid` here is the Firebase Auth UID of the signed-in email/password account, so each
person's ledger is tied to their account rather than to a single device/install.

`totalExpenses` and `savedAmount` are kept as running totals on the parent document
(updated atomically with `FieldValue.increment`) so the Dashboard and History screens
never need to fan out and sum a subcollection just to show a number. `monthlyContributions`
is a map keyed by month (`"2026-06": 3000`) stored right on the goal doc, which is how a
goal's card knows "have I put enough in *this* month" without a second listener.

## Design notes

- **Palette**: navy ink, warm paper, ledger green / gold / rust — meant to feel like a
  physical passbook rather than a generic Material demo. It's intentionally light-only.
- **AuthScreen** uses the same palette as the rest of the app (navy backdrop, paper
  card) so signing in doesn't feel like a different product bolted onto the front.
- **Responsiveness**: forms and the summary/goal cards use `FlowRow`, so on a phone
  they stack one-per-line and on a tablet or in landscape they naturally wrap into
  two or three per row — no hard-coded breakpoints.
- **Dates**: every date field opens the Material 3 `DatePicker` rather than a raw text
  field, to keep `yyyy-MM-dd` formatting always valid.

## Known simplifications / good next steps

- **No email verification enforced.** Accounts can be created and used immediately;
  `FirebaseUser.sendEmailVerification()` / checking `isEmailVerified` is the natural
  next step if you want to confirm addresses are real.
- **No multi-currency.** Amounts are stored as plain `Double`, formatted as ₹.
- **Deleting a goal** batch-deletes its `contributions` subcollection first, since
  Firestore doesn't cascade-delete subcollections automatically.
- Library versions in the Gradle files were current as of early 2025/2026 — Android
  Studio will flag newer patch versions if any exist; bumping them is safe.
