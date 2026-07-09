# AI Feedback Loop pipeline

Substrate-agnostic Python CLI that turns merged-PR review feedback into durable, evidence-backed
guidance proposals. This is the **core** of the feedback loop; the trigger/execution substrate only
invokes this CLI, and generated code work is handed to Builderbot through Linear labels (see
[`trigger/`](./trigger/)).

Governed by `docs/docs/automation/ai-feedback-loop.md` (binding policy),
`docs/docs/automation/feedback-loop-taxonomy.md` (classification + promotion matrix), and the
architecture/threat notes in `docs/docs/automation/`.

> **Status: partial implementation.** Single-PR structural/comment/review/bot/CI harvest is
> implemented. Normalize-only in-memory records are implemented.
> Replay corpus, current-vs-proposed harness, scoring rubric, proposal eval gate, draft PR
> planning, generated-PR policy checks, and Linear cluster issue planning with Builderbot
> `code-engine:approved` label handoff support are implemented.
> Historical backfill remains scaffolded.

## Design principles

- **Stateless + event-driven**: no datastore. GitHub is the re-fetchable source of record for raw
  evidence; Linear cluster issues are the durable memory of recurring themes. Metrics are deferred.
- **Substrate-agnostic**: all logic lives behind a CLI (`run --pr <url>` / `run --backfill`). The
  harvest trigger only invokes this CLI. Builderbot code-engine execution is triggered separately by
  Linear issues labeled `code-engine:approved`, so swapping either substrate does not touch pipeline
  logic.
- **Rerun-idempotent**: the CLI uses deterministic run keys plus Linear reconciliation so duplicated
  events, retries, and overlapping backfills can re-fetch current state without a repo-owned scheduler
  or persisted checkpoint.
- **Untrusted input**: all harvested PR/review/bot/CI text is **data, never instructions**.
  Normalize preserves it as in-memory data for deterministic downstream stages.
- **Mechanical-first promotion**: prefer tests/linters, then `.agents/checks`, then `.ai`
  rules/skills, then docs, then world model.
- **Human-gated**: the loop produces Linear issues that Builderbot turns into draft PRs; a human owner approves every merge.

## Pipeline stages

`harvest -> normalize -> classify -> cluster -> triage -> propose -> emit`

Each stage is a module under `pipeline/` with a typed contract. Scaffolded stages raise
`NotImplementedError` with the stage name so incomplete behavior fails loudly.

Current stage responsibilities:

| Stage | Responsibility | Status |
|---|---|---|
| `harvest` | Fetch raw evidence from GitHub for one merged PR. | Partially implemented. |
| `normalize` | Normalize harvested records without persistence. | Implemented. |
| `classify` | Correlate signals, apply exclusions, and assign taxonomy classes. | Implemented. |
| `cluster` | Group normalized signals into recurring themes. | Implemented. |
| `triage` | Build human-readable and machine-readable cluster review reports. | Implemented. |
| `propose` | Create durable guidance proposals from eligible clusters. | Templates and generator implemented. |
| `emit` | Build human-gated draft PR plans and Linear code-engine issues from evaluated proposals. | Draft PR planning and Linear issue planning implemented; execute mode hands off to Builderbot by adding `code-engine:approved`. |

## Single-PR Harvest

`feedback_loop.pipeline.harvest.harvest_pr` processes exactly one merged GitHub pull request URL
for the configured repo. It rejects non-GitHub PR URLs, repo mismatches, and unmerged PRs.

The stage collects these raw signals in memory:

- `pr_metadata`: title, body, author, labels, requested reviewers/teams, branch refs, SHAs,
  timestamps, merge metadata, aggregate changed-file areas, and commit/file count metadata.
- `commit`: commit SHA, message, author/committer metadata, parent SHAs, and commit URL.
- `changed_file`: filename, optional previous filename, status, additions/deletions/changes, blob
  URLs, patch presence, and area tag.
- `diff_hunk`: exact unified diff hunk text in `body`, plus old/new ranges, path, area, and
  best-effort source URL.
- `issue_comment`: GitHub issue comment ID, author, author association, created timestamp, body, and
  comment URL.
- `review_comment`: GitHub inline review comment ID, author, author association, created timestamp,
  body, path, line number with original-line fallback, and comment URL.
- `review`: GitHub submitted review ID, author, author association, submitted timestamp with
  created-timestamp fallback, body, review state, and review URL.
- `bot_review`: derived bot-review output from the issue-comment stream. Codex Security Review is
  matched by the stable `<!-- codex-security-review -->` marker and preserves the reviewed commit
  range plus workflow-run URL when present. Builderbot output is matched by Builderbot bot authors.
