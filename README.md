# Khaata — Android (Kotlin + Compose + Firebase)

A native Android rebuild of the Khaata savings tracker: monthly income, day-to-day
kharcha (expenses), budgets, savings goals (bike, laptop, whatever), a people ledger
for udhaar, and analytics — all backed by Firebase Firestore so it persists and could
later sync across devices. Styled deliberately like a physical passbook rather than a
generic Material demo.

Get the latest apk 👉 https://drive.google.com/drive/folders/1I2oiQE2QTZj9puM4Bvam081MG5xJ3EVU?usp=sharing

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
- **Glance** — the home-screen quick-add widget is Compose (Glance), keeping the
  no-XML-UI rule intact.
- **WorkManager + AlarmManager** — daily/periodic reminder notifications to log
  today's expenses, configurable from in-app settings.
- **MVVM**: `FinanceRepository` (Firestore reads/writes) → `FinanceViewModel`
  (`StateFlow`s) → Composable screens

## Project layout

```
app/src/main/java/com/khaata/app/
  MainActivity.kt              App shell: auth-state listener, security gate, top bar
                                (month/year nav + picker dialog, search, "More" sheet),
                                bottom nav, sub-screen back stack, Dashboard FAB,
                                tutorial-overlay wiring
  data/model/Models.kt          Expense, RecurringExpense, Template, Budget/BudgetProgress,
                                 MonthSummary, Goal, Contribution, GoalStats, Person,
                                 LedgerEntry + date helpers
  data/model/AnalyticsModels.kt Monthly trend points, category trend deltas,
                                 analytics snapshot, goal forecasts
  data/repository/FinanceRepository.kt   All Firestore reads (as Flows) and writes:
                                          budgets, goals, people/ledgers, categories,
                                          recurring posting, templates, analytics
                                          aggregation, backup/restore
  viewmodel/FinanceViewModel.kt          Exposes StateFlows the UI collects; hard/soft
                                          validation; posts due recurring expenses on init
  util/Formatters.kt            ₹ formatting, category metadata (labels/colors/icons)
  util/ExpressionEval.kt        Tiny recursive-descent parser so amount fields double
                                 as calculators ("340+55+12" → ₹407)
  util/InputValidation.kt       Shared money-input regex/parsing helpers
  util/Exporter.kt              CSV + PDF generation (FileProvider share sheet)
  security/SecurityPreferences.kt  Device-local app-lock enabled flag
  ui/theme/Theme.kt             Khaata's ledger/passbook color palette
  ui/components/Components.kt   SummaryCard, ProgressStamp (progress ring), StatusBadge,
                                 CategoryBarRow, DatePickerField
  ui/components/ExpenseDialogs.kt  CategoryDropdown, EditExpenseDialog,
                                 DeleteExpenseDialog, ExpenseListRow — shared by the
                                 Add Entry and Search screens
  ui/screens/
    AuthScreen.kt              Email/password sign-in, sign-up, and password reset
    SecurityGateScreen.kt      Biometric/device-credential lock screen shown on launch
    DashboardScreen.kt         Income/Kharcha/Net Savings cards, category breakdown,
                                budget snapshot, goals snapshot
    AddEntryScreen.kt          Set income, log an expense, quick-add template chips,
                                see/edit/delete this month's entries
    BudgetsScreen.kt           Set per-category monthly limits, copy last month's caps,
                                see spend vs. limit, on-track / watching / over status
    AnalyticsScreen.kt         Custom Canvas-drawn charts: income/expense/savings trend,
                                category deltas month-over-month, biggest expenses,
                                goal-completion forecasts
    GoalsScreen.kt             Add goals, log contributions, see pace/status per goal
    PeopleScreen.kt            Udhaar ledger: who owes whom, give/get entries,
                                settle up, per-person history
    SearchScreen.kt            All-time search across expenses/goals/people with
                                category, amount, and date-range filters
    RecurringExpensesScreen.kt Rent/subscription templates that auto-post monthly
    CategoryManagementScreen.kt Add/rename/recolor/re-icon categories
    ExportScreen.kt            CSV/PDF export (month / custom range / all data),
                                full JSON backup + restore
    HistoryScreen.kt           Every past month, tap to jump the Dashboard there
    NotificationSettingsScreen.kt   Toggle and time-pick the daily expense-log reminder
  widget/
    AddEntryWidget.kt          Glance home-screen widget: "＋ Add" button + shortcut
                                chips for the user's own categories
    AddEntryWidgetReceiver.kt  GlanceAppWidgetReceiver binding
    CategoryCache.kt           Device-local mirror of the category list for the widget
                                and quick-add popup (no Firestore listener out there)
    QuickAddActivity.kt        Transparent quick-add popup that writes one expense
                                straight to Firestore without loading the full app
  onboarding/
    OnboardingScreen.kt        First-run guided setup wizard (income, first goal, etc.)
    OnboardingPreferences.kt   "Has onboarding been completed" flag
  tutorial/
    TutorialOverlay.kt         Per-screen contextual tooltip overlay, shown once per
                                screen (re-triggerable from the help icon in the top bar)
  notifications/
    ReminderWorker.kt          WorkManager periodic job: fires the daily reminder and
                                posts any due recurring expenses
    AlarmReceiver.kt           AlarmManager fallback/precise-time trigger
    NotificationUtils.kt       Notification channel + builder helpers (incl. the
                                "Quick add" notification action)
    ReminderPreferences.kt     Reminder enabled/time settings
```

