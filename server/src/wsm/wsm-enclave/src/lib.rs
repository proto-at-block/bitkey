#![forbid(unsafe_code)]

use std::collections::HashMap;
use std::error::Error;
use std::fmt::{Debug, Display, Formatter};
use std::io::Read;
use std::net::SocketAddr;
use std::str::FromStr;
use std::sync::Arc;

use aes_gcm::aead::{Aead, Payload};
use aes_gcm::{Aes256Gcm, KeyInit, Nonce};
use anyhow::bail;

use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{extract::State, routing::post, Json, Router};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use bdk::bitcoin::hashes::hex::ToHex;
use bdk::bitcoin::hashes::Hash;
use bdk::bitcoin::secp256k1::{ecdsa::Signature, All, Message, Secp256k1, SecretKey};
use bdk::bitcoin::util::bip32::{DerivationPath, ExtendedPrivKey, ExtendedPubKey, Fingerprint};
use bdk::bitcoin::util::psbt::PartiallySignedTransaction;
use bdk::bitcoin::Network;
use bdk::database::MemoryDatabase;
use bdk::descriptor::Segwitv0;
use bdk::keys::{DerivableKey, DescriptorKey, DescriptorSecretKey, ExtendedKey};
use bdk::signer::{SignerContext, SignerOrdering, SignerWrapper, TransactionSigner};
use bdk::{bitcoin, KeychainKind, SignOptions, Wallet};

use rand::rngs::StdRng;
use rand::{RngCore, SeedableRng};
use serde::Serialize;

use tokio::net::TcpListener;
use tokio::sync::RwLock;

use wsm_common::derivation::WSMSupportedDomain;
use wsm_common::messages::api::SignedPsbt;
use wsm_common::messages::enclave::{
    CreateResponse, CreatedKey, DeriveResponse, DerivedKey, EnclaveCreateKeyRequest,
    EnclaveDeriveKeyRequest, EnclaveSignRequest, LoadSecretRequest, LoadedSecret,
};
use wsm_common::messages::TEST_KEY_IDS;
use wsm_common::{
    enclave_log::{LogBuffer, MAX_LOG_EVENT_SIZE_BYTES},
    try_with_log_and_error, wsm_log,
};

use crate::aad::Aad;
use crate::kms_tool::{KmsTool, KmsToolError};
use crate::settings::Settings;

mod aad;
mod kms_tool;
mod settings;

// The WSM test integrity key is not encrypted, and is not used in production.
const TEST_INTEGRITY_KEY_B64: &str = include_str!("test_integrity_key.b64");

const GLOBAL_CONTEXT: &[u8] = b"WsmIntegrityV1";

type KeyStore = Arc<RwLock<HashMap<String, KeySpec>>>;

fn new_keystore() -> KeyStore {
    Arc::new(RwLock::new(HashMap::new()))
}

type KeySpec = Vec<u8>;

#[derive(Debug)]
struct KeyStoreError {
    message: String,
}

impl Display for KeyStoreError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl Error for KeyStoreError {}

pub enum WsmError {
    BadRequest(String, LogBuffer),
    KeyNotFoundError {
        dek_id: String,
        log_buffer: LogBuffer,
    },
    InvalidDEK(String, LogBuffer),
    ServerError {
        message: String,
        log_buffer: LogBuffer,
    },
}

#[derive(Serialize)]
pub struct ErrorResponse<'a> {
    log_buffer: &'a LogBuffer,
    #[serde(default)]
    message: Option<String>,
    #[serde(default)]
    dek_id: Option<String>,
}

pub trait ToErrorResponse {
    fn to_response(&self) -> ErrorResponse;
}

#[derive(Serialize, Clone)]
#[serde(untagged)]
pub enum ErrorReponse {
    DekIdErrorResponse {
        dek_id: String,
        #[serde(skip_serializing)]
        log_buffer: LogBuffer,
    },
    GenericErrorResponse {
        #[serde(skip_serializing)]
        code: StatusCode,
        message: String,
        #[serde(skip_serializing)]
        log_buffer: LogBuffer,
    },
}

