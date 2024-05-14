use opentelemetry::metrics::AsyncInstrument;

pub use opentelemetry::metrics::{
    Counter, Histogram, ObservableCounter, ObservableGauge, UpDownCounter,
};

use crate::{
    metrics::error::MetricsError, metrics::KeyValue, metrics::Unit, middleware::RouterName,
};

pub trait ObservableCallbackRegistry<T> {
    fn register_callback(
        &self,
        instrument: impl AsyncInstrument<T> + 'static,
        callback: impl Fn() -> T + Send + Sync + 'static,
        attributes: &[KeyValue],
    ) -> Result<(), MetricsError>;
}

pub struct MetricsFactory {
    namespace: String,
    pub(crate) meter: opentelemetry::metrics::Meter,
}

impl MetricsFactory {
    pub fn new(namespace: impl std::fmt::Display) -> Self {
        let fqn = format!("bitkey.{}", namespace);
        Self {
            namespace: fqn.clone(),
            meter: opentelemetry::global::meter(fqn),
        }
    }

    pub fn u64_counter(&self, name: impl std::fmt::Display, unit: Option<Unit>) -> Counter<u64> {
        let fqn = format!("{}.{}", self.namespace, name);
        let counter = self.meter.u64_counter(fqn);
        if let Some(unit) = unit {
            return counter.with_unit(unit).init();
        }
        counter.init()
    }

    pub fn u64_observable_counter(
        &self,
        name: impl std::fmt::Display,
        unit: Option<Unit>,
    ) -> ObservableCounter<u64> {
        let fqn = format!("{}.{}", self.namespace, name);
        let counter = self.meter.u64_observable_counter(fqn);
        if let Some(unit) = unit {
            return counter.with_unit(unit).init();
        }
        counter.init()
    }

    pub fn i64_up_down_counter(
        &self,
        name: impl std::fmt::Display,
        unit: Option<Unit>,
    ) -> UpDownCounter<i64> {
        let fqn = format!("{}.{}", self.namespace, name);
        let counter = self.meter.i64_up_down_counter(fqn);
        if let Some(unit) = unit {
            return counter.with_unit(unit).init();
        }
        counter.init()
    }

    pub fn f64_histogram(
        &self,
        name: impl std::fmt::Display,
        unit: Option<Unit>,
    ) -> Histogram<f64> {
        let fqn = format!("{}.{}", self.namespace, name);
        let histogram = self.meter.f64_histogram(fqn);
        if let Some(unit) = unit {
            return histogram.with_unit(unit).init();
        }
        histogram.init()
    }

    pub fn u64_histogram(
        &self,
        name: impl std::fmt::Display,
        unit: Option<Unit>,
    ) -> Histogram<u64> {
        let fqn = format!("{}.{}", self.namespace, name);
        let histogram = self.meter.u64_histogram(fqn);
        if let Some(unit) = unit {
            return histogram.with_unit(unit).init();
        }
        histogram.init()
    }

    pub fn u64_observable_gauge(
        &self,
        name: impl std::fmt::Display,
        unit: Option<Unit>,
    ) -> ObservableGauge<u64> {
        let fqn = format!("{}.{}", self.namespace, name);
        let gauge = self.meter.u64_observable_gauge(fqn);
        if let Some(unit) = unit {
            return gauge.with_unit(unit).init();
        }
        gauge.init()
    }

    pub fn f64_observable_gauge(
        &self,
        name: impl std::fmt::Display,
        unit: Option<Unit>,
    ) -> ObservableGauge<f64> {
        let fqn = format!("{}.{}", self.namespace, name);
        let gauge = self.meter.f64_observable_gauge(fqn);
        if let Some(unit) = unit {
            return gauge.with_unit(unit).init();
        }
        gauge.init()
    }

    pub fn route_layer(&self, router_name: String) -> RouterName {
        RouterName(router_name)
    }
}

impl ObservableCallbackRegistry<u64> for MetricsFactory {
    fn register_callback(
        &self,
        instrument: impl AsyncInstrument<u64> + 'static,
        callback: impl Fn() -> u64 + Send + Sync + 'static,
        attributes: &[KeyValue],
    ) -> Result<(), MetricsError> {
        let attributes = attributes.to_owned();

        self.meter
            .register_callback(&[instrument.as_any()], move |observer| {
                observer.observe_u64(&instrument, callback(), &attributes);
            })?;

        Ok(())
    }
}

impl ObservableCallbackRegistry<i64> for MetricsFactory {
    fn register_callback(
        &self,
        instrument: impl AsyncInstrument<i64> + 'static,
        callback: impl Fn() -> i64 + Send + Sync + 'static,
        attributes: &[KeyValue],
    ) -> Result<(), MetricsError> {
        let attributes = attributes.to_owned();

        self.meter
            .register_callback(&[instrument.as_any()], move |observer| {
                observer.observe_i64(&instrument, callback(), &attributes);
            })?;

        Ok(())
    }
}

impl ObservableCallbackRegistry<f64> for MetricsFactory {
    fn register_callback(
        &self,
        instrument: impl AsyncInstrument<f64> + 'static,
        callback: impl Fn() -> f64 + Send + Sync + 'static,
        attributes: &[KeyValue],
    ) -> Result<(), MetricsError> {
        let attributes = attributes.to_owned();

        self.meter
            .register_callback(&[instrument.as_any()], move |observer| {
                observer.observe_f64(&instrument, callback(), &attributes);
            })?;

        Ok(())
    }
}
