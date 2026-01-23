---
name: edit-docs
description: Edit documentation (code KDoc, mkdocs). Maintains source of truth for humans and AI.
keywords: docs, documentation, mkdocs, kdoc, comments, write, update, delete
---

# Edit Documentation

## Principles

1. **Concise** - Every sentence earns its place. Code examples > explanations.
2. **No duplication** - Reference existing docs, don't repeat. Use `@see`.
3. **Single source of truth** - Update or delete when patterns change.
4. **Rewrite over patch** - Restructure if it improves the whole. Don't just append.
5. **Accuracy** - Source code is truth. Outdated docs are worse than no docs.

## Code Documentation (KDoc)

See @docs/docs/mobile/patterns/documentation.md for full patterns.

**Document**: Public APIs, contracts, non-obvious behavior, error conditions
**Skip**: Self-explanatory signatures, docs that repeat method name

## MkDocs Documentation

`docs/docs/mobile/` is source of truth for architecture and patterns—for humans and AI.

**Format**: Relative links with `.md`, admonitions for warnings
**Nav**: Add pages to `docs/mkdocs.yml`

**Add/Update**: New patterns, non-obvious decisions, outdated docs
**Delete**: Outdated content, duplicates, deprecated patterns

## After Every Edit

- Check if other docs reference or duplicate this content
- Update or remove affected docs to maintain consistency
- Consolidate duplicates into single source with references

Docs must remain coherent as a whole.
