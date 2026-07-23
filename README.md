<div align="center">

# 📒 Khaata

**Your money, kept like a proper khaata — not a spreadsheet.**

Income, kharcha, budgets, savings goals, and udhaar (who-owes-whom), all in one
native Android app. No ads, no tracking, no subscription — just a passbook-styled
ledger that's actually pleasant to use every day.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](#)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black)](#)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

[Download the APK](https://drive.google.com/drive/folders/1I2oiQE2QTZj9puM4Bvam081MG5xJ3EVU?usp=sharing) · [Features](#-features) · [Getting started](#-getting-started) · [Contributing](#-contributing)

</div>


## What is Khaata?

Most budgeting apps feel like a spreadsheet wearing a Material Design skin. Khaata
is built to feel like the physical passbook it's named after — every screen,
color, and interaction is deliberately styled that way instead of borrowing
generic templates.

Under the hood it's a full Firebase-backed app: your data lives in your own
Firestore-secured account, so it survives a phone swap and can't be read by
anyone else — not even by another Khaata user.

## ✨ Features

- **Dashboard** — income, kharcha, and net savings for the month at a glance, a
  category breakdown, a budget-health snapshot, and your goals' progress. A
  colored strip under the top bar tells you at a glance whether the month is
  healthy, tight, or over budget.
- **Add Entry** — log an expense in a few taps: category, amount, note, and date.
  One-tap **quick-add templates** for things you buy often, and amount fields that
  double as a calculator — type `340+55+12` and it resolves to `₹407` live.
- **Budgets** — set a monthly limit per category and watch on-track / watching /
  over status update as you spend, with a one-tap "copy last month's budgets."
- **Goals** — save toward a bike, a laptop, whatever — set a target and date, log
  contributions, and see whether you're on pace, behind, or overdue.
- **People (udhaar ledger)** — track who owes whom without a separate notebook:
  add a person, log "you gave" / "you got," see running balances, and settle up
  in one tap.
- **Analytics** — custom-drawn charts for income/expense/savings trends,
  category-level month-over-month changes, your biggest expenses, and
  goal-completion forecasts.
- **Search** — one box, searches every expense, goal, and person you've ever
  logged, with category/amount/date filters.
- **Recurring expenses** — rent, subscriptions, anything on a schedule — set it
  once and it posts itself every month, including catching up on months you
  missed.
- **Custom categories** — the built-in categories aren't the limit; add, rename,
  recolor, or re-icon your own.
- **Export & backup** — CSV or a printable PDF for a month, a custom range, or
  everything; or take a full JSON backup and restore it later.
- **Home-screen widget** — add an expense without opening the app, straight from
  a Glance widget with shortcut chips for your own categories.
- **Daily reminders** — an optional nudge so today's spending doesn't go unlogged.
- **App lock** — fingerprint/face/device-credential gate in front of the ledger,
  set per device.
- **Guided onboarding + in-app tutorials** — new to Khaata? A first-run wizard and
  contextual tooltips (re-triggerable anytime) get you oriented fast.

## 🔒 Privacy & security

Every account is backed by its own Firebase Auth identity, and Firestore security
rules enforce that a user can only ever read or write their own data — there's no
shared database, no admin backdoor, and no analytics SDK phoning home. The
optional app lock is device-local by design, so turning it on for one phone
never locks you out of another.

## 🛠 Built with

- **Kotlin** + **Jetpack Compose** (Material 3) — 100% Compose, no XML layouts
- **Firebase Authentication** — email/password sign-up, sign-in, and password reset
- **Firebase Firestore** — real-time, per-user data storage
- **Glance** — the home-screen widget, kept in Compose rather than classic
  App Widgets
- **WorkManager + AlarmManager** — reliable daily reminders and recurring-expense
  posting
- **AndroidX Biometric** — the optional app lock
- **MVVM** architecture throughout

## 🚀 Getting started

Want to run Khaata yourself, or contribute to it? Here's the whole setup:

### Prerequisites

- Android Studio (latest stable)
- A free [Firebase](https://console.firebase.google.com) project

### 1. Clone the repo

```bash
git clone https://github.com/AbhishekGhume/Khaata.git
cd Khaata
```

### 2. Connect Firebase

1. Create a project in the [Firebase console](https://console.firebase.google.com).
2. Add an Android app with the package name `com.khaata.app`.
3. Download the generated `google-services.json` and place it at `app/google-services.json`.
4. In the console, enable **Authentication → Email/Password** and **Firestore Database**.
5. Deploy the included security rules so every user's data stays isolated:

   ```bash
   firebase deploy --only firestore:rules
   ```

### 3. Build & run

Open the project in Android Studio and hit **Run**, or from the command line:

```bash
./gradlew installDebug
```

Minimum SDK 26 (Android 8.0)+.

## 🤝 Contributing

Contributions are very welcome, whether that's a bug fix, a new feature, or a
docs improvement.

1. Fork the repo and create a branch off `main`.
2. Make your change, keeping to the existing Compose/MVVM style.
3. Open a pull request describing what changed and why.

If you're planning something larger, opening an issue first to discuss the
approach is a good idea before investing a lot of time.