## Firestore data model

```
users/{uid}/months/{yyyy-MM}                  { income, totalExpenses }
users/{uid}/months/{yyyy-MM}/expenses/{id}     { category, amount, note, date }
users/{uid}/budgets/{yyyy-MM_category}         { category, limitAmount, monthKey }
users/{uid}/goals/{id}                         { name, targetAmount, targetDate,
                                                  createdAt, savedAmount, monthlyContributions }
users/{uid}/goals/{id}/contributions/{id}      { amount, date }
users/{uid}/contacts/{id}                      { name, note, balance, createdAt }
users/{uid}/contacts/{id}/ledger/{id}          { amount, note, date }
users/{uid}/categories/{key}                   { label, colorArgb, iconKey, order }
users/{uid}/recurring/{id}                     { category, amount, note, dayOfMonth,
                                                  active, createdAt, lastPostedMonth }
users/{uid}/templates/{id}                     { label, category, amount, note, createdAt }
```

`uid` here is the Firebase Auth UID of the signed-in email/password account, so each
person's ledger is tied to their account rather than to a single device/install.
Firestore security rules (`firestore.rules`) restrict every read/write to
`request.auth.uid == userId`, so one account can never see another's data.

Running totals live on the parent document and are updated atomically with
`FieldValue.increment` inside the same batch/transaction as the detail row, so the
total and its rows can never disagree: `totalExpenses` on the month doc,
`savedAmount` on the goal doc, and `balance` on the contact doc. The Dashboard,
History, and People screens never fan out and sum a subcollection just to show a
number. `monthlyContributions` is a map keyed by month (`"2026-06": 3000`) stored
right on the goal doc, which is how a goal's card knows "have I put enough in *this*
month" without a second listener. Budgets are one document per category per month,
so `BudgetsScreen` and the Dashboard's budget snapshot both read a small, flat
collection instead of nested per-month subcollections.

A contact's `ledger/{id}.amount` is the **signed** delta applied to the balance —
positive = you gave (they owe you), negative = you got (you owe them). "Settle up"
just posts one offsetting `-balance` entry.

## Feature tour

- **Dashboard** — income, kharcha, and net savings for the viewed month, a category
  breakdown, a budget-status snapshot, a goals snapshot, and a floating "+" button
  that jumps to Add Entry. A thin strip under the top bar tints green/gold/rust with
  overall budget health.
- **Month/year navigation** — the top bar's month pill has chevrons for quick ±1 month
  moves, and tapping the month label itself opens a dialog with a year stepper and a
  3×4 month grid so you can jump straight to any month instead of tapping repeatedly.
  Future months are disabled; a "Today" shortcut appears whenever you're not on the
  current month.
- **Add Entry** — set the month's income, log an expense (category, amount, note,
  date via the Material 3 date picker), and review/edit/delete this month's entries.
  One-tap **quick-add templates** prefill category + amount + note for things you log
  often. Amount fields double as calculators — type `340+55+12` and it resolves with
  a live `= ₹407` preview.
