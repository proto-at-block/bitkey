---
name: run-tests
description: Run tests with gradle, filter by module/class/name, debug failures, integration tests
---

# Run Tests

## Unit Tests

```bash
gradle jvmTest                                      # All JVM
gradle testDebugUnitTest                            # Android
gradle iosTest                                      # iOS
gradle :module:jvmTest                              # Single module
gradle :module:jvmTest --tests "TestClass.test name"  # Single test
```

## Integration Tests

**CRITICAL: Start backend first**

```bash
just start-backend                        # Start local server
gradle jvmIntegrationTest                 # Run tests
```

Filter by tags:
```bash
gradle jvmIntegrationTest -Dkotest.tags="!IsolatedTest"
gradle jvmIntegrationTest -Dkotest.tags="IsolatedTest" --max-workers=1
```

## Filtering

```bash
gradle :module:jvmTest --tests "*.ServiceTest*"   # By class
gradle :module:jvmTest --tests "*test name*"      # By name
```

## Debugging Failures

```bash
gradle :module:jvmTest --warn             # Diagnostic context
gradle :module:jvmTest --stacktrace       # Stack traces
gradle :module:jvmTest --console=plain    # Parseable output
```

## Common Issues

- **Flaky**: Shared mutable state, missing `reset()`
- **Timeout**: Missing `awaitItem()`, stuck Flow
- **Order-dependent**: Missing `beforeTest` setup
- **Integration fails**: `just restart-backend`

@docs/docs/mobile/testing/overview.md
@docs/docs/mobile/testing/integration.md
