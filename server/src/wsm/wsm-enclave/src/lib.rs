#![forbid(unsafe_code)]

use crate::psbt_verification::verify_inputs_only_have_one_signature;
use crate::psbt_verification::verify_inputs_pubkey_belongs_to_wallet;
use crate::psbt_verification::WalletDescriptors;
use std::collections::HashMap;
use std::error::Error;
use std::fmt::{Debug, Display, Formatter};
use std::net::SocketAddr;
use std::str::FromStr;
use std::sync::Arc;

use aes_gcm::aead::{Aead, AeadCore, OsRng, Payload};
use aes_gcm::{Aes256Gcm, KeyInit, Nonce};
use anyhow::bail;

use aws_nitro_enclaves_nsm_api::api::{Request as NsmRequest, Response as NsmResponse};
use aws_nitro_enclaves_nsm_api::driver::{nsm_exit, nsm_init, nsm_process_request};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{extract::State, routing::post, Json, Router};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use bdk::bitcoin::bip32::{DerivationPath, ExtendedPrivKey, ExtendedPubKey, Fingerprint};
use bdk::bitcoin::hashes::sha256;
use bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk::bitcoin::secp256k1::{ecdsa::Signature, All, Message, Secp256k1, SecretKey};
use bdk::bitcoin::Network;
use bdk::database::MemoryDatabase;
use bdk::descriptor::Segwitv0;
use bdk::keys::{DerivableKey, DescriptorKey, DescriptorSecretKey, ExtendedKey};

use bdk::signer::{SignerContext, SignerOrdering, SignerWrapper, TransactionSigner};
use bdk::{KeychainKind, SignOptions, Wallet};

use rand::rngs::StdRng;
use rand::{RngCore, SeedableRng};
use serde::Serialize;

use tokio::net::TcpListener;
use tokio::sync::RwLock;

use wsm_common::bitcoin::Network::Signet;
use wsm_common::derivation::WSMSupportedDomain;
use wsm_common::messages::api::AttestationDocResponse;
use wsm_common::messages::api::SignedPsbt;
use wsm_common::messages::enclave::{
    CreateResponse, CreatedKey, DeriveResponse, DerivedKey, EnclaveCreateKeyRequest,
    EnclaveDeriveKeyRequest, EnclaveSignRequest, KmsRequest, LoadIntegrityKeyRequest,
    LoadSecretRequest, LoadedSecret,
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
mod frost;
mod kms_tool;
mod psbt_verification;
mod settings;

const GLOBAL_CONTEXT: &[u8] = b"WsmIntegrityV1";
const INTEGRITY_KEY_ID: &str = "integrity";
const TEST_INTEGRITY_KEY_B64: &str = include_str!("../../keys/test_integrity_key.b64");

type KeyStore = Arc<RwLock<HashMap<String, KeySpec>>>;
type GlobalNsmCtx = Arc<NsmCtx>;

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
    IntegrityKeyNotLoaded(String, LogBuffer),
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

pub struct NsmCtx {
    pub fd: i32,
    test_mode: bool,
}

// Locks around this are not needed, as the driver is thread-safe
// https://codebrowser.dev/linux/linux/drivers/misc/nsm.c.html#333
impl NsmCtx {
    pub fn new() -> Result<Self, &'static str> {
        if !std::path::Path::new("/dev/nsm").exists() {
            // Use a dummy file descriptor for tests
            return Ok(NsmCtx {
                fd: 1337,
                test_mode: true,
            });
        }

        let fd = nsm_init();
        if fd < 0 {
            Err("Failed to get file descriptor for NSM library")
        } else {
            Ok(NsmCtx {
                fd,
                test_mode: false,
            })
        }
    }

    pub fn request(&self, request: NsmRequest) -> NsmResponse {
        if self.test_mode {
            NsmResponse::Attestation {
                document: vec![0, 1, 2, 3],
            }
        } else {
            nsm_process_request(self.fd, request)
        }
    }
}

impl Drop for NsmCtx {
    fn drop(&mut self) {
        nsm_exit(self.fd);
    }
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
            WsmError::IntegrityKeyNotLoaded(message, log_buffer) => {
                ErrorReponse::GenericErrorResponse {
                    code: StatusCode::PRECONDITION_REQUIRED,
                    message,
                    log_buffer,
                }
            }
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