- **People (udhaar ledger)** — track who owes whom: add a person, record "You gave" /
  "You got" entries, see running balances with Owes-you / You-owe / Settled status
  pills, expand per-person history, and "Settle up" in one tap. Not month-scoped.
- **Budgets** — set a monthly limit per category; progress bars flag on-track /
  watching (≥80%) / over statuses, with days-left-in-month context. A one-tap
  "copy last month's budgets" carries every cap forward into the current month.
  Budgets can only be set/deleted for the current month.
- **Analytics** — custom Canvas-drawn trend charts (income/expense/savings over time),
  category-level month-over-month deltas, biggest expenses, and goal-completion
  forecasts. Lives in the "More" sheet.
- **Goals** — create savings goals with a target amount/date, log contributions,
  and see pace (on track, behind, overdue) computed from `computeStats`.
- **Search** — one box that searches all-time expenses (notes + categories), goal
  names, and people; expense results filter further by category, min/max amount, and
  date range, with a live count and filtered total. Edit/delete inline.
- **Recurring expenses** — rent/subscription templates with a day-of-month that
  auto-post as real expenses once per month (missed months back-fill; days 29–31
  clamp to shorter months). Posting runs on app open and from the daily worker,
  race-safe via per-template Firestore transactions.
- **Custom categories** — add categories or rename/recolor/re-icon the built-ins.
  Deleting one moves its recurring templates to "Other"; past entries display as
  "Other" automatically.
- **Export / backup** — export expenses as CSV or a printable PDF for this month, a
  custom date range, or all data; or take a full JSON backup of every collection and
  restore it later (overwrites by document id — meant for rebuilding a fresh
  account). Files are shared via `FileProvider` and the system share sheet; CSV
  cells are escaped against spreadsheet formula injection.
- **Home-screen widget + quick add** — a Glance widget with a "＋ Add" button and
  shortcut chips for the user's own first few categories (custom names, colors and
  ordering included — mirrored device-locally via `CategoryCache`, since a widget
  can't hold a Firestore listener). Every tap opens `QuickAddActivity`,
  a transparent popup (chips arrive with their category preselected) that logs one
  expense straight to Firestore without loading the full app. The daily reminder
  and inactivity notifications carry the same "Quick add" action; informational
  alerts (budget warning, goal milestone) don't — they open the Budgets/Goals tab
  instead, since there's nothing to log. The popup deliberately bypasses
  the app lock — adding an expense reveals no ledger data — while *viewing* anything
  still goes through the gate; if no one is signed in it falls back to opening the
  app's sign-in.
- **History** — a scrollable list of every past month; tap one to jump the Dashboard
  there.
- **Onboarding** — a first-run guided wizard walks new users through setting up
  income and a first goal; a persisted flag ensures it only shows once.
- **In-app tutorials** — a contextual tooltip overlay per screen, shown automatically
  the first time that screen is visited and re-triggerable anytime via the help icon
  in the top bar.
- **Reminders** — an optional daily notification (WorkManager + AlarmManager) nudges
  the user to log today's expenses; time and on/off state are configurable from
  `NotificationSettingsScreen`.
- **App lock** — optional biometric/device-credential gate (`SecurityGateScreen`)
  shown before the ledger on every app open; the flag is device-local on purpose, so
  enabling it on one phone doesn't lock you out on another.

### Navigation shape

The bottom bar holds five tabs — **Dashboard, People, Budgets, Add Entry, Goals**.
Search sits behind the top-bar search icon. Everything else lives in the top-bar
"More" sheet: Analytics, History, Recurring expenses, Manage categories, Export data,
notification Settings, Reset tutorials, and Sign out — the sheet's sub-screens are
overlays with their own in-app back stack (no nav library).

## Known simplifications / good next steps

- **No email verification enforced.** Accounts can be created and used immediately;
  `FirebaseUser.sendEmailVerification()` / checking `isEmailVerified` is the natural
  next step if you want to confirm addresses are real.
- **No multi-currency.** Amounts are stored as plain `Double`, formatted as ₹.
- **The widget's chips refresh only while the app runs** — the category mirror
  (`CategoryCache`) is written from the app's live categories flow, so a category
  edit shows up on the widget the next time the list emits with the app open (in
  practice: immediately, since edits happen in the app).
- **Restore is overwrite-by-id, not a merge** — it's meant for rebuilding an empty
  account from a backup, not for reconciling two live datasets.
