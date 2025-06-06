use anyhow::{anyhow, bail};
use aws_credential_types::provider::{ProvideCredentials, SharedCredentialsProvider};
use aws_credential_types::Credentials;
use aws_types::region::Region;
use aws_types::SdkConfig;
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use log::{log, Level};
use reqwest::Response;
use serde::Serialize;
use serde_json;
use thiserror::Error;
use tracing::{event, instrument};
use wsm_common::enclave_log::LogBuffer;
use wsm_common::messages::api::{
    ApprovePsbtRequest, ApprovePsbtResponse, AttestationDocResponse, EvaluatePinRequest,
    EvaluatePinResponse, GrantRequest, GrantResponse, NoiseInitiateBundleRequest,
    NoiseInitiateBundleResponse,
};
use wsm_common::messages::enclave::{
    DerivedKey, EnclaveContinueDistributedKeygenRequest, EnclaveContinueDistributedKeygenResponse,
    EnclaveContinueShareRefreshRequest, EnclaveContinueShareRefreshResponse,
    EnclaveCreateKeyRequest, EnclaveCreateSelfSovereignBackupRequest,
    EnclaveCreateSelfSovereignBackupResponse, EnclaveDeriveKeyRequest,
    EnclaveGeneratePartialSignaturesRequest, EnclaveGeneratePartialSignaturesResponse,
    EnclaveInitiateDistributedKeygenRequest, EnclaveInitiateDistributedKeygenResponse,
    EnclaveInitiateShareRefreshRequest, EnclaveInitiateShareRefreshResponse,
    LoadIntegrityKeyRequest,
};
use wsm_common::messages::{
    api::SignedPsbt,
    enclave::{CreatedKey, EnclaveSignRequest, KmsRequest, LoadSecretRequest},
    SecretRequest,
};

use crate::dependencies::enclave_client::EnclaveClientError::CredentialProviderError;
use crate::{DekStore, Settings};

const PROD_WRAPPED_INTEGRITY_KEY_B64: &str = include_str!("../../../keys/prod_integrity_key.b64");

#[derive(Error, Debug)]
pub enum EnclaveClientError {
    #[error("Could not load kms credentials: {0}")]
    CredentialProviderError(String),
}

#[derive(Clone, Debug)]
pub struct KmsConfig {
    proxy_port: u32,
    region: Region,
    credentials_provider: SharedCredentialsProvider,
    cmk_id: String,
}

impl KmsConfig {
    pub async fn try_new(
        settings: &Settings,
        aws_config: &SdkConfig,
        cmk_id: String,
    ) -> Result<Self, EnclaveClientError> {
        Ok(Self {
            proxy_port: settings.kms_proxy_port,
            region: aws_config
                .region()
                .expect("No region provided through KMS config")
                .clone(),
            credentials_provider: aws_config.credentials_provider().ok_or(
                CredentialProviderError(
                    "No credentials provider provided through KMS config".to_string(),
                ),
            )?,
            cmk_id,
        })
    }

    pub async fn creds(&self) -> Result<Credentials, EnclaveClientError> {
        self.credentials_provider
            .provide_credentials()
            .await
            .map_err(|err| CredentialProviderError(err.to_string()))
    }
}

#[derive(Debug)]
pub struct EnclaveClient {
    endpoint: reqwest::Url,
    client: reqwest::Client,
    kms_config: Option<KmsConfig>,
    dek_store: DekStore,
}

impl EnclaveClient {
    pub fn new(dek_store: DekStore, kms_config: Option<KmsConfig>, settings: &Settings) -> Self {
        EnclaveClient {
            endpoint: reqwest::Url::try_from(settings.enclave_endpoint.as_str()).unwrap(),
            client: reqwest::Client::new(),
            kms_config,
            dek_store,
        }
    }

    pub async fn health_check(&self) -> anyhow::Result<()> {
        self.client
            .get(self.endpoint.join("health-check")?)
            .send()
            .await?;
        Ok(())
    }

