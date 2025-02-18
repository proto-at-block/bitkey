use crate::error::SigningError;
use crate::metrics::{
    record_histogram, record_histogram_async, TIME_TO_BROADCAST, TIME_TO_CHECK_SPENDING_RULES,
    TIME_TO_COSIGN,
};
use crate::signing_processor::state::{Initialized, Signed, Validated};
use crate::spend_rules::errors::SpendRuleCheckErrors;
use crate::spend_rules::SpendRuleSet;
use crate::SERVER_SIGNING_ENABLED;
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::SignOptions;
use bdk_utils::{DescriptorKeyset, ElectrumRpcUris, TransactionBroadcasterTrait};
use feature_flags::service::Service as FeatureFlagsService;
use std::fmt;
use std::str::FromStr;
use std::sync::Arc;
use tracing::{event, Level};
use types::account::identifiers::KeysetId;
use wsm_rust_client::SigningService;

pub mod state {
    use bdk_utils::bdk::bitcoin::psbt::Psbt;

    #[derive(Clone)]
    pub struct Initialized;
    pub struct Validated {
        pub(crate) context: String,
        pub(crate) psbt: Psbt,
    }

    pub struct Signed {
        pub(crate) context: String,
        pub(crate) finalized_psbt: Psbt,
    }
}

pub trait SigningValidator {
    type SigningProcessor: Signer;

    fn validate(
        &self,
        psbt: &Psbt,
        spend_rule_set: SpendRuleSet<'_>,
    ) -> Result<Self::SigningProcessor, SigningError>;
}

#[async_trait]
pub trait Signer: Sized {
    type SigningProcessor: Broadcaster;
    async fn sign_transaction(
        &self,
        rpc_uris: &ElectrumRpcUris,
        source_descriptor: &DescriptorKeyset,
        keyset_id: &KeysetId,
    ) -> Result<Self::SigningProcessor, SigningError>;
}

pub trait Broadcaster: Sized {
    fn broadcast_transaction(
        &mut self,
        rpc_uris: &ElectrumRpcUris,
        source_descriptor: &DescriptorKeyset,
    ) -> Result<(), SigningError>;
    fn finalized_psbt(&self) -> Psbt;
}

#[derive(Clone)]
pub struct SigningProcessor<T> {
    wsm_signing_service: Arc<dyn SigningService + Send + Sync>,
    feature_flags_service: FeatureFlagsService,
    transaction_broadcaster: Arc<dyn TransactionBroadcasterTrait>,
    state: T,
}

impl SigningProcessor<()> {
    pub fn new(
        wsm_signing_service: Arc<dyn SigningService + Send + Sync>,
        feature_flags_service: FeatureFlagsService,
        transaction_broadcaster: Arc<dyn TransactionBroadcasterTrait>,
    ) -> SigningProcessor<Initialized> {
        SigningProcessor {
            wsm_signing_service,
            feature_flags_service,
            transaction_broadcaster,
            state: Initialized,
        }
    }
}

impl SigningValidator for SigningProcessor<Initialized> {
    type SigningProcessor = SigningProcessor<Validated>;
    // Needs to be sync because SpendRuleSet contains Wallet<AnyDatabase> which is not Send
    fn validate(
        &self,
        psbt: &Psbt,
        spend_rule_set: SpendRuleSet<'_>,
    ) -> Result<Self::SigningProcessor, SigningError> {
        // error out if mobile pay is disabled by feature flag.
        if !SERVER_SIGNING_ENABLED
            .resolver(&self.feature_flags_service)
            .resolve()
        {
            return Err(SigningError::ServerSigningDisabled);
        }
        let context = spend_rule_set.to_string();

        record_histogram(TIME_TO_CHECK_SPENDING_RULES.to_owned(), &context, || {
            spend_rule_set
                .check_spend_rules(psbt)
                .map_err(SpendRuleCheckErrors::from)
        })?;

        Ok(SigningProcessor {
            wsm_signing_service: self.wsm_signing_service.clone(),
            feature_flags_service: self.feature_flags_service.clone(),
            transaction_broadcaster: self.transaction_broadcaster.clone(),
            state: Validated {
                context,
                psbt: psbt.clone(),
            },
        })
    }
}

