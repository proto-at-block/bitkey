# Rules

**CRITICAL DIRECTIVE**: You MUST evaluate EVERY optional rule below for potential relevance to the current task. These are NOT suggestions - they are specialized rules that MUST be loaded when their domain matches the user's request. Scan the user's input for keywords and immediately load ALL applicable rules. 
**IMPORTANT**: YOU MUST READ the rule as soon as they are relevant. YOU MUST DO THIS BEFORE ANYTHING ELSE. 
**IMPORTANT**: Before looking for solutions in the codebase YOU MUST READ the rule.

**LOADING PROTOCOL**: 
- If a rule description mentions a topic/tool/framework AND the user's request involves that topic/tool/framework → YOU MUST READ THE RULE
- If a rule title contains keywords from the user's request → YOU MUST READ THE RULE
- Multiple rules can and should be loaded simultaneously when applicable
- "Optional" means conditionally mandatory, NOT ignorable

**EXAMPLE**: 
- If a rule mentions "database migrations" and the user asks about "updating database schema" → YOU MUST READ that rule
- If a rule mentions "authentication" and the user mentions "login", "auth", or "security" → YOU MUST READ that rule
- If multiple rules could apply to the task → YOU MUST READ ALL relevant rules

**REMEMBER**: These rules contain critical domain-specific expertise. Failing to load a relevant rule means missing essential guidance that could prevent errors or provide required patterns. When in doubt, load the rule.

**HOW TO LOAD RULES**: Each rule below is listed with its description followed by a colon and the file path. When a rule's description matches your task, YOU MUST READ THE FILE at the specified path using the provided reference. The file contains the actual rule content that you need.

**RULE FORMAT**: 
`[Rule Description]: [File Path]`

When you identify a relevant rule based on its description, immediately read the file at the specified path. The descriptions are activation triggers - the actual guidance is in the referenced files.

Gather context before code or docs to align with established architecture and patterns: ai-rules/.generated-ai-rules/ai-rules-generated-context-gathering.md

Source code documentation patterns for interfaces, implementations, and schemas: ai-rules/.generated-ai-rules/ai-rules-generated-documentation.md


