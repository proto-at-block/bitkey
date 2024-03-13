use authn_authz::authorizer::AuthorizerConfig;
use aws_utils::secrets_manager::test::TestSecretsManager;
use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::{http, Router};
use external_identifier::ExternalIdentifier;
use http_body_util::BodyExt;
use partnerships::routes::RouteState;
use serde_json::{json, Value};
use server::test_utils::AuthenticatedRequest;
use std::collections::HashMap;
use userpool::userpool::{FakeCognitoConnection, UserPoolService};

use tower::ServiceExt;
use types::account::identifiers::AccountId;
use ulid::Ulid;

async fn test_route_state() -> RouteState {
    let secrets = HashMap::from([
        (
            "fromagerie-api/partnerships/signet_faucet/config".to_owned(),
            r#"{"display_name":"Signet Faucet","quote_url":"","purchase_url":"https://signet.bc-2.jp/","purchase_redirect_type":"WIDGET","partner":"SignetFaucet","partner_id":"","network":"signet"}"#.to_owned(),
        ),
        (
            "fromagerie-api/partnerships/testnet_faucet/config".to_owned(),
            r#"{"display_name":"Testnet Faucet","quote_url":"","transfer_url":"https://testnet-faucet.mempool.co/","transfer_redirect_type":"WIDGET","partner":"TestnetFaucet","partner_id":"","network":"testnet"}"#.to_owned(),
        ),
    ]);
    let user_pool_service = UserPoolService::new(Box::new(FakeCognitoConnection::new()));
    RouteState::from_secrets_manager(TestSecretsManager::new(secrets), user_pool_service).await
}

async fn authed_router() -> Router {
    let authorizer = AuthorizerConfig::Test
        .into_authorizer()
        .layer()
        .await
        .unwrap();

    Router::from(test_route_state().await).layer(authorizer)
}

async fn call_api(
    app: Router,
    authenticated: bool,
    method: http::Method,
    path: &str,
    body: Option<&str>,
) -> (StatusCode, Value) {
    let body = match body {
        Some(body) => Body::from(body.to_owned()),
        None => Body::empty(),
    };

    let mut request_builder = Request::builder()
        .method(method)
        .uri(path)
        .header(http::header::CONTENT_TYPE, mime::APPLICATION_JSON.as_ref());
    if authenticated {
        request_builder =
            request_builder.authenticated(&AccountId::new(Ulid::new()).unwrap(), false, false);
    }
    let request = request_builder.body(body).unwrap();

    let response = app.oneshot(request).await.unwrap();
    let status = response.status();

    if authenticated {
        let body = response.into_body().collect().await.unwrap().to_bytes();
        let body: Value = serde_json::from_slice(&body).unwrap();
        (status, body)
    } else {
        (status, Value::Null)
    }
}

#[tokio::test]
async fn transfers() {
    let app = authed_router().await;

    let request_body = json!({
        "country": "US",
    });

    let (status, body) = call_api(
        app,
        true,
        http::Method::POST,
        "/api/partnerships/transfers",
        Some(&request_body.to_string()),
    )
    .await;

    // Expected due to test configs, Testnet doesn't actually support transfers
    let expected_body = json!({
        "partners": [
            {
                "logo_url": null,
                "name": "Testnet Faucet",
                "partner": "TestnetFaucet",
            }]
    });
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, expected_body);
}