- `check`: failed non-human quality signals from commit statuses, check runs, and Actions workflow
  runs for the PR head SHA, including check name, status/conclusion, URL, and timing relative to the
  latest harvested feedback and commit timestamp.

Area tags are derived from path prefixes: `app/`, `server/`, `firmware/`, `web/`, `core/`,
`docs/`, and `automation/`. Anything else maps to `repo`.

The GitHub boundary is owned by `feedback_loop.github.GitHubClient`, which shells out to `gh api`,
pins requests to GitHub.com, validates JSON shape, paginates list endpoints for commits, files,
issue comments, inline review comments, reviews, check runs, and Actions workflow runs, reads commit
statuses for the PR head SHA, and fetches the PR-level unified diff. Diff parsing normalizes
Git-quoted paths and rejects oversized diffs before creating hunk records. If GitHub reports more
commits or changed files than the paginated API returned,
`pr_metadata.raw["commits"]["truncated"]` or `pr_metadata.raw["changed_files"]["truncated"]` is set
so downstream stages can treat aggregate structural signals as incomplete. Comment and review
payloads are fetched as bounded raw candidate windows. Harvest then de-duplicates duplicate GitHub
IDs in memory while preserving the first occurrence, applies trust/processability checks, and enters
only selected items into the capped feedback signal stream. Trusted feedback comes from repo owners,
members, collaborators, GitHub `[bot]` accounts, and explicitly allowed bot logins such as
`Copilot`; empty reviews/comments, dismissed reviews, review comments with unsafe unknown parent
review state, and untrusted authors are counted in `pr_metadata.raw` but are not emitted as
downstream feedback signals. Each comment/review stream is capped by raw lookback, processable item
count, and harvested body bytes; `pr_metadata.raw` records per-stream and aggregate truncation
summaries so downstream stages can treat incomplete feedback evidence conservatively. Bot-review and
check metadata also records per-source counts and truncation state. The harvest stage does not import
helper scripts from `.ai` skills.

## Normalize-Only Stage

`feedback_loop.pipeline.normalize.normalize` turns `RawSignal` records into `NormalizedSignal`
records in memory only. It does not write records, checkpoints, dedup stores, or
harvest-versioned tables.

Normalization preserves each raw signal as evidence while also exposing stable provenance directly:
signal kind, source, source ID, source URL, repo, PR number, capture timestamp, harvest version,
author metadata, path/line, bot flag, body text, and copied raw metadata.

## Comment-to-Change Correlation

`feedback_loop.pipeline.classify.classify` attaches deterministic BKW-84 correlation metadata to
reviewer, issue-comment, review, and bot-review feedback, then assigns BKW-75 taxonomy fields.

Correlation uses bounded, reviewable evidence only: matching changed paths and diff hunk lines,
later commits and commit-message fix language, later fixed/covered/done replies, resolved thread
metadata, failed validation signals after the feedback timestamp, and bot-review ranges that point
at an earlier head than the final PR head.

Every feedback signal gets a confidence, rationale, and evidence IDs. Signals below the likely-miss
threshold still carry a rationale so false or weak correlations remain visible to the future human
triage report.

Classifier output includes primary class, severity, confidence, rationale, evidence IDs, affected
area, source/area tags, suggested destination, and a `manual_triage` flag. Promotable classes below
confidence `0.5` are held for manual triage and do not get an automatic destination. The labeled eval
set lives in [`validation/bkw-75-classifier-eval.md`](./validation/bkw-75-classifier-eval.md).

## Exclusion Rules

BKW-83 exclusion rules run after correlation. They mark nits, subjective preferences, product
decisions, speculative questions, and not-actionable operational noise with auditable `Exclusion`
metadata instead of silently dropping those signals.

Excluded signals can still be summarized as context when useful, but excluded-only clusters are
blocked from creating guardrail proposals. The manual validation notes, including false
include/false exclude examples, live in [`validation/bkw-83-exclusion-validation.md`](./validation/bkw-83-exclusion-validation.md).

## Theme Clustering

`feedback_loop.pipeline.cluster.cluster` groups classified actionable and excluded feedback into
stable reviewable themes. The cluster key is deterministic: primary class, affected area,
destination/manual-triage/excluded state, and a normalized topic from path/body/rationale.

Clusters include frequency by distinct PR, highest severity, `severity_weight x frequency` rank,
representative examples, and source URLs. Excluded-only clusters are retained for audit with rank
`0.0` and no suggested destination.

## Triage Report

