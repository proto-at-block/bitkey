use crate::service::inheritance::sign_and_complete_inheritance_claim::SignAndCompleteInheritanceClaimInput;
use crate::service::inheritance::tests::{
    construct_test_inheritance_service, create_completed_claim, create_locked_claim,
    create_pending_inheritance_claim, setup_accounts, setup_keys_and_signatures,
};
use account::service::tests::default_electrum_rpc_uris;
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::DescriptorKeyset;
use mobile_pay::error::SigningError;
use mobile_pay::signing_processor::{SignerBroadcaster, SigningValidator};
use mobile_pay::spend_rules::errors::SpendRuleCheckError;
use mobile_pay::spend_rules::SpendRuleSet;
use mockall::mock;
use rstest::{fixture, rstest};
use std::str::FromStr;
use types::account::identifiers::KeysetId;
mock! {
    SigningProcessorBroadcaster {}
    #[async_trait]
    impl SignerBroadcaster for SigningProcessorBroadcaster {
        async fn sign_and_broadcast_transaction(
            &self,
            source_descriptor: &DescriptorKeyset,
            keyset_id: &KeysetId,
        ) -> Result<Psbt, SigningError>;
    }
}

mock! {
    SigningProcessor {}
    impl SigningValidator for SigningProcessor {
        type Signer = MockSigningProcessorBroadcaster;

        fn validate<'a>(
            &self,
            psbt: &Psbt,
            spend_rule_set: SpendRuleSet<'a>,
        ) -> Result<<MockSigningProcessor as SigningValidator>::Signer, SigningError>;
    }
}

#[fixture]
fn requested_psbt() -> Psbt {
    Psbt::from_str("cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA").expect("Failed to parse PSBT")
}

#[rstest]
#[tokio::test]
async fn test_sign_and_complete_claim_success(requested_psbt: Psbt) {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let locked_claim = create_locked_claim(&benefactor_account, &beneficiary_account).await;
    let rpc_uris = default_electrum_rpc_uris();

    let mut signing_processor_broadcaster = MockSigningProcessorBroadcaster::new();
    let valid_psbt = requested_psbt.clone();
    signing_processor_broadcaster
        .expect_sign_and_broadcast_transaction()
        .times(1)
        .return_once(move |_, _| Ok(valid_psbt));
    let mut signing_processor = MockSigningProcessor::new();
    signing_processor
        .expect_validate()
        .times(1)
        .return_once(move |_, _| Ok(signing_processor_broadcaster));

    // act
    let result = inheritance_service
        .sign_and_complete(SignAndCompleteInheritanceClaimInput {
            signing_processor,
            rpc_uris,
            inheritance_claim_id: locked_claim.common_fields.id,
            beneficiary_account: beneficiary_account.clone(),
            psbt: requested_psbt.clone(),
        })
        .await;

    // assert
    assert!(result.is_ok());

    let completed_claim = result.expect("Failed to complete claim");
    assert_eq!(completed_claim.psbt, requested_psbt);
    assert_eq!(
        completed_claim.common_fields.recovery_relationship_id,
        locked_claim.common_fields.recovery_relationship_id
    );
    assert!(completed_claim.completed_at > locked_claim.locked_at);
}

#[rstest]
#[tokio::test]
async fn test_sign_and_complete_claim_existing_psbt_rbf_success(requested_psbt: Psbt) {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let locked_claim = create_locked_claim(&benefactor_account, &beneficiary_account).await;
    let completed_claim = create_completed_claim(&locked_claim).await;
    let rpc_uris = default_electrum_rpc_uris();

    let mut signing_processor_broadcaster = MockSigningProcessorBroadcaster::new();
    let valid_psbt = requested_psbt.clone();
    signing_processor_broadcaster
        .expect_sign_and_broadcast_transaction()
        .times(1)
        .return_once(move |_, _| Ok(valid_psbt));
    let mut signing_processor = MockSigningProcessor::new();
    signing_processor
        .expect_validate()
        .times(1)
        .return_once(move |_, _| Ok(signing_processor_broadcaster));

    // act
    let result = inheritance_service
        .sign_and_complete(SignAndCompleteInheritanceClaimInput {
            signing_processor,
            rpc_uris,
            inheritance_claim_id: completed_claim.common_fields.id,
            beneficiary_account: beneficiary_account.clone(),
            psbt: requested_psbt.clone(),
        })
        .await;

    // assert
    assert!(result.is_ok());

    let updated_completed_claim = result.expect("Failed to complete claim");
    assert_eq!(updated_completed_claim.psbt, requested_psbt);
    assert_eq!(
        updated_completed_claim.completed_at,
        completed_claim.completed_at
    );
    assert!(
        updated_completed_claim.common_fields.updated_at > updated_completed_claim.completed_at
    );
}

#[rstest]
#[tokio::test]
async fn test_sign_and_complete_claim_unlocked_fails(requested_psbt: Psbt) {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, _challenge, _app_signature, _hardware_signature) =
        setup_keys_and_signatures(&secp);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        None,
    )
    .await;
    let rpc_uris = default_electrum_rpc_uris();

    let signing_processor = MockSigningProcessor::new();

    // act
    let result = inheritance_service
        .sign_and_complete(SignAndCompleteInheritanceClaimInput {
            signing_processor,
            rpc_uris,
            inheritance_claim_id: pending_claim.common_fields.id,
            beneficiary_account: beneficiary_account.clone(),
            psbt: requested_psbt,
        })
        .await;

    // assert
    assert!(result.is_err());
    assert_eq!(
        result.unwrap_err().to_string(),
        "Locked claim not found between benefactor and beneficiary"
    );
}

#[rstest(
    signing_error,
    expected_msg,
    case(
        SigningError::SpendRuleCheckFailed(vec![SpendRuleCheckError::OutputsBelongToSanctionedIndividuals].into()),
        "One or more outputs belong to sanctioned individuals. "
    ),
    case(
        SigningError::ServerSigningDisabled,
        "Server side signing is disabled"
    ),
    case(
        SigningError::InvalidPsbt("Test".to_string()),
        "Could not decode psbt due to error: Test"
    ),
    case(
        SigningError::CannotBroadcastNonFullySignedPsbt,
        "Psbt was not broadcasted because it wasn't fully signed"
    )
)]
#[tokio::test]
async fn test_sign_and_complete_claim_signing_failures(
    signing_error: SigningError,
    expected_msg: &str,
    requested_psbt: Psbt,
) {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let locked_claim = create_locked_claim(&benefactor_account, &beneficiary_account).await;
    let rpc_uris = default_electrum_rpc_uris();

    let mut signing_processor = MockSigningProcessor::new();
    signing_processor
        .expect_validate()
        .times(1)
        .return_once(move |_, _| Err(signing_error));

    // act
    let result = inheritance_service
        .sign_and_complete(SignAndCompleteInheritanceClaimInput {
            signing_processor,
            rpc_uris,
            inheritance_claim_id: locked_claim.common_fields.id,
            beneficiary_account: beneficiary_account.clone(),
            psbt: requested_psbt.clone(),
        })
        .await;

    // assert
    assert!(result.is_err());
    assert_eq!(result.unwrap_err().to_string(), expected_msg);
}
