# AI Feedback Loop pipeline

Substrate-agnostic Python CLI that turns merged-PR review feedback into durable, evidence-backed
guidance proposals. This is the **core** of the feedback loop; the trigger/execution substrate only
invokes this CLI, and generated code work is handed to Builderbot through Linear labels (see
[`trigger/`](./trigger/)).

Governed by `docs/docs/automation/ai-feedback-loop.md` (binding policy),
`docs/docs/automation/feedback-loop-taxonomy.md` (classification + promotion matrix), and the
architecture/threat notes in `docs/docs/automation/`.

## Design principles

- **Stateless + event-driven**: no datastore. GitHub is the re-fetchable source of record for raw
  evidence; Linear cluster issues are the durable memory of recurring themes.
- **Substrate-agnostic**: all logic lives behind a CLI (`run --pr <url>` / `run --backfill` /
  `reconcile-outcomes`). The harvest trigger only invokes this CLI. Builderbot code-engine
  execution is triggered separately by Linear issues labeled `code-engine:approved`.
- **Deterministic facts, LLM judgment**: the deterministic layer extracts only objective facts
  (thread structure, timestamps, diff membership, check conclusions, head tracking). Taxonomy,
  severity, exclusions, resolution, clustering, and route planning are LLM judgments — validated
  structurally, never trusted on identity or evidence.
- **Reality-checked gates**: plans are verified against the actual repository (paths must exist or
  belong to allowed new-path families, validation commands must execute a real runner, new fixture
  files must be referenced by something) — never against their own text.
- **Untrusted input**: all harvested PR/review/bot/CI text is **data, never instructions**, at
  every LLM stage.
- **Mechanical-first promotion**: prefer tests/linters, then `.agents/checks`, then `.ai`
  rules/skills, then docs, then world model. The taxonomy promotion matrix (critical 1 / high 2 /
  medium 3 / low 5-and-mechanical-only distinct PRs) is a hard readiness gate.
- **Human-gated at merge**: `--execute` creates Linear issues and auto-applies the
  `code-engine:approved` label to pr_ready proposals; Builderbot opens the draft PR. The human
  gate is draft-PR review/merge — a human owner approves every merge, and the gates exist to make
  what reaches review worth reviewing.
- **Provider-neutral LLMs**: a JSON subprocess adapter configured by environment. No provider SDK
  is coupled to the feedback-loop core.

## Pipeline stages

`harvest -> normalize -> facts -> noise -> llm_classify -> llm_cluster -> triage -> llm_evaluator
(extract -> plan -> reality preflight -> replay gate -> judge -> one repair) -> readiness -> emit
-> plan_cluster_memory_upserts`

| Stage | Responsibility |
|---|---|
| `harvest` | Fetch raw evidence from GitHub for merged PRs (bounded, trust-checked, truncation-tracked). |
| `normalize` | Shape/provenance normalization in memory (part of `harvest`). |
| `facts` | Deterministic objective facts per signal: thread structure, timestamp ordering, diff membership, reviewed-vs-final head, author trust. Check signals are classified here (pure metadata: ci/validation failure). |
| `noise` | Deterministic prefilter for pure bot/process noise (Linear linkbacks, merge-gatekeeper, owner-table, codex wrappers, agent acknowledgements). |
| `llm_classify` | LLM taxonomy/severity/exclusion/resolution per feedback signal, grounded in facts, batched by PR, structurally validated (unknown ids rejected; unsupported durable-coverage claims downgraded). |
| `llm_cluster` | LLM groups actionable signals into durable themes and matches them against existing Linear memory records. Identity (slug + idempotency key) is forced from matched records; frequency/severity/rank/decision are computed deterministically from members + matched history. |
| `triage` | Human-readable report of clusters, decisions, and audit-only signals. |
| `llm_evaluator` | Learning extraction, per-route concrete patch planning, reality preflight, replay gate, LLM judge, one repair attempt. |
| `emit` | Draft PR plans + Linear code-engine issues for pr_ready proposals. |
| `plan_cluster_memory_upserts` | Decision-gated Linear memory upserts (promote / convert_to_mechanical_check / gather_more_evidence only), capped at 30/run with logged drops. |
| `reconcile-outcomes` | Separate subcommand: syncs Linear issue states from draft-PR results (merged→adopted, closed→rejected, open→pr_open). |

## The gates

A route proposal reaches Builderbot handoff only if **all** of these pass:

