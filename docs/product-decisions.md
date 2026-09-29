# Receipts product decisions

> **Status note (2026-09-29).** Several 2026-08-23 entries below describe features that no longer ship: Feed, Goals, Stamps, Drops, Wrapped, the rolling-pace baseline, app modes and the "Check inbox" / SMS high-water-mark flow (there is no inbox reading, only live `RECEIVE_SMS`). They were removed in `83053e4` without a note; the `goals`, `stamps`, `dismissed_drops` and `period_snapshots` tables remain in the schema (additive migrations only) but nothing reads or writes them. The widget follows the in-app theme preference, not the platform theme. Read the 2026-09-29 section first where it conflicts.

This file records deliberate changes or clarifications to `receipts-build-spec.html` so the product does not drift through one-off implementation choices.

## 2026-08-23

### Ship the complete product in one release

The original six phases are implementation order only. The release includes the full Receipts surface: Today, Feed, Goals, Ledger, review, fast manual entry, modes, rhythms, Stamps, Wrapped, sharing, migration, and the widget.

### Chill is the new-user default

New users start with **Chill** selected in onboarding. Existing TrackBudget users migrate to **Pace**, preserving the budget-led behavior they already use. Nobody is silently moved into a different experience during upgrade.

### Sharing arrives with Drops

Share infrastructure is available to Drops as well as Wrapped. Full monthly Wrapped still depends on closed-period snapshots and earned Stamps, but sharing is not held back until the end of implementation.

### Pace guidance stays mathematically honest

The pace state still uses the existing spending math. The “₹x/day holds this” line uses remaining budget divided by remaining days; if the budget is already exhausted it reports the amount past budget instead of displaying an absolute negative allowance as spendable money.

Rolling pace remains neutral until there are at least 28 observed days and spending in three prior weeks. Its baseline is the median active-week total, adjusted by the share of active weeks, divided by seven. That preserves irregular spending frequency without letting zero-heavy daily medians collapse the baseline or treating every day like an active spending day.

### SMS titles come from the payee after “to”

For debit messages, the first meaningful payee after `to`, `paid to`, `sent to`, or an equivalent transfer phrase becomes the default receipt title. UPI/VPA prefixes are stripped, and existing generic SMS titles are backfilled once without changing amount, category, direction, or status.

### Learned rules are direction-aware

A merchant rule now records debit or credit direction. Old three-field rules still load, but they are only applied where their category is direction-compatible. This prevents a learned refund from making a later debit disappear from spending.

### Clearing transactions does not silently repopulate them

“Clear all transaction data” also removes derived Wrapped snapshots and Drop history while preserving Goals and earned Stamps. The SMS high-water mark stays in place so data does not silently return. Pull-to-refresh and “Check inbox” only process SMS IDs newer than that high-water mark; the app never bulk-imports old inbox history during normal use.

### Category presentation belongs to the UI system

The spec proposed replacing the 25 colour values in `model/Models.kt`. The existing category records remain intact; Receipts maps category IDs to the new palette and two-letter marks in `ui/Tokens.kt`. This keeps presentation out of the domain model and preserves existing behavior.

### Existing package and storage names stay

The launcher name becomes **Receipts**, but `com.prudvi.expensetracker`, `TrackBudgetApplication`, the `track_budget` preferences file, and `track_budget.db` remain unchanged. Renaming those internals would create migration risk without changing the product users see.

### Wrapped uses frozen totals and live detail

`period_snapshots` remains the source of truth for a closed month's total and budget. Category and merchant detail is reconstructed from local receipts for that month because the specified additive schema does not store a category breakdown. Historical months found during the v5 upgrade are backfilled once; their unknown historical budget is stored as `0` and shown as “Not recorded,” rather than pretending today’s budget applied then.

### Accessibility wins four close colour calls

The light-theme `Fade` token is darkened from `#6E736B` to `#5F645C`, and light-theme `Chilli` from `#DC3B23` to `#B92D19`, so small text clears 4.5:1 on both Paper and PaperRaised. Travel moves from `#2A8C82` to `#258178`, and Small shops from `#B06C33` to `#A75F27`, so their 9sp monograms can use a 4.5:1 foreground. These are centralized token changes; no composable gets a one-off substitute.

### Widget follows platform theme

The app defaults to light and supports Light, Dark, and System preferences. RemoteViews cannot consume Compose locals, so the widget uses matching light/night Android colour resources and follows the device theme.

## 2026-09-29

### What counts as spending

Only debits outside the "not spending" categories (transfers, repayments, and investments unless the Settings toggle is on) count as spent. Only a **refund** credit gives money back to the budget. Salary, top-ups and other money in are recorded but never reduce spend, so an ₹80,000 credit can no longer make a month look unspent.

### "Skip in daily pace" is gone

Every payment counts towards today's allowance, the burn-up curve and the projection, on the day it happened. The per-receipt flag, the SKIP tag, the Settings category defaults and the auto-detector were removed. The `committed` database column stays (unused) so the schema needs no migration.

### The projection

"Heading for" is the median *spending* day scaled by how often a day has spending, added to what is already spent. A median over all days collapsed to zero for anyone who spends on fewer than half their days.

### Obligations that cover the whole budget

The budget is still "set"; nothing is left to spend. The widget, the home hero and the alerts all say so instead of reading it as "no budget".

### Switching Monthly ⇄ Weekly scales the budget

By 7/30 (or 30/7), rounded to the nearest ₹10, instead of keeping the number.

### Alerts

Budget alerts are keyed by period *and* ceiling, so changing the budget mid-period is a new budget. The Settings amount field commits after typing pauses rather than on every keystroke. Per-category limits now survive saving the budget and raise their own 80% / 100% alerts. Receipt-review notifications use a default-importance channel (`review`), not a heads-up one.

### Learning merchant rules

A rule is learned only from a category the user chose, never from the app's own best guess.

### Refund matching

A refund that names a merchant links only to that merchant's purchase. An unnamed refund may fall back to an exact amount.

### SMS parsing

`credited to your a/c` is not a self-transfer (only "from your a/c … to your a/c" is). A reference number must contain a digit. `Credit Card` / `Debit Card` are not verbs. The dedupe key includes the receive time, so two identical alerts are two payments.

### Export

Settings → Data → Export writes every receipt to CSV and opens the share sheet. Backup is off and there is no network, so this is the only copy anyone can keep.
