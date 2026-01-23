---
name: use-slack-mcp
description: Search Slack for context, discussions, team decisions, channel mapping
---

# Slack MCP

## Setup

```bash
claude mcp add slack -s local -- env SLACK_TOKEN=$SLACK_TOKEN SLACK_DEFAULT_WORKSPACE_ID=T05HJ0CKWG5 uvx mcp_slack
```

## Privacy

**REDACT before output:** Customer PII, API keys/tokens.

## Key Channels

**Engineering:**
- `#bitkey-software` - Main engineering, PRs, CI/CD, oncall
- `#bitkey-firmware` - Embedded/hardware
- `#bitkey-security` - Security topics

**Operations:**
- `#bitkey-situation` - Oncall, incidents
- `#bitkey-alerts` - Datadog alerts
- `#bitkey-releases` - App releases

**Product:**
- `#bitkey-discuss` - General discussion
- `#bitkey-team` - Team coordination
- `#bitkey-ai` - AI tooling

**Projects:** `#-bitkey-*` channels for feature work

## Search Strategy

| Intent | Start with |
|--------|------------|
| Architecture/patterns | `#bitkey-software`, `#bitkey-security` |
| Bugs/incidents | `#bitkey-situation`, `#bitkey-software` |
| Product requirements | `#bitkey-discuss`, `#bitkey-team` |
| Feature development | `#-bitkey-*` project channel |

## Always Include

Discussion links in responses:

```markdown
## Key Discussion Links

**Topic:**
- [Description](https://sq-block.slack.com/archives/...) - Context
```

Slack domain: `sq-block.slack.com`
