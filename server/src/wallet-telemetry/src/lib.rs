use std::env;
use std::net::AddrParseError;

use opentelemetry::metrics::MetricsError;
use opentelemetry::trace::{TraceError, TracerProvider};
use opentelemetry::{KeyValue, StringValue};
use opentelemetry_sdk::metrics::SdkMeterProvider;
use opentelemetry_sdk::Resource;
use opentelemetry_semantic_conventions::resource;
use serde::Deserialize;
use thiserror::Error;
use tracing::subscriber::SetGlobalDefaultError;
use tracing_subscriber::fmt::format::JsonFields;
use tracing_subscriber::{prelude::__tracing_subscriber_SubscriberExt, EnvFilter};

mod datadog;
mod json;

pub mod baggage_keys {
    pub const APP_INSTALLATION_ID: &str = "app_installation_id";
    pub const ACCOUNT_ID: &str = "account_id";
    pub const HARDWARE_SERIAL_NUMBER: &str = "hardware_serial_number";
    pub const APP_ID: &str = "app_id";
}

pub const METRICS_REPORTING_PERIOD_SECS: u64 = 15;

// TODO:[W-1236] Remove changes to Config and Mode (switch back to pub(crate))
#[derive(Clone, Debug, Deserialize)]
pub struct Config {
    pub service_name: String,
    pub mode: Option<Mode>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Mode {
    Datadog,
    Jaeger,
}

#[derive(Debug, Error)]
pub enum Error {
    #[error(transparent)]
    AddrParseError(#[from] AddrParseError),
    #[error(transparent)]
    OpentelemetryError(#[from] opentelemetry::global::Error),
    #[error(transparent)]
    TracingSetGlobalDefaultError(#[from] SetGlobalDefaultError),
    #[error(transparent)]
    TraceError(#[from] TraceError),
    #[error(transparent)]
    MetricsError(#[from] MetricsError),
    #[error("configuration error: {0:?}")]
    ConfigurationError(#[from] Box<dyn std::error::Error + Send + Sync + 'static>),
}

pub fn set_global_telemetry(config: &Config) -> Result<(), Error> {
    env::set_var(
        "RUST_LOG",
        env::var("RUST_LOG").unwrap_or_else(|_| "info".to_string()),
    );

    opentelemetry::global::set_error_handler(handle_error)?;

    // Datadog and Jaeger both use the OpenTelemetry propagator and exporter. The export
    // ports are different and are configured by environment variables.
    match config.mode {
        None => Ok(()),
        Some(Mode::Datadog) => {
            set_global_tracing(config)?;
            set_global_metrics(config, datadog::init_metrics)
        }
        Some(Mode::Jaeger) => set_global_tracing(config),
    }
}

fn handle_error<T: Into<opentelemetry::global::Error>>(err: T) {
    match err.into() {
        opentelemetry::global::Error::Trace(err) => {
            ::tracing::error!("OpenTelemetry trace error occurred. {}", err)
        }
        opentelemetry::global::Error::Metric(err) => {
            ::tracing::error!("OpenTelemetry metrics error occurred. {}", err)
        }
        opentelemetry::global::Error::Other(err_msg) => {
            ::tracing::error!("OpenTelemetry error occurred. {}", err_msg)
        }
        err => ::tracing::error!("OpenTelemetry error occurred. {}", err),
    }
}

fn set_global_tracing(config: &Config) -> Result<(), Error> {
    // We use the OpenTelemetry W3C TraceContext propagator for propagating traces across services.
    // This is independent of how they are exported to the respective collector (Datadog or Jaeger).
    opentelemetry::global::set_text_map_propagator(
        opentelemetry_sdk::propagation::TraceContextPropagator::default(),
    );

    // Initialize the OpenTelemetry tracer, and configure the OTLP exporter to send spans to the
    // default OTLP gRPC port 4317. Both Datadog and Jaeger use the OpenTelemetry exporter, and
    // the agents listen on the same port.
    let resource = make_resource(config.service_name.clone(), env!("CARGO_PKG_VERSION"));
    let tracer = opentelemetry_otlp::new_pipeline()
        .tracing()
        .with_exporter(opentelemetry_otlp::new_exporter().tonic())
        .with_trace_config(
            opentelemetry_sdk::trace::Config::default()
                .with_resource(resource.clone())
                .with_sampler(opentelemetry_sdk::trace::Sampler::AlwaysOn),
        )
        .install_batch(opentelemetry_sdk::runtime::Tokio)
        .map(|p| p.tracer(config.service_name.clone()))?;

    // Configure the Tokio tracing library to forward to the OpenTelemetry tracer
    let layer = tracing_opentelemetry::layer().with_tracer(tracer);

    // Configure log level from environment variables. Add an override for opentelemetry because
    // opentelemetry logs spans as TRACE, so don't filter them from the propagation pipeline
    let filter =
        EnvFilter::from_default_env().add_directive("otel::tracing=trace".parse().unwrap());

    // Configure the JSON formatter for logs and inject trace ID into log events.
    // Also wire up the configurations above into the tracing subscriber.
    let subscriber = tracing_subscriber::fmt::fmt()
        .with_env_filter(filter)
        .fmt_fields(JsonFields::default())
        .event_format(json::JsonTraceIdFormat::default())
        .finish()
        .with(layer);

    tracing::subscriber::set_global_default(subscriber)?;

    Ok(())
}

fn set_global_metrics<F>(config: &Config, metrics_fn: F) -> Result<(), Error>
where
    F: FnOnce(Resource) -> opentelemetry::metrics::Result<SdkMeterProvider>,
{
    let resource = make_resource(config.service_name.clone(), env!("CARGO_PKG_VERSION"));
    let provider = metrics_fn(resource)?;
    opentelemetry::global::set_meter_provider(provider);

    Ok(())
}

fn make_resource(
    service_name: impl Into<StringValue>,
    service_version: impl Into<StringValue>,
) -> Resource {
    Resource::default().merge(&Resource::new(vec![
        KeyValue::new(resource::SERVICE_NAME, service_name.into()),
        KeyValue::new(resource::SERVICE_VERSION, service_version.into()),
    ]))
}
