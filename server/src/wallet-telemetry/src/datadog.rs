use std::time::Duration;

use opentelemetry::trace::TraceError;
use opentelemetry_sdk::{
    metrics::{
        data::Temporality,
        reader::{AggregationSelector, TemporalitySelector},
        Aggregation, InstrumentKind, MeterProvider,
    },
    trace::Tracer,
    Resource,
};

use crate::METRICS_REPORTING_PERIOD_SECS;

pub fn init_tracer(resource: Resource) -> Result<Tracer, TraceError> {
    opentelemetry::global::set_text_map_propagator(
        opentelemetry_sdk::propagation::TraceContextPropagator::default(),
    );

    // Initialize tracing pipeline
    opentelemetry_otlp::new_pipeline()
        .tracing()
        .with_exporter(opentelemetry_otlp::new_exporter().tonic())
        .with_trace_config(
            opentelemetry_sdk::trace::config()
                .with_resource(resource)
                .with_sampler(opentelemetry_sdk::trace::Sampler::AlwaysOn),
        )
        .install_batch(opentelemetry_sdk::runtime::Tokio)
}

#[derive(Clone)]
struct DatadogSelector;

impl AggregationSelector for DatadogSelector {
    fn aggregation(&self, kind: InstrumentKind) -> Aggregation {
        match kind {
            InstrumentKind::Counter
            | InstrumentKind::UpDownCounter
            | InstrumentKind::ObservableCounter
            | InstrumentKind::ObservableUpDownCounter => Aggregation::Sum,
            InstrumentKind::ObservableGauge => Aggregation::LastValue,
            // Slightly modified from defaults to be more meaningful up to the range of number
            //   of minutes in ~1-2 months and still be meaningful for a more standard usecase
            //   of request latency milliseconds
            InstrumentKind::Histogram => Aggregation::ExplicitBucketHistogram {
                boundaries: vec![
                    0.0, 7.5, 15.0, 30.0, 60.0, 120.0, 240.0, 480.0, 960.0, 1920.0, 3840.0, 7680.0,
                    15360.0, 30720.0, 61440.0,
                ],
                record_min_max: true,
            },
        }
    }
}

impl TemporalitySelector for DatadogSelector {
    // https://docs.datadoghq.com/opentelemetry/guide/otlp_delta_temporality/
    fn temporality(&self, kind: InstrumentKind) -> Temporality {
        match kind {
            InstrumentKind::Counter
            | InstrumentKind::Histogram
            | InstrumentKind::ObservableCounter => Temporality::Delta,
            InstrumentKind::UpDownCounter
            | InstrumentKind::ObservableUpDownCounter
            | InstrumentKind::ObservableGauge => Temporality::Cumulative,
        }
    }
}

pub fn init_metrics(resource: Resource) -> opentelemetry::metrics::Result<MeterProvider> {
    let selector = DatadogSelector;
    opentelemetry_otlp::new_pipeline()
        .metrics(opentelemetry_sdk::runtime::Tokio)
        .with_exporter(opentelemetry_otlp::new_exporter().tonic())
        .with_aggregation_selector(selector.clone())
        .with_temporality_selector(selector)
        .with_resource(resource)
        .with_period(Duration::from_secs(METRICS_REPORTING_PERIOD_SECS))
        .build()
}
