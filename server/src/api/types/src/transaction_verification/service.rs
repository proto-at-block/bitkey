use strum_macros::{Display, EnumDiscriminants, EnumString};
use time::OffsetDateTime;

use super::{
    router::{
        InitiateTransactionVerificationView, InitiateTransactionVerificationViewRequested,
        InitiateTransactionVerificationViewSigned, TransactionVerificationGrantView,
    },
    TransactionVerificationId,
};
use crate::account::spending::PrivateMultiSigSpendingKeyset;
use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use bdk_utils::error::BdkUtilError;
use bdk_utils::{
    get_outflow_addresses_for_psbt, get_total_outflow_for_psbt,
    ChaincodeDelegationCollaboratorWallet, DescriptorKeyset, ElectrumRpcUris,
};

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

pub enum TransactionVerificationWallet {
    Legacy {
        wallet: Box<Wallet<AnyDatabase>>,
    },
    Private {
        wallet: ChaincodeDelegationCollaboratorWallet,
        network: Network,
    },
}

impl TransactionVerificationWallet {
    pub fn total_outflow_sats(&self, psbt: &Psbt) -> Result<u64, BdkUtilError> {
        match self {
            TransactionVerificationWallet::Legacy { wallet } => {
                Ok(get_total_outflow_for_psbt(wallet.as_ref(), psbt))
            }
            TransactionVerificationWallet::Private { wallet, .. } => {
                let chaincode_delegation_psbt = wallet
                    .chaincode_delegation_psbt(psbt)
                    .map_err(|err| BdkUtilError::InvalidChaincodeDelegationPsbt(err.to_string()))?;

                wallet
                    .get_outflow_for_psbt(&chaincode_delegation_psbt)
                    .map_err(|err| BdkUtilError::InvalidChaincodeDelegationPsbt(err.to_string()))
            }
        }
    }

    pub fn outflow_addresses(&self, psbt: &Psbt) -> Result<Vec<String>, BdkUtilError> {
        match self {
            TransactionVerificationWallet::Legacy { wallet } => {
                get_outflow_addresses_for_psbt(wallet.as_ref(), psbt, wallet.network())
            }
            TransactionVerificationWallet::Private {
                wallet, network, ..
            } => get_outflow_addresses_for_psbt(wallet, psbt, *network),
        }
    }
}

#[derive(Clone)]
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

#[derive(Clone)]
pub struct PrivateKeysetWalletProvider {
    keyset: PrivateMultiSigSpendingKeyset,
}

impl PrivateKeysetWalletProvider {
    pub fn new(keyset: PrivateMultiSigSpendingKeyset) -> Self {
        Self { keyset }
    }
}

pub struct StaticWalletProvider {
    pub wallet: Box<Wallet<AnyDatabase>>,
}

pub enum VerificationWalletProvider {
    Descriptor(DescriptorKeysetWalletProvider),
    Private(PrivateKeysetWalletProvider),
    Static(StaticWalletProvider),
}

impl VerificationWalletProvider {
    pub fn into_wallet(self) -> Result<TransactionVerificationWallet, BdkUtilError> {
        match self {
            VerificationWalletProvider::Descriptor(provider) => {
                let wallet = provider
                    .descriptor_keyset
                    .generate_wallet(false, &provider.rpc_uris)
                    .map(Box::new)?;

                Ok(TransactionVerificationWallet::Legacy { wallet })
            }
            VerificationWalletProvider::Private(provider) => {
                let network: Network = provider.keyset.network.into();
                Ok(TransactionVerificationWallet::Private {
                    wallet: provider.keyset.clone().into(),
                    network,
                })
            }
            VerificationWalletProvider::Static(provider) => {
                Ok(TransactionVerificationWallet::Legacy {
                    wallet: provider.wallet,
                })
            }
        }
    }
}

impl From<DescriptorKeysetWalletProvider> for VerificationWalletProvider {
    fn from(value: DescriptorKeysetWalletProvider) -> Self {
        VerificationWalletProvider::Descriptor(value)
    }
}

impl From<PrivateKeysetWalletProvider> for VerificationWalletProvider {
    fn from(value: PrivateKeysetWalletProvider) -> Self {
        VerificationWalletProvider::Private(value)
    }
}

impl From<StaticWalletProvider> for VerificationWalletProvider {
    fn from(value: StaticWalletProvider) -> Self {
        VerificationWalletProvider::Static(value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::account::bitcoin::Network as AccountNetwork;
    use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
    use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
    use std::str::FromStr;

    #[test]
    fn private_wallet_without_tweaks_errors() {
        let keyset = PrivateMultiSigSpendingKeyset::new(
            AccountNetwork::BitcoinSignet,
            PublicKey::from_str(
                "025a01f7f457ce585a30d88c236a58314fc32c8643d4f80ce5b16f049f1150e49d",
            )
            .unwrap(),
            PublicKey::from_str(
                "023ca635f2eec3d9c5631809443c35d18973b135b35cbe13c00639c7f6bcedb3b9",
            )
            .unwrap(),
            PublicKey::from_str(
                "032b8b47dd439ef0390f92e97c87378beab3f7d47d7765b7d2a7f4f8b9d6fb0116",
            )
            .unwrap(),
            "integrity_sig".to_string(),
        );

        let wallet = VerificationWalletProvider::Private(PrivateKeysetWalletProvider::new(keyset))
            .into_wallet()
            .expect("Should build private wallet");

        let psbt = Psbt::from_str("cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA").unwrap();

        let err = wallet.total_outflow_sats(&psbt).unwrap_err();

        assert!(matches!(
            err,
            BdkUtilError::InvalidChaincodeDelegationPsbt(_)
        ));
    }
}