    let message = Message::from_hashed_data::<sha256::Hash>(&hash_input);
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

    // See note in LoadSecretRequest about why this is bit redundant.
    let kms_request = KmsRequest {
        region: request.region.clone(),
        proxy_port: request.proxy_port.clone(),
        akid: request.akid.clone(),
        skid: request.skid.clone(),
        session_token: request.session_token.clone(),
        ciphertext: request.ciphertext.clone(),
        cmk_id: request.cmk_id.clone(),
    };

    let decrypted_key = kms_tool.fetch_secret_from_kms(&kms_request, &mut log_buffer)?;
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

async fn load_integrity_key(
    State(keystore): State<KeyStore>,
    State(kms_tool): State<Arc<KmsTool>>,
    Json(request): Json<LoadIntegrityKeyRequest>,
) -> Result<Json<LoadedSecret>, WsmError> {
    let mut ks = keystore.write().await;

    // Check keystore to see if the key is already loaded.
    if ks.contains_key(INTEGRITY_KEY_ID) {
        return Ok(Json(LoadedSecret {
            status: "Ok!".to_string(),
        }));
    }

    let mut log_buffer = LogBuffer::new();

    let key = if request.use_test_key {
        BASE64
            .decode(TEST_INTEGRITY_KEY_B64)
            .expect("Could not decode test integrity key")
    } else {
        let der_encoded = kms_tool.fetch_secret_from_kms(&request.request, &mut log_buffer)?;
        decode_der_secp256k1_private_key(&der_encoded, log_buffer.clone())?
    };

    ks.insert(INTEGRITY_KEY_ID.to_string(), key);

    Ok(Json(LoadedSecret {
        status: "Ok!".to_string(),
    }))
}

async fn attestation_doc(
    State(nsm_ctx): State<GlobalNsmCtx>,
) -> Result<Json<AttestationDocResponse>, WsmError> {
    let log_buffer = LogBuffer::new();

    // `None` in the optional arguments for now.
    let rsp = nsm_ctx.request(NsmRequest::Attestation {
        user_data: None,
        nonce: None,
        public_key: None,
    });

    match rsp {
        NsmResponse::Attestation { document } => Ok(Json(AttestationDocResponse { document })),
        NsmResponse::Error(e) => Err(WsmError::ServerError {
            message: format!("NSM Error: {:?}", e),
            log_buffer: log_buffer.clone(),
        }),
        _ => Err(WsmError::ServerError {
            message: "Unexpected response from NSM".to_string(),
            log_buffer: log_buffer.clone(),
        }),
    }
}

async fn get_integrity_key(
    keystore: &KeyStore,
    log_buffer: &mut LogBuffer,
) -> Result<Vec<u8>, WsmError> {
    keystore
        .read()
        .await
        .get(INTEGRITY_KEY_ID)
        .cloned()
        .ok_or_else(|| {
            WsmError::IntegrityKeyNotLoaded(
                "Integrity key not found in keystore".to_string(),
                log_buffer.clone(),
            )
        })
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

    let network = request.network.unwrap_or(Signet);
    let derivation_path = DerivationPath::from(WSMSupportedDomain::Spend(network.into()));

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
            network,
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

    // Check inputs have only one signature.
    try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        verify_inputs_only_have_one_signature(&psbt.inputs)
    )?;

