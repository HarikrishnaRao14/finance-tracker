# Test plan

## 1. Automated unit tests (run on every push)
| Area | File | Covers |
|---|---|---|
| SMS parser | `SmsParserTest` | UPI/card/ATM debits, credits, refunds, amount formats (Rs., INR, ₹, commas, paise), amount after verb, balance and limit not taken as the amount; ignores OTP, promo, balance alert, failed payment, "will be debited", statement due, collect request |
| Bank senders | `BankDirectoryTest` | DLT header parsing (AX-HDFCBK, JD-HDFCBK-S), phone numbers rejected |
| Categories | `CategorizerTest` | keywords, word-start matching, learned rules win, income/refund handling |
| Duplicates | `DeduperTest` | same ref, 10-minute window, different refs = different payments |
| Own transfers | `TransferMatcherTest` | debit + credit across own accounts within 60 min |
| Bills | `BillTest` | amount/date matching, day 31 in short months, paid bills, reminder window across month end |
| Budget | `BudgetEngineTest` | no history, one month, wants trim, one-off removal, refunds, income cap scaling, pace |
| Input | `FormatTest` | typed rupee amounts |

**Rule:** every bug found gets a new test that reproduces it before it is fixed.

## 2. Real SMS test (next session)
- Collect 15–20 real bank SMS with account numbers masked: debits, credits, UPI, card, ATM, refund, plus OTPs and promos.
- Add each one to `SmsParserTest` with the expected result.
- **Pass:** every transaction is parsed with the correct amount and direction, and every non-transaction is ignored.

## 3. Manual test on Samsung Galaxy A33 (Android 16)
| # | Case | Expected |
|---|---|---|
| M1 | Fresh install, allow SMS | Bank list shows your banks, with known banks pre-ticked |
| M2 | Deny SMS | App still works for manual entries; Settings can enable SMS later |
| M3 | Deny, then "don't ask again" | Settings button explains; no crash |
| M4 | Start tracking | Last 90 days imported; Home shows totals |
| M5 | Sync now twice quickly | No duplicate transactions |
| M6 | Recategorise with "Always use" | Older and future spends from that merchant change |
| M7 | Add a cash spend; delete it | Totals update both times |
| M8 | Apply the suggested budget | Limits shown with progress bars; Home tip appears |
| M9 | Add a bill due in 2 days | Notification within a day; "Mark paid" moves it to next month |
| M10 | Pay a bill (matching debit SMS) | Bill ticked off automatically |
| M11 | Switch themes; follow dark mode | Whole app recolours instantly; choice survives restart |
| M12 | Restart phone, wait 3+ hours | New SMS imported without opening the app (battery set to Unrestricted) |
| M13 | Very large font (Settings > Display) | No cut-off amounts or buttons |
| M14 | Install a newer CI build over the old one | Data kept |

## 4. One-week UAT
Compare the app against your bank statements every day.
**Pass:** at least 98% of transactions captured, 0 duplicates, and 0 OTPs or promos counted.