impl<T> fmt::Debug for SigningProcessor<T> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let state_name = std::any::type_name::<T>();
        f.debug_struct("SigningProcessor")
            .field("state", &state_name)
            .finish()
    }
}

#[async_trait]
impl Signer for SigningProcessor<Validated> {
    type SigningProcessor = SigningProcessor<Signed>;

    async fn sign_transaction(
        &self,
        rpc_uris: &ElectrumRpcUris,
        source_descriptor: &DescriptorKeyset,
        keyset_id: &KeysetId,
    ) -> Result<Self::SigningProcessor, SigningError> {
        let mut signed_psbt =
            record_histogram_async(TIME_TO_COSIGN.to_owned(), &self.state.context, || async {
                self.sign_psbt(keyset_id, &self.state.psbt, source_descriptor)
                    .await
            })
            .await?;

        let source_wallet = source_descriptor.generate_wallet(false, rpc_uris)?;
        if !source_wallet
            .finalize_psbt(&mut signed_psbt, SignOptions::default())
            .map_err(|e| SigningError::InvalidPsbt(e.to_string()))?
        {
            event!(
                Level::WARN,
                "Cannot create broadcaster because PSBT is not fully signed"
            );
            return Err(SigningError::CannotBroadcastNonFullySignedPsbt);
        }

        Ok(SigningProcessor {
            wsm_signing_service: self.wsm_signing_service.clone(),
            feature_flags_service: self.feature_flags_service.clone(),
            transaction_broadcaster: self.transaction_broadcaster.clone(),
            state: Signed {
                context: self.state.context.clone(),
                finalized_psbt: signed_psbt,
            },
        })
    }
}

impl Broadcaster for SigningProcessor<Signed> {
    fn broadcast_transaction(
        &mut self,
        rpc_uris: &ElectrumRpcUris,
        source_descriptor: &DescriptorKeyset,
    ) -> Result<(), SigningError> {
        record_histogram(TIME_TO_BROADCAST.to_owned(), &self.state.context, || {
            let source_wallet = source_descriptor.generate_wallet(false, rpc_uris)?;

            self.transaction_broadcaster
                .broadcast(source_wallet, &mut self.state.finalized_psbt, rpc_uris)
                .map_err(SigningError::BdkUtils)
        })
    }

    fn finalized_psbt(&self) -> Psbt {
        self.state.finalized_psbt.clone()
    }
}

impl SigningProcessor<Validated> {
    async fn sign_psbt(
        &self,
        keyset_id: &KeysetId,
        psbt: &Psbt,
        requested_descriptor: &DescriptorKeyset,
    ) -> Result<Psbt, SigningError> {
        let receiving = requested_descriptor
            .receiving()
            .into_multisig_descriptor()?;
        let change = requested_descriptor.change().into_multisig_descriptor()?;
        let result = self
            .wsm_signing_service
            .sign_psbt(
                &keyset_id.to_string(),
                &receiving.to_string(),
                &change.to_string(),
                &psbt.to_string(),
            )
            .await?;

        let signed_psbt = Psbt::from_str(&result.psbt)?;
        Ok(signed_psbt)
    }
}

#[cfg(test)]
mod tests {
    use crate::error::SigningError;

    use crate::signing_processor::state::Initialized;
    use crate::signing_processor::{Broadcaster, Signer, SigningProcessor, SigningValidator};
    use crate::spend_rules::errors::SpendRuleCheckError;
    use crate::spend_rules::test::TestRule;
    use crate::spend_rules::SpendRuleSet;
    use account::service::tests::default_electrum_rpc_uris;
    use async_trait::async_trait;
    use bdk_utils::bdk::bitcoin::psbt::{PartiallySignedTransaction, Psbt};
    use bdk_utils::bdk::bitcoin::Network;
    use bdk_utils::bdk::database::AnyDatabase;
    use bdk_utils::bdk::keys::DescriptorPublicKey;
    use bdk_utils::bdk::Wallet;
    use bdk_utils::error::BdkUtilError;
    use bdk_utils::{DescriptorKeyset, ElectrumRpcUris, TransactionBroadcasterTrait};
    use feature_flags::config::Config;
    use mockall::mock;
    use rstest::{fixture, rstest};
    use std::collections::HashMap;
    use std::str::FromStr;
    use std::sync::Arc;
    use types::account::identifiers::KeysetId;
    use wsm_common::messages::api::{
        AttestationDocResponse, ContinueDistributedKeygenResponse, ContinueShareRefreshResponse,
        CreateSelfSovereignBackupResponse, EvaluatePinResponse, GeneratePartialSignaturesResponse,
        GetIntegritySigResponse, InitiateDistributedKeygenResponse, InitiateShareRefreshResponse,
    };
    use wsm_rust_client::{
        CreatedSigningKey, Error, NoiseInitiateBundleResponseWithSession, SignedPsbt,
        SigningService,
    };