impl IntoResponse for WsmError {
    fn into_response(self) -> Response {
        let resp = match self {
            WsmError::BadRequest(message, log_buffer) => ErrorReponse::GenericErrorResponse {
                code: StatusCode::BAD_REQUEST,
                message,
                log_buffer,
            },
            WsmError::KeyNotFoundError { dek_id, log_buffer } => {
                ErrorReponse::DekIdErrorResponse { dek_id, log_buffer }
            }
            WsmError::InvalidDEK(message, log_buffer) => ErrorReponse::GenericErrorResponse {
                code: StatusCode::BAD_REQUEST,
                message,
                log_buffer,
            },
            WsmError::ServerError {
                message,
                log_buffer,
            } => ErrorReponse::GenericErrorResponse {
                code: StatusCode::INTERNAL_SERVER_ERROR,
                message,
                log_buffer,
            },
        };

        match &resp {
            ErrorReponse::GenericErrorResponse {
                code,
                message: _,
                log_buffer,
            } => (
                code.to_owned(),
                log_buffer.to_owned().to_header(),
                Json(resp),
            )
                .into_response(),
            ErrorReponse::DekIdErrorResponse {
                dek_id: _,
                log_buffer,
            } => (
                StatusCode::NOT_FOUND,
                log_buffer.to_owned().to_header(),
                Json(resp),
            )
                .into_response(),
        }
    }
}

impl From<KmsToolError> for WsmError {
    fn from(err: KmsToolError) -> Self {
        WsmError::ServerError {
            message: format!("KmsToolError: {err:?}"),
            log_buffer: err.log_buffer,
        }
    }
}

pub fn sign_with_integrity_key(
    secp: Secp256k1<All>,
    log_buffer: &mut LogBuffer,
    key: &[u8],
    context: &[u8],
    data: &[u8],
) -> Result<Signature, WsmError> {
    let secret_key = SecretKey::from_slice(key).map_err(|e| WsmError::ServerError {
        message: format!("Failed to create secp256k1 secret key: {}", e),
        log_buffer: log_buffer.clone(),
    })?;

    let mut hash_input = Vec::new();
    hash_input.extend_from_slice(GLOBAL_CONTEXT);
    hash_input.extend_from_slice(context);
    hash_input.extend_from_slice(data);

    let digest = bitcoin::hashes::sha256::Hash::hash(&hash_input);
    let message = Message::from_slice(&digest).map_err(|e| WsmError::ServerError {
        message: format!("Failed to create secp256k1 message: {}", e),
        log_buffer: log_buffer.clone(),
    })?;

    Ok(secp.sign_ecdsa(&message, &secret_key))
}

async fn index() -> &'static str {
    "wsm enclave"
}

// Shallow health check
async fn health_check() -> &'static str {
    "enclave healthy"
}

async fn load_secret(
    State(keystore): State<KeyStore>,
    State(kms_tool): State<Arc<KmsTool>>,
    Json(request): Json<LoadSecretRequest>,
) -> Result<Json<LoadedSecret>, WsmError> {
    let mut log_buffer = LogBuffer::new();
    let decrypted_key = kms_tool.fetch_dek_from_kms(&request, &mut log_buffer)?;
    let mut ks = keystore.write().await;
    if let Some(oldval) = ks.get(&request.dek_id) {
        if oldval != &decrypted_key {
            wsm_log!(
                log_buffer,
                &format!(
                    "ERROR: Request was made to overwrite DEK ID {} with a different key",
                    &request.dek_id
                )
            );
            return Err(WsmError::ServerError {
                message: "Illegal request to overwrite DEK".to_string(),
                log_buffer,
            });
        }
    }
    wsm_log!(
        log_buffer,
        &format!(
            "Was able to decrypt and decode the DEK of length {}. Inserting into local keystore",
            decrypted_key.len()
        )
    );
    ks.insert(request.dek_id.to_string(), decrypted_key);
    Ok(Json(LoadedSecret {
        status: "Ok!".to_string(),
    }))
}

