# Telemetry (aka observability)

This crate sets up telemetry!

## Environment variables

- `OTEL_EXPORTER_JAEGER_ENDPOINT` (default: `127.0.0.1:6831`) controls the Jaeger endpoint

  ```sh
  export OTEL_EXPORTER_JAEGER_ENDPOINT=hostname:6831
  ```

- `RUST_LOG` (default: `info`) controls the log _and_ span level

  ```sh
  export RUST_LOG=trace
  ```

## How to use

```rust
// The easy way!
#[tracing::instrument]   // Add me!
fn my_function() {
    tracing::info!("something has happened inside my_function!");    // Use me!
}

// The harder way!
tracing::info_span!("my_span").in_scope(|| {
    tracing::info!("something has happened inside my_span!");
});

// The hardest way! (really, don't)
let span = tracing::span!(tracing::Level::INFO, "my_span");
let _guard = span.enter();
tracing::event!(tracing::Level::INFO, "something happened inside my_span");
```

## Features

- [x] 🪵 Logs
  - JSON structed logging to stdout
- [x] 📏 Traces
  - via [Datadog](https://crates.io/crates/opentelemetry-datadog) (production)
  - via [Jaeger](https://crates.io/crates/opentelemetry-jaeger) (development)
- [x] 🔢 Metrics
  - via [Datadog](https://crates.io/crates/opentelemetry-datadog) (production)
