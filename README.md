# Arthix

*Artha* (अर्थ, Sanskrit: wealth, purpose) + tech. A personal Android app that tracks spending from bank SMS. It sorts spends into categories, suggests a monthly budget, and reminds you about bills.

**Privacy:** your SMS and money data never leave the phone. The only network use is the update check, which downloads `version.json` and the APK from this repo's releases.

## Features (v0.2)
- **Bank SMS auto-tracking.** On first launch the app finds the banks that send you SMS, and you choose which ones to track. New SMS are checked every 3 hours, and there's also a "Sync now" button.
- **Skips non-transactions.** OTPs, promos, failed payments, "will be debited" notices and balance alerts are ignored.
- **No double counting.** Duplicate alerts are merged. Money moved between your own accounts is marked as "Own transfer", so it isn't counted as spend or income.
- **Categories that learn.** If you change a merchant's category once, every future spend from that merchant gets the new category.
- **Smart budget.** Limits come from your last 3 months of spending, checked against a 50/30/20 split of your income, with pace alerts during the month.
- **Bills.** Reminders before each due date, autopay or manual. A bill is ticked off automatically when a matching debit SMS arrives.
- **Manual entries** for cash spends.
- **Accounts and statements.** Every bank account and credit card found in SMS gets its own statement (money in, money out, last balance). You can give each one a name.
- **Frequent payee suggestions.** For example: "Paid 9 times at lunch time, about ₹110: set as Food?"
- **SMS activity screen.** Shows every bank SMS and what the app did with it (added, OTP, promo, duplicate...).
- **Possible duplicates.** A late second alert for the same payment is flagged and isn't counted until you keep it.
- **3 deep-black themes** (Cobalt, Crimson, Nebula) and a bundled Poppins font.

## Install on your phone
**Direct link (always the newest build):**
https://github.com/HarikrishnaRao14/finance-tracker/releases/latest/download/arthix.apk

1. Open the link on your phone. The APK downloads directly, with no zip and no login.
2. In India, Play Protect blocks SMS-reading apps installed from a browser. Pause it while you install: Play Store > profile > Play Protect > ⚙️ > turn off "Scan apps", install, then turn it back on.
3. After that, **Arthix updates itself.** Each time you open it, it checks this repo's latest release. If there's a new version, an "Update now" banner appears. Tap it, then tap Install.
4. Every build is signed with the same key, so updates keep your data.

**Samsung tip:** go to Settings > Apps > Arthix > Battery and choose **Unrestricted**. Also make sure the app isn't in "Sleeping apps". Otherwise the 3-hour check may not run.

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
