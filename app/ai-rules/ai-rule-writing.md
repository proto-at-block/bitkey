---
description: Guidelines for writing and maintaining AI rules in this codebase
globs: ["ai-rules/**/*.md", "README.md"]
alwaysApply: true
---

**ACTIVATION TRIGGERS:** ACTIVATE IMMEDIATELY when the user mentions wanting to modify ai-rules, claude memory, agents.md or goosehints files. LOAD THIS RULE for any modifications or changes to ai rules. If code review is requested and changes in ai-rules/ folder are detected, LOAD THIS RULE and proceed with review.

# AI Rule Writing and Maintenance

## Summary

Standards for creating, updating, and maintaining AI rules in this codebase. Ensures rules are composable, minimal, declarative, and form a consistent foundation for AI guidance across all AI assistants.

## When to Apply

- Creating new AI rule files
- Updating existing AI rules  
- Maintaining ruleset consistency
- Referencing rules in configurations
- **Modifying any existing rule file**

## How to Apply

### Rule Structure

Each rule must follow this exact structure:

```markdown
---
description: Brief description of the rule's purpose
globs: ["**/*.extension"] # File patterns where rule applies
alwaysApply: false # Set to true only for meta-rules like this one
---

# Rule Title

## Summary
One paragraph explaining what this rule does and why it exists.

## When to Apply
Clear conditions for when this rule should be followed.

## How to Apply
Specific implementation guidance and steps.

## Example
Code examples showing good vs bad patterns.

## Related Rules
References to other rules that complement or interact with this one.
```

### File Management

**Before creating:** Search existing rules, check if topic can be addressed by updating existing rule, analyze codebase for real patterns.

**Naming:** Use kebab-case (`rule-name.md`) in `ai-rules/` directory. Be concise (2-3 words max), descriptive, domain-focused, and action-oriented.

**Examples:**
- ✅ `error-handling.md`, `strong-typing.md`, `api-versioning.md`
- ❌ `guidelines-for-error-handling.md`, `stuff.md`, `kotlin-programming-best-practices.md`

### Rule Distribution and Discovery

**Source of Truth:** All AI rules live in `app/ai-rules/` as canonical source. Never edit rules outside this directory.

**Index Maintenance:** Update `bitkey-mobile.md` whenever rules are added/removed/changed. Add new rules with brief, specific descriptions (1-2 lines max) focused on primary purpose.

**Rule Index Reference:** The complete, authoritative list of all AI rules is maintained in `@ai-rules/bitkey-mobile.md`. This index serves as the single source of truth for rule discovery and is automatically referenced by all AI assistant configurations.

**Rule Application Settings:**
- **`alwaysApply: true`**: Index files and meta-rules only
- **`alwaysApply: false`**: Specialized rules for specific contexts
- **`globs`**: Always specify file patterns where rule applies

**AI Tool Integration:**

**ALWAYS run `ai-rules generate` after modifying any file in the ai-rules/ directory.**

```bash
ai-rules generate
```

**You MUST run `ai-rules generate` after:**
- Creating a new rule file in ai-rules/
- Editing an existing rule file in ai-rules/
- Deleting a rule file from ai-rules/
- Changing ai-rules/ai-rules-config.yaml

This command automatically:
- Generates platform-specific rule files for all supported AI assistants
- Creates the appropriate directory structure (`.roo/`, `.cursor/`, `.clinerules/`, etc.)
- Maintains consistency across all AI tool configurations
- Updates generated files with current rule content

**Check generation status:**
```bash
ai-rules status
```

### Rule Principles

**Token-optimized:** Target <3000 tokens/rule. Use bullet points, tables, concise sentences.

**Minimal:** One concern per rule. Split complex topics.

**Composable:** Work together without conflicts.

**Evidence-based:** Extract patterns from actual codebase.

**Concise examples:** Show key patterns only. Reference actual code for details.

**Include consequences:** Document negative outcomes of bad patterns for context.

### Maintenance Guidelines

**Rule Updates:** Update existing rules vs duplicating. Apply token optimization when modifying any rule.

**Ruleset Evolution:** Review for relevance, merge outdated rules, maintain consistency.

**Related Rules:** Only reference existing rules (verify with `ls ai-rules/*.md`), never reference future/planned rules, remove broken references when rules are deleted/renamed.

**New Rule Creation Checklist:**
1. Create rule file in `ai-rules/`
2. Add to `bitkey-mobile.md` index with clear description
3. Generate AI assistant configurations:
   ```bash
   ai-rules generate
   ```
4. Verify all references point to existing rules
5. Test rule with multiple AI assistants

**Consistency Verification:**
- After any rule changes, run `ai-rules generate` to update all AI assistant configurations
- Check status with `ai-rules status`
- Audit: `ls ai-rules/*.md` for source rules
- Check generated directories: `.roo/`, `.cursor/`, `.clinerules/`, `.kilocode/`, `ai-rules/.generated-ai-rules/`

## Example

```markdown
# Good Rule Structure
---
description: Error handling patterns for Kotlin services
globs: ["**/*Service.kt", "**/*Repository.kt"]
alwaysApply: false
---

# Service Error Handling

## Summary
Defines consistent error handling patterns for service layer components to ensure predictable exception propagation and user-friendly error messages.

## When to Apply
- All service layer classes
- Repository implementations
- Any component that handles business logic exceptions

## How to Apply
[Implementation details...]

## Example
```kotlin
// ✅ GOOD: Structured error handling
fun processData(input: String): Result<Data, ServiceError> {
  return try {
    val result = validateAndProcess(input)
    Ok(result)
  } catch (e: ValidationException) {
    Err(ServiceError.InvalidInput(e.message))
  }
}

// ❌ BAD: Unhandled exceptions
// CONSEQUENCES: App crashes, poor user experience, difficult debugging, inconsistent error states
fun processData(input: String): Data {
  return validateAndProcess(input) // Can throw unhandled exceptions
}
```

## Related Rules
- @ai-rules/strong-typing.md (for error result types)
```

## Related Rules

This is the foundational rule that all other rules should follow. Future rules about specific coding practices, architecture patterns, or conventions should reference this rule for structural guidance.