async fn sign_psbt(
    State(keystore): State<KeyStore>,
    Json(request): Json<EnclaveSignRequest>,
) -> Result<Json<SignedPsbt>, WsmError> {
    let mut log_buffer = LogBuffer::new();
    let xprv = decode_wrapped_xprv(
        keystore,
        &request.wrapped_xprv,
        &request.key_nonce,
        &request.dek_id,
        &request.root_key_id,
        request.network,
        &mut log_buffer,
    )
    .await?;
    let secp = Secp256k1::new();
    let derivation_path = DerivationPath::from(WSMSupportedDomain::Spend(
        request.network.unwrap_or(Network::Signet).into(),
    ));
    let derived_xprv = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        xprv.derive_priv(&secp, &derivation_path)
    )?;
    // Will need to change the descriptor key type param for taproot
    let external_descriptor_xpriv: DescriptorKey<Segwitv0> = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        derived_xprv.into_descriptor_key(
            Some((xprv.fingerprint(&secp), derivation_path.clone())),
            try_with_log_and_error!(
                log_buffer,
                WsmError::ServerError,
                DerivationPath::from_str("m/0")
            )?,
        )
    )?;
    let internal_descriptor_xpriv: DescriptorKey<Segwitv0> = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        derived_xprv.into_descriptor_key(
            Some((xprv.fingerprint(&secp), derivation_path)),
            try_with_log_and_error!(
                log_buffer,
                WsmError::ServerError,
                DerivationPath::from_str("m/1")
            )?,
        )
    )?;
    // We'll need to change the context for taproot
    let signer_context = SignerContext::Segwitv0;
    let external_signer = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        descriptor_key_to_signer(external_descriptor_xpriv, signer_context)
    )?;
    let internal_signer = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        descriptor_key_to_signer(internal_descriptor_xpriv, signer_context)
    )?;
    let mut wallet = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        Wallet::new(
            request.descriptor.as_str(),
            Some(request.change_descriptor.as_str()),
            request.network.unwrap_or(Network::Signet),
            MemoryDatabase::default(),
        )
    )?;

    wallet.add_signer(
        KeychainKind::External,
        SignerOrdering(9001),
        external_signer,
    );
    wallet.add_signer(
        KeychainKind::Internal,
        SignerOrdering(9002),
        internal_signer,
    );
    let mut psbt =
        PartiallySignedTransaction::from_str(request.psbt.as_str()).expect("Could not parse PSBT");
    // Do we want to do any policy enforcement in the enclave? If so, it needs to go right HERE
    let _finalized = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        wallet.sign(&mut psbt, SignOptions::default())
    )?;
    Ok(Json(SignedPsbt {
        psbt: psbt.to_string(),
        root_key_id: request.root_key_id.clone(),
    }))
}

fn descriptor_key_to_signer(
    descriptor_xpriv: DescriptorKey<Segwitv0>,
    signer_context: SignerContext,
) -> anyhow::Result<Arc<dyn TransactionSigner>> {
    let signer: Arc<dyn TransactionSigner> = match descriptor_xpriv {
        DescriptorKey::Public(_, _, _) => {
            bail!("Unpacked public key from secret key. This shouldn't be possible")
        }
        DescriptorKey::Secret(dsk, _, _) => match dsk {
            DescriptorSecretKey::Single(dsp) => {
                Arc::new(SignerWrapper::new(dsp.key, signer_context))
            }
            DescriptorSecretKey::XPrv(xkey) => Arc::new(SignerWrapper::new(xkey, signer_context)),
        },
    };
    Ok(signer)
}

async fn create_key(
    State(route_state): State<RouteState>,
    Json(request): Json<EnclaveCreateKeyRequest>,
) -> Result<Json<CreateResponse>, WsmError> {
    let keystore = route_state.keystore.clone();

    let mut log_buffer = LogBuffer::new();
    let secp = Secp256k1::new();
    let (xprv, xpub) = create_root_key_internal(
        &request.root_key_id,
        request.network,
        &secp,
        &mut log_buffer,
    )
    .await?;
    let datakey = get_dek(&request.dek_id, keystore, &mut log_buffer).await?;
    let (wrapped_xprv, wrapped_xprv_nonce) = encrypt_root_key(
        &request.root_key_id,
        &datakey,
        &xprv,
        Some(request.network),
        &mut log_buffer,
    )?;
    let keysource = (xpub.fingerprint(), DerivationPath::master());
    let dpub = calculate_descriptor_pubkey(keysource, &xpub, &mut log_buffer)?;
    let dpub_sig = sign_with_integrity_key(
        secp,
        &mut log_buffer,
        &route_state.integrity_key,
        b"CreateKeyV1",
        dpub.as_bytes(),
    )?
    .serialize_compact()
    .to_hex();

    Ok(Json(CreateResponse::Single(CreatedKey {
        xpub,
        dpub,
        dpub_sig,
        wrapped_xprv,
        wrapped_xprv_nonce,
    })))
}