#[tokio::test]
async fn transfers_unauthorized_error() {
    let app = authed_router().await;

    let request_body = json!({
        "country": "US",
    });

    let (status, _) = call_api(
        app,
        false,
        http::Method::POST,
        "/api/partnerships/transfers",
        Some(&request_body.to_string()),
    )
    .await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn transfers_redirect_transfer_not_supported() {
    let app = authed_router().await;
    let request_body = json!({
        "fiat_amount": 100,
        "fiat_currency": "USD",
        "payment_method": "CARD",
        "address": "x",
        "partner": "SignetFaucet"
    });

    let (status, body) = call_api(
        app,
        true,
        http::Method::POST,
        "/api/partnerships/transfers/redirects",
        Some(&request_body.to_string()),
    )
    .await;

    let expected_body = json!({
        "error": "Internal Server Error",
        "message": "Issue with configuration for partner SignetFaucet: Transfer not supported"
    });
    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(body, expected_body);
}

#[tokio::test]
async fn transfers_redirect_unauthorized_error() {
    let app = authed_router().await;
    let request_body = json!({
        "fiat_amount": 100,
        "fiat_currency": "USD",
        "payment_method": "CARD",
        "address": "x",
        "partner": "SignetFaucet"
    });

    let (status, _) = call_api(
        app,
        false,
        http::Method::POST,
        "/api/partnerships/transfers/redirects",
        Some(&request_body.to_string()),
    )
    .await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn quotes() {
    let app = authed_router().await;
    let request_body = json!({
        "fiat_amount": 100,
        "fiat_currency": "USD",
        "payment_method": "CARD",
        "country": "US"
    });

    let (status, body) = call_api(
        app,
        true,
        http::Method::POST,
        "/api/partnerships/purchases/quotes",
        Some(&request_body.to_string()),
    )
    .await;

    let expected_body = json!({
        "quotes": [
            {
                "crypto_amount": 0.0,
                "crypto_price": 0.0,
                "fiat_currency": "USD",
                "network_fee_crypto": 0.0,
                "network_fee_fiat": 0.0,
                "partner_info": {
                    "logo_url": null,
                    "name": "Signet Faucet",
                    "partner": "SignetFaucet"
                },
                "user_fee_fiat": 0.0
            },
            {
                "crypto_amount": 0.0,
                "crypto_price": 0.0,
                "fiat_currency": "USD",
                "network_fee_crypto": 0.0,
                "network_fee_fiat": 0.0,
                "partner_info": {
                    "logo_url": null,
                    "name": "Testnet Faucet",
                    "partner": "TestnetFaucet"
                },
                "user_fee_fiat": 0.0
            }
        ]
    });
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, expected_body);
}

#[tokio::test]
async fn quotes_unauthorized_error() {
    let app = authed_router().await;
    let request_body = json!({
        "fiat_amount": 100,
        "fiat_currency": "USD",
        "payment_method": "CARD",
        "country": "US"
    });

    let (status, _) = call_api(
        app,
        false,
        http::Method::POST,
        "/api/partnerships/purchases/quotes",
        Some(&request_body.to_string()),
    )
    .await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn purchases_redirect() {
    let app = authed_router().await;
    let request_body = json!({
        "fiat_amount": 100,
        "fiat_currency": "USD",
        "payment_method": "CARD",
        "address": "x",
        "partner": "SignetFaucet"
    });

    let (status, body) = call_api(
        app,
        true,
        http::Method::POST,
        "/api/partnerships/purchases/redirects",
        Some(&request_body.to_string()),
    )
    .await;

    let expected_body = json!({
        "redirect_info": {
            "app_restrictions": null,
            "redirect_type": "WIDGET",
            "url": "https://signet.bc-2.jp/",
        }
    });
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, expected_body);
}

#[tokio::test]
async fn purchases_redirect_unauthorized_error() {
    let app = authed_router().await;
    let request_body = json!({
        "fiat_amount": 100,
        "fiat_currency": "USD",
        "payment_method": "CARD",
        "address": "x",
        "partner": "SignetFaucet"
    });

    let (status, _) = call_api(
        app,
        false,
        http::Method::POST,
        "/api/partnerships/purchases/redirects",
        Some(&request_body.to_string()),
    )
    .await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn purchase_options() {
    let (status, _body) = call_api(
        authed_router().await,
        true,
        http::Method::GET,
        "/api/partnerships/purchases/options?country=US&fiat_currency=USD",
        None,
    )
    .await;

    assert_eq!(status, StatusCode::OK);
}

#[tokio::test]
async fn purchase_options_unauthorized_error() {
    let (status, _) = call_api(
        authed_router().await,
        false,
        http::Method::GET,
        "/api/partnerships/purchases/options?country=US&fiat_currency=USD",
        None,
    )
    .await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
}
