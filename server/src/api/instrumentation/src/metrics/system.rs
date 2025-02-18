use std::{
    sync::{Arc, RwLock},
    time::Duration,
};

use once_cell::sync::Lazy;
use opentelemetry::{
    global,
    metrics::{Counter, Meter, ObservableGauge},
};
use tokio::runtime::Handle;
use tokio_metrics::{RuntimeMetrics, RuntimeMonitor};

use crate::metrics::error::MetricsError;

// https://github.com/tokio-rs/tokio-metrics/tree/main#base-metrics-1
static TOKIO_METER: Lazy<Meter> = Lazy::new(|| global::meter("bitkey.tokio"));
static TOKIO_WORKERS: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.workers_count")
        .init()
});
static TOKIO_TOTAL_PARK: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_park_count")
        .init()
});
static TOKIO_MAX_PARK: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_park_count")
        .init()
});
static TOKIO_MIN_PARK: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_park_count")
        .init()
});
static TOKIO_TOTAL_NOOP: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_noop_count")
        .init()
});
static TOKIO_MAX_NOOP: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_noop_count")
        .init()
});
static TOKIO_MIN_NOOP: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_noop_count")
        .init()
});
static TOKIO_TOTAL_STEAL: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_steal_count")
        .init()
});
static TOKIO_MAX_STEAL: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_steal_count")
        .init()
});
static TOKIO_MIN_STEAL: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_steal_count")
        .init()
});
static TOKIO_TOTAL_STEAL_OPERATIONS: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_steal_operations")
        .init()
});
static TOKIO_MAX_STEAL_OPERATIONS: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_steal_operations")
        .init()
});
static TOKIO_MIN_STEAL_OPERATIONS: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_steal_operations")
        .init()
});
static TOKIO_NUM_REMOTE_SCHEDULES: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.num_remote_schedules")
        .init()
});
static TOKIO_TOTAL_LOCAL_SCHEDULE: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_local_schedule_count")
        .init()
});
static TOKIO_MAX_LOCAL_SCHEDULE: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_local_schedule_count")
        .init()
});
static TOKIO_MIN_LOCAL_SCHEDULE: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_local_schedule_count")
        .init()
});
static TOKIO_TOTAL_OVERFLOW: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_overflow_count")
        .init()
});
static TOKIO_MAX_OVERFLOW: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_overflow_count")
        .init()
});
static TOKIO_MIN_OVERFLOW: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_overflow_count")
        .init()
});
static TOKIO_TOTAL_POLLS: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_polls_count")
        .init()
});
static TOKIO_MAX_POLLS: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_polls_count")
        .init()
});
static TOKIO_MIN_POLLS: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_polls_count")
        .init()
});
static TOKIO_TOTAL_BUSY_DURATION: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.total_busy_duration_count")
        .with_unit("ms")
        .init()
});
static TOKIO_MAX_BUSY_DURATION: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_busy_duration_count")
        .with_unit("ms")
        .init()
});
static TOKIO_MIN_BUSY_DURATION: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_busy_duration_count")
        .with_unit("ms")
        .init()
});
static TOKIO_INJECTION_QUEUE_DEPTH: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.injection_queue_depth")
        .init()
});
static TOKIO_TOTAL_LOCAL_QUEUE_DEPTH: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.total_local_queue_depth")
        .init()
});
static TOKIO_MAX_LOCAL_QUEUE_DEPTH: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.max_local_queue_depth")
        .init()
});
static TOKIO_MIN_LOCAL_QUEUE_DEPTH: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_local_queue_depth")
        .init()
});
static TOKIO_ELAPSED: Lazy<ObservableGauge<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_observable_gauge("bitkey.tokio.min_local_queue_depth")
        .with_unit("ms")
        .init()
});
static TOKIO_BUDGET_FORCED_YIELD: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.budget_forced_yield_count")
        .init()
});
static TOKIO_IO_DRIVER_READY: Lazy<Counter<u64>> = Lazy::new(|| {
    TOKIO_METER
        .u64_counter("bitkey.tokio.io_driver_ready_count")
        .init()
});
static TOKIO_MEAN_POLLS_PER_PARK: Lazy<ObservableGauge<f64>> = Lazy::new(|| {
    TOKIO_METER
        .f64_observable_gauge("bitkey.tokio.mean_polls_per_park")
        .init()
});
static TOKIO_BUSY_RATIO: Lazy<ObservableGauge<f64>> = Lazy::new(|| {
    TOKIO_METER
        .f64_observable_gauge("bitkey.tokio.busy_ratio")
        .init()
});

