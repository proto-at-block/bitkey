# Context Gathering

## Summary
Before starting any implementation work — including writing new code, refactoring, adding documentation, or preparing code-related answers — gather context from existing documentation, established patterns, and relevant AI rules. **Always start with documentation-first approach, never default to code-first exploration.** Timebox to ~2 minutes for small changes, longer for larger or cross-cutting work. Always reuse existing solutions and follow conventions before introducing something new.

## When to Apply
- Writing new code or refactoring existing code
- Writing commit messages or preparing PR descriptions
- Adding or updating comments, README files, or other documentation
- Answering questions about the codebase, architecture, or system design
- Reviewing pull requests to ensure consistency with architecture and patterns
- Investigating unfamiliar parts of the codebase before making changes
- Implementing functionality similar to an existing feature
- Explaining system components, data structures, or business logic

## How to Apply

**Follow exactly in this order:**

1. **Documentation First**: Search for and read relevant `.md` files
   - Use `Glob` to find: `**/*ARCH*.md`, `**/*DESIGN*.md`, `**/*README*.md`
   - Look for domain-specific docs in relevant module directories
   - Architecture documents contain authoritative system design information
   - Code exploration cannot replace reading documentation

2. **Code Investigation**: Search for similar code and tests, ignore `_build` folders
   - Only after documentation review is complete
   - Use code to fill gaps not covered in documentation
   - Verify documentation accuracy against implementation

3. **AI Rules Review**: Review AI rules for relevant patterns
4. **Pattern Confirmation**: Confirm pattern to follow, or document why a new one is needed

## Consequences if Skipped
- Missing architectural context and design decisions
- Incomplete or incorrect explanations of system behavior
- Inconsistent patterns and duplicated code
- Architectural violations
- Harder reviews and maintenance
- Code-first bias leading to implementation details focus instead of system understanding

## Related Rules
- @ai-rules/bitkey-mobile.md
