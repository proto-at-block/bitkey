use bdk_electrum::electrum_client;
use bdk_wallet::{
    chain::local_chain::CannotConnectError, descriptor::DescriptorError,
    miniscript::Error as MiniscriptError,
};
use errors::ApiError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum BdkUtilError {
    #[error("Couldn't generate Descriptor")]
    GenerateDescriptorForDescriptorKeyset(MiniscriptError),
    #[error("Error when parsing input for SignatureCheck {0}")]
    ParseSignatureCheckInput(#[from] bdk_wallet::bitcoin::secp256k1::Error),
    #[error("Error when decoding signature: {0}")]
    DecodeHexSignature(#[from] hex::FromHexError),
    #[error("Couldn't parse XPub: {0}")]
    ParseXPub(String),
    #[error("Invalid Signature with message: {0} and signature: {1}")]
    SignatureMismatch(String, String),
    #[error("Couldn't create wallet due to descriptor error {0}")]
    CreateWallet(#[from] DescriptorError),
    #[error("Couldn't update wallet due to chain connection error {0}")]
    UpdateWallet(#[from] CannotConnectError),
    #[error("Minimum derivation index required: {0} for keychain kind: {1:?}")]
    MinimumDerivationIndexRequired(u32, bdk_wallet::KeychainKind),
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
    #[error("Invalid chaincode delegation PSBT: {0}")]
    InvalidChaincodeDelegationPsbt(String),
    #[error(transparent)]
    ElectrumClientError(#[from] bdk_electrum::electrum_client::Error),
    #[error("Unable to broadcast transaction: {0}")]
    TransactionBroadcastError(electrum_client::Error),
    #[error("Transaction with one or more inputs already exists in the mempool.")]
    TransactionAlreadyInMempoolError,
    #[error("Min relay fee not met.")]
    MinRelayFeeNotMetError,
    #[error("Invalid output address in PSBT")]
    InvalidOutputAddressInPsbt,
    #[error("Error when extracting transaction from PSBT: {0}")]
    ExtractTx(#[from] bdk_wallet::bitcoin::psbt::ExtractTxError),
}

impl From<&BdkUtilError> for ApiError {
    fn from(val: &BdkUtilError) -> Self {
        match val {
            BdkUtilError::GenerateDescriptorForDescriptorKeyset(_)
            | BdkUtilError::MalformedURI
            | BdkUtilError::TransactionBroadcastError(_)
            | BdkUtilError::ExtractTx(_)
            | BdkUtilError::MinimumDerivationIndexRequired(_, _)
            | BdkUtilError::CreateWallet(_)
            | BdkUtilError::UpdateWallet(_) => {
                ApiError::GenericInternalApplicationError(val.to_string())
            }
            BdkUtilError::ElectrumClientError(_) => {
                ApiError::GenericServiceUnavailable(val.to_string())
            }
            BdkUtilError::ParseSignatureCheckInput(_)
            | BdkUtilError::DecodeHexSignature(_)
            | BdkUtilError::ParseXPub(_)
            | BdkUtilError::SignatureMismatch(_, _)
            | BdkUtilError::PsbtInconsistentDerivationPaths
            | BdkUtilError::MalformedDerivationPath
            | BdkUtilError::UnsupportedBitcoinNetwork(_)
            | BdkUtilError::MissingWitnessUtxo
            | BdkUtilError::MinRelayFeeNotMetError
            | BdkUtilError::InvalidOutputAddressInPsbt
            | BdkUtilError::InvalidChaincodeDelegationPsbt(_) => {
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
