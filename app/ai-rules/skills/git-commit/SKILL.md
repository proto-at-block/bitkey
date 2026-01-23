---
name: git-commit
description: "Use proactively before ANY git commit. Invoke when: committing code, creating PR, submitting changes, saving work. Covers: signed commits, GPG, commit messages, squash, atomic commits"
---

# Git Commit

- **Sign**: Always use `-S` flag (GitHub vigilant mode)
- **Atomic**: One complete unit per commit.
  - **Squash**: iterative fixes to earlier commits in the same branch (typos, forgotten files, "oops" fixes)
  - **Keep separate**: independent changes with distinct purposes
- **Comprehensive messages**: Capture full context - business domain, technical decisions, architectural rationale, system design. Intent: enable future investigation and crime scene work from git history.
- **Holistic rewrites**: When squashing or amending, rewrite the message to encapsulate all changes as a unified whole. Don't patch on top - create a coherent message that makes sense standalone.
- **Domain language**: Use relevant domain terminology. Human readable but technical - specific over vague.
