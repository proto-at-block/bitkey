use crate::METRICS_REPORTING_PERIOD_SECS;
use opentelemetry_sdk::{
    metrics::{
        data::Temporality,
        reader::{AggregationSelector, TemporalitySelector},
        Aggregation, InstrumentKind, SdkMeterProvider,
    },
    Resource,
};
use std::time::Duration;

#[derive(Clone)]
struct DatadogSelector;

impl AggregationSelector for DatadogSelector {
    fn aggregation(&self, kind: InstrumentKind) -> Aggregation {
        match kind {
            InstrumentKind::Counter
            | InstrumentKind::UpDownCounter
            | InstrumentKind::ObservableCounter
            | InstrumentKind::ObservableUpDownCounter => Aggregation::Sum,
            InstrumentKind::ObservableGauge | InstrumentKind::Gauge => Aggregation::LastValue,
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
            | InstrumentKind::Gauge
            | InstrumentKind::ObservableGauge => Temporality::Cumulative,
        }
    }
}

pub fn init_metrics(resource: Resource) -> opentelemetry::metrics::Result<SdkMeterProvider> {
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
