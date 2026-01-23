---
name: debug-ios-ci
description: Investigate iOS CI builds - failures, performance, flaky tests, build times. Buildkite MCP, mobuild/Runway.
---

# Investigate iOS CI

Use this skill to investigate iOS CI builds: debug failures, analyze performance, identify flaky tests, or understand what a build does.

## Workflow

### Step 1: Get Build Overview

```
mcp__buildkite-mcp__get_build(
  org_slug: "runway",
  pipeline_slug: "wallet",
  build_number: <number>,
  detail_level: "summary"
)
```

For failures: identify which job(s) failed.
For performance: note total duration and job count.

### Step 2: Check Log Size and Plan Strategy

Logs are almost always huge. Check size first to determine strategy:

```
mcp__buildkite-mcp__get_logs_info(
  org_slug: "runway",
  pipeline_slug: "wallet",
  build_number: <number>,
  job_id: <job_id>
)
```

| Log Size | Strategy |
|----------|----------|
| <50KB | Direct analysis with `tail_logs(tail: 200)` |
| 50-500KB | Use `search_logs` with patterns |
| >500KB | Chunk analysis with subagent delegation (see Large Log Strategy) |

### Step 3: Read Logs Efficiently

**For small logs (<50KB)** - read the tail directly:
```
mcp__buildkite-mcp__tail_logs(..., tail: 200)
```

**For medium logs (50-500KB)** - use targeted searches:
```
mcp__buildkite-mcp__search_logs(..., pattern: "<pattern>", context: 3)
```

**For large logs (>500KB)** - delegate to subagents (see Large Log Strategy section).

#### Error Patterns

| Issue Type | Search Pattern |
|------------|----------------|
| Xcode errors | `\[x\]\|error:\|BUILD FAILED` |
| Rust compiler | `error\[E\d{4}\]:` |
| Rust field errors | `struct.*has no field named\|does not have a field` |
| Test failure | `\[x\].*Test\|FAILED\|XCTestCase.*failed` |
| Recipe failure | `Recipe.*failed.*exit code` |
| Missing bundle | `\.xcresult.*not found` |
| Swift compile | `cannot find\|type.*has no member` |
| Linker | `undefined symbol\|ld:.*error` |
| Timing/perf | `Lap Time\|elapsed\|cache` |

### Step 4: Compare Builds (if needed)

For flaky tests or performance regression, compare multiple builds:
```
mcp__buildkite-mcp__list_builds(
  org_slug: "runway",
  pipeline_slug: "wallet",
  branch: "<branch>",
  per_page: 10
)
```

### Step 5: Investigate Infrastructure (if needed)

If it's an infra issue, explore relevant repos:

```bash
# Core Build System
gh repo view squareup/ios-builder                    # Core build toolchain (sqiosbuild)
gh repo view squareup/ios-builder-buildkite-plugin   # Buildkite plugin
gh repo view squareup/macos-environment-buildkite-plugin  # macOS env setup

# Infrastructure
gh repo view squareup/runway                         # Runway infrastructure
gh repo view squareup/tf-mobuild-workers             # Terraform for EC2 workers
```

### Step 6: Escalate if Needed

- **#mdx-ios** - iOS build infrastructure
- **#mobuild-buildkite** - MoBuild and Buildkite
- **#ci-infrastructure** - General CI

---

## Reference

### Architecture

```
Buildkite Pipeline → ios-builder-buildkite-plugin → ios-builder (sqiosbuild) → Xcode
                   ↘ macos-environment-buildkite-plugin ↗
```

iOS builds run on mobuild/Runway infrastructure using Buildkite with macOS EC2 workers managed by MDX team.

### Pipeline Configs

Located in `.buildkite/mobuild/`:

| File | Purpose |
|------|---------|
| `pipeline.pr.yml` | PR builds (unit tests, snapshots, KMP tests) |
| `pipeline.main.yml` | Main branch builds |
| `pipeline.release.ios.yml` | Release builds |
| `pipeline.team.testflight.ios.yml` | TestFlight builds |

Router: `.buildkite/pipeline.sh` routes based on branch/labels.

### Build Actions

| Action | Description |
|--------|-------------|
| `unit` | Run unit tests |
| `ios-snapshots` | Run snapshot tests |
| `debug` | Debug build |
| `release` | Customer release build |
| `team-testflight` | TestFlight build |
| `team-alpha` | Enterprise distribution |

### Key Scripts

| Script | Purpose |
|--------|---------|
| `app/ios/Scripts/CI/BuildProject` | Main build script |
| `app/ios/Scripts/CI/BuildReleaseProject` | Release build script |
| `app/ios/Scripts/CI/RunKmpIosTests` | KMP iOS test runner |

### Caching

- **sccache** - Rust compilation cache
- **Gradle** - Kotlin/KMP build cache

### Buildkite Log Format Patterns

**All jobs:**

| Marker | Pattern | Use |
|--------|---------|-----|
| Section divider | `~~~` | Major phase boundaries |

**ios-builder jobs (Xcode builds):**

| Marker | Pattern | Use |
|--------|---------|-----|
| Action start | `==== Starting to run action: <name> ====` | Find specific build steps |
| Action result | `^^^^ run <name> FAILED!!! ^^^^` | Quickly find failures |
| Failure cascade | `WITH PRIOR FAILURE!` | Distinguish root cause from symptoms |
| Timing | `Logging timing event <step>, Lap Time: <s>s, Elapsed Time: <t>s` | Performance debugging |

**Tip:** Search for `^^^^ run.*FAILED` to find the first failure, then look for `WITH PRIOR FAILURE` to identify cascading failures vs root cause.

**Gradle/KMP jobs:**

| Marker | Pattern | Use |
|--------|---------|-----|
| Task failure | `> Task :module:task FAILED` | Find failed Gradle task |
| Build summary | `BUILD FAILED in` | Confirm build failed |
| Test failure | `FAILED` (with ANSI codes) | Find failed tests |

---

## Buildkite MCP Guidelines

**Always include links** to builds/jobs in responses:
`[Build #123](https://buildkite.com/runway/wallet/builds/123)`

**Token efficiency** (logs can be huge):
1. Start with `get_build` using `detail_level: "summary"`
2. Use `get_logs_info` to check log size before reading
3. Use `tail_logs` (50-100 lines) for failure context
4. Use `search_logs` with patterns for specific issues
5. Only use `read_logs` with `limit` parameter - never unlimited

---

## Large Log Subagent Strategy

For logs >500KB, delegate to subagents to avoid context exhaustion:

| Task | Model | Rationale |
|------|-------|-----------|
| Pattern extraction | `haiku` | Fast/cheap for extracting errors, timings from log chunks |
| Root cause analysis | `sonnet` | Capable model for synthesizing findings, determining fix |

**Workflow for large logs:**

1. Use `search_logs` to identify relevant line ranges (e.g., around failures)
2. Spawn `haiku` subagent with `read_logs(seek: <start>, limit: 500)` to extract details
3. Repeat for multiple failure points if needed
4. Synthesize findings yourself (or spawn `sonnet` for complex root cause analysis)

**Rule of thumb:** Use the fastest model that can do the job. Extraction = fast. Synthesis = capable.