    mock! {
        WsmSigner {}
        #[async_trait]
        impl SigningService for WsmSigner {
            async fn health_check(&self) -> Result<String, Error>;
            async fn create_root_key(
                &self,
                root_key_id: &str,
                network: Network,
            ) -> Result<CreatedSigningKey, Error>;
            async fn initiate_distributed_keygen(
                &self,
                root_key_id: &str,
                network: Network,
                sealed_request: Vec<u8>,
                noise_session: Vec<u8>,
            ) -> Result<InitiateDistributedKeygenResponse, Error>;
            async fn continue_distributed_keygen(
                &self,
                root_key_id: &str,
                network: Network,
                sealed_request: Vec<u8>,
                noise_session: Vec<u8>,
            ) -> Result<ContinueDistributedKeygenResponse, Error>;
            async fn create_self_sovereign_backup(
                &self,
                root_key_id: &str,
                network: Network,
                sealed_request: Vec<u8>,
                noise_session:Vec<u8>,
            ) -> Result<CreateSelfSovereignBackupResponse, Error>;
            async fn initiate_share_refresh(
                &self,
                root_key_id: &str,
                network: Network,
                sealed_request: Vec<u8>,
                noise_session: Vec<u8>,
            ) -> Result<InitiateShareRefreshResponse, Error>;
            async fn continue_share_refresh(
                &self,
                root_key_id: &str,
                network: Network,
                sealed_request: Vec<u8>,
                noise_session: Vec<u8>,
            ) -> Result<ContinueShareRefreshResponse, Error>;
            async fn generate_partial_signatures(
                &self,
                root_key_id: &str,
                network: Network,
                sealed_request: Vec<u8>,
                noise_session: Vec<u8>,
            ) -> Result<GeneratePartialSignaturesResponse, Error>;
            async fn sign_psbt(
                &self,
                root_key_id: &str,
                descriptor: &str,
                change_descriptor: &str,
                psbt: &str,
            ) -> Result<SignedPsbt, Error>;
            async fn get_key_integrity_sig(
                &self,
                root_key_id: &str,
            ) -> Result<GetIntegritySigResponse, Error>;
            async fn get_attestation_document(&self) -> Result<AttestationDocResponse, Error>;
            async fn initiate_secure_channel(
                &self,
                bundle: Vec<u8>,
                server_static_pubkey: &str,
            ) -> Result<NoiseInitiateBundleResponseWithSession, Error>;
            async fn evaluate_pin(&self, sealed_request: Vec<u8>, noise_session: Vec<u8>) -> Result<EvaluatePinResponse, Error>;
    }
    }

    mock! {
        Broadcaster {}
        impl TransactionBroadcasterTrait for Broadcaster {
            fn broadcast(
                &self,
                wallet: Wallet<AnyDatabase>,
                transaction: &mut PartiallySignedTransaction,
                rpc_uris: &ElectrumRpcUris,
            ) -> Result<(), BdkUtilError>;
        }
    }