    #[instrument(skip(self))]
    pub async fn create_key(&self, req: EnclaveCreateKeyRequest) -> anyhow::Result<CreatedKey> {
        let result = self
            .post_request_with_dek(SecretRequest::new("create-key", req.dek_id.clone(), req))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn initiate_distributed_keygen(
        &self,
        req: EnclaveInitiateDistributedKeygenRequest,
    ) -> anyhow::Result<EnclaveInitiateDistributedKeygenResponse> {
        let result = self
            .post_request_with_dek(SecretRequest::new(
                "initiate-distributed-keygen",
                req.dek_id.clone(),
                req,
            ))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn continue_distributed_keygen(
        &self,
        req: EnclaveContinueDistributedKeygenRequest,
    ) -> anyhow::Result<EnclaveContinueDistributedKeygenResponse> {
        let result = self
            .post_request_with_dek(SecretRequest::new(
                "continue-distributed-keygen",
                req.dek_id.clone(),
                req,
            ))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn generate_partial_signatures(
        &self,
        req: EnclaveGeneratePartialSignaturesRequest,
    ) -> anyhow::Result<EnclaveGeneratePartialSignaturesResponse> {
        let result = self
            .post_request_with_dek(SecretRequest::new(
                "generate-partial-signatures",
                req.dek_id.clone(),
                req,
            ))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn create_self_sovereign_backup(
        &self,
        req: EnclaveCreateSelfSovereignBackupRequest,
    ) -> anyhow::Result<EnclaveCreateSelfSovereignBackupResponse> {
        let result = self
            .post_request_with_dek(SecretRequest::new(
                "create-self-sovereign-backup",
                req.dek_id.clone(),
                req,
            ))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn initiate_share_refresh(
        &self,
        req: EnclaveInitiateShareRefreshRequest,
    ) -> anyhow::Result<EnclaveInitiateShareRefreshResponse> {
        let result = self
            .post_request_with_dek(SecretRequest::new(
                "initiate-share-refresh",
                req.dek_id.clone(),
                req,
            ))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn continue_share_refresh(
        &self,
        req: EnclaveContinueShareRefreshRequest,
    ) -> anyhow::Result<EnclaveContinueShareRefreshResponse> {
        let result = self
            .post_request_with_dek(SecretRequest::new(
                "continue-share-refresh",
                req.dek_id.clone(),
                req,
            ))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn derive_key(&self, req: EnclaveDeriveKeyRequest) -> anyhow::Result<DerivedKey> {
        let result = self
            .post_request_with_dek(SecretRequest::new("derive-key", req.dek_id.clone(), req))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn sign_psbt(&self, req: EnclaveSignRequest) -> anyhow::Result<SignedPsbt> {
        let result = self
            .post_request_with_dek(SecretRequest::new("sign-psbt", req.dek_id.clone(), req))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn attestation_doc(&self) -> anyhow::Result<AttestationDocResponse> {
        let result = self
            .client
            .get(self.endpoint.join("attestation-doc-from-enclave")?)
            .send()
            .await?;
        handle_enclave_logs(&result).await;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn initiate_secure_channel(
        &self,
        bundle: Vec<u8>,
        server_static_pubkey: &str,
    ) -> anyhow::Result<NoiseInitiateBundleResponse> {
        let result = self
            .client
            .post(self.endpoint.join("initiate-secure-channel")?)
            .json(&NoiseInitiateBundleRequest {
                bundle,
                server_static_pubkey: server_static_pubkey.to_string(),
            })
            .send()
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn evaluate_pin(
        &self,
        sealed_request: Vec<u8>,
        noise_session_id: String,
    ) -> anyhow::Result<EvaluatePinResponse> {
        let result = self
            .client
            .post(self.endpoint.join("evaluate-pin")?)
            .json(&EvaluatePinRequest {
                sealed_request,
                noise_session_id,
            })
            .send()
            .await?;
        if result.status() != 200 {
            bail!("Error from the enclave: {}", result.text().await?);
        }
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn approve_grant(&self, request: GrantRequest) -> anyhow::Result<GrantResponse> {
        self.load_integrity_key().await?;
        let result = self
            .client
            .post(self.endpoint.join("approve-grant")?)
            .json(&request)
            .send()
            .await?;
        if result.status() != 200 {
            bail!("Error from the enclave: {}", result.text().await?);
        }
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn approve_psbt(
        &self,
        request: ApprovePsbtRequest,
    ) -> anyhow::Result<ApprovePsbtResponse> {
        self.load_integrity_key().await?;
        let result = self
            .client
            .post(self.endpoint.join("approve-psbt")?)
            .json(&request)
            .send()
            .await?;
        handle_enclave_logs(&result).await;
        if result.status() != 200 {
            bail!("Error from the enclave: {}", result.text().await?);
        }
        Ok(result.json().await?)
    }

    /// This method first tries an "optimistic" call to the enclave with the user's provided
    /// data-encryption key. Upon first failure, which is usually due to the DEK not being loaded
    /// onto the enclave, we "force" the load. Then, we try again.
    #[instrument(skip(self))]
    async fn post_request_with_dek<T: Serialize>(
        &self,
        req: SecretRequest<'_, T>,
    ) -> anyhow::Result<Response> {
        // Ensure the integrity key has been loaded into the enclave; this only needs to happen once,
        // but the state is managed by the enclave (in case the enclave is restarted, for example).
        self.load_integrity_key().await?;

        for _attempt in 0..2 {
            let res = self
                .client
                .post(self.endpoint.join(req.endpoint)?)
                .json(&req.data)
                .send()
                .await?;
            // get the wsm-enclave logs from the response header and include them in a trace
            if let Some(logs) = get_enclave_logs_from_header(&res) {
                // the logs are a base64-encoded LogBuffer. We decode them and log them as a trace
                // If decoding fails, we log that but don't fail the whole call
                match unpack_enclave_logs(&logs) {
                    Ok(logs) => {
                        event!(tracing::Level::WARN, "Enclave error logs: {}", logs);
                        log!(Level::Warn, "Enclave error logs: {}", logs);
                    }
                    Err(e) => {
                        event!(tracing::Level::WARN, "Could not decode enclave logs: {}", e);
                        log!(Level::Warn, "Could not decode enclave logs: {}", e);
                    }
                }
            }
            if res.status() == 404 {
                // DEK not loaded into enclave
                event!(
                    tracing::Level::DEBUG,
                    "404 from enclave, loading DEK into enclave"
                );
                self.load_wrapped_dek(&req.dek_id).await?;
                // now that the dek is loaded, try making the call again
                continue;
            } else if res.status() == 200 {
                return Ok(res);
            } else {
                match res.text().await {
                    Ok(v) => bail!("Error from the enclave: {}", v),
                    Err(e) => bail!("Error from the enclave: {}", e),
                }
            }
        }
        bail!("Could not get result from server")
    }

    #[instrument(skip(self))]
    pub async fn get_available_dek_id(&self) -> anyhow::Result<String> {
        match self.kms_config {
            Some(_) => self.dek_store.get_availabile_dek_id().await,
            None => Ok("FAKE_DEK_ID".to_string()),
        }
    }

    async fn create_kms_request(
        &self,
        kms_config: &Option<KmsConfig>,
        ciphertext: String,
    ) -> anyhow::Result<KmsRequest> {
        match kms_config {
            None => Ok(KmsRequest {
                region: "FAKE_REGION".to_string(),
                proxy_port: "FAKE_PROXY_PORT".to_string(),
                akid: "FAKE_AKID".to_string(),
                skid: "FAKE_SKID".to_string(),
                session_token: "FAKE_SESSION_TOKEN".to_string(),
                ciphertext: "FAKE_CIPHERTEXT".to_string(),
                cmk_id: "FAKE_CMK_ID".to_string(),
            }),
            Some(kms_config) => {
                let region = kms_config.region.to_string();
                let creds = kms_config.creds().await?;
                let session_token = creds.session_token().unwrap_or_default().to_string();
                Ok(KmsRequest {
                    region,
                    proxy_port: kms_config.proxy_port.to_string(),
                    akid: creds.access_key_id().to_string(),
                    skid: creds.secret_access_key().to_string(),
                    session_token,
                    ciphertext,
                    cmk_id: kms_config.cmk_id.to_string(),
                })
            }
        }
    }

    async fn load_integrity_key(&self) -> anyhow::Result<()> {
        let use_test_key = match &self.kms_config {
            None => true,
            // 597478299196 is the account id for production.
            Some(kms_config) => !kms_config.cmk_id.contains("597478299196"),
        };

        let wrapped_key = if use_test_key {
            "FAKE_WRAPPED_KEY".to_string()
        } else {
            PROD_WRAPPED_INTEGRITY_KEY_B64.to_string()
        };

        let kms_req = self
            .create_kms_request(&self.kms_config, wrapped_key)
            .await?;
        let req = LoadIntegrityKeyRequest {
            request: kms_req,
            use_test_key,
        };

        let res = self
            .client
            .post(self.endpoint.join("load-integrity-key")?)
            .json(&req)
            .send()
            .await?;

        handle_enclave_logs(&res).await;
        Ok(())
    }

    #[instrument(skip(self))]
    async fn load_wrapped_dek(&self, dek_id: &str) -> anyhow::Result<()> {
        let req = match &self.kms_config {
            None => {
                event!(tracing::Level::WARN, "Loading fake secret into enclave");
                LoadSecretRequest {
                    region: "FAKE_REGION".to_string(),
                    proxy_port: "FAKE_PROXY_PORT".to_string(),
                    akid: "FAKE_AKID".to_string(),
                    skid: "FAKE_SKID".to_string(),
                    session_token: "FAKE_SESSION_TOKEN".to_string(),
                    dek_id: "FAKE_DEK_ID".to_string(),
                    ciphertext: "FAKE_DEK_CIPHERTEXT".to_string(),
                    cmk_id: "FAKE_CMK_ID".to_string(),
                }
            }
            Some(kms_config) => {
                let dek = self
                    .dek_store
                    .get_wrapped_key(dek_id)
                    .await
                    .map_err(|e| anyhow!("Could not load DEK: {}", e))?;
                let region = kms_config.region.to_string();
                let creds = kms_config.creds().await?;
                let akid = creds.access_key_id().to_string();
                let skid = creds.secret_access_key().to_string();
                let session_token = creds.session_token().unwrap_or("").to_string();
                LoadSecretRequest {
                    region,
                    proxy_port: kms_config.proxy_port.to_string(),
                    akid,
                    skid,
                    session_token,
                    dek_id: dek_id.to_string(),
                    ciphertext: dek,
                    cmk_id: kms_config.cmk_id.to_string(),
                }
            }
        };
        let res = self
            .client
            .post(self.endpoint.join("load-secret")?)
            .json(&req)
            .send()
            .await?;
        // get the wsm-enclave logs from the response header and include them in a trace
        handle_enclave_logs(&res).await;
        Ok(())
    }
}

// Get the X-WSM-Logs header from the response, if it exists
fn get_enclave_logs_from_header(res: &Response) -> Option<String> {
    res.headers()
        .get("X-WSM-Logs")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.to_string())
}

fn unpack_enclave_logs(logs: &str) -> anyhow::Result<LogBuffer> {
    let logs = BASE64.decode(logs)?;
    let logs = serde_json::from_str(&String::from_utf8(logs)?)?;
    Ok(logs)
}

async fn handle_enclave_logs(res: &Response) {
    if let Some(logs) = get_enclave_logs_from_header(res) {
        match unpack_enclave_logs(&logs) {
            Ok(logs) => event!(tracing::Level::WARN, "Enclave error logs: {}", logs),
            Err(e) => event!(tracing::Level::WARN, "Could not decode enclave logs: {}", e),
        }
    }
}