1. **Reality preflight** (deterministic, `pipeline/reality_preflight.py`): new files must land in
   existing directories (the only allowed new paths are `.agents/checks/<slug>.md` and
   `.ai/skills/<name>/SKILL.md`); unified diffs must target existing files; validation-command
   path tokens must exist; inspection-only command sets (jq/rg/grep/cat) are vacuous;
   `test_or_linter` plans must invoke the real runner for their area (`bin/ai-gradle` for app,
   `cargo test` for server/core, `python -m unittest` for automation, `inv`/`meson test` for
   firmware); new fixture/data files nothing in the repo references are rejected.
2. **Promotion frequency gate** (`eval_gate.frequency_gate_blocking_reason`): the taxonomy matrix
   enforced on memory-reconciled distinct-PR frequency.
3. **Replay gate** (`replay_gate.py`, mechanical routes): matched historical corpus cases are
   reconstructed from local git history and an LLM runner applies ONLY the proposed guardrail
   content to each diff — every matched case must be caught. The runner never sees the expected
   finding; matching stays deterministic. Zero resolvable matches is recorded as `sparse`, not
   blocking. Every run also emits curatable corpus suggestions with real commit SHAs
   (`suggested-replay-cases.json` + a Linear issue section).
4. **LLM judge** (one proposal per call, 1–5 scores, every dimension must be ≥4): instructed to
   treat unshown infrastructure as nonexistent (`invented_infrastructure`) and to block plans
   already covered by existing guidance (`already_covered_by_guidance`), with the deterministic
   runner-vs-inspection command classification and existing guidance (scoped AGENTS.md, checks,
   skills) in its input.

One repair attempt is shared across the gates; hard blockers (wrong destination, unsupported
evidence, frequency, invented infrastructure, replay runtime failures) are never repaired.

## Linear cluster memory (schema v2)

`feedback_loop.cluster_memory` treats Linear cluster issues as the durable memory of recurring
themes. Identity is the semantic cluster slug + destination
(`idempotency_key_for_memory(slug, destination)`); matched records keep their original keys
verbatim, so legacy v1/lexical issues upgrade in place the first time a cluster matches them.
Writes are decision-gated (`promote`, `convert_to_mechanical_check`, `gather_more_evidence` —
sub-threshold themes must persist so frequency can accumulate across runs), capped at 30 per run
with every drop logged, and would-be duplicate creates fold into existing records on ≥50% PR
overlap. The reader pages past the per-page limit and warns loudly if the CLI exposes no cursor.

Feedback-loop status maps onto the Bitkey Linear workflow as:

| Feedback-loop status | Linear state |
|---|---|
| `harvested`, `classified`, `needs_triage` | `Todo` |
| `proposal_drafted`, `eval_passed` | `In Progress` |
| `pr_open` | `In Review` |
| `adopted` | `Done` |
| `rejected` | `Canceled` |

`reconcile-outcomes` drives the last three transitions from actual PR state, using the
`change-set:<sha16>` marker every generated draft PR body carries.

## Usage

```bash
# Single merged PR (the Builderbot/Blox trigger and the GitHub-Action fallback both call this form)
python -m feedback_loop run --pr https://github.com/squareup/wallet/pull/12345 --dry-run

# Bounded historical backfill (classified and clustered as one window)
python -m feedback_loop run --backfill --since 2026-05-01 --limit 100 --dry-run

# Reviewable local run bundle
python -m feedback_loop run --pr https://github.com/squareup/wallet/pull/12345 \
  --dry-run --output-dir automation/feedback-loop/.dry-runs/single-pr

# Sync Linear issue states from draft-PR outcomes
python -m feedback_loop reconcile-outcomes --dry-run
```

`--dry-run` (default) performs no writes. `--execute` writes Linear issues, auto-applies the
Builderbot trigger label to pr_ready proposals, and always records a run bundle (defaulting to
`.feedback-loop-runs/<mode>-<timestamp>` when `--output-dir` is omitted) plus a stdout summary of
every Linear write and Builderbot trigger.

Without `FEEDBACK_LOOP_LLM_COMMAND` configured, dry-run degrades to a facts-only inventory and
`--execute` refuses to run: memory writes depend on LLM classification.

The run bundle contains `run-summary.json`, concise/full triage reports, `classifications.json`,
`clusters.json`, `proposals.json`, `proposal-evals.json`, `llm-learnings.json`, `llm-debug.json`,
`eval-blocked.json`, `cluster-memory.json`, `replay-gate.json`, `suggested-replay-cases.json`,
emit previews, and (execute) `linear-writes.json`.

