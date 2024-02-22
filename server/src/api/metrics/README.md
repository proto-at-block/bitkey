# Metrics

This crate provides a thin wrapper around opentelemetry-rust's metrics features with some sane conventions and helper functionality.

An example of this being used currently can be seen in the [recovery crate](../recovery/src/metrics.rs).

## How to use

In the crate of your choice, instantiate a [MetricsFactory](./src/factory.rs) by providing a `namespace` (e.g. `recovery`).
### Instruments

The `MetricsFactory` can then be used to create the following instruments, each with a `name` and an optional `unit`. The name gets fully-qualified to `bitkey.{namespace}.{name}` at instantiation.

- `factory.{T}_counter(name, unit)` -> `Counter<T>`: A value that accumulates over time – you can think of this like an odometer on a car; it only ever goes up.
- `factory.{T}_observable_counter(name, unit)` -> `ObservableCounter<T>`: Same as the Counter, but is collected once for each export. Could be used if you don’t have access to the continuous increments, but only to the aggregated value.
- `factory.{T}_up_down_counter(name, unit)` -> `UpDownCounter<T>`: A value that accumulates over time, but can also go down again. An example could be a queue length, it will increase and decrease with the number of work items in the queue.
- `factory.{T}_histogram` -> `Histogram<T>`: A client-side aggregation of values, such as request latencies. A histogram is a good choice if you are interested value statistics. For example: How many requests take fewer than 1s?
- `factory.{T}_observable_gauge(name, unit)` -> `ObservableGauge<T>`: Measures a current value at the time it is read. An example would be the fuel gauge in a vehicle. Gauges are asynchronous.

(Explanations lifted from [OTEL](https://opentelemetry.io/docs/concepts/signals/metrics/#metric-instruments))

#### Observable instruments (advanced; you probably don't need to use)

In order to record values for `Observable*` instruments, they must be registered with the `MetricsFactory` via `factory.register_callback`. The provided callback will be invoked once per metrics reporting cycle, and the value will be observed for the specified instrument.

### Middleware

The `MetricsFactory` also provides an axum middleware via `factory.route_layer(router_name: String)` that adds the router name as an attribute to the following system-level metrics produced by the nested routes.

- `bitkey.http.response` counter
- `bitkey.http.response.latency` histogram

## System metrics

This crate also declares and emits its own [system-level metrics](./src/system.rs). For now, these consist of Tokio's [runtime metrics](https://github.com/tokio-rs/tokio-metrics#base-metrics-1).
