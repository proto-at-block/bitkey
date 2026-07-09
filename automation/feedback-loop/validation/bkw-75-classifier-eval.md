# BKW-75 Classifier Evaluation Set

Manually labeled examples for the deterministic feedback-loop classifier in
`feedback_loop/pipeline/classify.py`.

The examples are representative wallet PR-review and CI signal shapes. They are intended to validate
taxonomy routing, confidence, evidence, and manual-triage behavior; they are not a model-training
corpus.

| Signal shape | Expected primary class | Expected severity | Expected routing |
|---|---|---|---|
| Inline review: `Please add a regression test here.` with matching changed file, diff hunk, and later commit. | `miss` | `high` | `test_or_linter`, high confidence. |
| Issue comment: `Can we validate this edge case?` with later fixed reply, commit, and failed validation check. | `miss` | `medium` | `test_or_linter`, high confidence. |
| Inline review: `Missing validation for this edge case.` with no follow-up evidence. | `miss` | `medium` | `manual_triage`, no automatic destination. |
| Check signal: `ai-context-check | failure`. | `validation_failure` | `medium` | `test_or_linter`, confidence `0.8`. |
| Bot review: `False positive, this is safe as-is.` | `false_positive` | `low` | No destination. |
| General review: `General concern.` with no deterministic evidence. | `not_actionable` | `low` | No destination. |
| Style comment: `Nit: typo in this variable name.` | `nit` | Excluded by BKW-83 | No destination; no context summary. |
| Product comment: `Do we want this UX copy for launch?` | `product_decision` | Excluded by BKW-83 | No destination; summarize as context. |

## Idempotency Check

The classifier is pure over normalized input records:

- correlation reasons and evidence IDs are recalculated from the same signal set;
- tags are de-duplicated;
- exclusion rationale is rebuilt after correlation/classification rather than repeatedly appended;
- low-confidence promotable classes get `manual_triage=true` and `suggested_destination=None`.

## Manual Triage Rule

Any promotable class below confidence `0.5` is held for manual triage. This currently applies to
miss-like feedback without deterministic follow-up evidence, and it prevents automatic guardrail
promotion from uncertain text-only signals.