### LLM adapters

In-repo adapters under `adapters/` implement the contract for two backends — **Claude**
(Anthropic) and **Codex** (OpenAI). The command string is shlex-split (no shell), so use
absolute paths:

```bash
REPO="$(git rev-parse --show-toplevel)"
export FEEDBACK_LOOP_LLM_COMMAND="$REPO/bin/python3 $REPO/automation/feedback-loop/adapters/llm_adapter.py --provider claude"
export FEEDBACK_LOOP_LLM_TIMEOUT=600   # core per-call kill; keep above adapter retries x HTTP timeout
```

The adapter receives one JSON request on stdin (`task`, `prompt_version`, `system_prompt`,
`input`, `response_contract`) and returns one strict JSON object on stdout; any nonzero exit
leaves stdout empty so the core's transport retry fires.

**Transport is API-first.** When the provider's key env var is set
(`ANTHROPIC_API_KEY` / `OPENAI_API_KEY`), the adapter calls the provider API directly via stdlib
HTTP — no agent-harness boot per call, and the stage-constant prefix (system prompt + response
contract) is served from the provider prompt cache. Claude can fall back to the authenticated
`claude -p` CLI when `ANTHROPIC_API_KEY` is absent (slower and more expensive per call; keep
concurrency <= 2 in CLI mode). Codex is API-only: `--provider codex` requires `OPENAI_API_KEY`
and fails closed when CLI fallback is forced.

| Env var | Default | Purpose |
|---|---|---|
| `FEEDBACK_LOOP_LLM_PROVIDER` | `claude` | provider when `--provider` is absent |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | unset | presence selects API mode |
| `FEEDBACK_LOOP_ADAPTER_FORCE_CLI` | `0` | force Claude CLI fallback despite a key (testing); unsupported for Codex |
| `FEEDBACK_LOOP_CLAUDE_MODEL` / `FEEDBACK_LOOP_OPENAI_MODEL` | flagship | provider default model |
| `FEEDBACK_LOOP_CLAUDE_MODEL_<TASK>` / `FEEDBACK_LOOP_OPENAI_MODEL_<TASK>` | unset | per-task model override (task uppercased, e.g. `..._CLASSIFY_FEEDBACK_SIGNALS`) |
| `FEEDBACK_LOOP_ADAPTER_ANTHROPIC_BASE_URL` / `..._OPENAI_BASE_URL` | provider default | internal-gateway override |
| `FEEDBACK_LOOP_ADAPTER_MAX_TOKENS` | `16000` | API output-token cap |
| `FEEDBACK_LOOP_ADAPTER_HTTP_TIMEOUT` / `..._CLI_TIMEOUT` | `240` | per-attempt deadlines (seconds) |
| `FEEDBACK_LOOP_ADAPTER_RETRIES` | `2` | internal retries on 429/5xx (honors retry-after) |
| `FEEDBACK_LOOP_ADAPTER_USAGE_LOG` | unset | JSONL usage sidecar path (else one stderr line per call) |

### Concurrency

Stage-internal fan-out is env-gated and **default-off** (sequential, byte-identical to the
single-threaded pipeline). Recommended production values:

```bash
export FEEDBACK_LOOP_LLM_CONCURRENCY=4      # classify batches, evaluator routes, replay cases
export FEEDBACK_LOOP_HARVEST_CONCURRENCY=3  # per-PR harvest; keep small (gh secondary rate limits)
```

One shared semaphore caps in-flight adapter subprocesses across all stages, and each stage merges
results in submission order, so artifacts are ordering-identical at any concurrency. The
clustering stage stays sequential by design (chunks chain through pending clusters).

### Usage telemetry

With `FEEDBACK_LOOP_ADAPTER_USAGE_LOG=/tmp/fl-usage.jsonl` set, every call appends one JSON line
(task, provider, mode, model, duration, token + cache counters). Aggregate per task with:

```bash
bin/python3 -c "
import collections, json, sys
rows = [json.loads(line) for line in open(sys.argv[1])]
by_task = collections.defaultdict(list)
for row in rows: by_task[row['task']].append(row)
for task, items in sorted(by_task.items()):
    read = sum(r['cache_read_input_tokens'] or 0 for r in items)
    write = sum(r['cache_creation_input_tokens'] or 0 for r in items)
    fresh = sum(r['input_tokens'] or 0 for r in items)
    ms = sum(r['duration_ms'] for r in items) / len(items)
    rate = read / (read + write + fresh) if read + write + fresh else 0.0
    print(f'{task}: {len(items)} calls, avg {ms:.0f}ms, cache hit {rate:.0%}')
" /tmp/fl-usage.jsonl
```

