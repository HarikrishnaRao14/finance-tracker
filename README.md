# Finance Tracker

A personal Android app that tracks spending from bank SMS. It sorts spends into categories, suggests a monthly budget, and reminds you about bills.

**Privacy:** the app has no internet permission. Your SMS and money data never leave the phone.

## Features in v0.1 (prototype)
- **Bank SMS auto-tracking.** On first launch the app finds the banks that send you SMS, and you choose which ones to track. New SMS are checked every 3 hours, and there's also a "Sync now" button.
- **Skips non-transactions.** OTPs, promos, failed payments, "will be debited" notices and balance alerts are ignored.
- **No double counting.** Duplicate alerts are merged. Money moved between your own accounts is marked as "Own transfer", so it isn't counted as spend or income.
- **Categories that learn.** If you change a merchant's category once, every future spend from that merchant gets the new category.
- **Smart budget.** Limits come from your last 3 months of spending, checked against a 50/30/20 split of your income, with pace alerts during the month.
- **Bills.** Reminders before each due date, autopay or manual. A bill is ticked off automatically when a matching debit SMS arrives.
- **Manual entries** for cash spends.
- **3 themes** (Forest, Midnight, Saffron), plus an option to follow the phone's dark mode.

## Install on your phone
1. Open the **Actions** tab on GitHub and pick the latest green **CI** run.
2. Under **Artifacts**, download `finance-tracker-debug-N` and unzip it.
3. Copy the `.apk` to your phone, open it, and allow "Install unknown apps" when asked.
4. Newer builds install over older ones and keep your data. Every build is signed with the same key.

**Samsung tip:** go to Settings > Apps > Finance Tracker > Battery and choose **Unrestricted**. Also make sure the app isn't in "Sleeping apps". Otherwise the 3-hour check may not run.

## Project layout
```
app/src/main/java/com/harikrishnarao/financetracker/
  domain/   pure logic: models, BudgetEngine, Deduper, TransferMatcher, BillMatcher
  sms/      BankDirectory, SmsParser, Categorizer, SmsReader
  data/     Room database, DataStore settings, FinanceRepository
  work/     3-hourly SMS sync, daily bill reminders, notifications
  ui/       Compose screens (onboarding, home, spends, budget, bills, settings), themes
app/src/test/  JVM unit tests for all the pure logic
```

## Build environments (GitHub)
| Environment | Workflow | When it runs | Output |
|---|---|---|---|
| `development` | `.github/workflows/ci.yml` | every push and pull request | unit tests, lint, debug APK (artifact) |
| `release` | `.github/workflows/release.yml` | pushing a tag like `v0.2.0` | signed release APK attached to a GitHub Release |

Before the first release, add these secrets to the **release** environment: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.

## Local build
Requires JDK 17 and the Android SDK (or Android Studio).
```
./gradlew testDebugUnitTest assembleDebug
```

See [docs/TEST_PLAN.md](docs/TEST_PLAN.md) for the full test plan.
