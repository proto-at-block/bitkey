# Pull Request Principles

## Purpose
Make PRs easy to review, easy to land, and easy to understand later (by humans *and* agents).

---

## Sizing & Scope
- **Small PRs**: Prefer small, reviewable diffs. If scope creeps, split.
- **Single concern**: Keep refactors, features, and fixes in separate PRs when practical.
- **Stacking OK**: Chained/stacked PRs are fine when there are dependencies—keep each PR coherent and reviewable.
- **Avoid drive-by changes**: If it's unrelated, spin it into a separate PR (or defer).

---

## Tickets (Linear)
- Include `W-XXXXX` when available.

---

## Branch Hygiene
- Work off `main` (or the repo's default branch).
- Before requesting review: ensure your branch is based on the latest `main` (rebase when appropriate).
- Don't include unrelated merges or noise; keep history understandable. If you have unrelated local changes, either commit them on a separate branch or use `git stash` temporarily—don't rely on stashes as long-term storage.

---

## Title Format
Use a brief, descriptive, outcome-oriented title.

Preferred format:
- `type(scope): brief description`

Optional ticket annotation:
- `type(scope): brief description [W-XXXXX]`

**Types**: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`, `build`

Examples:
- `fix(sync): prevent duplicate uploads on retry`
- `refactor(auth): simplify token refresh flow`
- `feat(wallet): add export transaction history [W-12345]`

---

## Description Principles
Write for a reviewer skimming quickly, then drilling in.

Include:
- **Summary (impact-first)**: 1–3 sentences on what changes and why it matters.
- **Key changes**: bullets of the most important changes.
- **Non-obvious details**: edge cases, invariants, tricky parts.
- **Architecture decisions**: call out important tradeoffs/rationale when relevant.
- **Testing / Checks**: list what you ran (AI chooses what's relevant; always report results).
- **Ticket**: `W-XXXXX` when available.

Optional (when applicable):
- **Screenshots/demo** for UI changes
- **Diagrams** for flows/architecture—use Mermaid or ASCII (encouraged when it clarifies complexity)
- **Risk/Rollout** for migrations or feature flags

---

## Diagrams
When helpful, include diagrams directly in the PR description:
- **Mermaid**: for sequence diagrams, flowcharts, state diagrams
- **ASCII**: for simple flows or when Mermaid is overkill

---

## Testing & Checks
- Run **relevant checks** for the changes (AI decides what's appropriate based on what changed).
- Always **document what you ran** and the result in the PR description.
- If you didn't run something, state why (and how it will be validated, e.g., CI).

---

## Commits
- Use the **git-commit** skill for commit message discipline and history management.
- It's OK for a PR to have many commits if the commit story is coherent and follows the git-commit rules.
- When pushing follow-up changes to an existing PR, explicitly invoke the **git-commit** skill.

---

## Updating an Existing PR
When pushing new changes after review:
- Clearly note what changed since last review (brief update in PR body or a comment).
- Call out which feedback was addressed (and what remains, if anything).