A near-zero cache-hit rate on a high-volume task (classify) means a silent prefix invalidator —
diff two rendered request bodies.

### Deferred follow-ups

- Message Batches API mode for `--backfill` (50% token cost, async).
- Cluster-stage map-reduce (parallel chunks + one merge call).
- Raising `MAX_CLASSIFY_SIGNALS_PER_CALL` above 40 — gated on re-running the BKW-75 classifier
  eval (`validation/bkw-75-classifier-eval.md`) at the larger batch size.

Rerun idempotency is owned by Linear memory reconciliation: semantic slug matching lets repeated
invocations update the same issues instead of duplicating them. Scheduling is Builderbot
configuration, not feedback-loop code.

## Layout

```
automation/feedback-loop/
├── README.md                 # this file
├── adapters/
│   ├── llm_adapter.py        # executable entry (--provider claude|codex), API-first; Codex API-only
│   ├── common.py             # request/prompt assembly, HTTP retry, CLI runner, usage log
│   ├── anthropic_provider.py # Claude: Messages API (prompt caching) + `claude -p` fallback
│   └── openai_provider.py    # Codex: Chat Completions API
├── feedback_loop/
│   ├── __init__.py
│   ├── __main__.py           # `python -m feedback_loop`
│   ├── cli.py                # arg parsing + stage orchestration (run / reconcile-outcomes)
│   ├── concurrency.py        # bounded fan-out helpers (default-off, env-gated)
│   ├── config.py             # repo, harvest version, dry-run, repo root
│   ├── artifacts.py          # run bundle writer (dry-run and execute)
│   ├── cluster_memory.py     # Linear-backed durable memory (schema v2)
│   ├── corpus_suggest.py     # replay-corpus suggestions from judged learnings
│   ├── eval_gate.py          # PR-ready guard + promotion frequency gate
│   ├── github.py             # gh CLI boundary (harvest, change-set search, PR status)
│   ├── gitio.py              # read-only git boundary (replay diff reconstruction)
│   ├── linear_control.py     # Linear issue plans and state mapping
│   ├── llm.py                # provider-neutral JSON subprocess adapter + shared retry
│   ├── models.py             # typed records (RawSignal, SignalFacts, Cluster, Proposal, ...)
│   ├── outcomes.py           # reconcile-outcomes implementation
│   ├── pr_policy.py          # one-change generated PR policy and reviewer checklist
│   ├── repo_reality.py       # injectable read-only checkout boundary for reality checks
│   ├── replay.py             # replay corpus loader + current-vs-proposed harness
│   ├── replay_gate.py        # runtime replay gate (LLM runner over historical diffs)
│   ├── rubric.py             # replay scoring rubric
│   ├── route_metadata.py     # handoff titles + change-set ids
│   ├── util.py               # shared helpers (dedupe, severity weights, promotion matrix, ...)
│   └── pipeline/
│       ├── __init__.py
│       ├── harvest.py        # GitHub harvest + normalization
│       ├── facts.py          # deterministic objective facts layer
│       ├── noise.py          # bot/process-noise prefilter
│       ├── llm_classify.py   # LLM signal classification
│       ├── llm_cluster.py    # LLM clustering + semantic memory matching
│       ├── triage.py         # human triage report
│       ├── templates.py      # promotion templates (titles/sections per destination)
│       ├── reality_preflight.py # repo-reality checks for plans
│       ├── llm_evaluator.py  # learning extraction, route planning, gates, judge, repair
│       └── emit.py           # draft PR + Linear issue planning
├── replay/
│   └── corpus.json           # versioned historical miss corpus (grown via suggestions)
└── trigger/                  # DESIGN ONLY — UI-configured Builderbot automation
```

## See also

- `docs/docs/automation/ai-feedback-loop.md` — binding scope/owners/source-of-truth policy.
- `docs/docs/automation/feedback-loop-taxonomy.md` — taxonomy + promotion matrix (enforced at the gate).
- `docs/docs/automation/feedback-loop-architecture.md` — architecture notes.
- `docs/docs/automation/feedback-loop-checks-integration.md` — `.agents/checks` integration.
- `docs/docs/automation/feedback-loop-threat-model.md` — threat model.
