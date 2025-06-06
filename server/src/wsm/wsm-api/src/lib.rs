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

use dependencies::customer_key_share_store::{CustomerKeyShare, CustomerKeyShareStore};
use serde_json::json;
use tokio::net::TcpListener;
use tracing::{event, instrument};

use wallet_telemetry::Mode::{Datadog, Jaeger};
use wallet_telemetry::{set_global_telemetry, Config};
use wsm_common::bitcoin::bip32::DerivationPath;

use wsm_common::derivation::WSMSupportedDomain;
use wsm_common::messages::api::{
    ApprovePsbtRequest, ApprovePsbtResponse, AttestationDocResponse,
    ContinueDistributedKeygenRequest, ContinueDistributedKeygenResponse,
    ContinueShareRefreshRequest, ContinueShareRefreshResponse, CreateRootKeyRequest,
    CreateSelfSovereignBackupRequest, CreateSelfSovereignBackupResponse, CreatedSigningKey,
    EvaluatePinRequest, EvaluatePinResponse, GenerateIntegrityKeyResponse,
    GeneratePartialSignaturesRequest, GeneratePartialSignaturesResponse, GetIntegritySigRequest,
    GetIntegritySigResponse, GrantRequest, GrantResponse, InitiateDistributedKeygenRequest,
    InitiateDistributedKeygenResponse, InitiateShareRefreshRequest, InitiateShareRefreshResponse,
    NoiseInitiateBundleRequest, NoiseInitiateBundleResponse, SignPsbtRequest, SignedPsbt,
};
use wsm_common::messages::enclave::{
    EnclaveContinueDistributedKeygenRequest, EnclaveContinueShareRefreshRequest,
    EnclaveCreateKeyRequest, EnclaveCreateSelfSovereignBackupRequest, EnclaveDeriveKeyRequest,
    EnclaveGeneratePartialSignaturesRequest, EnclaveInitiateDistributedKeygenRequest,
    EnclaveInitiateShareRefreshRequest, EnclaveSignRequest,
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
    pub customer_key_share_store: CustomerKeyShareStore,
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
            .route(
                "/generate-partial-signatures",
                post(generate_partial_signatures),
            )
            .route("/integrity-sig", get(integrity_sig))
            .route("/generate-integrity-key", get(generate_integrity_key))
            .route("/attestation-doc", get(attestation_doc))
            .route(
                "/initiate-distributed-keygen",
                post(initiate_distributed_keygen),
            )
            .route(
                "/continue-distributed-keygen",
                post(continue_distributed_keygen),
            )
            .route(
                "/create-self-sovereign-backup",
                post(create_self_sovereign_backup),
            )
            .route("/initiate-secure-channel", post(initiate_secure_channel))
            .route("/evaluate-pin", post(evaluate_pin))
            .route("/initiate-share-refresh", post(initiate_distributed_keygen))
            .route("/approve-grant", post(approve_grant))
            .route("/approve-psbt", post(approve_psbt))
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

#[instrument(err, skip(customer_key_share_store, enclave_client))]
async fn initiate_distributed_keygen(
    State(customer_key_share_store): State<CustomerKeyShareStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<InitiateDistributedKeygenRequest>,
) -> Result<Json<InitiateDistributedKeygenResponse>, ApiError> {
    let root_key_id = &request.root_key_id;
    let dek_id = enclave_client
        .get_available_dek_id()
        .await
        .map_err(|e| ApiError::ServerError(format!("Could not get DEK for new wallet: {e}")))?;

    // Create the root key
    let enclave_request = EnclaveInitiateDistributedKeygenRequest {
        root_key_id: root_key_id.clone(),
        dek_id: dek_id.clone(),
        network: request.network,
        sealed_request: request.sealed_request,
        noise_session_id: request.noise_session_id,
    };
    let enclave_response = enclave_client
        .initiate_distributed_keygen(enclave_request)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!(
                "Could not initiate distributed keygen in enclave: {e}",
            ))
        })?;

    let customer_key_share = CustomerKeyShare::new(
        root_key_id.clone(),
        enclave_response.wrapped_share_details,
        enclave_response.wrapped_share_details_nonce,
        dek_id,
        enclave_response.aggregate_public_key,
        request.network,
    );

    customer_key_share_store
        .put_customer_key_share(&customer_key_share)
        .await
        .map_err(|e| ApiError::ServerError(e.to_string()))?;

    Ok(Json(InitiateDistributedKeygenResponse {
        root_key_id: root_key_id.clone(),
        sealed_response: enclave_response.sealed_response,
        aggregate_public_key: enclave_response.aggregate_public_key,
    }))
}