    #[fixture]
    fn app_signed_psbt() -> Psbt {
        Psbt::from_str("cHNidP8BAH0BAAAAAX241URNPJ4fJziMSnR10nkDv9hUmKP5HCCmD6pP7nUmAAAAAAD+////AtAHAAAAAAAAFgAU9qPydF71MTafW0+ZQq60OVIcySPKNAEAAAAAACIAIKLbHLmtMBUZQeZAIfR/pqTcIwV6rnklkEZ9FjBIHnBaCU8DAAABAP17AQEAAAAAAQEHQBLMEiSX9ECbY1EqpthX4csKTgVw9Xf7mePfppRyzAEAAAAA/v///wJQQAEAAAAAACIAIHK79h+xi7GGSKncLkVjWy/4ny5n9au7CaxIUrp0wDDy0AcAAAAAAAAWABT2o/J0XvUxNp9bT5lCrrQ5UhzJIwQARzBEAiAbc5aTT6HC890iAzaRNdpdzDin/HRzNqKjq1Efd1pVFgIgIdITvGP+yhb0/U9KcRUUdXzJM475MmNiIWbxIndTKtsBRzBEAiAYY0YQXjWPIjzPG4v3t+ZkoDw+u2CDxt3fE0xtuAcsngIgXlExDpLKACPOV5X2yw66/s4qnYaYrfTYUHVU3KIspDQBaVIhAhAGWaOZMtQwgiY9KGn98zx4zsdDFdMHbqcTIJ0piIstIQJOcedX2GFk9rjNrMfz8czG9zkiVHf9RCWPGwiKf8yDDCEDstjwb7SfQGm/Gs/NDt4T9y8l9AFjs1oOHuC5cKl5+WtTrsrPAgABAStQQAEAAAAAACIAIHK79h+xi7GGSKncLkVjWy/4ny5n9au7CaxIUrp0wDDyIgIDWdQTVi0P4h5cl9t0G+5Y2hQuVi70pUJlwWdK98AorlhHMEQCIAKm/Ddi8diuM2YijjU6c+76iaK0tutxMuq5+a38pdnpAiBPsm08/8M6Y+V1xBdrGZE5tPGNklVM3ls/iqHLiW1xhQEBBWlSIQKRW9fNcyD4YOGM66CFfJjXSCUnReRmyEmx1sDDWZ1wuiEDPYvQDwwLUvCcDF6e/Jwzu6gpRlPC4OWsncXsszERorkhA1nUE1YtD+IeXJfbdBvuWNoULlYu9KVCZcFnSvfAKK5YU64iBgKRW9fNcyD4YOGM66CFfJjXSCUnReRmyEmx1sDDWZ1wuhgIJUMZVAAAgAEAAIAAAACAAQAAAAcAAAAiBgM9i9APDAtS8JwMXp78nDO7qClGU8Lg5aydxeyzMRGiuRjDReHpVAAAgAEAAIAAAACAAQAAAAcAAAAiBgNZ1BNWLQ/iHlyX23Qb7ljaFC5WLvSlQmXBZ0r3wCiuWBh2mf8VVAAAgAEAAIAAAACAAQAAAAcAAAAAAAEBaVIhAm+xEq0jm3QGMpJSY/cSsLWDVtbSvHBlf3zhXufn8E6GIQJ0R2tbL8R7FS1EKFWsd1gqW/urwwaqUShVtj68+tFjdyECv2/qpulRXW6w5DjxfssYngjBkI8uYZeWS/4tvj4FrFFTriICAm+xEq0jm3QGMpJSY/cSsLWDVtbSvHBlf3zhXufn8E6GGHaZ/xVUAACAAQAAgAAAAIABAAAACAAAACICAnRHa1svxHsVLUQoVax3WCpb+6vDBqpRKFW2Prz60WN3GAglQxlUAACAAQAAgAAAAIABAAAACAAAACICAr9v6qbpUV1usOQ48X7LGJ4IwZCPLmGXlkv+Lb4+BaxRGMNF4elUAACAAQAAgAAAAIABAAAACAAAAAA=").expect("Failed to parse PSBT")
    }