async fn derive_key(
    State(route_state): State<RouteState>,
    Json(request): Json<EnclaveDeriveKeyRequest>,
) -> Result<Json<DeriveResponse>, WsmError> {
    let keystore = route_state.keystore.clone();

    let mut log_buffer = LogBuffer::new();
    let secp = Secp256k1::new();
    let xprv = decode_wrapped_xprv(
        keystore.clone(),
        &request.wrapped_xprv,
        &request.key_nonce,
        &request.dek_id,
        &request.key_id,
        request.network,
        &mut log_buffer,
    )
    .await?;
    let root_xpub = ExtendedPubKey::from_priv(&secp, &xprv);

    let derived_xprv = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        xprv.derive_priv(&secp, &request.derivation_path)
    )?;
    let derived_xpub = ExtendedPubKey::from_priv(&secp, &derived_xprv);

    let keysource = (root_xpub.fingerprint(), request.derivation_path.clone());
    let dpub = calculate_descriptor_pubkey(keysource, &derived_xpub, &mut log_buffer)?;
    let dpub_sig = sign_with_integrity_key(
        secp,
        &mut log_buffer,
        &route_state.integrity_key,
        b"DeriveKeyV1",
        dpub.as_bytes(),
    )?
    .serialize_compact()
    .to_hex();

    Ok(Json(DeriveResponse(DerivedKey {
        xpub: derived_xpub,
        dpub,
        dpub_sig,
    })))
}

async fn create_root_key_internal(
    key_id: &str,
    network: Network,
    secp: &Secp256k1<All>,
    log_buffer: &mut LogBuffer,
) -> Result<(ExtendedPrivKey, ExtendedPubKey), WsmError> {
    let xprv = generate_root_xprv(key_id, &network, log_buffer)?;
    wsm_log!(log_buffer, "Generated new root xprv");
    let xpub = <ExtendedKey<Segwitv0>>::from(xprv).into_xpub(network, secp);
    wsm_log!(log_buffer, "Generated new root xpub");

    Ok((xprv, xpub))
}

fn encrypt_root_key(
    root_key_id: &String,
    datakey: &Aes256Gcm,
    xprv: &ExtendedPrivKey,
    network: Option<Network>,
    log_buffer: &mut LogBuffer,
) -> Result<(String, String), WsmError> {
    let nonce_bytes = get_nonce_bytes(root_key_id.as_ref());
    let nonce = Nonce::from(nonce_bytes);
    let aad = Aad::new(root_key_id.to_string(), network);
    let payload = Payload {
        aad: &try_with_log_and_error!(log_buffer, WsmError::ServerError, aad.serialize())?,
        msg: &xprv.encode(),
    };
    let ciphertext = datakey
        .encrypt(&nonce, payload)
        .map_err(|e| WsmError::ServerError {
            message: format!("Failed to encrypt root key: {}", e),
            log_buffer: log_buffer.clone(),
        })?;
    Ok((BASE64.encode(ciphertext), BASE64.encode(nonce)))
}

fn calculate_descriptor_pubkey(
    keysource: (Fingerprint, DerivationPath),
    xpub: &ExtendedPubKey,
    log_buffer: &mut LogBuffer,
) -> Result<String, WsmError> {
    let descriptor_key: DescriptorKey<Segwitv0> = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        xpub.into_descriptor_key(Some(keysource), DerivationPath::default())
    )?;

    let xpub_desc_str = match descriptor_key {
        DescriptorKey::Public(public, _, _) => public.to_string(),
        _ => {
            return Err(WsmError::ServerError {
                message: "Got a privkey out of a pubkey".to_string(),
                log_buffer: log_buffer.clone(),
            });
        }
    };
    Ok(xpub_desc_str)
}

async fn get_dek(
    dek_id: &String,
    keystore: KeyStore,
    log_buffer: &mut LogBuffer,
) -> Result<Aes256Gcm, WsmError> {
    if let Some(d) = keystore.read().await.get(dek_id) {
        let key = Aes256Gcm::new_from_slice(d.as_slice()).map_err(|_| {
            wsm_log!(log_buffer, "Unable to create DEK from bytes in get_dek");
            WsmError::InvalidDEK(dek_id.to_string(), log_buffer.clone())
        })?;
        Ok(key)
    } else {
        wsm_log!(
            log_buffer,
            format!("DEK {dek_id} not found in local keystore. returning error")
        );
        Err(WsmError::KeyNotFoundError {
            dek_id: dek_id.to_string(),
            log_buffer: log_buffer.clone(),
        })
    }
}

