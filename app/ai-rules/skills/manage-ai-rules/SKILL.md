---
name: manage-ai-rules
description: "Create, edit, audit AI rules and skills. Manage docs, keywords, discoverability. Use when: 'add skill', 'update rules', 'fix ai context'"
---

# Manage AI Rules

Skills follow [agentskills.io](https://agentskills.io) specification. Generated via [block/ai-rules](https://github.com/block/ai-rules).

## What Goes Where

- `docs/docs/mobile/` — architecture, patterns, how-to (primary source)
- `ai-rules/` — task workflows, skills (thin entry points referencing docs)

**Principle:** Docs first. Skills reference docs, never duplicate.

## Edit Source Files Only

- `docs/docs/mobile/`
- `ai-rules/`

**Don't edit generated files:** `CLAUDE.md`, `AGENTS.md`, `.claude/`, `.cursor/`, `.generated-ai-rules/`, etc.

## Creating Skills

Create in `ai-rules/skills/<name>/SKILL.md`:

```yaml
---
name: skill-name        # lowercase, hyphens only
description: What it does and when to use it
---

# Instructions here
Reference docs: @docs/docs/mobile/...
```

Optional directories: `references/` (demand-loaded), `scripts/`, `assets/`

**After creating/renaming a skill:** Add to `.claude/settings.json` allowlist:
```json
"Skill(ai-rules-generated-<skill-name>)"
```

## Updating Allowlist

When adding, renaming, or removing skills, update `.claude/settings.json`:

```json
{
  "permissions": {
    "allow": [
      "Skill(ai-rules-generated-<skill-name>)"
    ]
  }
}
```

- **Add:** New skill entry when creating a skill
- **Rename:** Update entry when renaming a skill
- **Remove:** Delete entry when removing a skill

## Writing Skill Descriptions

Descriptions are the PRIMARY matching mechanism. Include phrases users actually say.

**Formula:** `<action verbs>. Use when: '<trigger phrases>'`

**Example:** `"Explain, research, investigate. Use when: 'explain X', 'what is X', 'how does X work'"`

**Intent phrases by type:**
- Exploratory: "explain", "what is", "how does", "understand"
- Implementation: "implement", "add", "create", "build"
- Debugging: "debug", "fix", "investigate error"
- Validation: "test", "verify", "check", "run"

## Writing Doc Keywords

Docs use HTML comments for AI discoverability. Place at the very top of the file, before the title.

**Format:**
```html
<!-- keywords: term1, term2, term3 -->

# Doc Title
```

**Guidelines:**
- Include primary concepts the doc covers
- Add synonyms and related terms users might search for
- Include specific type names (e.g., `DbError`, `ScreenModel`, `AppScope`)
- Keep concise but comprehensive

**Example:**
```html
<!-- keywords: DAO, SQLDelight, database, queries, DbError, persistence, fake, data access -->
```

**Audit existing docs:** Check that keywords match actual content. Add missing terms when concepts are covered but not in keywords.

## Commands

```bash
ai-rules generate  # Regenerate after changes
```
