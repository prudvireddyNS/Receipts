# Receipts — handoff

**Repo:** `~/AndroidStudioProjects/ExpenseTracker` (the app is currently called TrackBudget)
**Spec:** https://claude.ai/code/artifact/6e2d5996-bed7-46ea-ac1c-5ec087b7885e
(local copy: `docs/receipts-build-spec.html` — open it in a browser)

## What this is

I built a working expense tracker. It parses bank SMS, auto-categorises, tracks a budget, has a home-screen widget. The engineering is solid. The problem is that it looks and feels like accounting software, and the people I want using it are 20–25.

So this is a rebuild of the UI layer into something called **Receipts**, aimed at UPI-native Indian Gen Z. The spec above is complete — every colour, size, font, animation timing, and line of copy is already decided, plus rendered mockups of six screens.

## You own this product

Not a formality. **You can add features, cut features, reorder the build phases, and overrule anything in the spec** — including things I sound confident about. If you get three phases in and think Goals should die or Stamps should be the whole app, make that call. You'll know things after a week in the code that neither of us knows now.

Two things I'd ask, and they're both about coherence rather than permission:

1. **Change things deliberately, in one place.** If you replace a decision, replace it properly and write down what you replaced it with. The failure mode isn't you disagreeing with the spec — it's the app drifting away from it file by file until nothing matches anything.
2. **Tell me what you decided.** Not to approve it. I just want to know what the product is.

## Why the spec is so prescriptive

Not because I don't trust your judgement. It's a division of labour: I paid for the design thinking up front so that you never have to stop mid-feature and decide what shade of grey a divider is. Every one of those micro-decisions is already made. Follow the tokens and the app will be coherent without you spending attention on it.

The **design system** is the one area where I'd rather you change things wholesale than incrementally. Pick a different palette if you hate this one — but pick it once, in `Tokens.kt`, and let it flow everywhere. Don't nudge individual colours in individual files.

## The five things I'd actually push back on

Everything else is genuinely yours. These five I'd want to argue about before you drop them:

1. **Don't touch `model/`, `data/`, `sms/`.** That's ~1,000 lines of correct domain logic — SMS dedupe, refund-netting, learned rules. It took real work to get right and it has tests. Extend it; don't rewrite it.
2. **The migration must not destroy data.** There's real data on my phone. DB v5 is additive only — new tables, no changes to `transactions`. Test the upgrade against `device-backup/final-device-state/track_budget.db`.
3. **No network calls.** The app stays fully local and offline. That's a feature, not a limitation.
4. **No shame mechanics.** No streak-loss warnings, no "we miss you" notifications, no guilt-tripping for skipping SMS permission. This is the single biggest reason budgeting apps get uninstalled, and the spec's whole emotional design depends on it.
5. **Manual entry has to be genuinely fast** — under three seconds, no typed words. Plenty of people bank somewhere the parser doesn't cover, or won't grant SMS access. The app has to be complete for them.

## How to start

**Do Phase 1 on a throwaway branch first**, before committing to the other five. It's the foundation — design tokens, three fonts, shared components, new tab shell, and Today + Ledger rebuilt against it. It's enough to hold in your hand and judge.

Then let's both look at it and decide whether the direction is right before you go further. If it feels wrong at that point, we've lost a week, not two months.

Note the repo is **not under git**. Please `git init` before you touch anything.

Two open questions I'd genuinely like your read on once you're in the code:

- **Wrapped** is scheduled for Phase 6. It's the only part of the app that travels beyond someone's own phone, so there's an argument for pulling it much earlier. Your call.
- Whether **Chill mode** (no budget, no judgement, just tracking) should be the default for everyone rather than just for people who skip onboarding. I suspect it's more popular than the spec assumes.

## What I'll be useless for

I have no design taste and I know it — that's what the spec is for. Don't ask me to pick between two visual options; ask the spec, or decide it yourself and tell me. I'm useful for product questions, real user behaviour, and anything about how the existing SMS parsing works.