fn generate_root_xprv(
    root_key_id: &str,
    network: &Network,
    log_buffer: &mut LogBuffer,
) -> Result<ExtendedPrivKey, WsmError> {
    let mut seed = [0u8; 64];
    let mut rng = StdRng::from_entropy();
    rng.fill_bytes(&mut seed);
    // if we're not running on mainnet AND you pass in a special root_key_id, use a known seed to get known keys for testing
    if *network != Network::Bitcoin && TEST_KEY_IDS.contains(&root_key_id) {
        // overwrite seed with known-value for testing
        println!("ZEROING OUT SEED FOR TEST WALLET");
        seed = [0u8; 64];
    }

    ExtendedPrivKey::new_master(*network, &seed).map_err(|_| WsmError::ServerError {
        message: "Unable to derive root xprv".to_string(),
        log_buffer: log_buffer.clone(),
    })
}

async fn decode_wrapped_xprv(
    keystore: KeyStore,
    wrapped_xprv: &String,
    key_nonce: &String,
    dek_id: &String,
    root_key_id: &String,
    network: Option<Network>,
    log_buffer: &mut LogBuffer,
) -> Result<ExtendedPrivKey, WsmError> {
    let decoded_wrapped_xprv = BASE64
        .decode(wrapped_xprv)
        .map_err(|_| WsmError::ServerError {
            message: "Could not b64 decode wrapped xprv".to_string(),
            log_buffer: log_buffer.clone(),
        })?;
    let decoded_nonce = BASE64
        .decode(key_nonce)
        .map_err(|_| WsmError::ServerError {
            message: "Could not b64 decode nonce".to_string(),
            log_buffer: log_buffer.clone(),
        })?;

    let cipher = get_dek(dek_id, keystore, log_buffer).await?;
    let aad = Aad::new(root_key_id.to_string(), network);
    let plaintext_prv = cipher
        .decrypt(
            Nonce::from_slice(decoded_nonce.as_slice()),
            Payload {
                aad: &aad.serialize().map_err(|_| WsmError::ServerError {
                    message: "Could not serialize aad".to_string(),
                    log_buffer: log_buffer.clone(),
                })?,
                msg: decoded_wrapped_xprv.as_ref(),
            },
        )
        .expect("Could not decrypt xprv");

    let xprv = ExtendedPrivKey::decode(plaintext_prv.as_slice()).expect("Could not decrypt xprv");

    Ok(xprv)
}

/*  generate a a 12-byte nonce from msg_seed.
   Why 12 bytes? AES256-GCM nonces are 96 bits (12 bytes)
   *Security Warning*: We can NEVER reuse nonces for the same key. So we
   add some random junk to the wallet-id, take the sha256, and then take the first 12 bytes.
   We'll probably want to either partition wallets across a set of data keys, or do a data key per
   wallet to avoid eventual nonce-collision
*/
const NONCE_SIZE: usize = 12;

