# BKW-83 Exclusion Validation

Manual validation pass for the deterministic exclusion rules in
`feedback_loop/exclusions.py`.

Scope: representative wallet PR-review comment shapes for nits, naming preferences, product/scope
decisions, speculative questions, CI/infra noise, and likely-miss questions with follow-up evidence.
The examples below are intentionally small because BKW-83 validates routing behavior, not model
quality.

## Expected Routing

| Sample comment shape | Expected result | Why |
|---|---|---|
| `Nit: typo in this variable name.` | Exclude as `style_nit`; do not summarize as context. | Cosmetic wording/naming feedback should not produce guidance. |
| `Prefer naming this accountStatus for readability.` | Exclude as `subjective_preference`; summarize as context. | Naming preference can be useful context but is not a guardrail by itself. |
| `Do we want this UX copy for launch, or is that a PM call?` | Exclude as `product_decision`; summarize as context. | Product/scope calls are outside agent-quality guidance. |
| `Can we validate this edge case?` plus a later fixed reply and commit. | Do not exclude as speculative; keep likely-miss correlation. | Follow-up evidence makes the question actionable. |
| `Retry CI; this runner is flaky.` | Exclude as `not_actionable`; summarize as context. | One-off infra noise should not produce repo guidance. |
| `What if this path has no active keyset?` with no later reply/change/check evidence. | Exclude as `speculative_question`; summarize as context. | No defect was established, but the question can inform triage. |

## Boundary Examples

False include example:

- `This should be a product requirement, not a code default.`
- Expected: `product_decision`.
- Risk: Without the `product|requirement` rule, this could look like an engineering miss.
- Current result: excluded and summarized as context.

False exclude example:

- `Can we validate this edge case?`
- Expected: include when a later reply/commit/check shows it was addressed.
- Risk: A broad question rule would hide real misses phrased as questions.
- Current result: not excluded when BKW-84 correlation crosses the likely-miss threshold.

Preference repeat example:

- `Prefer the Result helper here.`
- Expected: excluded for a single PR; future clustering may summarize it as context if it recurs.
- Risk: A repeated convention could become useful guidance, but a single preference should not create
  a guardrail.
- Current result: excluded with `summarize_as_context=true`.

## Acceptance Notes

- Excluded feedback is not dropped: each excluded signal carries `Exclusion` metadata, tags, and
  rationale.
- Excluded-only clusters are blocked by `Proposal.__post_init__`.
- Context-worthy exclusions use `summarize_as_context=true`; low-value style nits do not.
