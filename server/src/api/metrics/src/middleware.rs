use axum::{body::Body, extract::MatchedPath, http::Request, response::Response};
use futures_util::future::BoxFuture;
use std::sync::Arc;
use std::task::{Context, Poll};
use std::time::Instant;
use tower::{Layer, Service};

use crate::factory::{Counter, Histogram, UpDownCounter};
use crate::KeyValue;

#[derive(Clone)]
pub struct RouterName(pub(crate) String);

impl<S> Layer<S> for RouterName {
    type Service = RouterNameMiddleware<S>;

    fn layer(&self, inner: S) -> Self::Service {
        RouterNameMiddleware {
            inner,
            router_name: self.clone(),
        }
    }
}

// Simple middleware that tags some of the metrics emitted by HttpMetricsMiddleware
//   with a given router name (useful for grouping the metrics produced by routers)
#[derive(Clone)]
pub struct RouterNameMiddleware<S> {
    inner: S,
    router_name: RouterName,
}

impl<S> Service<Request<Body>> for RouterNameMiddleware<S>
where
    S: Service<Request<Body>, Response = Response> + Send + 'static,
    S::Future: Send + 'static,
{
    type Response = S::Response;
    type Error = S::Error;
    type Future = BoxFuture<'static, Result<Self::Response, Self::Error>>;

    fn poll_ready(&mut self, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        self.inner.poll_ready(cx)
    }

    fn call(&mut self, request: Request<Body>) -> Self::Future {
        let router_name = self.router_name.clone();
        let future = self.inner.call(request);

        Box::pin(async move {
            let mut response: Response = future.await?;

            response.extensions_mut().insert(router_name);

            Ok(response)
        })
    }
}

#[derive(Clone)]
pub struct HttpMetrics {
    pub(crate) http_response: Counter<u64>,
    pub(crate) http_response_latency: Histogram<f64>,
    pub(crate) http_active_request: UpDownCounter<i64>,
}

impl Default for HttpMetrics {
    fn default() -> Self {
        Self::new()
    }
}

impl HttpMetrics {
    pub fn new() -> Self {
        let meter = opentelemetry::global::meter("bitkey.http");

        Self {
            http_response: meter.u64_counter("bitkey.http.response").init(),
            http_response_latency: meter
                .f64_histogram("bitkey.http.response.latency")
                .with_unit(opentelemetry::metrics::Unit::new("ms"))
                .init(),
            http_active_request: meter
                .i64_up_down_counter("bitkey.http.active_request")
                .init(),
        }
    }
}

impl<S> Layer<S> for HttpMetrics {
    type Service = HttpMetricsMiddleware<S>;

    fn layer(&self, inner: S) -> Self::Service {
        HttpMetricsMiddleware {
            inner,
            metrics: Arc::new(self.clone()),
        }
    }
}

// Middlware that produces http_response, http_response_latency, and http_active_request
//   metrics
#[derive(Clone)]
pub struct HttpMetricsMiddleware<S> {
    inner: S,
    metrics: Arc<HttpMetrics>,
}

impl<S> Service<Request<Body>> for HttpMetricsMiddleware<S>
where
    S: Service<Request<Body>, Response = Response> + Send + 'static,
    S::Future: Send + 'static,
{
    type Response = S::Response;
    type Error = S::Error;
    type Future = BoxFuture<'static, Result<Self::Response, Self::Error>>;

    fn poll_ready(&mut self, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        self.inner.poll_ready(cx)
    }

    fn call(&mut self, request: Request<Body>) -> Self::Future {
        let method = request.method().to_string();
        let path = request
            .extensions()
            .get::<MatchedPath>()
            .map(|p| p.as_str().to_owned());

        let mut attributes = vec![KeyValue::new("method", method)];
        if let Some(path) = path {
            attributes.push(KeyValue::new("path", path));
        }

        let metrics = self.metrics.clone();

        self.metrics.http_active_request.add(1, &attributes);
        let start = Instant::now();
        let future = self.inner.call(request);

        Box::pin(async move {
            let response: Response = future.await?;

            let duration = start.elapsed();
            metrics.http_active_request.add(-1, &attributes);

            let status_code = response.status();
            if status_code.is_success() {
                attributes.push(KeyValue::new("status", "2xx"));
            } else if status_code.is_redirection() {
                attributes.push(KeyValue::new("status", "3xx"));
            } else if status_code.is_client_error() {
                attributes.push(KeyValue::new("status", "4xx"));
            } else if status_code.is_server_error() {
                attributes.push(KeyValue::new("status", "5xx"));
            } else {
                attributes.push(KeyValue::new("status", "1xx"));
            };

            if let Some(RouterName(router_name)) = response.extensions().get() {
                attributes.push(KeyValue::new("router_name", router_name.to_owned()));
            }

            metrics.http_response.add(1, &attributes);
            metrics
                .http_response_latency
                .record(duration.as_secs_f64() * 1000.0, &attributes);

            Ok(response)
        })
    }
}
