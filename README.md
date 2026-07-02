# Khaata — Android (Kotlin + Compose + Firebase)

A native Android rebuild of the Khaata savings tracker: monthly income, day-to-day
kharcha (expenses), budgets, savings goals (bike, laptop, whatever), and analytics —
all backed by Firebase Firestore so it persists and could later sync across devices.
Styled deliberately like a physical passbook rather than a generic Material demo.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3) — single-activity app, no XML layouts
- **Firebase Firestore** — the database
- **Firebase Authentication (Email/Password)** — sign up and sign in with email +
  password, with inline validation, a password-visibility toggle, and a
  "Forgot password?" reset flow. `MainActivity` listens for Firebase's auth state
  via an `AuthStateListener` and swaps between `AuthScreen` and the main app
  automatically — no manual navigation calls needed after sign-in/sign-up.
- **AndroidX Biometric** — optional app-lock: if enabled, `SecurityGateScreen` sits in
  front of the app behind fingerprint/face/device-credential auth before the ledger
  is shown.
- **WorkManager + AlarmManager** — daily/periodic reminder notifications to log
  today's expenses, configurable from in-app settings.
- **MVVM**: `FinanceRepository` (Firestore reads/writes) → `FinanceViewModel`
  (`StateFlow`s) → Composable screens

## Project layout

```
app/src/main/java/com/khaata/app/
  MainActivity.kt              App shell: auth-state listener, security gate, top bar
                                (month/year nav + picker dialog, sign out), bottom nav,
                                tutorial-overlay wiring
  data/model/Models.kt          Expense, Budget/BudgetProgress, MonthSummary, Goal,
                                 Contribution, GoalStats + date helpers
  data/model/AnalyticsModels.kt Monthly trend points, category trend deltas,
                                 analytics snapshot, goal forecasts
  data/repository/FinanceRepository.kt   All Firestore reads (as Flows) and writes,
                                          incl. budgets and analytics aggregation
  viewmodel/FinanceViewModel.kt          Exposes StateFlows the UI collects
  util/Formatters.kt            ₹ formatting, expense category list/colors
  ui/theme/Theme.kt             Khaata's ledger/passbook color palette
  ui/components/Components.kt   SummaryCard, ProgressStamp (progress ring), StatusBadge,
                                 CategoryBarRow, DatePickerField
  ui/screens/
    AuthScreen.kt              Email/password sign-in, sign-up, and password reset
    SecurityGateScreen.kt      Biometric/device-credential lock screen shown on launch
    DashboardScreen.kt         Income/Kharcha/Net Savings cards, category breakdown,
                                budget snapshot, goals snapshot
    AddEntryScreen.kt          Set income, log an expense, see/delete this month's entries
    BudgetsScreen.kt           Set per-category monthly limits, see spend vs. limit,
                                on-track / watching / over status
    AnalyticsScreen.kt         Custom Canvas-drawn charts: income/expense/savings trend,
                                category deltas month-over-month, biggest expenses,
                                goal-completion forecasts
    GoalsScreen.kt             Add goals, log contributions, see pace/status per goal
    HistoryScreen.kt           Every past month, tap to jump the Dashboard there
    NotificationSettingsScreen.kt   Toggle and time-pick the daily expense-log reminder
  onboarding/
    OnboardingScreen.kt        First-run guided setup wizard (income, first goal, etc.)
    OnboardingPreferences.kt   DataStore-backed "has onboarding been completed" flag
  tutorial/
    TutorialOverlay.kt         Per-screen contextual tooltip overlay, shown once per
                                screen (re-triggerable from the help icon in the top bar)
  notifications/
    ReminderWorker.kt          WorkManager periodic job that fires the daily reminder
    AlarmReceiver.kt           AlarmManager fallback/precise-time trigger
    NotificationUtils.kt       Notification channel + builder helpers
    ReminderPreferences.kt     DataStore-backed reminder enabled/time settings
```

## Firestore data model

```
users/{uid}/months/{yyyy-MM}                  { income, totalExpenses }
users/{uid}/months/{yyyy-MM}/expenses/{id}     { category, amount, note, date }
users/{uid}/budgets/{yyyy-MM_category}         { category, limitAmount, monthKey }
users/{uid}/goals/{id}                         { name, targetAmount, targetDate,
                                                  createdAt, savedAmount, monthlyContributions }
users/{uid}/goals/{id}/contributions/{id}      { amount, date }
```

`uid` here is the Firebase Auth UID of the signed-in email/password account, so each
person's ledger is tied to their account rather than to a single device/install.
Firestore security rules (`firestore.rules`) restrict every read/write to
`request.auth.uid == userId`, so one account can never see another's data.

`totalExpenses` and `savedAmount` are kept as running totals on the parent document
(updated atomically with `FieldValue.increment`) so the Dashboard and History screens
never need to fan out and sum a subcollection just to show a number. `monthlyContributions`
is a map keyed by month (`"2026-06": 3000`) stored right on the goal doc, which is how a
goal's card knows "have I put enough in *this* month" without a second listener.
Budgets are one document per category per month, so `BudgetsScreen` and the Dashboard's
budget snapshot both read a small, flat collection instead of nested per-month subcollections.

## Feature tour

- **Dashboard** — income, kharcha, and net savings for the viewed month, a category
  breakdown, a budget-status snapshot, and a goals snapshot at a glance.
- **Month/year navigation** — the top bar's month pill has chevrons for quick ±1 month
  moves, and tapping the month label itself opens a dialog with a year stepper and a
  3×4 month grid so you can jump straight to any month instead of tapping repeatedly.
  Future months are disabled; a "Today" shortcut appears whenever you're not on the
  current month.
- **Add Entry** — set the month's income, log an expense (category, amount, note,
  date via the Material 3 date picker), and review/delete this month's entries.
- **Budgets** — set a monthly limit per category; progress bars flag on-track /
  watching (≥80%) / over statuses, with days-left-in-month context.
- **Analytics** — custom Canvas-drawn trend charts (income/expense/savings over time),
  category-level month-over-month deltas, biggest expenses, and goal-completion
  forecasts.
- **Goals** — create savings goals with a target amount/date, log contributions,
  and see pace (on track, behind, overdue) computed from `computeStats`.
- **History** — a scrollable list of every past month; tap one to jump the Dashboard
  there.
- **Onboarding** — a first-run guided wizard walks new users through setting up
  income and a first goal; a DataStore flag ensures it only shows once.
- **In-app tutorials** — a contextual tooltip overlay per screen, shown automatically
  the first time that screen is visited and re-triggerable anytime via the help icon
  in the top bar.
- **Reminders** — an optional daily notification (WorkManager + AlarmManager) nudges
  the user to log today's expenses; time and on/off state are configurable from
  `NotificationSettingsScreen`.
- **App lock** — optional biometric/device-credential gate (`SecurityGateScreen`) shown
  before the ledger on every app open.

## Known simplifications / good next steps

- **No email verification enforced.** Accounts can be created and used immediately;
  `FirebaseUser.sendEmailVerification()` / checking `isEmailVerified` is the natural
  next step if you want to confirm addresses are real.
- **No multi-currency.** Amounts are stored as plain `Double`, formatted as ₹.
- **Widget / quick-actions** (home-screen widget for a fast expense log) is planned
  but not yet wired up.