`feedback_loop.pipeline.triage.build_triage_report` turns ranked clusters into a markdown report and
a machine-readable summary. Each cluster includes decision guidance (`promote`,
`gather_more_evidence`, `ignore`, or `convert_to_mechanical_check`), source links, representative
examples, severity, frequency, confidence, suggested destination, and explicit open questions.

## Promotion Templates

`feedback_loop.pipeline.propose` defines one promotion template for every destination in the
promotion matrix: tests/linters, `.agents/checks`, `.ai/skills`, scoped `.ai/AGENTS.md`, docs, and
world model. The templates require evidence, scope, examples, non-goals, validation steps, reviewer
instructions, and rollback guidance.

Evidence is summarized with source IDs and URLs. Templates explicitly avoid pasting raw PR comments
verbatim when a concise summary is enough.

## Guardrail Proposal Generator

`feedback_loop.pipeline.propose.propose` turns eligible clusters into dry-run `Proposal` artifacts.
It selects the destination from the cluster, fills the matching template, includes evidence links and
confidence, and records validation commands plus replay cases for the future P4 eval harness.

The generator creates at most one proposal per cluster and skips excluded-only, manual-triage,
low-confidence, and below-threshold research-only clusters. It does not edit the repo; the
replay/eval gate remains separate and generated proposals stay at `eval_passed=False` until that
gate approves them.

## Replay Corpus

`feedback_loop.replay.load_replay_corpus` loads the committed historical miss fixture at
[`replay/corpus.json`](./replay/corpus.json). Each case records the PR, commit range, changed files,
miss class, source comment URL, expected finding, labels, and a short summary. The corpus stores
links and summaries only; raw review comment bodies stay in GitHub as the source of record.

## Replay Harness

`feedback_loop.replay.run_replay_harness` runs two caller-provided guidance runners, `current` and
`proposed`, over the same replay cases. The harness is pure and deterministic: it does not edit the
repo, open PRs, comment, or write files unless the caller explicitly passes the returned report to
`write_replay_report`.

Each runner returns `ReplayFinding` objects for one `ReplayCase`. The harness classifies each
case result into comparable artifacts:

- `caught_miss`: the runner emitted a finding for the replay case, with the expected destination
  when the corpus case declares one.
- `missed_miss`: the runner returned successfully but did not catch the expected miss.
- `extra_findings`: findings that do not match the case expectation.
- `runtime_failure`: runner exceptions or invalid runner output.

`ReplayReport.proposal_publishable` is `False` when proposed guidance misses a replay case or
fails at runtime. The scoring rubric records noisy extra findings, and the proposal eval gate
enforces the publication decision.

## Replay Scoring Rubric

`feedback_loop.rubric.score_proposal` scores a `Proposal` plus `ReplayReport` before the proposal
can be published. The default thresholds require:

- perfect proposed-guidance recall on replayed historical misses;
- no proposed-guidance runtime failures;
- correct expected severity when replay cases declare one, otherwise recognized severity
  (`critical`, `high`, `medium`, or `low`);
- actionable proposal details: target artifacts, validation commands, replay cases, scope, and
  validation steps;
- source grounding: evidence links, an evidence section, and source URLs on caught replay findings;
- noise cost no higher than `0.5` extra findings per replay case;
- at least two replay cases unless a high/critical severity proposal has an explicit manual
  override.

The rubric records recall and noise/regression cost separately. `rubric_markdown` renders the
result for draft PR descriptions so reviewers can see the gate inputs.

## Proposal Eval Gate

`feedback_loop.eval_gate.evaluate_proposal` runs the rubric and moves proposals through the eval
state machine:

`proposed -> eval_running -> eval_passed | eval_failed -> pr_ready`

Failed evals attach a `ProposalEvalArtifact` with rubric markdown, blocking reasons, and a failure
destination. Sparse high/critical evidence returns to research unless an explicit manual override
is recorded; other weak proposals return to triage. `mark_pr_ready` is the only transition from
`eval_passed` to `pr_ready`, and `pipeline.emit.emit` refuses every proposal that is not both
`eval_passed` and `pr_ready` before any Builderbot-triggering Linear write can happen.

## Draft PR Emission

`feedback_loop.pipeline.emit.emit` builds one `DraftPrPlan` and one Linear cluster issue plan per
`pr_ready` proposal. Dry-run mode returns the plans without writing to Linear or triggering
Builderbot. Execute mode calls an injected Linear writer; the Linear issue receives the
`code-engine:approved` label so the existing Builderbot code-engine automation opens the draft PR
and comments back in Linear. Generated draft PR plans are handed to Builderbot through the Linear
issue and include:

- proposal summary, destination, target artifacts, and proposed file changes;
- evidence links instead of pasted source comments;
- attached replay rubric markdown;
- validation commands, with AI context regenerate/check commands added when `.ai` sources change;
- reviewer instructions from the proposal template.