#[instrument(err, skip(customer_key_share_store, enclave_client))]
async fn continue_distributed_keygen(
    State(customer_key_share_store): State<CustomerKeyShareStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<ContinueDistributedKeygenRequest>,
) -> Result<Json<ContinueDistributedKeygenResponse>, ApiError> {
    let root_key_id = &request.root_key_id;
    match customer_key_share_store
        .get_customer_key_share(root_key_id)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!("Could not read customer key shares DDB table: {e}"))
        })? {
        Some(cks) => {
            let enclave_request = EnclaveContinueDistributedKeygenRequest {
                root_key_id: root_key_id.clone(),
                dek_id: cks.dek_id,
                network: request.network,
                wrapped_share_details: cks.share_details_ciphertext,
                wrapped_share_details_nonce: cks.share_details_nonce,
                sealed_request: request.sealed_request,
                noise_session_id: request.noise_session_id,
            };
            enclave_client
                .continue_distributed_keygen(enclave_request)
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Error continuing distributed keygen: {e}"))
                })?;
            Ok(Json(ContinueDistributedKeygenResponse {}))
        }
        None => Err(ApiError::NotFound(format!(
            "Customer key share {root_key_id} not found"
        ))),
    }
}

#[instrument(err, skip(customer_key_share_store, enclave_client))]
async fn create_self_sovereign_backup(
    State(customer_key_share_store): State<CustomerKeyShareStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<CreateSelfSovereignBackupRequest>,
) -> Result<Json<CreateSelfSovereignBackupResponse>, ApiError> {
    let root_key_id = &request.root_key_id;
    match customer_key_share_store
        .get_customer_key_share(root_key_id)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!("Could not read customer key shares DDB table: {e}"))
        })? {
        Some(cks) => {
            let enclave_request = EnclaveCreateSelfSovereignBackupRequest {
                root_key_id: root_key_id.clone(),
                dek_id: cks.dek_id,
                network: request.network,
                wrapped_share_details: cks.share_details_ciphertext,
                wrapped_share_details_nonce: cks.share_details_nonce,
                sealed_request: request.sealed_request,
                noise_session_id: request.noise_session_id,
            };
            let response = enclave_client
                .create_self_sovereign_backup(enclave_request)
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Error creating self-sovereign backup: {e}"))
                })?;
            Ok(Json(CreateSelfSovereignBackupResponse {
                sealed_response: response.sealed_response,
            }))
        }
        None => Err(ApiError::NotFound(format!(
            "Customer key share {root_key_id} not found"
        ))),
    }
}

#[instrument(err, skip(customer_key_share_store, enclave_client))]
async fn initiate_share_refresh(
    State(customer_key_share_store): State<CustomerKeyShareStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<InitiateShareRefreshRequest>,
) -> Result<Json<InitiateShareRefreshResponse>, ApiError> {
    let root_key_id = &request.root_key_id;
    match customer_key_share_store
        .get_customer_key_share(root_key_id)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!("Could not read customer key shares DDB table: {e}"))
        })?
        .as_ref()
    {
        Some(cks) => {
            let enclave_request = EnclaveInitiateShareRefreshRequest {
                root_key_id: root_key_id.clone(),
                dek_id: cks.dek_id.clone(),
                network: request.network,
                wrapped_share_details: cks.share_details_ciphertext.clone(),
                wrapped_share_details_nonce: cks.share_details_nonce.clone(),
                sealed_request: request.sealed_request,
                noise_session_id: request.noise_session_id,
            };
            let response = enclave_client
                .initiate_share_refresh(enclave_request)
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Error initiating share refresh: {e}"))
                })?;

            customer_key_share_store
                .put_customer_key_share(&cks.with_pending_share_details(
                    response.wrapped_pending_share_details,
                    response.wrapped_pending_share_details_nonce,
                ))
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Could not update customer key share: {e}"))
                })?;

            Ok(Json(InitiateShareRefreshResponse {
                sealed_response: response.sealed_response,
            }))
        }
        None => Err(ApiError::NotFound(format!(
            "Customer key share {root_key_id} not found"
        ))),
    }
}

#[instrument(err, skip(customer_key_share_store, enclave_client))]
async fn continue_share_refresh(
    State(customer_key_share_store): State<CustomerKeyShareStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<ContinueShareRefreshRequest>,
) -> Result<Json<ContinueShareRefreshResponse>, ApiError> {
    let root_key_id = &request.root_key_id;
    match customer_key_share_store
        .get_customer_key_share(root_key_id)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!("Could not read customer key shares DDB table: {e}"))
        })?
        .as_ref()
    {
        Some(cks) => {
            let (Some(pending_share_details_ciphertext), Some(pending_share_details_nonce)) = (
                cks.pending_share_details_ciphertext.as_ref(),
                cks.pending_share_details_nonce.as_ref(),
            ) else {
                return Err(ApiError::ServerError(
                    "Missing pending share details".to_string(),
                ));
            };

            let enclave_request = EnclaveContinueShareRefreshRequest {
                root_key_id: root_key_id.clone(),
                dek_id: cks.dek_id.clone(),
                network: request.network,
                wrapped_pending_share_details: pending_share_details_ciphertext.to_owned(),
                wrapped_pending_share_details_nonce: pending_share_details_nonce.to_owned(),
                sealed_request: request.sealed_request,
                noise_session_id: request.noise_session_id,
            };
            enclave_client
                .continue_share_refresh(enclave_request)
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Error continuing share refresh: {e}"))
                })?;

            customer_key_share_store
                .put_customer_key_share(&cks.with_share_details(
                    pending_share_details_ciphertext.to_owned(),
                    pending_share_details_nonce.to_owned(),
                ))
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Could not update customer key share: {e}"))
                })?;

            Ok(Json(ContinueShareRefreshResponse {}))
        }
        None => Err(ApiError::NotFound(format!(
            "Customer key share {root_key_id} not found"
        ))),
    }
}

