# PR Review Output

## Phase 1: Local Output

Output review to console with full GitHub URLs.

**Link format**: Use complete GitHub blob URLs with line anchors:
- Format: `https://github.com/{owner}/{repo}/blob/{commit_sha}/{file_path}#L{line}`
- Get commit SHA: `gh pr view <PR> --json headRefOid -q .headRefOid`
- Example: `https://github.com/squareup/wallet/blob/abc123def456/src/auth/jwt.kt#L156`
- Terminals auto-detect URLs and make them command-clickable

Use indentation and visual grouping for readability.

```
## Review: ✅ Approve / ❌ Request Changes

<1-2 sentence summary of what this PR actually does, not just what it claims>

────────────────────────────────────────────────────────────────

🚫 BLOCKERS (must fix before merge)

• https://github.com/squareup/wallet/blob/abc123def456/src/auth/jwt.kt#L156
  Token expiration only checked client-side. Server accepts expired tokens.
  → Must validate 'exp' claim server-side in middleware.

• https://github.com/squareup/wallet/blob/abc123def456/src/auth/AuthService.kt#L89
  Passwords logged in plaintext on auth failures.
  → Remove password from log statement (security policy violation).


CONCERNS (should fix)

• https://github.com/squareup/wallet/blob/abc123def456/src/middleware.kt#L45
  Catching all exceptions masks network/parsing errors.
  → Use specific exceptions (JWTDecodeException, etc.) with appropriate status codes.

• https://github.com/squareup/wallet/blob/abc123def456/src/database/UserDao.kt#L201
  DB query on main thread.
  → Use withContext(Dispatchers.IO) per docs/architecture/coroutines.md.


NITS (optional)

• https://github.com/squareup/wallet/blob/abc123def456/src/models.kt#L23
  Consider explicit @SerialName for API compatibility.


MISSING

  • No tests for token expiration edge cases
  • Token refresh implementation not found (mentioned in principles doc)


NOT REVIEWED

  • Integration tests (marked WIP)
  • Performance impact of JWT verification
```

## Phase 2: Ask to Post

Ask the developer:
1. Do you want to post this review to the PR?
2. Is there anything you'd like to add or modify?

## Phase 3: Post to PR

### Writing Feedback

Feedback should be educational:
- **Explain the why** - not just what's wrong, but why it matters
- **Reference patterns** - point to existing codebase examples
- **Link to docs** - reference relevant documentation and conventions
- **Help them learn** - the goal is growth, not just fixes

### General Review

```bash
gh pr review <PR> --comment --body "..."
# or --approve / --request-changes
```

### Inline Comments

Use `gh api` (gh pr review doesn't support inline comments):

```bash
gh api repos/{owner}/{repo}/pulls/{pr}/comments \
  --method POST \
  -f body="<comment>" \
  -f commit_id="$(gh pr view <PR> --json headRefOid -q .headRefOid)" \
  -f path="<file>" \
  -F line=<line_number> \
  -f side="RIGHT"
```

Parameters:
- `line` - actual line number in file (not diff position)
- `side` - `RIGHT` for additions, `LEFT` for deletions
- `commit_id` - must be a commit SHA from the PR

For multi-line comments add `start_line` and `start_side`.

### Suggestions

Use GitHub's suggestion format for simple fixes:

```markdown
```suggestion
fixed code here
```
```

The author can click "Apply suggestion" to commit directly.
