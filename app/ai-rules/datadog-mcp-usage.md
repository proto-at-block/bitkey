---
description: Guidelines for using Datadog MCP for observability and debugging
globs: ["**/*"]
alwaysApply: true
---

# Datadog MCP Usage

## Summary
Use Datadog MCP to investigate production issues, analyze patterns, monitor services, and support oncall tickets. Always provide summaries with exact links to source data.

## Setup

**Note:** Automated ai-rules MCP config for Datadog is blocked by [block/ai-rules#24](https://github.com/block/ai-rules/pull/24) (HTTP transport support). For now, configure manually:

**Claude Code:**
```bash
claude mcp add --transport http datadog-mcp https://mcp.datadoghq.com/api/unstable/mcp-server/mcp
```

**Authentication:** Use Block SSO when prompted for OAuth.

## When to Apply
- Debugging production issues and errors
- Analyzing performance patterns and bottlenecks
- Monitoring service health and metrics
- Supporting oncall tickets and bug investigations
- Gathering context during code reviews or architectural discussions

## How to Apply

### Query Guidelines

**Always include exact links:**
- Provide direct Datadog URLs to logs, traces, metrics, dashboards
- Enable developers to verify source data
- Format: `[Description](https://app.datadoghq.com/...)`

**Privacy requirements:**
- REDACT all PII (names, emails, phone numbers)
- REDACT sensitive identifiers (user IDs, account IDs)
- REDACT API keys, tokens, credentials
- Provide summaries rather than raw logs when possible

### Token-Efficient Query Strategy

**CRITICAL: Datadog MCP responses can be large (10k+ tokens). Always optimize queries to reduce token usage.**

**Step 1: Ask clarifying questions BEFORE querying:**
1. What app flavor? (production/team/debug/dev)
2. What user identifier? (usr.app_installation_id preferred, or usr.hardware_serial_number, session_id, account_id)
3. What time window? (specific hour/date, not "last 24 hours")
4. What issue type? (error/crash/slow performance/specific feature)

**Step 2: Start with COUNT queries, not full log retrieval:**
- Use SQL aggregation to count logs first
- Understand volume before retrieving full logs
- Avoid retrieving all logs without knowing volume

**Step 3: Use narrow time windows:**
- Start with 1-2 hours, not 24 hours
- Expand only if needed
- Ask developer for specific time if they know when issue occurred

**Step 4: Use efficient query parameters:**
- Use SQL aggregation for counting and pattern analysis
- Limit response size with token parameters
- Limit number of results returned
- Use count-only modes when appropriate

**Step 5: Progressive refinement workflow:**
1. **Count query** → understand volume (how many logs match?)
2. **Pattern query** → identify error types/messages using SQL aggregation
3. **Sample query** → retrieve limited sample if count reasonable (<50 logs)
4. **Full logs** → only retrieve when narrowed to specific issue with filters

### Common Use Cases

**Production debugging:**
```
Search logs for errors in fromagerie-api service
Analyze traces for slow requests
Check service status and metrics
```

**Pattern analysis:**
```
Identify error trends over time
Find performance bottlenecks
Analyze request patterns
```

**Service monitoring:**
```
Check service health and uptime
Monitor key metrics (latency, error rates)
Review recent deployments
```

**Oncall support:**
```
Investigate incident-related logs
Gather context for bug tickets
Find related errors and patterns
```

### Identifying App Flavor

**Always ask about app flavor when debugging mobile issues (unless already known):**

| Package ID | Description | Use Case |
|------------|-------------|----------|
| `world.bitkey.app` | Production app (public stores) | User-reported production issues |
| `world.bitkey.team` | Internal app (TestFlight/employees) | Internal testing issues |
| `world.bitkey.debug` | Developer Android build (local) | Android development debugging |
| `world.bitkey.dev` | Developer iOS build (local) | iOS development debugging |

**Query pattern:** `service:(world.bitkey.app OR world.bitkey.team OR world.bitkey.debug OR world.bitkey.dev)`

### Identifying User Sessions

**Always request identifying attributes at start of debugging session (unless already known):**

**Attribute Reference (in order of preference):**

1. **`usr.app_installation_id`** - **BEST and PREFERRED attribute**
   - **What**: Unique UUID identifying a specific app installation
   - **Format**: UUID uppercase with hyphens (e.g., `AB2CA7AE-2B9C-401E-B830-45ADBD8B07A0`)
   - **Where**: Mobile app logs (`world.bitkey.*`) and API logs (`fromagerie-api`)
   - **When to use**: Primary identifier for tracking a specific app instance
   - **Query**: `@usr.app_installation_id:"AB2CA7AE-2B9C-401E-B830-45ADBD8B07A0"`
   - **Note**: This changes when user performs Lost App recovery or switches devices

2. **`usr.hardware_serial_number`** - **Use for hardware-specific issues**
   - **What**: Physical Bitkey device serial number
   - **Format**: Alphanumeric string (e.g., `409FS20308003801`, `408FS20308002619`)
   - **Where**: Mobile app logs when hardware connected, API logs during hardware operations
   - **When to use**: NFC issues, firmware updates, device pairing, signing failures
   - **Query**: `@usr.hardware_serial_number:"409FS20308003801"`

3. **`session_id`** - **Use to narrow to exact session**
   - **What**: UUID for a single app session (app launch to background/close)
   - **Format**: UUID lowercase with hyphens (e.g., `3a026d36-0b66-4669-ae5d-4b784b73a96f`)
   - **Where**: Mobile app logs only
   - **When to use**: Issue occurred in specific session, developer knows exact session, reducing noise
   - **Query**: `@session_id:"3a026d36-0b66-4669-ae5d-4b784b73a96f"`

4. **`usr.account_id`** - **Use for API-side debugging**
   - **What**: Bitkey account identifier in URN format
   - **Format**: `urn:wallet-account:{ULID}` (e.g., `urn:wallet-account:01KBGD6PREA8GPTC3BS7N5F6K8`)
   - **Where**: Fromagerie API logs, sometimes in mobile app logs
   - **When to use**: Backend issues, transaction problems, account-level errors
   - **Query**: `@usr.account_id:"urn:wallet-account:01KBGD6PREA8GPTC3BS7N5F6K8"`

5. **`version`** - **Use to correlate with releases**
   - **What**: App version number
   - **Format**: Semantic version (e.g., `2025.21.1`, `2025.20.0`)
   - **Where**: Mobile app logs, tag in Datadog
   - **When to use**: Issue started after release, checking if version-specific
   - **Query**: `@version:"2025.21.1"` or `version:2025.21.1` (tag format)

6. **`build_version`** - **Use for build-specific issues**
   - **What**: Incremental build number
   - **Format**: Integer string (e.g., `4`, `123`)
   - **Where**: Mobile app logs as attribute
   - **When to use**: Differentiating between builds of same version, CI/CD debugging
   - **Query**: `@build_version:"4"`

**Decision tree for which attribute to request:**
- **General issue, need to track user**: Ask for `usr.app_installation_id`
- **Hardware/NFC/device issue**: Ask for `usr.hardware_serial_number` (and app_installation_id)
- **Specific session issue**: Ask for `session_id` (if developer has it)
- **Backend/API issue**: Ask for `usr.account_id` (account URN)
- **Version-related issue**: Ask for `version` or `build_version`
- **Combine multiple attributes** for precision when needed

### Reconstructing Complete User Journey

**CRITICAL: Same user often has multiple installation IDs and devices throughout their lifecycle.**

**Common scenarios where users change installation IDs:**
- **Lost App recovery** - User loses phone, recovers account on new device (new installation ID)
- **Device upgrade** - User switches to new phone (new installation ID)
- **App reinstall** - User deletes and reinstalls app (new installation ID)
- **Hardware replacement** - User gets new Bitkey device (new hardware serial number)

**Your role as AI: Help developer see the complete user story across all installations and devices.**

**High-level investigation workflow:**

1. **Start with what developer provides** (usually one installation ID or account ID)

2. **Find all related installations** by querying for the account ID:
   - Use `usr.account_id` to find all installation IDs associated with this user
   - Each installation ID represents a different app instance or device

3. **Map hardware changes** by tracking serial numbers:
   - Look for hardware serial number changes over time
   - Identify old hardware vs new hardware (e.g., hardware replacement flow)

4. **Reconstruct chronological timeline** across all installations:
   - Combine logs from all installation IDs and hardware serials
   - Sort by timestamp to see complete user journey
   - Identify key events: onboarding, deposits, withdrawals, hardware pairing, recovery flows, sweeps

5. **Present high-level story** to developer:
   - "User onboarded on {date} with installation ID {A} and hardware {serial1}"
   - "User deposited Bitcoin on {date}"
   - "User initiated Lost App recovery on {date}"
   - "User recovered to new device with installation ID {B}"
   - "User replaced hardware from {serial1} to {serial2} on {date}"
   - "User performed sweep on {date}"

**Example investigation approach:**
- Developer provides: "User having issues, installation ID: ABC-123"
- You query for account ID associated with ABC-123
- You find account has 3 installation IDs over time: ABC-123 (current), DEF-456 (previous), GHI-789 (original)
- You find hardware changed from serial 408xxx to 409xxx between installations
- You reconstruct timeline showing: initial onboarding → deposits → lost phone → recovery to new device → hardware replacement → current issue
- You identify the issue started specifically after hardware replacement step

**Key insight: Don't just look at isolated sessions - connect the dots across the user's entire journey.**

### Service Index Reference

**Mobile Applications**
- **world.bitkey.app** - Production iOS/Android app. Query for user-reported issues, crashes, production bugs. Key attributes: `usr.app_installation_id`, `usr.account_id`, `usr.hardware_serial_number`, `session_id`, `version`. Related: fromagerie-api.
- **world.bitkey.team** - Internal app (Block employees, TestFlight). Internal testing, team validation. Same attributes as production.
- **world.bitkey.debug** - Developer Android build (local only). Android development debugging. Same attributes as production.
- **world.bitkey.dev** - Developer iOS build (local only). iOS development debugging. Same attributes as production.
- **build.wallet** - Mobile app build/compilation service. Query for build failures, CI/CD issues.

**Fromagerie API (F8e Backend)**
- **fromagerie-api** - Main backend API serving mobile wallet operations. Query for API errors, slow requests, authentication issues, business logic failures. Key metrics: error rate, p99 latency, request volume. Key attributes: `usr.account_id`, `usr.app_installation_id`, `usr.hardware_serial_number`, `http.status_code`, `http.url`, `http.route`. Related: mobile apps.

**Query Tips**
- Always ask about app flavor (unless known): production, team, debug, or dev build
- Always request identifying attributes (unless known): `usr.app_installation_id` (preferred), `usr.hardware_serial_number`, `session_id`, account ID
- Filter by `service:` to scope to specific service
- Use `usr.app_installation_id` to track user journey across sessions
- Use `session_id` to narrow down to exact session
- Check `http.status_code` for API errors (4xx, 5xx)
- Monitor `error.type` and `error.message` for specific failures
- Use `version` to correlate issues with app releases

## Related Rules
- @ai-rules/slack-mcp-usage.md (for cross-referencing team discussions)
