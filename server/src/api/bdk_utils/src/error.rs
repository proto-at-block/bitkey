use bdk::miniscript::Error as MiniscriptError;
use errors::ApiError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum BdkUtilError {
    #[error("Couldn't generate Descriptor")]
    GenerateDescriptorForDescriptorKeyset(MiniscriptError),
    #[error("Couldn't create wallet for keyset")]
    GenerateWalletForDescriptorKeyset(bdk::Error),
    #[error("Error when parsing input for SignatureCheck {0}")]
    ParseSignatureCheckInput(#[from] bdk::bitcoin::secp256k1::Error),
    #[error("Error when decoding signature: {0}")]
    DecodeHexSignature(#[from] hex::FromHexError),
    #[error("Couldn't parse XPub: {0}")]
    ParseXPub(String),
    #[error("Invalid Signature with message: {0} and signature: {1}")]
    SignatureMismatch(String, String),
    #[error("Couldn't sync wallet to the blockchain")]
    WalletSync(bdk::Error),
    #[error("Couldn't cache wallet addresses")]
    WalletCacheAddresses(bdk::Error),
    #[error("PSBT is not addressed to a wallet")]
    PsbtNotAddressedToAWallet(bdk::Error),
    #[error("Inconsistent Derivation paths on psbt entry")]
    PsbtInconsistentDerivationPaths,
    #[error("Malformed Derivation path on psbt entry")]
    MalformedDerivationPath,
    #[error("PSBT input missing witness UTXO data")]
    MissingWitnessUtxo,
    #[error("Unsupported bitcoin network: {0}")]
    UnsupportedBitcoinNetwork(String),
    #[error("Malformed RPC URI")]
    MalformedURI,
    #[error("Electrum client error: {0}")]
    ElectrumClientError(#[from] bdk::electrum_client::Error),
    #[error("Unable to broadcast transaction: {0}")]
    TransactionBroadcastError(#[from] bdk::Error),
    #[error("Transaction with one or more inputs already exists in the mempool.")]
    TransactionAlreadyInMempoolError,
    #[error("Min relay fee not met.")]
    MinRelayFeeNotMetError,
    #[error("Invalid output address in PSBT")]
    InvalidOutputAddressInPsbt,
}

impl From<&BdkUtilError> for ApiError {
    fn from(val: &BdkUtilError) -> Self {
        match val {
            BdkUtilError::GenerateWalletForDescriptorKeyset(_)
            | BdkUtilError::GenerateDescriptorForDescriptorKeyset(_)
            | BdkUtilError::WalletCacheAddresses(_)
            | BdkUtilError::MalformedURI
            | BdkUtilError::TransactionBroadcastError(_) => {
                ApiError::GenericInternalApplicationError(val.to_string())
            }
            BdkUtilError::ElectrumClientError(_) | BdkUtilError::WalletSync(_) => {
                ApiError::GenericServiceUnavailable(val.to_string())
            }
            BdkUtilError::ParseSignatureCheckInput(_)
            | BdkUtilError::DecodeHexSignature(_)
            | BdkUtilError::ParseXPub(_)
            | BdkUtilError::SignatureMismatch(_, _)
            | BdkUtilError::PsbtNotAddressedToAWallet(_)
            | BdkUtilError::PsbtInconsistentDerivationPaths
            | BdkUtilError::MalformedDerivationPath
            | BdkUtilError::UnsupportedBitcoinNetwork(_)
            | BdkUtilError::MissingWitnessUtxo
            | BdkUtilError::MinRelayFeeNotMetError
            | BdkUtilError::InvalidOutputAddressInPsbt => {
                ApiError::GenericBadRequest(val.to_string())
            }
            BdkUtilError::TransactionAlreadyInMempoolError => {
                ApiError::GenericConflict(val.to_string())
            }
        }
    }
}

impl From<BdkUtilError> for ApiError {
    fn from(val: BdkUtilError) -> Self {
        ApiError::from(&val)
    }
}
