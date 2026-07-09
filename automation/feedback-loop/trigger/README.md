# Feedback-loop trigger: merged PR harvest → Linear label → Builderbot

**Decision (locked):** feedback-loop code generation uses the existing Builderbot code-engine
automation path: create/update a Linear issue and apply the `code-engine:approved` label. Builderbot
then opens the draft PR and comments back in Linear.

The merged-PR harvest trigger can still be a Builderbot UI automation that dispatches a generic
**`blox-vanilla`** Blox run. That run's task description tells the Blox AI agent to clone
`squareup/wallet` and invoke the substrate-agnostic CLI in `automation/feedback-loop/`. It does not
own code edits or PR creation.

> **DESIGN ONLY.** Nothing here is registered or executed. The human configures any merged-PR harvest
> automation in the UI per [`automation-setup-ui.md`](./automation-setup-ui.md) /
> [`PROVISIONING.md`](./PROVISIONING.md). The agent team ships only the CLI in
> `automation/feedback-loop/`.

## Why this shape

The CLI is the core and is substrate-agnostic (`run --pr <url>` / `run --backfill`). The harvest
trigger is a thin wrapper configured outside the pipeline. Generated code work is delegated through
Linear labels, which keeps the feedback-loop CLI out of branch checkout, file mutation, PR creation,
and PR follow-up.

The wallet repo does not own a scheduler for the feedback loop. Event-driven runs and optional
scheduled backfills are Builderbot automation configuration; the CLI owns deterministic run keys and
Linear reconciliation so repeated invocations are safe.

## Flow Summary

```
GitHub: pull_request closed (merged==true)
        │  webhook
        ▼
Builderbot routing rule (created in the UI: Automations → Routing)
  conditions: action=="closed" && pull_request.merged=="true" && repo==squareup/wallet
  outcome label: blox-vanilla   (dispatches a generic Blox workstation + AI agent)
  task description = the prompt (clone wallet; run the CLI)
        │
        ▼
Blox workstation — generic blox-vanilla run using Builderbot/Blox-managed execution
  agent follows the prompt: clone squareup/wallet, then
  python -m feedback_loop run --pr "$PR_URL" --execute
        │
        ▼
CLI pipeline: harvest → classify → cluster → propose → emit
  emit → Linear cluster issue with code-engine:approved label
        │
        ▼
Builderbot code-engine automation
  opens draft PR, runs requested validation, comments back in Linear
```

## Files

- [`automation-setup-ui.md`](./automation-setup-ui.md) — **primary**: exact UI steps/fields + the
  identity/permission notes the human configures.
- [`PROVISIONING.md`](./PROVISIONING.md) — the minimal human checklist (UI + two capability confirms).
- [`routing-rule.json`](./routing-rule.json) — **advanced/alternative**: equivalent rule body for the
  Builderbot API, for anyone who prefers API/IaC over the UI. Not required for the UI path.

For exact UI fields, identity notes, scheduled backfill, and rollout checklist, use
[`automation-setup-ui.md`](./automation-setup-ui.md) as the canonical trigger setup doc.

## See also

- `automation/feedback-loop/README.md` — the CLI this automation invokes.
- `docs/docs/automation/feedback-loop-checks-integration.md` (BKW-71) — Builderbot/Blox findings.
- BKW-80 Linear comment — substrate evaluation + this reframe.
- `squareup/agents`: `skills/world-model-builder/references/scheduling.md`,
  `skills/vitess-query-analysis/references/builderbot-scheduled-automation.md` — the UI+`blox-vanilla`
  pattern this follows.