    // Check inputs already presigned by a key that belongs to the wallet.
    let extended_descriptors = try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        WalletDescriptors::new(
            &request.descriptor,
            &request.change_descriptor,
            &secp,
            network,
        )
    )?;
    try_with_log_and_error!(
        log_buffer,
        WsmError::ServerError,
        verify_inputs_pubkey_belongs_to_wallet(&extended_descriptors, &psbt.inputs, &secp)
    )?;

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
            _ => unimplemented!("Multisig descriptors not yet supported"),
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

    let integrity_key = get_integrity_key(&keystore, &mut log_buffer).await;

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

    let xpub_sig = if let Ok(key) = integrity_key {
        hex::encode(
            sign_with_integrity_key(secp, &mut log_buffer, &key, b"CreateKeyV1", &xpub.encode())?
                .serialize_compact(),
        )
    } else {
        "".to_string()
    };

    Ok(Json(CreateResponse::Single(CreatedKey {
        xpub,
        dpub,
        xpub_sig,
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

    let integrity_key = get_integrity_key(&keystore, &mut log_buffer).await;

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

    let keysource = (root_xpub.fingerprint(), request.derivation_path);
    let dpub = calculate_descriptor_pubkey(keysource, &derived_xpub, &mut log_buffer)?;

    let xpub_sig = if let Ok(key) = integrity_key {
        hex::encode(
            sign_with_integrity_key(
                secp,
                &mut log_buffer,
                &key,
                b"DeriveKeyV1",
                &derived_xpub.encode(),
            )?
            .serialize_compact(),
        )
    } else {
        "".to_string()
    };

    Ok(Json(DeriveResponse(DerivedKey {
        xpub: derived_xpub,
        dpub,
        xpub_sig,
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
    let nonce = Aes256Gcm::generate_nonce(&mut OsRng);
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

fn decode_der_secp256k1_private_key(
    der_bytes: &[u8],
    log_buffer: LogBuffer,
) -> Result<Vec<u8>, WsmError> {
    // ASN.1 like this:
    //      PrivateKeyInfo SEQUENCE (3 elem)
    //       version Version INTEGER 0
    //       privateKeyAlgorithm AlgorithmIdentifier SEQUENCE (2 elem)
    //         algorithm OBJECT IDENTIFIER 1.2.840.10045.2.1 ecPublicKey (ANSI X9.62 public key type)
    //         parameters ANY OBJECT IDENTIFIER 1.3.132.0.10 secp256k1 (SECG (Certicom) named elliptic curve)
    //       privateKey PrivateKey OCTET STRING (118 byte) 30740201010420A0A444B1E7E9FE764261FF4B318F22E50B7E70F7BDA6864DF24F691…
    //         SEQUENCE (4 elem)
    //           INTEGER 1
    //           OCTET STRING (32 byte) A0A444B1E7E9FE764261FF4B318F22E50B7E70F7BDA6864DF24F6914F981551D
    //           [0] (1 elem)
    //             OBJECT IDENTIFIER 1.3.132.0.10 secp256k1 (SECG (Certicom) named elliptic curve)
    //      [1] (1 elem)

    // https://lapo.it/asn1js/#MIGNAgEAMBAGByqGSM49AgEGBSuBBAAKBHYwdAIBAQQgoKREsefp_nZCYf9LMY8i5Qt-cPe9poZN8k9pFPmBVR2gBwYFK4EEAAqhRANCAARr8ZChHCFZ82wDdFtbxG2HD90n7DaTrbp1Hw0LgwxM7dGiURhoj8ZSTI46yPclkrG40lsBMSOqGwstyvr0cjZ3

    let expected_prefix: [u8; 33] = [
        0x30, 0x81, 0x8D, 0x02, 0x01, 0x00, 0x30, 0x10, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D,
        0x02, 0x01, 0x06, 0x05, 0x2B, 0x81, 0x04, 0x00, 0x0A, 0x04, 0x76, 0x30, 0x74, 0x02, 0x01,
        0x01, 0x04, 0x20,
    ];

    if der_bytes[0..33] != expected_prefix {
        return Err(WsmError::ServerError {
            message: "Invalid DER prefix".to_string(),
            log_buffer,
        });
    }

    Ok(der_bytes[33..(32 + 33)].to_vec())
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState {
    keystore: KeyStore,
    kms_tool: Arc<KmsTool>,
    nsm_ctx: Arc<NsmCtx>,
}

impl From<RouteState> for Router {
    fn from(state: RouteState) -> Self {
        Router::new()
            .route("/", get(index))
            .route("/health-check", get(health_check))
            .route("/load-secret", post(load_secret))
            .route("/load-integrity-key", post(load_integrity_key))
            .route("/sign-psbt", post(sign_psbt))
            .route("/create-key", post(create_key))
            .route("/derive-key", post(derive_key))
            .route("/attestation-doc-from-enclave", get(attestation_doc))
            .with_state(state)
    }
}

pub async fn axum() -> (TcpListener, Router) {
    let settings = Settings::new().unwrap();
    let kms_tool = KmsTool::new(settings.run_mode);
    let nsm_ctx = NsmCtx::new().unwrap();

    let router = Router::from(RouteState {
        keystore: new_keystore(),
        kms_tool: Arc::new(kms_tool),
        nsm_ctx: Arc::new(nsm_ctx),
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

    use bdk::bitcoin::hashes::sha256;
    use bdk::bitcoin::secp256k1::{Message, Secp256k1, SecretKey};

    use http_body_util::BodyExt;
    use tower::ServiceExt;
    use wsm_common::bitcoin::Network::Signet; // for `collect`

    use wsm_common::bitcoin::bip32::DerivationPath;
    use wsm_common::derivation::{CoinType, WSMSupportedDomain};
    use wsm_common::enclave_log::LogBuffer;
    use wsm_common::messages::enclave::{
        CreatedKey, DerivedKey, EnclaveCreateKeyRequest, EnclaveDeriveKeyRequest, KmsRequest,
        LoadIntegrityKeyRequest, LoadSecretRequest,
    };
    use wsm_common::messages::{
        TEST_CMK_ID, TEST_DEK_ID, TEST_DPUB_SPEND, TEST_KEY_ID, TEST_XPUB, TEST_XPUB_SPEND,
    };
    use wsm_common::wsm_log;

    use crate::NsmCtx;
    use crate::{
        decode_der_secp256k1_private_key, kms_tool::KmsTool, new_keystore, settings::RunMode,
        RouteState,
    };

    fn get_client() -> Router {
        let kms_tool = KmsTool::new(RunMode::Test);
        let nsm_ctx = NsmCtx::new().unwrap();

        Router::from(RouteState {
            keystore: new_keystore(),
            kms_tool: Arc::new(kms_tool),
            nsm_ctx: Arc::new(nsm_ctx),
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

    async fn load_test_integrity_key(client: Router) {
        client
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/load-integrity-key")
                    .header(http::header::CONTENT_TYPE, mime::APPLICATION_JSON.as_ref())
                    .body(Body::from(
                        serde_json::to_vec(&LoadIntegrityKeyRequest {
                            request: KmsRequest {
                                ciphertext: "doesntmatter".to_owned(),
                                cmk_id: TEST_CMK_ID.to_owned(),
                                ..Default::default()
                            },
                            use_test_key: true,
                        })
                        .unwrap(),
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();
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
        load_test_integrity_key(client.clone()).await;
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
                            network: Signet,
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
        load_test_integrity_key(client.clone()).await;
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
                            network: Signet,
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
                            network: Some(Signet),
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
        assert_eq!(actual_created_spend_key.dpub.to_string(), *TEST_DPUB_SPEND);
    }

    #[tokio::test]
    async fn test_errors_return_enclave_logs() {
        let client = get_client();
        load_test_integrity_key(client.clone()).await;
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
                            network: Signet,
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

    #[test]
    fn test_parse_wrapped_key() {
        let b64_der_key = "MIGNAgEAMBAGByqGSM49AgEGBSuBBAAKBHYwdAIBAQQgoKREsefp/nZCYf9LMY8i5Qt+cPe9poZN8k9pFPmBVR2gBwYFK4EEAAqhRANCAARr8ZChHCFZ82wDdFtbxG2HD90n7DaTrbp1Hw0LgwxM7dGiURhoj8ZSTI46yPclkrG40lsBMSOqGwstyvr0cjZ3";
        let der_key = BASE64.decode(b64_der_key).unwrap();

        let key = decode_der_secp256k1_private_key(&der_key, LogBuffer::new());
        assert!(key.is_ok());
        let key = key.unwrap_or_else(|_| vec![]);

        let secp = Secp256k1::new();

        let secret_key = SecretKey::from_slice(&key).unwrap();

        let hash_input = vec![1, 2, 3];

        let message = Message::from_hashed_data::<sha256::Hash>(hash_input.as_ref());

        secp.sign_ecdsa(&message, &secret_key);
    }
}
