---
name: use-datadog-mcp
description: Debug production issues with Datadog MCP. Find logs, traces, user sessions, oncall support
---

# Datadog MCP

## Setup

```bash
claude mcp add datadog-mcp -s local -t http https://mcp.datadoghq.com/api/unstable/mcp-server/mcp
```

## Privacy

**REDACT before output:** Customer PII, API keys/tokens.

## Query Strategy

1. **Ask first:** App flavor? User ID? Time window? Issue type?
2. **COUNT first:** Understand volume before retrieving logs
3. **Narrow windows:** Start 1-2 hours, expand if needed
4. **Progressive:** Count → Pattern → Sample → Full logs

---

## User Identifiers

### Attribute Reference (preference order)

**1. `usr.app_installation_id`** - BEST and PREFERRED
- Format: UUID uppercase with hyphens (`AB2CA7AE-2B9C-401E-B830-45ADBD8B07A0`)
- Where: Mobile app logs and API logs
- Query: `@usr.app_installation_id:"AB2CA7AE-2B9C-401E-B830-45ADBD8B07A0"`
- Note: Changes on Lost App recovery or device switch

**2. `usr.hardware_serial_number`** - Hardware-specific issues
- Format: Alphanumeric (`409FS20308003801`)
- When: NFC issues, firmware updates, device pairing, signing failures
- Query: `@usr.hardware_serial_number:"409FS20308003801"`

**3. `session_id`** - Narrow to exact session
- Format: UUID lowercase (`3a026d36-0b66-4669-ae5d-4b784b73a96f`)
- Where: Mobile app logs only
- Query: `@session_id:"3a026d36-0b66-4669-ae5d-4b784b73a96f"`

**4. `usr.account_id`** - API-side debugging
- Format: `urn:wallet-account:{ULID}`
- When: Backend issues, transaction problems
- Query: `@usr.account_id:"urn:wallet-account:01KBGD6PREA8GPTC3BS7N5F6K8"`

**5. `version`** / `build_version` - Release correlation
- Query: `@version:"2025.21.1"` or `@build_version:"4"`

### Decision Tree

| Issue Type | Ask For |
|------------|---------|
| General user tracking | `usr.app_installation_id` |
| Hardware/NFC/device | `usr.hardware_serial_number` + app_installation_id |
| Specific session | `session_id` |
| Backend/API | `usr.account_id` |
| Version-related | `version` or `build_version` |

---

## User Journey Reconstruction

**CRITICAL: Same user often has multiple installation IDs and devices throughout their lifecycle.**

### Common Scenarios

- **Lost App recovery** - New installation ID on new device
- **Device upgrade** - New installation ID
- **App reinstall** - New installation ID
- **Hardware replacement** - New hardware serial number

### Investigation Workflow

1. **Start with what developer provides** (usually one installation ID or account ID)

2. **Find all related installations** by querying for the account ID:
   - Use `usr.account_id` to find all installation IDs
   - Each installation ID = different app instance or device

3. **Map hardware changes** by tracking serial numbers:
   - Look for hardware serial number changes over time
   - Identify old vs new hardware

4. **Reconstruct chronological timeline** across all installations:
   - Combine logs from all installation IDs and hardware serials
   - Sort by timestamp
   - Key events: onboarding, deposits, withdrawals, hardware pairing, recovery flows, sweeps

5. **Present high-level story** to developer:
   - "User onboarded on {date} with installation ID {A} and hardware {serial1}"
   - "User initiated Lost App recovery on {date}"
   - "User recovered to new device with installation ID {B}"
   - "User replaced hardware from {serial1} to {serial2} on {date}"

**Key insight: Don't just look at isolated sessions - connect the dots across the user's entire journey.**

---

## App Flavors

| Package | Description |
|---------|-------------|
| `world.bitkey.app` | Production |
| `world.bitkey.team` | Internal/TestFlight |
| `world.bitkey.debug` | Android dev |
| `world.bitkey.dev` | iOS dev |

Query pattern: `service:(world.bitkey.app OR world.bitkey.team)`

## Services

| Service | Description | Key Attributes |
|---------|-------------|----------------|
| `world.bitkey.*` | Mobile apps | `usr.app_installation_id`, `session_id`, `version` |
| `fromagerie-api` | Backend API | `usr.account_id`, `http.status_code`, `http.route` |
| `build.wallet` | CI/CD | Build failures |

## Query Tips

- Filter by `service:` to scope to specific service
- Check `http.status_code` for API errors (4xx, 5xx)
- Monitor `error.type` and `error.message` for specific failures
- Use `version` to correlate issues with app releases

## Always Include

- Direct Datadog URLs to source data
- Summaries with patterns identified
- Related Slack discussions if relevant