#[instrument(err, skip(customer_key_share_store, enclave_client))]
async fn generate_partial_signatures(
    State(customer_key_share_store): State<CustomerKeyShareStore>,
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<GeneratePartialSignaturesRequest>,
) -> Result<Json<GeneratePartialSignaturesResponse>, ApiError> {
    let root_key_id = &request.root_key_id;

    match customer_key_share_store
        .get_customer_key_share(root_key_id)
        .await
        .map_err(|e| {
            ApiError::ServerError(format!("Could not read customer key shares DDB table: {e}"))
        })? {
        Some(cks) => {
            let enclave_request = EnclaveGeneratePartialSignaturesRequest {
                root_key_id: root_key_id.clone(),
                dek_id: cks.dek_id,
                network: request.network,
                wrapped_share_details: cks.share_details_ciphertext,
                wrapped_share_details_nonce: cks.share_details_nonce,
                sealed_request: request.sealed_request,
                noise_session_id: request.noise_session_id,
            };
            let enclave_response = enclave_client
                .generate_partial_signatures(enclave_request)
                .await
                .map_err(|e| {
                    ApiError::ServerError(format!("Error generating partial signatures: {e}"))
                })?;

            Ok(Json(GeneratePartialSignaturesResponse {
                sealed_response: enclave_response.sealed_response,
            }))
        }
        None => Err(ApiError::NotFound(format!(
            "Customer key share {root_key_id} not found"
        ))),
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

async fn attestation_doc(
    State(enclave_client): State<Arc<EnclaveClient>>,
) -> Result<Json<AttestationDocResponse>, ApiError> {
    let result = enclave_client
        .attestation_doc()
        .await
        .map_err(|e| ApiError::ServerError(format!("Failed to retrieve attestation doc: {e}")))?;
    Ok(Json(result))
}

#[instrument(err, skip(enclave_client))]
async fn initiate_secure_channel(
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<NoiseInitiateBundleRequest>,
) -> Result<Json<NoiseInitiateBundleResponse>, ApiError> {
    tracing::info!("wsm-api initiate_secure_channel: {:?}", request);
    let result = enclave_client
        .initiate_secure_channel(request.bundle, &request.server_static_pubkey)
        .await
        .map_err(|e| ApiError::ServerError(format!("Failed to initiate secure channel: {e}")))?;
    Ok(Json(result))
}

#[instrument(err, skip(enclave_client))]
async fn evaluate_pin(
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<EvaluatePinRequest>,
) -> Result<Json<EvaluatePinResponse>, ApiError> {
    tracing::info!("wsm-api evaluate_pin: {:?}", request);
    let result = enclave_client
        .evaluate_pin(request.sealed_request, request.noise_session_id)
        .await
        .map_err(|e| ApiError::ServerError(format!("Failed compute PRF output: {e}")))?;
    Ok(Json(result))
}

#[instrument(err, skip(enclave_client), fields(version = request.version, action = request.action, device_id = request.device_id))]
async fn approve_grant(
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<GrantRequest>,
) -> Result<Json<GrantResponse>, ApiError> {
    tracing::info!("wsm-api create_grant: {:?}", request);
    let result = enclave_client
        .approve_grant(request)
        .await
        .map_err(|e| ApiError::ServerError(format!("Failed to create grant: {e}")))?;

    Ok(Json(result))
}

#[instrument(err, skip(enclave_client))]
async fn approve_psbt(
    State(enclave_client): State<Arc<EnclaveClient>>,
    Json(request): Json<ApprovePsbtRequest>,
) -> Result<Json<ApprovePsbtResponse>, ApiError> {
    let result = enclave_client
        .approve_psbt(request)
        .await
        .map_err(|e| ApiError::ServerError(format!("Failed to approve PSBT: {e}")))?;

    Ok(Json(result))
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
        customer_key_store: CustomerKeyStore::new(ddb.clone(), &settings.customer_keys_table_name),
        customer_key_share_store: CustomerKeyShareStore::new(
            ddb,
            &settings.customer_key_shares_table_name,
        ),
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
