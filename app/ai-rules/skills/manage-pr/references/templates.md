# PR Description Templates

Adapt depth to PR complexity. Templates are starting points—modify as needed.

---

## Standard Template

```markdown
## Summary
- <what changes and why it matters>
- <key technical changes>

## Context
<why this is needed + any important decisions/trade-offs>

## Testing
- Ran: <command> — PASS
- Manual: <steps if needed>

Refs: W-XXXXX (if applicable)
```

**Add only if needed:**
- **Risk/Rollout**: For breaking changes, migrations, or feature flags
- **Screenshots**: For UI changes
- **Diagram**: For complex flows (Mermaid or ASCII)

---

## Simple Changes

For typos, small fixes, config tweaks—minimal template.

```markdown
## Summary
<brief description of what changed>

Refs: W-XXXXX (if applicable)
```

---

## Version/Dependency Updates

```markdown
## Summary
Bump [package] X.Y.Z → A.B.C

## Changes
- <key features or fixes>
- <breaking changes if any>

## Testing
- Ran: <build/test command> — PASS

Refs: W-XXXXX (if applicable)
```

---

## Alternative Approaches (optional)

Only include if multiple valid solutions were considered:

```markdown
## Alternative Approaches
- **Chosen**: [approach] — <why this one>
- **Rejected**: [approach] — <why not>
```
