#![forbid(unsafe_code)]

use std::env;
use std::net::AddrParseError;

use opentelemetry::metrics::MetricsError;
use opentelemetry::trace::TraceError;
use opentelemetry::StringValue;
use opentelemetry_sdk::metrics::MeterProvider;
use opentelemetry_sdk::trace::Tracer;
use opentelemetry_sdk::Resource;
use opentelemetry_semantic_conventions::resource;
use serde::Deserialize;
use thiserror::Error;
use tracing::subscriber::SetGlobalDefaultError;
use tracing_subscriber::fmt::format::JsonFields;
use tracing_subscriber::{prelude::__tracing_subscriber_SubscriberExt, EnvFilter};

mod datadog;
mod jaeger;
mod json;

pub const APP_INSTALLATION_ID_BAGGAGE_KEY: &str = "app_installation_id";
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

    match config.mode {
        None => Ok(()),
        Some(Mode::Datadog) => {
            set_global_tracing(config, datadog::init_tracer)?;
            set_global_metrics(config, datadog::init_metrics)
        }
        Some(Mode::Jaeger) => set_global_tracing(config, jaeger::init_tracer),
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

fn set_global_tracing<F>(config: &Config, tracer_fn: F) -> Result<(), Error>
where
    F: FnOnce(Resource) -> Result<Tracer, TraceError>,
{
    let resource = make_resource(config.service_name.clone(), env!("CARGO_PKG_VERSION"));
    let tracer = tracer_fn(resource)?;
    let layer = tracing_opentelemetry::layer().with_tracer(tracer);

    // opentelemetry logs spans as TRACE, so don't filter them from the propagation pipeline
    let filter =
        EnvFilter::from_default_env().add_directive("otel::tracing=trace".parse().unwrap());

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
    F: FnOnce(Resource) -> opentelemetry::metrics::Result<MeterProvider>,
{
    let resource = make_resource(config.service_name.clone(), env!("CARGO_PKG_VERSION"));
    metrics_fn(resource)?;

    Ok(())
}

fn make_resource(
    service_name: impl Into<StringValue>,
    service_version: impl Into<StringValue>,
) -> Resource {
    Resource::default().merge(&Resource::new(vec![
        resource::SERVICE_NAME.string(service_name),
        resource::SERVICE_VERSION.string(service_version),
    ]))
}