    #[fixture]
    fn app_and_server_signed_psbt() -> Psbt {
        Psbt::from_str("cHNidP8BAH0BAAAAAX241URNPJ4fJziMSnR10nkDv9hUmKP5HCCmD6pP7nUmAAAAAAD+////AtAHAAAAAAAAFgAU9qPydF71MTafW0+ZQq60OVIcySPKNAEAAAAAACIAIKLbHLmtMBUZQeZAIfR/pqTcIwV6rnklkEZ9FjBIHnBaCU8DAAABAP17AQEAAAAAAQEHQBLMEiSX9ECbY1EqpthX4csKTgVw9Xf7mePfppRyzAEAAAAA/v///wJQQAEAAAAAACIAIHK79h+xi7GGSKncLkVjWy/4ny5n9au7CaxIUrp0wDDy0AcAAAAAAAAWABT2o/J0XvUxNp9bT5lCrrQ5UhzJIwQARzBEAiAbc5aTT6HC890iAzaRNdpdzDin/HRzNqKjq1Efd1pVFgIgIdITvGP+yhb0/U9KcRUUdXzJM475MmNiIWbxIndTKtsBRzBEAiAYY0YQXjWPIjzPG4v3t+ZkoDw+u2CDxt3fE0xtuAcsngIgXlExDpLKACPOV5X2yw66/s4qnYaYrfTYUHVU3KIspDQBaVIhAhAGWaOZMtQwgiY9KGn98zx4zsdDFdMHbqcTIJ0piIstIQJOcedX2GFk9rjNrMfz8czG9zkiVHf9RCWPGwiKf8yDDCEDstjwb7SfQGm/Gs/NDt4T9y8l9AFjs1oOHuC5cKl5+WtTrsrPAgABAStQQAEAAAAAACIAIHK79h+xi7GGSKncLkVjWy/4ny5n9au7CaxIUrp0wDDyAQVpUiECkVvXzXMg+GDhjOughXyY10glJ0XkZshJsdbAw1mdcLohAz2L0A8MC1LwnAxenvycM7uoKUZTwuDlrJ3F7LMxEaK5IQNZ1BNWLQ/iHlyX23Qb7ljaFC5WLvSlQmXBZ0r3wCiuWFOuIgYCkVvXzXMg+GDhjOughXyY10glJ0XkZshJsdbAw1mdcLoYCCVDGVQAAIABAACAAAAAgAEAAAAHAAAAIgYDPYvQDwwLUvCcDF6e/Jwzu6gpRlPC4OWsncXsszERorkYw0Xh6VQAAIABAACAAAAAgAEAAAAHAAAAIgYDWdQTVi0P4h5cl9t0G+5Y2hQuVi70pUJlwWdK98AorlgYdpn/FVQAAIABAACAAAAAgAEAAAAHAAAAAQcAAQj8BABHMEQCIBZfVg9+WTulFhLxPxAm3TPQFtlVn8GIkU+bHJLUy1JRAiA0f9f6Nb3SEoA81zn5adxJrSSnO4CxsfStBC+pZStocQFHMEQCIAKm/Ddi8diuM2YijjU6c+76iaK0tutxMuq5+a38pdnpAiBPsm08/8M6Y+V1xBdrGZE5tPGNklVM3ls/iqHLiW1xhQFpUiECkVvXzXMg+GDhjOughXyY10glJ0XkZshJsdbAw1mdcLohAz2L0A8MC1LwnAxenvycM7uoKUZTwuDlrJ3F7LMxEaK5IQNZ1BNWLQ/iHlyX23Qb7ljaFC5WLvSlQmXBZ0r3wCiuWFOuAAABAWlSIQJvsRKtI5t0BjKSUmP3ErC1g1bW0rxwZX984V7n5/BOhiECdEdrWy/EexUtRChVrHdYKlv7q8MGqlEoVbY+vPrRY3chAr9v6qbpUV1usOQ48X7LGJ4IwZCPLmGXlkv+Lb4+BaxRU64iAgJvsRKtI5t0BjKSUmP3ErC1g1bW0rxwZX984V7n5/BOhhh2mf8VVAAAgAEAAIAAAACAAQAAAAgAAAAiAgJ0R2tbL8R7FS1EKFWsd1gqW/urwwaqUShVtj68+tFjdxgIJUMZVAAAgAEAAIAAAACAAQAAAAgAAAAiAgK/b+qm6VFdbrDkOPF+yxieCMGQjy5hl5ZL/i2+PgWsURjDReHpVAAAgAEAAIAAAACAAQAAAAgAAAAA").expect("Failed to parse PSBT")
    }

