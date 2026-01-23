# Review Checklist

## How to Review

1. **Read the full diff** - code is source of truth, don't rely solely on PR title/description
2. **Review holistically** - understand the full context of changes, not just line-by-line
3. **Check codebase practices** - discover and verify against relevant conventions, patterns, and rules
4. **Verify intent** - check PR description accuracy against actual code changes

## What to Flag

- **Unrelated changes** - irrelevant commits, sneaked-in refactors, scope creep
- **Unused abstractions** - over-generalization, premature optimization
- **Mismatched docs** - comments/documentation that don't match code
- **Weak tests** - tests that pass but don't assert meaningful behavior