pub fn init_tokio_metrics(period: Duration) -> Result<(), MetricsError> {
    let handle = Handle::current();

    let sync_metrics = Arc::new(RwLock::<Option<RuntimeMetrics>>::new(None));
    let async_metrics = sync_metrics.clone();

    std::thread::spawn(move || {
        let mut intervals = RuntimeMonitor::new(&handle).intervals();

        loop {
            {
                let mut metrics = sync_metrics.write().unwrap();
                let new_metrics = intervals.next();
                *metrics = new_metrics;
            }

            {
                if let Some(metrics) = sync_metrics.read().unwrap().as_ref() {
                    TOKIO_TOTAL_PARK.add(metrics.total_park_count, &[]);
                    TOKIO_TOTAL_NOOP.add(metrics.total_noop_count, &[]);
                    TOKIO_TOTAL_STEAL.add(metrics.total_steal_count, &[]);
                    TOKIO_TOTAL_STEAL_OPERATIONS.add(metrics.total_steal_operations, &[]);
                    TOKIO_NUM_REMOTE_SCHEDULES.add(metrics.num_remote_schedules, &[]);
                    TOKIO_TOTAL_LOCAL_SCHEDULE.add(metrics.total_local_schedule_count, &[]);
                    TOKIO_TOTAL_OVERFLOW.add(metrics.total_overflow_count, &[]);
                    TOKIO_TOTAL_POLLS.add(metrics.total_polls_count, &[]);
                    TOKIO_TOTAL_BUSY_DURATION
                        .add(metrics.total_busy_duration.as_millis() as u64, &[]);
                    TOKIO_BUDGET_FORCED_YIELD.add(metrics.budget_forced_yield_count, &[]);
                    TOKIO_IO_DRIVER_READY.add(metrics.io_driver_ready_count, &[]);
                }
            }

            std::thread::sleep(period);
        }
    });

    TOKIO_METER.register_callback(
        &[
            TOKIO_WORKERS.as_any(),
            TOKIO_MAX_PARK.as_any(),
            TOKIO_MIN_PARK.as_any(),
            TOKIO_MAX_NOOP.as_any(),
            TOKIO_MIN_NOOP.as_any(),
            TOKIO_MAX_STEAL.as_any(),
            TOKIO_MIN_STEAL.as_any(),
            TOKIO_MAX_STEAL_OPERATIONS.as_any(),
            TOKIO_MIN_STEAL_OPERATIONS.as_any(),
            TOKIO_MAX_LOCAL_SCHEDULE.as_any(),
            TOKIO_MIN_LOCAL_SCHEDULE.as_any(),
            TOKIO_MAX_OVERFLOW.as_any(),
            TOKIO_MIN_OVERFLOW.as_any(),
            TOKIO_MAX_POLLS.as_any(),
            TOKIO_MIN_POLLS.as_any(),
            TOKIO_MAX_BUSY_DURATION.as_any(),
            TOKIO_MIN_BUSY_DURATION.as_any(),
            TOKIO_INJECTION_QUEUE_DEPTH.as_any(),
            TOKIO_TOTAL_LOCAL_QUEUE_DEPTH.as_any(),
            TOKIO_MAX_LOCAL_QUEUE_DEPTH.as_any(),
            TOKIO_MIN_LOCAL_QUEUE_DEPTH.as_any(),
            TOKIO_ELAPSED.as_any(),
            TOKIO_MEAN_POLLS_PER_PARK.as_any(),
            TOKIO_BUSY_RATIO.as_any(),
        ],
        move |observer| {
            if let Some(metrics) = async_metrics.read().unwrap().as_ref() {
                observer.observe_u64(&*TOKIO_WORKERS, metrics.workers_count as u64, &[]);
                observer.observe_u64(&*TOKIO_MAX_PARK, metrics.max_park_count, &[]);
                observer.observe_u64(&*TOKIO_MIN_PARK, metrics.min_park_count, &[]);
                observer.observe_u64(&*TOKIO_MAX_NOOP, metrics.max_noop_count, &[]);
                observer.observe_u64(&*TOKIO_MIN_NOOP, metrics.min_noop_count, &[]);
                observer.observe_u64(&*TOKIO_MAX_STEAL, metrics.max_steal_count, &[]);
                observer.observe_u64(&*TOKIO_MIN_STEAL, metrics.min_steal_count, &[]);
                observer.observe_u64(
                    &*TOKIO_MAX_STEAL_OPERATIONS,
                    metrics.max_steal_operations,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_MIN_STEAL_OPERATIONS,
                    metrics.min_steal_operations,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_MAX_LOCAL_SCHEDULE,
                    metrics.max_local_schedule_count,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_MIN_LOCAL_SCHEDULE,
                    metrics.min_local_schedule_count,
                    &[],
                );
                observer.observe_u64(&*TOKIO_MAX_OVERFLOW, metrics.max_overflow_count, &[]);
                observer.observe_u64(&*TOKIO_MIN_OVERFLOW, metrics.min_overflow_count, &[]);
                observer.observe_u64(&*TOKIO_MAX_POLLS, metrics.max_polls_count, &[]);
                observer.observe_u64(&*TOKIO_MIN_POLLS, metrics.min_polls_count, &[]);
                observer.observe_u64(
                    &*TOKIO_MAX_BUSY_DURATION,
                    metrics.max_busy_duration.as_millis() as u64,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_MIN_BUSY_DURATION,
                    metrics.min_busy_duration.as_millis() as u64,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_INJECTION_QUEUE_DEPTH,
                    metrics.injection_queue_depth as u64,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_TOTAL_LOCAL_QUEUE_DEPTH,
                    metrics.total_local_queue_depth as u64,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_MAX_LOCAL_QUEUE_DEPTH,
                    metrics.max_local_queue_depth as u64,
                    &[],
                );
                observer.observe_u64(
                    &*TOKIO_MIN_LOCAL_QUEUE_DEPTH,
                    metrics.min_local_queue_depth as u64,
                    &[],
                );
                observer.observe_u64(&*TOKIO_ELAPSED, metrics.elapsed.as_millis() as u64, &[]);
                observer.observe_f64(
                    &*TOKIO_MEAN_POLLS_PER_PARK,
                    metrics.mean_polls_per_park(),
                    &[],
                );
                observer.observe_f64(&*TOKIO_BUSY_RATIO, metrics.busy_ratio(), &[]);
            }
        },
    )?;

    Ok(())
}
