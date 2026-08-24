# Receipts product decisions

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

### Learned rules are direction-aware

A merchant rule now records debit or credit direction. Old three-field rules still load, but they are only applied where their category is direction-compatible. This prevents a learned refund from making a later debit disappear from spending.

### Clearing transactions does not silently repopulate them

“Clear all transaction data” also removes derived Wrapped snapshots and Drop history while preserving Goals and earned Stamps. The SMS high-water mark stays in place so data does not silently return; an explicit pull-to-rescan can still rebuild receipts from the last 90 days.

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
