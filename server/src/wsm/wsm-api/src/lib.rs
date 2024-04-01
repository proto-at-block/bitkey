#![forbid(unsafe_code)]
use std::net::SocketAddr;

use std::sync::Arc;

use anyhow::Context;
use aws_config::BehaviorVersion;
use aws_sdk_kms::client::Client as KmsClient;
use aws_sdk_kms::types::DataKeyPairSpec;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::routing::get;
use axum::{extract::State, routing::post, Json, Router};
use axum_tracing_opentelemetry::middleware::{OtelAxumLayer, OtelInResponseLayer};

use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use serde_json::json;
use tokio::net::TcpListener;
use tracing::{event, instrument};

use wallet_telemetry::Mode::{Datadog, Jaeger};
use wallet_telemetry::{set_global_telemetry, Config};
use wsm_common::bitcoin::bip32::DerivationPath;

use wsm_common::derivation::WSMSupportedDomain;
use wsm_common::messages::api::{
    CreateRootKeyRequest, CreatedSigningKey, GenerateIntegrityKeyResponse, GetIntegritySigRequest,
    GetIntegritySigResponse, SignPsbtRequest, SignedPsbt,
};
use wsm_common::messages::enclave::{
    EnclaveCreateKeyRequest, EnclaveDeriveKeyRequest, EnclaveSignRequest,
};
use wsm_common::messages::DomainFactoredXpub;

use crate::dependencies::customer_key_store::{CustomerKey, CustomerKeyStore};
use crate::dependencies::ddb;
use crate::dependencies::dek_store::DekStore;
use crate::dependencies::enclave_client::{EnclaveClient, KmsConfig};
use crate::settings::RunMode;
use crate::settings::Settings;

mod dependencies;
mod settings;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState {
    pub customer_key_store: CustomerKeyStore,
    pub enclave: Arc<EnclaveClient>,
    pub kms: KmsClient,
    pub cmk_id: String,
}

impl From<RouteState> for Router {
    fn from(state: RouteState) -> Self {
        Router::new()
            .route("/", get(index))
            .route("/health-check", get(health_check))
            .route("/create-key", post(create_key))
            .route("/sign-psbt", post(sign_psbt))
            .route("/integrity-sig", get(integrity_sig))
            .route("/generate-integrity-key", get(generate_integrity_key))
            .with_state(state)
    }
}

async fn index() -> &'static str {
    "wsm api"
}

#[derive(Debug, thiserror::Error)]
enum ApiError {
    #[error("Not Found: {0}")]
    NotFound(String),
    #[error("Server Error: {0}")]
    ServerError(String),
}

impl IntoResponse for ApiError {
    fn into_response(self) -> axum::response::Response {
        let (status, message) = match self {
            ApiError::NotFound(message) => (StatusCode::NOT_FOUND, message),
            ApiError::ServerError(message) => (StatusCode::INTERNAL_SERVER_ERROR, message),
        };

        let body = Json(json!({
            "error": message,
        }));

        (status, body).into_response()
    }
}

#[instrument(err, skip(customer_key_store, enclave_client))]
async fn create_key(
    State(customer_key_store): State<CustomerKeyStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<CreateRootKeyRequest>,
) -> Result<Json<CreatedSigningKey>, ApiError> {
    let root_key_id = &request.root_key_id;
    match customer_key_store
        .get_customer_key(root_key_id)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!("Could not read customer keys DDB table: {e}"))
        })? {
        Some(ck) => {
            // Key is already in the table, return the xpub
            event!(
                tracing::Level::DEBUG,
                "create_key called on wallet that already exists"
            );
            let xpub_sig = if let Some(sig) = ck.integrity_signature {
                sig
            } else {
                "".to_string()
            };
            Ok(Json(CreatedSigningKey {
                root_key_id: root_key_id.clone(),
                xpub: ck.xpub_descriptor,
                xpub_sig,
            }))
        }
        None => {
            let dek_id = enclave_client.get_available_dek_id().await.map_err(|e| {
                ApiError::ServerError(format!("Could not get DEK for new wallet: {e}"))
            })?;

            // Create the root key
            let enclave_create_key_req = EnclaveCreateKeyRequest {
                root_key_id: root_key_id.clone(),
                dek_id: dek_id.clone(),
                network: request.network,
            };
            let new_key = enclave_client
                .create_key(enclave_create_key_req)
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Could not generate new key in enclave: {e}",))
                })?;

            let spend_domain = WSMSupportedDomain::Spend(request.network.into());
            let spend_derive_request = EnclaveDeriveKeyRequest {
                key_id: root_key_id.clone(),
                dek_id: dek_id.clone(),
                wrapped_xprv: new_key.wrapped_xprv.clone(),
                key_nonce: new_key.wrapped_xprv_nonce.clone(),
                derivation_path: DerivationPath::from(spend_domain),
                network: Some(request.network),
            };

            let spend_key = enclave_client
                .derive_key(spend_derive_request)
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!(
                        "Could not derive spend key from root key in enclave: {e}",
                    ))
                })?;

            let customer_key = CustomerKey::new(
                request.root_key_id.clone(),
                new_key.wrapped_xprv,
                new_key.wrapped_xprv_nonce,
                spend_key.dpub.clone(),
                dek_id,
                vec![DomainFactoredXpub {
                    domain: spend_domain,
                    xpub: spend_key.dpub.clone(),
                }],
                request.network,
                spend_key.xpub_sig.clone(),
            );

            customer_key_store
                .put_customer_key(&customer_key)
                .await
                .map_err(|e| ApiError::ServerError(e.to_string()))?;

            Ok(Json(CreatedSigningKey {
                root_key_id: root_key_id.clone(),
                xpub: spend_key.dpub,
                xpub_sig: spend_key.xpub_sig,
            }))
        }
    }
}

