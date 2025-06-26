use strum_macros::{Display, EnumDiscriminants, EnumString};
use time::OffsetDateTime;

use super::{
    router::{
        InitiateTransactionVerificationView, InitiateTransactionVerificationViewRequested,
        InitiateTransactionVerificationViewSigned, TransactionVerificationGrantView,
    },
    TransactionVerificationId,
};
use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use bdk_utils::error::BdkUtilError;
use bdk_utils::{DescriptorKeyset, ElectrumRpcUris};

#[derive(Debug, PartialEq, EnumDiscriminants)]
#[strum_discriminants(derive(Display, EnumString))]
pub enum InitiateVerificationResult {
    VerificationRequired,
    VerificationRequested {
        verification_id: TransactionVerificationId,
        expiration: OffsetDateTime,
    },
    SignedWithoutVerification {
        psbt: Psbt,
        hw_grant: TransactionVerificationGrantView,
    },
}

impl InitiateVerificationResult {
    pub fn to_response(self) -> InitiateTransactionVerificationView {
        match self {
            InitiateVerificationResult::VerificationRequired => {
                InitiateTransactionVerificationView::VerificationRequired
            }
            InitiateVerificationResult::VerificationRequested {
                verification_id,
                expiration,
            } => InitiateTransactionVerificationView::VerificationRequested(
                InitiateTransactionVerificationViewRequested {
                    verification_id,
                    expiration,
                },
            ),
            InitiateVerificationResult::SignedWithoutVerification { psbt, hw_grant } => {
                InitiateTransactionVerificationView::Signed(
                    InitiateTransactionVerificationViewSigned { psbt, hw_grant },
                )
            }
        }
    }
}

pub trait WalletProvider {
    fn get_wallet(self) -> Result<Box<Wallet<AnyDatabase>>, BdkUtilError>;
}

pub struct DescriptorKeysetWalletProvider {
    descriptor_keyset: DescriptorKeyset,
    rpc_uris: ElectrumRpcUris,
}

impl DescriptorKeysetWalletProvider {
    pub fn new(descriptor_keyset: DescriptorKeyset, rpc_uris: ElectrumRpcUris) -> Self {
        Self {
            descriptor_keyset,
            rpc_uris,
        }
    }
}

impl WalletProvider for DescriptorKeysetWalletProvider {
    fn get_wallet(self) -> Result<Box<Wallet<AnyDatabase>>, BdkUtilError> {
        self.descriptor_keyset
            .generate_wallet(false, &self.rpc_uris)
            .map(Box::new)
    }
}

pub struct StaticWalletProvider {
    pub wallet: Box<Wallet<AnyDatabase>>,
}

impl<'a> WalletProvider for StaticWalletProvider {
    fn get_wallet(self) -> Result<Box<Wallet<AnyDatabase>>, BdkUtilError> {
        Ok(self.wallet)
    }
}
