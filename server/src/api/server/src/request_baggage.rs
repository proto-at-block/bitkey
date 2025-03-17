use authn_authz::key_claims::extract_account_id;
use axum::extract::Request;
use axum::middleware::Next;
use axum::response::Response;
use http::{HeaderMap, StatusCode};
use instrumentation::middleware::{APP_INSTALLATION_ID_HEADER_NAME, HARDWARE_SERIAL_HEADER_NAME};
use opentelemetry::baggage::BaggageExt;
use opentelemetry::trace::FutureExt;
use opentelemetry::KeyValue;
use wallet_telemetry::baggage_keys::{ACCOUNT_ID, APP_INSTALLATION_ID, HARDWARE_SERIAL_NUMBER};

pub async fn request_baggage(
    headers: HeaderMap,
    request: Request,
    next: Next,
) -> Result<Response, StatusCode> {
    let baggage: Vec<KeyValue> = [
        extract_account_id(&headers).map(|id| KeyValue::new(ACCOUNT_ID, id)),
        headers
            .get(APP_INSTALLATION_ID_HEADER_NAME)
            .and_then(|v| v.to_str().ok())
            .map(|v| KeyValue::new(APP_INSTALLATION_ID, v.to_owned())),
        headers
            .get(HARDWARE_SERIAL_HEADER_NAME)
            .and_then(|v| v.to_str().ok())
            .map(|v| KeyValue::new(HARDWARE_SERIAL_NUMBER, v.to_owned())),
    ]
    .into_iter()
    .flatten()
    .collect();

    if !baggage.is_empty() {
        let context = opentelemetry::Context::current_with_baggage(baggage);
        return Ok(next.run(request).with_context(context).await);
    }

    Ok(next.run(request).await)
}

#[cfg(test)]
mod tests {
    use super::*;
    use authn_authz::test_utils::get_test_access_token;
    use axum::body::Body;
    use axum::middleware::from_fn;
    use axum::routing::get;
    use axum::Router;
    use tower::ServiceExt;

    #[tokio::test]
    async fn request_baggage_with_headers() {
        let account_id = "urn:wallet-account:000000000000000000000000000";
        let app_install_id = "test_app_installation_id";
        let hw_serial = "test_hw_serial";
        let access_token = get_test_access_token();

        let assert_request_baggage = move || async move {
            let context = opentelemetry::Context::current();
            let baggage = context.baggage();

            assert_eq!(
                baggage.get(APP_INSTALLATION_ID).unwrap().to_string(),
                app_install_id
            );
            assert_eq!(baggage.get(ACCOUNT_ID).unwrap().to_string(), account_id);
            assert_eq!(
                baggage.get(HARDWARE_SERIAL_NUMBER).unwrap().to_string(),
                hw_serial
            );
        };

        let app = Router::new()
            .route("/", get(assert_request_baggage))
            .layer(from_fn(request_baggage));

        let request = Request::builder()
            .uri("/")
            .header(APP_INSTALLATION_ID_HEADER_NAME, app_install_id)
            .header(HARDWARE_SERIAL_HEADER_NAME, hw_serial)
            .header("Authorization", format!("Bearer {}", access_token))
            .body(Body::empty())
            .unwrap();

        let res = app.oneshot(request).await.unwrap();

        assert_eq!(res.status(), StatusCode::OK);
    }
}