#[instrument(skip(customer_key_store, enclave_client))]
async fn sign_psbt(
    State(customer_key_store): State<CustomerKeyStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<SignPsbtRequest>,
) -> Result<Json<SignedPsbt>, ApiError> {
    let root_key_id = &request.root_key_id;
    let descriptor = &request.descriptor;
    let change_descriptor = &request.change_descriptor;
    let psbt = &request.psbt;

    match customer_key_store
        .get_customer_key(root_key_id)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!("Could not read customer keys DDB table: {e}"))
        })? {
        Some(ck) => {
            let req = EnclaveSignRequest {
                root_key_id: root_key_id.to_string(),
                wrapped_xprv: ck.key_ciphertext,
                dek_id: ck.dek_id,
                key_nonce: ck.key_nonce,
                descriptor: descriptor.to_string(),
                change_descriptor: change_descriptor.to_string(),
                psbt: psbt.to_string(),
                network: ck.network,
            };
            let signed_psbt = enclave_client
                .sign_psbt(req)
                .await
                .map_err(|e| ApiError::ServerError(format!("Error Signing PSBT: {e}")))?;
            Ok(Json(SignedPsbt {
                psbt: signed_psbt.psbt,
                root_key_id: root_key_id.clone(),
            }))
        }
        None => Err(ApiError::NotFound(format!(
            "Customer signing key for KeySet {root_key_id} not found"
        ))),
    }
}

// Shallow health check
async fn health_check(State(enclave_client): State<Arc<EnclaveClient>>) -> Result<String, String> {
    enclave_client
        .health_check()
        .await
        .map_err(|e| e.to_string())?;
    Ok("server healthy".to_string())
}

/// Generate a keypair wrapped with the KMS CMK, and export the wrapped private key and public key
/// as base64 encoded strings.
///
/// The resulting key is known as the "WSM Integrity Key" and is used to sign select fields in WSM
/// JSON outputs.
///
/// The public half is embedded in the app source code, and the wrapped private half is stored in
/// the WSM enclave source code. It's decrypted at runtime.
async fn generate_integrity_key(
    State(state): State<RouteState>,
) -> Result<Json<GenerateIntegrityKeyResponse>, ApiError> {
    let kms = &state.kms;
    let cmk_id = &state.cmk_id;

    let response = kms
        .generate_data_key_pair_without_plaintext()
        .set_key_id(Some(cmk_id.clone()))
        .key_pair_spec(DataKeyPairSpec::EccSecgP256K1)
        .send()
        .await
        .context("Failed to call KMS to generate fresh data key pair")
        .map_err(|e| ApiError::ServerError(e.to_string()))?;

    let wrapped_privkey = response
        .private_key_ciphertext_blob
        .context("Ciphertext blob is missing in the KMS response")
        .map_err(|e| ApiError::ServerError(e.to_string()))?
        .into_inner();

    let pubkey = response
        .public_key
        .context("Public key is missing in the KMS response")
        .map_err(|e| ApiError::ServerError(e.to_string()))?
        .into_inner();

    Ok(Json(GenerateIntegrityKeyResponse {
        wrapped_privkey: BASE64.encode(wrapped_privkey),
        pubkey: BASE64.encode(pubkey),
    }))
}

async fn integrity_sig(
    State(state): State<RouteState>,
    request: Json<GetIntegritySigRequest>,
) -> Result<Json<GetIntegritySigResponse>, ApiError> {
    let signature = state
        .customer_key_store
        .get_customer_key(&request.root_key_id)
        .await
        .map_err(|e| ApiError::ServerError(format!("Failed to retrieve customer key: {e}")))?
        .ok_or(ApiError::NotFound("Customer key not found".to_string()))?
        .integrity_signature
        .ok_or(ApiError::NotFound(
            "Integrity signature not found".to_string(),
        ))?;
    Ok(Json(GetIntegritySigResponse { signature }))
}

pub async fn axum() -> (TcpListener, Router) {
    tracing::info!("Loading server config");
    let aws_config = aws_config::load_defaults(BehaviorVersion::latest()).await;
    let settings = Settings::new().unwrap();
    let ddb = ddb::new_client(&aws_config, &settings.dynamodb_endpoint);
    let kms = KmsClient::new(&aws_config);
    let kms_config = match settings.run_mode {
        RunMode::Test => None,
        _ => Some(
            KmsConfig::try_new(&settings, &aws_config, settings.cmk_id.clone())
                .await
                .unwrap(),
        ),
    };
    let dek_store = DekStore::new(
        ddb.clone(),
        kms.clone(),
        &settings.dek_table_name,
        &settings.cmk_id,
    );
    // TODO:[W-1236] Figment to grab the configuration params from Rocket.toml, there should be a local and a production
    set_global_telemetry(&Config {
        service_name: "wsm".to_string(),
        mode: match settings.run_mode {
            RunMode::Test => Some(Jaeger),
            _ => Some(Datadog),
        },
    })
    .unwrap();
    tracing::info!("starting main web service");

    let mut router = Router::from(RouteState {
        customer_key_store: CustomerKeyStore::new(ddb, &settings.customer_keys_table_name),
        enclave: Arc::new(EnclaveClient::new(dek_store, kms_config, &settings)),
        kms,
        cmk_id: settings.cmk_id.clone(),
    });
    router = router
        .layer(OtelInResponseLayer)
        .layer(OtelAxumLayer::default());

    let addr = SocketAddr::from((settings.address, settings.port));
    let listener = TcpListener::bind(addr).await.unwrap();
    (listener, router)
}
