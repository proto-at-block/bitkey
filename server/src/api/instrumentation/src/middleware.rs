use axum::extract::Request;
use axum::http::header::USER_AGENT;
use axum::middleware::Next;
use axum::{body::Body, extract::MatchedPath, response::Response};
use futures_util::future::BoxFuture;
use std::sync::Arc;
use std::task::{Context, Poll};
use std::time::Instant;
use tower::{Layer, Service};

use crate::metrics::factory::{Counter, Histogram, UpDownCounter};
use crate::metrics::{KeyValue, APP_ID_KEY};

pub const APP_INSTALLATION_ID_HEADER_NAME: &str = "Bitkey-App-Installation-ID";
pub const HARDWARE_SERIAL_HEADER_NAME: &str = "Bitkey-Hardware-Serial";

const METHOD_KEY: &str = "method";
const PATH_KEY: &str = "path";
const ROUTER_NAME_KEY: &str = "router_name";
const STATUS_KEY: &str = "status";
const STATUS_EXACT_KEY: &str = "status_exact";
const STATUS_1XX: &str = "1xx";
const STATUS_2XX: &str = "2xx";
const STATUS_3XX: &str = "3xx";
const STATUS_4XX: &str = "4xx";
const STATUS_5XX: &str = "5xx";

tokio::task_local! {
    pub static CLIENT_REQUEST_CONTEXT: ClientRequestContext;
}

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
                .with_unit("ms")
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

        let mut attributes = vec![KeyValue::new(METHOD_KEY, method)];
        if let Some(path) = path {
            attributes.push(KeyValue::new(PATH_KEY, path));
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
                attributes.push(KeyValue::new(STATUS_KEY, STATUS_2XX));
            } else if status_code.is_redirection() {
                attributes.push(KeyValue::new(STATUS_KEY, STATUS_3XX));
            } else if status_code.is_client_error() {
                attributes.push(KeyValue::new(STATUS_KEY, STATUS_4XX));
                attributes.push(KeyValue::new(
                    STATUS_EXACT_KEY,
                    status_code.as_u16().to_string(),
                ));
            } else if status_code.is_server_error() {
                attributes.push(KeyValue::new(STATUS_KEY, STATUS_5XX));
            } else {
                attributes.push(KeyValue::new(STATUS_KEY, STATUS_1XX));
            };

            if let Some(RouterName(router_name)) = response.extensions().get() {
                attributes.push(KeyValue::new(ROUTER_NAME_KEY, router_name.to_owned()));
            }

            if let Ok(Some(app_id)) = CLIENT_REQUEST_CONTEXT.try_with(|c| c.app_id.clone()) {
                attributes.push(KeyValue::new(APP_ID_KEY, app_id));
            }

            metrics.http_response.add(1, &attributes);
            metrics
                .http_response_latency
                .record(duration.as_secs_f64() * 1000.0, &attributes);

            Ok(response)
        })
    }
}

pub struct ClientRequestContext {
    pub app_id: Option<String>,
    pub app_installation_id: Option<String>,
    pub hardware_serial: Option<String>,
}

pub async fn client_request_context(request: Request, next: Next) -> Response {
    let app_id = request
        .headers()
        .get(USER_AGENT)
        .and_then(|h| h.to_str().ok())
        .and_then(|v| v.split('/').next().filter(|s| s.contains("bitkey")))
        .map(|s| s.to_string());

    let app_installation_id = request
        .headers()
        .get(APP_INSTALLATION_ID_HEADER_NAME)
        .and_then(|h| h.to_str().ok())
        .map(|v| v.to_string());

    let hardware_serial = request
        .headers()
        .get(HARDWARE_SERIAL_HEADER_NAME)
        .and_then(|h| h.to_str().ok())
        .map(|v| v.to_string());

    CLIENT_REQUEST_CONTEXT
        .scope(
            ClientRequestContext {
                app_id,
                app_installation_id,
                hardware_serial,
            },
            next.run(request),
        )
        .await
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::StatusCode;
    use axum::middleware::from_fn;
    use axum::routing::get;
    use axum::Router;
    use tower::ServiceExt;

    use crate::middleware::CLIENT_REQUEST_CONTEXT;

    #[tokio::test]
    async fn client_request_context_with_headers() {
        let app_id = "world.bitkey.test";
        let app_install_id = "test_app_installation_id";
        let hw_serial = "test_hw_serial";

        let assert_client_request_context = move || async move {
            CLIENT_REQUEST_CONTEXT
                .try_with(|c| {
                    assert_eq!(c.app_id.as_deref(), Some(app_id));
                    assert_eq!(c.app_installation_id.as_deref(), Some(app_install_id));
                    assert_eq!(c.hardware_serial.as_deref(), Some(hw_serial));
                })
                .unwrap();
        };

        let app = Router::new()
            .route("/", get(assert_client_request_context))
            .layer(from_fn(client_request_context));

        let request = Request::builder()
            .uri("/")
            .header(USER_AGENT, format!("{}/whatever", app_id))
            .header(APP_INSTALLATION_ID_HEADER_NAME, app_install_id)
            .header(HARDWARE_SERIAL_HEADER_NAME, hw_serial)
            .body(Body::empty())
            .unwrap();

        let res = app.oneshot(request).await.unwrap();

        assert_eq!(res.status(), StatusCode::OK);
    }
}