fn get_nonce_bytes(msg_seed: &str) -> [u8; NONCE_SIZE] {
    let mut rng = rand::thread_rng();
    let mut random_junk = [0u8; 256];
    rng.fill_bytes(&mut random_junk);
    // nonce <- h(seed || random)
    let nonce = bitcoin::hashes::sha256::Hash::hash(
        format!("{}{}", msg_seed, random_junk.to_hex()).as_ref(),
    );
    // aes256-gcm nonces are 96 bits = 12 bytes
    let mut nonce_bytes = [0u8; NONCE_SIZE];
    let read_bytes = nonce.as_ref().take(12).read(&mut nonce_bytes).unwrap();
    assert_eq!(read_bytes, NONCE_SIZE);
    nonce_bytes
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState {
    keystore: KeyStore,
    kms_tool: Arc<KmsTool>,
    integrity_key: Vec<u8>,
}

impl From<RouteState> for Router {
    fn from(state: RouteState) -> Self {
        Router::new()
            .route("/", get(index))
            .route("/health-check", get(health_check))
            .route("/load-secret", post(load_secret))
            .route("/sign-psbt", post(sign_psbt))
            .route("/create-key", post(create_key))
            .route("/derive-key", post(derive_key))
            .with_state(state)
    }
}

pub async fn axum() -> (TcpListener, Router) {
    let settings = Settings::new().unwrap();
    let kms_tool = KmsTool::new(settings.run_mode);

    let integrity_key = if settings.use_test_integrity_key {
        BASE64
            .decode(TEST_INTEGRITY_KEY_B64)
            .expect("Could not decode test integrity key")
    } else {
        // TODO(W-5884): Replace with real key
        BASE64
            .decode(TEST_INTEGRITY_KEY_B64)
            .expect("Could not decode production integrity key")
    };

    let router = Router::from(RouteState {
        keystore: new_keystore(),
        kms_tool: Arc::new(kms_tool),
        integrity_key,
    });

    let addr = SocketAddr::from((settings.address, settings.port));
    let listener = TcpListener::bind(addr).await.unwrap();
    (listener, router)
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use axum::response::{IntoResponse, Response};
    use axum::routing::get;
    use axum::{http, Router};
    use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
    use bdk::bitcoin::Network;
    use http_body_util::BodyExt;
    use tower::ServiceExt; // for `collect`

    use wsm_common::bitcoin::util::bip32::DerivationPath;
    use wsm_common::derivation::{CoinType, WSMSupportedDomain};
    use wsm_common::enclave_log::LogBuffer;
    use wsm_common::messages::enclave::{
        CreatedKey, DerivedKey, EnclaveCreateKeyRequest, EnclaveDeriveKeyRequest, LoadSecretRequest,
    };
    use wsm_common::messages::{
        TEST_CMK_ID, TEST_DEK_ID, TEST_DPUB_SPEND, TEST_KEY_ID, TEST_XPUB, TEST_XPUB_SPEND,
    };
    use wsm_common::wsm_log;

    use crate::{
        kms_tool::KmsTool, new_keystore, settings::RunMode, RouteState, TEST_INTEGRITY_KEY_B64,
    };

    fn get_client() -> Router {
        let kms_tool = KmsTool::new(RunMode::Test);

        Router::from(RouteState {
            keystore: new_keystore(),
            kms_tool: Arc::new(kms_tool),
            integrity_key: BASE64
                .decode(TEST_INTEGRITY_KEY_B64)
                .expect("Could not decode test integrity key"),
        })
    }

    #[tokio::test]
    async fn test_index() {
        let client = get_client();
        let response = client
            .oneshot(Request::builder().uri("/").body(Body::empty()).unwrap())
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn test_health_check() {
        let client = get_client();
        let response = client
            .oneshot(
                Request::builder()
                    .uri("/health-check")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
    }

    async fn load_empty_secret(client: Router) -> Response {
        client
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/load-secret")
                    .header(http::header::CONTENT_TYPE, mime::APPLICATION_JSON.as_ref())
                    .body(Body::from(
                        serde_json::to_vec(&LoadSecretRequest {
                            dek_id: TEST_DEK_ID.to_owned(),
                            region: Default::default(),
                            proxy_port: Default::default(),
                            akid: Default::default(),
                            skid: Default::default(),
                            session_token: Default::default(),
                            ciphertext: Default::default(),
                            cmk_id: TEST_CMK_ID.to_owned(),
                        })
                        .unwrap(),
                    ))
                    .unwrap(),
            )
            .await
            .unwrap()
    }

    #[tokio::test]
    async fn test_load_secret() {
        let client = get_client();
        let response = load_empty_secret(client).await;
        assert_eq!(response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn test_create_key() {
        let client = get_client();
        load_empty_secret(client.clone()).await;
        let response = client
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/create-key")
                    .header(http::header::CONTENT_TYPE, mime::APPLICATION_JSON.as_ref())
                    .body(Body::from(
                        serde_json::to_vec(&EnclaveCreateKeyRequest {
                            root_key_id: TEST_KEY_ID.to_string(),
                            dek_id: TEST_DEK_ID.to_string(),
                            network: Network::Signet,
                        })
                        .unwrap(),
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);

        let body = response.into_body().collect().await.unwrap().to_bytes();
        let actual_created_key: CreatedKey = serde_json::from_slice(&body).unwrap();
        assert_eq!(actual_created_key.xpub.to_string(), TEST_XPUB);
        assert!(!actual_created_key.wrapped_xprv.is_empty());
        assert!(!actual_created_key.wrapped_xprv_nonce.is_empty());
    }

    #[tokio::test]
    async fn test_derive_keys() {
        let client = get_client();
        load_empty_secret(client.clone()).await;
        let response = client
            .clone()
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/create-key")
                    .header(http::header::CONTENT_TYPE, mime::APPLICATION_JSON.as_ref())
                    .body(Body::from(
                        serde_json::to_vec(&EnclaveCreateKeyRequest {
                            root_key_id: TEST_KEY_ID.to_string(),
                            dek_id: TEST_DEK_ID.to_string(),
                            network: Network::Signet,
                        })
                        .unwrap(),
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = response.into_body().collect().await.unwrap().to_bytes();
        let actual_created_key: CreatedKey = serde_json::from_slice(&body).unwrap();

        let response = client
            .clone()
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/derive-key")
                    .header(http::header::CONTENT_TYPE, mime::APPLICATION_JSON.as_ref())
                    .body(Body::from(
                        serde_json::to_vec(&EnclaveDeriveKeyRequest {
                            key_id: TEST_KEY_ID.to_string(),
                            dek_id: TEST_DEK_ID.to_string(),
                            wrapped_xprv: actual_created_key.wrapped_xprv.to_string(),
                            key_nonce: actual_created_key.wrapped_xprv_nonce.to_string(),
                            derivation_path: DerivationPath::from(WSMSupportedDomain::Spend(
                                CoinType::Testnet,
                            )),
                            network: Some(Network::Signet),
                        })
                        .unwrap(),
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        let body = response.into_body().collect().await.unwrap().to_bytes();
        let actual_created_spend_key: DerivedKey = serde_json::from_slice(&body).unwrap();
        assert_eq!(actual_created_spend_key.xpub.to_string(), TEST_XPUB_SPEND);
        assert_eq!(actual_created_spend_key.dpub.to_string(), TEST_DPUB_SPEND);
    }

    #[tokio::test]
    async fn test_errors_return_enclave_logs() {
        let client = get_client();
        let response = client
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/create-key")
                    .header(http::header::CONTENT_TYPE, mime::APPLICATION_JSON.as_ref())
                    .body(Body::from(
                        serde_json::to_vec(&EnclaveCreateKeyRequest {
                            root_key_id: TEST_KEY_ID.to_string(),
                            dek_id: TEST_DEK_ID.to_string(),
                            network: Network::Signet,
                        })
                        .unwrap(),
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
        // assert that the response contains the enclave logs in the X-WSM-Logs header
        let enclave_logs: &str = response
            .headers()
            .get("X-WSM-Logs")
            .unwrap()
            .to_str()
            .unwrap();
        assert!(!enclave_logs.is_empty());
    }

    #[tokio::test]
    async fn test_log_truncation() {
        const MAX_LOG_EVENT_SIZE_BYTES: usize = 1024; // 1KB buffer for test

        // response struct that contains a LogBuffer
        struct LogBufferResponse {
            message: String,
            log_buffer: LogBuffer,
        }
        // create a handler that adds more than 1KB of log messages to a LogBuffer and then returns it
        async fn spammy_handler() -> Response {
            let mut log_buffer = LogBuffer::new();
            for i in 0..1024 {
                wsm_log!(log_buffer, format!("spammy log {}", i));
            }
            (log_buffer.to_header(), "spammy handler".to_string()).into_response()
        }
        // create a client that has a route for the spammy handler
        let client = Router::new().route("/spammy", get(spammy_handler));
        // send a request to the spammy handler
        let response = client
            .oneshot(Request::get("/spammy").body(Body::empty()).unwrap())
            .await
            .unwrap();
        let logs_header = response.headers().get_all("X-WSM-Logs");
        let mut iter = logs_header.iter();
        let enclave_logs = iter.next().unwrap();
        // the response should contain a single X-WSM-Logs header
        assert!(iter.next().is_none());
        println!("enclave_logs: {:?}", enclave_logs);
        // base64-decode and deserialize the X-WSM-Logs header into a LogBuffer
        let log_buffer: LogBuffer =
            serde_json::from_str(&String::from_utf8(BASE64.decode(enclave_logs).unwrap()).unwrap())
                .unwrap();
        // the LogBuffer should be truncated
        assert!(log_buffer.truncated);
        // the last event in the log buffer should be the 1023rd event
        assert_eq!(
            log_buffer
                .events
                .iter()
                .last()
                .unwrap()
                .as_str()
                .split(' ')
                .last()
                .unwrap(),
            "1023"
        );
    }
}