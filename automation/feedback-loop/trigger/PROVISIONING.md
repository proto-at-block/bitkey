# Feedback-loop trigger: human setup checklist (UI-only)

The minimal steps a human performs to stand up the harvest trigger. The locked approach is the
**Builderbot automations UI** with a generic `blox-vanilla` Blox run, followed by the existing
Builderbot code-engine automation keyed off the Linear `code-engine:approved` label — **no
cash-server routine and no service-account provisioning.** Full field-by-field steps are in
[`automation-setup-ui.md`](./automation-setup-ui.md); this is the checklist.

**Nothing here has been done by the agent team.** We ship only the CLI in `automation/feedback-loop/`.

## Checklist

- [ ] **0. Devguide spot-check (human).** Skim the Builderbot automations setup guide
      (`https://devguide.blockeng.xyz/docs/tools/builderbot/automations/setup-guide`) to confirm the UI
      fields below match the current product. The page is auth-gated; this design was derived from the
      `squareup/agents` source (`world-model-builder` + `vitess-query-analysis` UI/`blox-vanilla`
      automations), which may lag the live UI.
- [ ] **1. Role access.** Ensure the operator has the `builderbot--users` role
      (`registry.sqprod.co/groups/builderbot--users`). This is the UI/operator access prerequisite —
      **not** a service account.
- [ ] **2. Create the routing rule (UI).** Automations → Routing → Create, with the merged-PR conditions,
      `blox-vanilla` label, idempotency key, and task-description prompt from `automation-setup-ui.md`.
      (Optionally also a Scheduled Trigger for backfill.)
- [ ] **2.5. Provision the LLM API key (vendor key).** The in-repo adapter
      (`automation/feedback-loop/adapters/llm_adapter.py`) is API-first: it needs
      `ANTHROPIC_API_KEY` (or `OPENAI_API_KEY` for `--provider codex`) in the run env. Claude can
      fall back to the workstation CLI; Codex is API-only and fails closed without `OPENAI_API_KEY`.
      - Create a named Anthropic key in the **Development workspace** via
        https://console.anthropic.com (SSO; see the "Anthropic" dev guide,
        `docs/tools/anthropic/getting-started`). Name it for the project, e.g.
        `bitkey-feedback-loop`.
      - Register it as a Blox vendor key: `sq blox vendor-key set anthropic` (or the Blox
        Settings UI → API Keys). Vendor keys are injected as env vars into the owning user's
        Blox workstations, including Builderbot-triggered runs executing as that user.
      - OpenAI (optional, for the codex provider): personal keys come from the
        `LDAP-based-local-keys` project (see `docs/tools/llms/getting-started`); a durable
        service key needs a go/swops project. Security approval: go/genaiapproval.
      - The trigger prompt must export `FEEDBACK_LOOP_LLM_COMMAND` (see
        `automation-setup-ui.md`); without it the run degrades to a facts-only report.
- [ ] **3. Confirm the managed run can create/update Linear issues and apply labels.** If not
      ambiently available, add a **least-privilege Linear token** to the Blox run env (the one
      possible grant; still no bespoke SA). Security note per BKW-60.
- [ ] **4. Confirm Builderbot code-engine pickup.** A BKW issue labeled `code-engine:approved`
      should start Builderbot, produce a draft PR, and receive the Builderbot PR comment in Linear.
- [ ] **5. Rollout.** Start the rule **disabled** or scoped to a test PR; first runs in CLI
      `--dry-run` (no writes); then `--execute` (Linear issue + label only); kill switch = disable
      the rule in the UI.

## Explicitly NOT required

- A `BloxRepoCommandRoutine` in `squareup/cash-server` — the generic `blox-vanilla` run covers it.
- Provisioning / pinning `sa-code-review` — the Blox run uses Builderbot/Blox-managed execution.
- Code or config landed outside `squareup/wallet` — setup is entirely in the UI.

## What the agent team did NOT do (constraints honored)

- No `squareup/cash-server` edits. No service-account creation/grants. No calls to
  `builderbot.sqprod.co` / `bbsubscriber.sqprod.co`. No routing rule or scheduled trigger registered.
  No Blox workstation provisioned.

## Unverified by the agent (flagged for the human)

- Live automations-UI fields (page auth-gated; derived from `squareup/agents` analogs).
- Whether the `blox-vanilla` execution identity can write **Linear** without an added token, and
  whether the BKW `code-engine:approved` label starts the intended Builderbot code-engine automation.
  Everything else is droppable.
- Whether **vendor keys are injected into Builderbot-triggered runs** (Glean-synthesized from the
  Blox image/runbooks: ephemeral workstations run as the triggering user with that user's vendor
  keys; `claude` CLI preinstalled for Claude fallback). Confirm in #blox / #builderbot-team.
- Outbound HTTPS to `api.anthropic.com` / `api.openai.com` from `blox-vanilla` (allowlisted in the
  Builderbot egress manifests per Glean; prod render not independently confirmed).
