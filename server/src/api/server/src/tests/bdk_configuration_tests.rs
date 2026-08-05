use http::{HeaderMap, HeaderValue, StatusCode};
use onboarding::routes::{BdkConfigResponse, ElectrumServer, ElectrumServers};
use once_cell::sync::Lazy;
use rstest::{fixture, rstest};

use crate::tests::{gen_services, requests::axum::TestClient};

static DEFAULT_BDK_CONFIGURATION_HEADERS: Lazy<HeaderMap> = Lazy::new(|| {
    let mut headers = HeaderMap::new();
    headers.insert(
        "Bitkey-App-Installation-ID",
        HeaderValue::from_static("test-app-installation-id"),
    );
    headers
});

struct TestContext {
    client: TestClient,
}

#[fixture]
async fn test_context() -> TestContext {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    TestContext { client }
}

#[fixture]
fn expected_bdk_config() -> BdkConfigResponse {
    BdkConfigResponse {
        electrum_servers: ElectrumServers {
            mainnet: ElectrumServer {
                scheme: "ssl".into(),
                host: "electrum.blockstream.info".into(),
                port: 50002,
            },
            testnet: ElectrumServer {
                scheme: "ssl".into(),
                host: "electrum.blockstream.info".into(),
                port: 60002,
            },
            signet: ElectrumServer {
                scheme: "ssl".into(),
                host: "bitkey.mempool.space".into(),
                port: 60602,
            },
            regtest: None,
        },
    }
}

#[fixture]
fn expected_bdk_config_with_regtest() -> BdkConfigResponse {
    BdkConfigResponse {
        electrum_servers: ElectrumServers {
            mainnet: ElectrumServer {
                scheme: "ssl".into(),
                host: "electrum.blockstream.info".into(),
                port: 50002,
            },
            testnet: ElectrumServer {
                scheme: "ssl".into(),
                host: "electrum.blockstream.info".into(),
                port: 60002,
            },
            signet: ElectrumServer {
                scheme: "ssl".into(),
                host: "bitkey.mempool.space".into(),
                port: 60602,
            },
            regtest: Some(ElectrumServer {
                scheme: "tcp".into(),
                host: "localhost".into(),
                port: 50001,
            }),
        },
    }
}

#[fixture]
fn expected_bdk_config_with_regtest_external_uri() -> BdkConfigResponse {
    BdkConfigResponse {
        electrum_servers: ElectrumServers {
            mainnet: ElectrumServer {
                scheme: "ssl".into(),
                host: "electrum.blockstream.info".into(),
                port: 50002,
            },
            testnet: ElectrumServer {
                scheme: "ssl".into(),
                host: "electrum.blockstream.info".into(),
                port: 60002,
            },
            signet: ElectrumServer {
                scheme: "ssl".into(),
                host: "bitkey.mempool.space".into(),
                port: 60602,
            },
            regtest: Some(ElectrumServer {
                scheme: "tcp".into(),
                host: "otherhost".into(),
                port: 50009,
            }),
        },
    }
}

#[rstest]
#[tokio::test]
async fn test(
    expected_bdk_config: BdkConfigResponse,
    expected_bdk_config_with_regtest: BdkConfigResponse,
    expected_bdk_config_with_regtest_external_uri: BdkConfigResponse,
    #[future] test_context: TestContext,
) {
    let test_ctx = test_context.await;
    let response = test_ctx
        .client
        .get_bdk_configuration(DEFAULT_BDK_CONFIGURATION_HEADERS.clone())
        .await;
    let body = response.body.expect("Response body is missing");

    assert_eq!(StatusCode::OK, response.status_code);
    assert_eq!(expected_bdk_config, body);

    std::env::set_var("REGTEST_ELECTRUM_SERVER_URI", "tcp://localhost:50001");
    let response = test_ctx
        .client
        .get_bdk_configuration(DEFAULT_BDK_CONFIGURATION_HEADERS.clone())
        .await;
    let body = response.body.expect("Response body is missing");

    assert_eq!(StatusCode::OK, response.status_code);
    assert_eq!(expected_bdk_config_with_regtest, body);

    std::env::set_var("REGTEST_ELECTRUM_SERVER_URI", "localhost:50001");
    let response = test_ctx
        .client
        .get_bdk_configuration(DEFAULT_BDK_CONFIGURATION_HEADERS.clone())
        .await;

    assert_eq!(StatusCode::INTERNAL_SERVER_ERROR, response.status_code);

    std::env::set_var(
        "REGTEST_ELECTRUM_SERVER_EXTERNAL_URI",
        "tcp://otherhost:50009",
    );
    let response = test_ctx
        .client
        .get_bdk_configuration(DEFAULT_BDK_CONFIGURATION_HEADERS.clone())
        .await;
    let body = response.body.expect("Response body is missing");

    assert_eq!(StatusCode::OK, response.status_code);
    assert_eq!(expected_bdk_config_with_regtest_external_uri, body);
}
