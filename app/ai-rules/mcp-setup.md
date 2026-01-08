---
description: Guidelines for adding new MCP server support to the AI rules system
globs: ["ai-rules/**"]
alwaysApply: true
---

# MCP Server Setup

## Summary
Add Model Context Protocol (MCP) servers to the AI rules system.

## When to Apply
- Adding a new MCP server
- Setting up MCP authentication

## How to Apply

1. **Configure MCP server** using official MCP protocol format
   - Reference: https://modelcontextprotocol.io/docs/concepts/transports
   - Add configuration to `ai-rules/mcp.json`
   - Use stdio transport format:
   ```json
   {
     "mcpServers": {
       "server-name": {
         "command": "executable",
         "args": ["arg1", "arg2"],
         "env": {
           "VAR_NAME": "${VAR_NAME}"
         }
       }
     }
   }
   ```

2. **Generate configs**
   ```bash
   ai-rules generate
   ```

3. **Create usage rule**
   - Create `[mcp-name]-mcp-usage.md` in `ai-rules/`
   - Ask clarifying questions:
     - What data/context does this MCP provide?
     - When should AI assistants use it?
     - Privacy/security considerations?
     - Best practices?
   - Follow pattern from `@ai-rules/slack-mcp-usage.md`
   - Add entry to `@ai-rules/bitkey-mobile.md` under "Development Tools"
   - Run `ai-rules generate`

## Related Rules
- @ai-rules/slack-mcp-usage.md (example)
