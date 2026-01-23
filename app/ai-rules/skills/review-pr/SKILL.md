---
name: review-pr
description: Review pull requests thoroughly and critically. Focus on correctness, security, and quality.
---

# Review PR

## Fetch

```bash
gh pr view <PR> --json title,body,baseRefName,headRefName,files,additions,deletions,url
gh pr diff <PR>
gh pr checks <PR>
```

## Review

Spawn an agent to review the changes following @references/checklist.md

## Output

@references/output-format.md