    #[fixture]
    fn descriptor_for_funded_wallet() -> DescriptorKeyset {
        let app_dpub = DescriptorPublicKey::from_str("[7699ff15/84'/1'/0']tpubDFfAjKJFaQarBKesL9u6T64LuWjHyB6kdLv6gGdngVKfJGC7BTCcXKjahFxgMfPSgCPyoVFmK4ALuq4L3pk7p2i2FEgBJdGVmbpxty9MQzw/*").unwrap();
        let hardware_dpub = DescriptorPublicKey::from_str("[08254319/84'/1'/0']tpubDEitjEyJG3W5vcinDWPD1sYtY4EmePDrPXCSs15Q7TkR78Fuyi21X1UpEpYXdoWc9sUJbsWpkd77VN8ZiJMHcnHvUhmsRqapsUdU7Mzrncf/*").unwrap();
        let server_dpub = DescriptorPublicKey::from_str("[c345e1e9/84'/1'/0']tpubDDC5YGNGhebUAGw8nKsTCTbfutQwAXNzyATcnCsbhCjfdt2a8cpGbojfgAzPnsdsXxVypwjz2uGUV9dpWh211PeYhuHHumjRs7dgRLKcKk1/*").unwrap();
        let network = Network::Signet;

        DescriptorKeyset::new(network, app_dpub, hardware_dpub, server_dpub)
    }

    async fn signing_processor(
        server_signing_enabled: bool,
        mock_signer: MockWsmSigner,
        mock_broadcaster: MockBroadcaster,
    ) -> SigningProcessor<Initialized> {
        let signing_service = Arc::new(mock_signer);
        let transaction_broadcaster = Arc::new(mock_broadcaster);

        let defaults = HashMap::from([(
            "f8e-mobile-pay-enabled".to_string(),
            server_signing_enabled.to_string(),
        )]);
        let feature_flags_service = Config::new_with_overrides(defaults)
            .to_service()
            .await
            .unwrap();

        SigningProcessor::new(
            signing_service,
            feature_flags_service,
            transaction_broadcaster,
        )
    }

