use thiserror::Error;

#[derive(Debug, Error)]
pub enum MetricsError {
    #[error(transparent)]
    OtelMetricsError(#[from] opentelemetry::metrics::MetricsError),
}
