use std::error::Error;
use std::fmt::{Debug, Display, Formatter};

use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use subprocess::{Popen, PopenConfig, Redirection};

use wsm_common::messages::enclave::KmsRequest;
use wsm_common::{
    enclave_log::{LogBuffer, MAX_LOG_EVENT_SIZE_BYTES},
    try_with_log_and_error, wsm_log,
};

use crate::native_kms_client::fetch_secret_from_kms;
use crate::settings::RunMode;

pub struct KmsTool {
    fetcher: Box<dyn SecretFetcher + Send + Sync>,
}

#[derive(Debug)]
pub struct KmsToolError {
    message: String,
    pub(crate) log_buffer: LogBuffer,
}

impl Display for KmsToolError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl Error for KmsToolError {}

impl KmsTool {
    pub fn new(run_mode: RunMode) -> Self {
        Self {
            fetcher: match run_mode {
                RunMode::Test => Box::new(FakeFetcher {}),
                _ => Box::new(Fetcher {}),
            },
        }
    }

    pub fn fetch_secret_from_kms(
        &self,
        request: &KmsRequest,
        log_buffer: &mut LogBuffer,
    ) -> Result<Vec<u8>, KmsToolError> {
        self.fetcher.fetch_secret_from_kms(request, log_buffer)
    }
}

trait SecretFetcher {
    fn fetch_secret_from_kms(
        &self,
        request: &KmsRequest,
        log_buffer: &mut LogBuffer,
    ) -> Result<Vec<u8>, KmsToolError>;
}

#[derive(Debug)]
struct Fetcher {}

struct FakeFetcher {}

impl SecretFetcher for Fetcher {
    fn fetch_secret_from_kms(
        &self,
        request: &KmsRequest,
        log_buffer: &mut LogBuffer,
    ) -> Result<Vec<u8>, KmsToolError> {
        // TODO: replace this with an async variant
        // Docs for kmstool_enclave_cli: https://github.com/aws/aws-nitro-enclaves-sdk-c/tree/main/bin/kmstool-enclave-cli#how-to-use-it
        let mut p = try_with_log_and_error!(
            log_buffer,
            KmsToolError,
            Popen::create(
                &[
                    "/kmstool_enclave_cli",
                    "decrypt",
                    "--region",
                    &request.region,
                    "--proxy-port",
                    &request.proxy_port,
                    "--aws-access-key-id",
                    &request.akid,
                    "--aws-secret-access-key",
                    &request.skid,
                    "--aws-session-token",
                    &request.session_token,
                    "--ciphertext",
                    &request.ciphertext,
                    "--key-id",
                    &request.cmk_id,
                    "--encryption-algorithm",
                    "SYMMETRIC_DEFAULT",
                ],
                PopenConfig {
                    stdout: Redirection::Pipe,
                    ..Default::default()
                },
            )
        )?;

        // Obtain the output from the standard streams.
        let (out, _) = try_with_log_and_error!(log_buffer, KmsToolError, p.communicate(None))?;

        if let Some(_exit_status) = p.poll() {
            // the process has finished
            wsm_log!(log_buffer, "Call to kmstool_enclave_cli complete");
        } else {
            // it is still running, terminate it
            try_with_log_and_error!(log_buffer, KmsToolError, p.terminate())?;
        }
        // the return of the tool is `PLAINTEXT: <base64 encoded plaintext>`
        // we need to strip the PLAINTEXT: part and decode the base64
        let plaintext = out.ok_or_else(|| {
            wsm_log!(log_buffer, "Could not get output from kmstool subprocess");
            KmsToolError {
                message: "Could not get output from kmstool subprocess".to_string(),
                log_buffer: log_buffer.clone(),
            }
        })?;
        // return value is `PLAINTEXT: <base64-encoded plaintext>`
        let plaintext = plaintext
            .split(':')
            .nth(1)
            .ok_or_else(|| {
                wsm_log!(
                    log_buffer,
                    &format!(
                        "Could not parse output from kmstool subprocess. kmstool output: {}",
                        plaintext
                    )
                );
                KmsToolError {
                    message: "Could not parse output from kmstool subprocess".to_string(),
                    log_buffer: log_buffer.clone(),
                }
            })?
            .trim();
        Ok(try_with_log_and_error!(
            log_buffer,
            KmsToolError,
            BASE64.decode(plaintext)
        )?)
    }
}

impl SecretFetcher for FakeFetcher {
    fn fetch_secret_from_kms(
        &self,
        _request: &KmsRequest,
        log_buffer: &mut LogBuffer,
    ) -> Result<Vec<u8>, KmsToolError> {
        wsm_log!(
            log_buffer,
            "using fake KMS instead of calling the real thing"
        );
        // use 256 0 bits as a key if we're running in dev/test
        Ok([0u8; 32].to_vec())
    }
}

/// This fetcher uses the native AWS SDK to fetch the secret from KMS.
struct NativeFetcher;

impl SecretFetcher for NativeFetcher {
    fn fetch_secret_from_kms(
        &self,
        request: &KmsRequest,
        log_buffer: &mut LogBuffer,
    ) -> Result<Vec<u8>, KmsToolError> {
        // TODO [W-11815]: We use a blocking call here to be interoperable with the existing Fetcher,
        // which forces the caller to wait for the secret to be fetched, since it uses a shell
        //subprocess. We should remove this once we deprecate the existing Fetcher.
        tokio::task::block_in_place(|| {
            tokio::runtime::Handle::current().block_on(async {
                match fetch_secret_from_kms(request, log_buffer).await {
                    Ok(res) => Ok(res.into()),
                    Err(e) => {
                        wsm_log!(
                            log_buffer,
                            &format!("Error when fetching secret from KMS: {:?}", e)
                        );

                        Err(KmsToolError {
                            message: format!("Error when fetching secret from KMS: {:?}", e),
                            log_buffer: log_buffer.clone(),
                        })
                    }
                }
            })
        })
    }
}