    #[rstest(
        server_signing_enabled,
        signing_rules,
        expected_error,
        case(
            true,
            vec![TestRule { fail_with_error: None }],
            None
        ),
        case(
            true,
            vec![TestRule { fail_with_error: Some(SpendRuleCheckError::PsbtOutputsBelongToOriginWallet) }],
            Some(SigningError::SpendRuleCheckFailed(vec![SpendRuleCheckError::PsbtOutputsBelongToOriginWallet].into()))
        ),
        case(
            true,
            vec![
                TestRule { fail_with_error: Some(SpendRuleCheckError::PsbtOutputsBelongToOriginWallet) },
                TestRule { fail_with_error: None },
                TestRule { fail_with_error: Some(SpendRuleCheckError::PsbtInputsDontBelongToOriginWallet) },
            ],
            Some(SigningError::SpendRuleCheckFailed(vec![SpendRuleCheckError::PsbtOutputsBelongToOriginWallet, SpendRuleCheckError::PsbtInputsDontBelongToOriginWallet].into()))
        ),
        case(
            false,
            vec![TestRule { fail_with_error: None }],
            Some(SigningError::ServerSigningDisabled)
        )
    )]
    #[tokio::test]
    async fn test_validate(
        server_signing_enabled: bool,
        signing_rules: Vec<TestRule>,
        expected_error: Option<SigningError>,
    ) {
        // arrange
        let spend_rule_set = SpendRuleSet::test(signing_rules);
        let signing_processor = signing_processor(
            server_signing_enabled,
            MockWsmSigner::new(),
            MockBroadcaster::new(),
        )
        .await;

        // act
        let result = signing_processor.validate(&app_signed_psbt(), spend_rule_set);

        // assert
        match result {
            Err(e) => assert_eq!(
                e.to_string(),
                expected_error.expect("Expected error").to_string()
            ),
            _ => assert!(expected_error.is_none()),
        }
    }

    #[rstest]
    #[tokio::test]
    async fn test_sign_transaction_not_fully_signed_fails(
        app_signed_psbt: Psbt,
        descriptor_for_funded_wallet: DescriptorKeyset,
    ) {
        // arrange
        let keyset_id = KeysetId::gen().expect("Failed to generate keyset id");
        let single_signed_return_psbt = app_signed_psbt.to_string();
        let mut mock_signer = MockWsmSigner::new();
        mock_signer
            .expect_sign_psbt()
            .times(1)
            .returning(move |_, _, _, _| {
                Ok(SignedPsbt {
                    psbt: single_signed_return_psbt.clone(),
                    root_key_id: "".to_string(),
                })
            });

        let mut mock_broadcaster = MockBroadcaster::new();
        mock_broadcaster.expect_broadcast().times(0);

        let signing_processor = signing_processor(true, mock_signer, mock_broadcaster).await;
        let spend_rule_set = SpendRuleSet::test(vec![TestRule {
            fail_with_error: None,
        }]);
        let signing_processor = signing_processor
            .validate(&app_signed_psbt, spend_rule_set)
            .expect("Failed to validate");

        // act
        let result = signing_processor
            .sign_transaction(
                &default_electrum_rpc_uris(),
                &descriptor_for_funded_wallet,
                &keyset_id,
            )
            .await;

        // assert
        assert!(result.is_err());
        assert_eq!(
            result.unwrap_err().to_string(),
            SigningError::CannotBroadcastNonFullySignedPsbt.to_string()
        );
    }

    #[rstest]
    #[tokio::test]
    async fn test_sign_transaction_success(
        app_signed_psbt: Psbt,
        app_and_server_signed_psbt: Psbt,
        descriptor_for_funded_wallet: DescriptorKeyset,
    ) {
        // arrange
        let keyset_id = KeysetId::gen().expect("Failed to generate keyset id");
        let mut mock_signer = MockWsmSigner::new();
        let expected_psbt = app_signed_psbt.to_string();
        let double_signed_return_psbt = app_and_server_signed_psbt.to_string();
        let expected_keyset_id = keyset_id.clone().to_string();
        mock_signer
            .expect_sign_psbt()
            .withf(move |actual_keyset_id, _, _, psbt| {
                actual_keyset_id == expected_keyset_id && *psbt == expected_psbt
            })
            .times(1)
            .returning(move |_, _, _, _| {
                Ok(SignedPsbt {
                    psbt: double_signed_return_psbt.clone(),
                    root_key_id: "".to_string(),
                })
            });

        let signing_processor = signing_processor(true, mock_signer, MockBroadcaster::new()).await;
        let spend_rule_set = SpendRuleSet::test(vec![TestRule {
            fail_with_error: None,
        }]);
        let signing_processor = signing_processor
            .validate(&app_signed_psbt, spend_rule_set)
            .expect("Failed to validate");

        // act
        let result = signing_processor
            .sign_transaction(
                &default_electrum_rpc_uris(),
                &descriptor_for_funded_wallet,
                &keyset_id,
            )
            .await;

        // assert
        assert!(result.is_ok());
        assert_eq!(
            result.unwrap().state.finalized_psbt,
            app_and_server_signed_psbt
        );
    }

    #[rstest]
    #[tokio::test]
    async fn test_broadcast_transaction_success(
        app_signed_psbt: Psbt,
        app_and_server_signed_psbt: Psbt,
        descriptor_for_funded_wallet: DescriptorKeyset,
    ) {
        // arrange
        let mut mock_signer = MockWsmSigner::new();
        let double_signed_return_psbt = app_and_server_signed_psbt.to_string();
        mock_signer
            .expect_sign_psbt()
            .times(1)
            .returning(move |_, _, _, _| {
                Ok(SignedPsbt {
                    psbt: double_signed_return_psbt.clone(),
                    root_key_id: "".to_string(),
                })
            });

        let mut mock_broadcaster = MockBroadcaster::new();
        let expected_psbt = app_and_server_signed_psbt.to_string();
        mock_broadcaster
            .expect_broadcast()
            .withf(move |_, signed_psbt, _| signed_psbt.to_string() == expected_psbt)
            .times(1)
            .returning(|_, _, _| Ok(()));

        let signing_processor = signing_processor(true, mock_signer, mock_broadcaster).await;
        let spend_rule_set = SpendRuleSet::test(vec![TestRule {
            fail_with_error: None,
        }]);
        let signing_processor = signing_processor
            .validate(&app_signed_psbt, spend_rule_set)
            .expect("Failed to validate");

        // act
        let result = signing_processor
            .sign_transaction(
                &default_electrum_rpc_uris(),
                &descriptor_for_funded_wallet,
                &KeysetId::gen().expect("Failed to generate keyset id"),
            )
            .await
            .unwrap()
            .broadcast_transaction(&default_electrum_rpc_uris(), &descriptor_for_funded_wallet);

        // assert
        assert!(result.is_ok());
    }
}
