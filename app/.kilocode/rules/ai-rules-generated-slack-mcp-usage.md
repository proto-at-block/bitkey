# Slack MCP Channel Reference

## Summary
Channel mapping for gathering development context from Bitkey team discussions. Focus on core engineering and product channels.

## Channel Mapping

**Core Engineering:**
- `#bitkey-software` - Main software engineering channel: PR reviews, dependency updates, CI/CD, oncall coordination
- `#bitkey-firmware` - Embedded software for bitkey.build: hardware debugging, CLI tooling, manufacturing tests
- `#bitkey-security` - Security topics, designs, problems, solutions (NOT for active incidents)
- `#bitkey-reproducibility` - Build reproducibility: dependency management, environment consistency
- `#bitcoin-engineering` - Cross-org Bitcoin engineering coordination
- `#bitcoin-engineering-education-wg` - Bitcoin engineering education working group

**Operations:**
- `#bitkey-situation` - On-call and production support: incidents, emergency response
- `#bitkey-alerts` - Datadog alerting and monitoring notifications
- `#bitkey-robots` - Automated notifications from Bitkey systems: CI/CD, bots
- `#bitkey-releases` - App release updates for App Store/Play Store, feature rollouts (bi-weekly Fridays)

**Product & Business:**
- `#bitkey-discuss` - General discussion about all things Bitkey
- `#bitkey-team` - Team announcements and coordination
- `#bitkey-partnerships` - Partnerships strategy for on/off ramps and distribution partners
- `#bitkey-marketing` - Marketing updates and questions for Bitkey Marketing team
- `#bitkey-ai` - AI tooling and integration: Claude MCP servers, AI-assisted development
- `#bitkey-market` - Market usage for Bitkey: exchange integrations
- `#bitkey-money-and-growth` - Growth initiatives, privacy features, descriptor work
- `#bitkey-w3-monetization` - W3 monetization discussions
- `#w3-core` - Extended Core Team communication (previously bitkey-w3)
- `#bitkey-discover-pay` - Discover and Pay feature development, purchase flows

**Hardware & Integration:**
- `#bitkey-w1-hardware` - W1 hardware team: product definition, features, program planning
- `#cash-bitkey-integration` - Cash App integration coordination
- `#proj-cash-bitcoin-bitkey` - W1 and Cash App team partner opportunities

**External Partners:**
- `#ext-bitkey-blockstream` - Blockstream integration: Electrum RPC API service issues
- `#ext-bitkey-mempool` - mempool.space team communication
- `#ext-bitkey-touchlab` - TouchLab KMP partnership

**Support & Escalation:**
- `#bitkey-cs-linear-oncall` - Support Linear escalations from Zendesk to oncall
- `#bitkey-socials-escalation` - Social media comments requiring Bitkey response

**Projects (`-bitkey-` prefix, short/mid-term feature work):**
- `#-bitkey-bdk-10` - BDK 1.0 upgrade project
- `#-bitkey-security-hub` - Security Hub feature development
- `#-bitkey-your-balance-graph` - Balance graph UI feature
- `#-bitkey-nfc-investigation-proj` - NFC investigation and improvements

**Archived (historical context, searchable):**
- `#bitkey-mobile` - Mobile development discussions (archived, merged into #bitkey-software)
- `#bitkey-server` - Server/backend development (archived, merged into #bitkey-software)
- `#bitkey-recovery` - Access and Recovery feature work (archived)
- `#bitkey-recovery-alerts` - Recovery monitoring alerts (archived)
- `#bitkey-customer-support` - Customer support coordination (archived)
- `#bitkey-copy` - Copy/content requests (archived)
- `#-bitkey-android-verifiability-flakes` - Android build reproducibility investigation (archived, resolved)

## Search Strategy

**Privacy:** REDACT all PII, API keys, and customer info from search results before outputting.

**Channel Priority by Intent:**
- Architecture/patterns → `#bitkey-software`, `#bitkey-security` first
- Implementation bugs → `#bitkey-situation`, `#bitkey-software`
- Product requirements → `#bitkey-discuss`, `#bitkey-team`
- Integration issues → Relevant `#ext-bitkey-*` channel
- Feature development → Relevant `#-bitkey-*` project channel

**Slack Domain:** Use `sq-block.slack.com` for all Slack links.

**Always Include Discussion Links:** When using Slack MCP to answer queries, always include relevant Slack discussion links at the end of responses. Format as:

```
## Key Discussion Links

**Topic Category:**
- [Brief description](https://sq-block.slack.com/archives/...) - Context about what this discusses
- [Another discussion](https://sq-block.slack.com/archives/...) - Additional context

**Related Context:**
- [Supporting discussion](https://sq-block.slack.com/archives/...) - How this relates to the main topic
```

This enables users to dive deeper into the source discussions and verify context.

## Related Rules
- @ai-rules/domain-service-pattern.md
- @ai-rules/strong-typing.md