Each Linear issue represents one conceptual feedback-loop improvement, and the trigger label lets the
shared Builderbot automation own branch checkout, code edits, PR creation, and Linear follow-up.

## Generated PR Policy

`feedback_loop.pr_policy.validate_pr_policy` enforces one cluster or one guardrail per generated PR.
It fails local validation when a proposal mixes unrelated artifact families, such as `.agents/checks`
and docs, or when a proposal routes AI/doc files to the wrong destination. A human can apply an
explicit `PrPolicyOverride` with approver and rationale for tightly coupled changes.

Every generated draft PR body includes a reviewer checklist covering evidence quality, destination
choice, eval results, noise risk, source-of-truth compliance, and rollback.

## Linear Cluster Issues

`feedback_loop.linear_control.build_cluster_issue_plan` builds an idempotent Linear upsert plan for
each accepted cluster. Plans keep `assignee=None`, attach the `Linear-driven code engine` project,
include evidence links, proposal routing, eval markdown, target artifacts, proposed file changes,
validation commands, and the draft PR URL when available. Execute mode adds the
`code-engine:approved` label to trigger the shared Builderbot code-engine automation. The
deterministic idempotency key is derived from the stable cluster theme and destination so reruns can
update the same issue instead of duplicating it when summary text changes.

Feedback-loop status maps onto the Bitkey Linear workflow as:

| Feedback-loop status | Linear state |
|---|---|
| `harvested`, `classified`, `needs_triage` | `Todo` |
| `proposal_drafted`, `eval_passed` | `In Progress` |
| `pr_open` | `In Review` |
| `adopted` | `Done` |
| `rejected` | `Canceled` |

Execute mode requires an injected Linear writer before any external write; dry-run returns the plans
without applying the Builderbot trigger label to Linear.

## Usage

```bash
# Single merged PR (the Builderbot/Blox trigger and the GitHub-Action fallback both call this form)
python -m feedback_loop run --pr https://github.com/squareup/wallet/pull/12345 --dry-run

# Bounded historical backfill
python -m feedback_loop run --backfill --since 2026-05-01 --limit 100 --dry-run
```

`--dry-run` (default in the scaffold) performs no writes: no Linear issue, no Builderbot trigger
label, no comments.

Scheduling is Builderbot configuration, not feedback-loop code. The repo CLI only provides
event/backfill entrypoints plus rerun/reconcile idempotency for repeated invocations.

## Layout

```
automation/feedback-loop/
├── README.md                 # this file
├── feedback_loop/
│   ├── __init__.py
│   ├── __main__.py           # `python -m feedback_loop`
│   ├── cli.py                # arg parsing + stage orchestration (run --pr / --backfill)
│   ├── config.py             # repo, idempotency, dry-run
│   ├── eval_gate.py          # proposal eval state machine and emit guard
│   ├── linear_control.py     # Linear cluster issue plans and state mapping
│   ├── models.py             # typed records (RawSignal, NormalizedSignal, Cluster, Proposal)
│   ├── pr_policy.py          # one-change generated PR policy and reviewer checklist
│   ├── replay.py             # replay corpus loader + current-vs-proposed harness
│   ├── rubric.py             # replay scoring rubric + PR markdown
│   └── pipeline/
│       ├── __init__.py
│       ├── harvest.py        # GitHub harvest inputs
│       ├── normalize.py      # normalization, no store
│       ├── classify.py       # correlation + taxonomy classification
│       ├── cluster.py        # theme clustering
│       ├── triage.py         # human triage report
│       ├── propose.py        # proposal generation + eval gate
│       └── emit.py           # draft PR + Linear issue planning
├── replay/
│   └── corpus.json           # small versioned historical miss corpus
└── trigger/                  # DESIGN ONLY — UI-configured Builderbot automation; no cash-server, no SA
    ├── README.md             # trigger overview (Builderbot automations UI → blox-vanilla → CLI)
    ├── automation-setup-ui.md # primary: exact UI steps/fields + identity/permission notes
    ├── PROVISIONING.md        # minimal human checklist (UI + two capability confirms)
    └── routing-rule.json      # advanced/alternative: equivalent rule body for the Builderbot API
```

## See also

- `docs/docs/automation/ai-feedback-loop.md` — binding scope/owners/source-of-truth policy.
- `docs/docs/automation/feedback-loop-taxonomy.md` — taxonomy + promotion matrix.
- `docs/docs/automation/feedback-loop-data-source.md` — data-source recommendation.
- `docs/docs/automation/feedback-loop-checks-integration.md` — `.agents/checks` integration.
