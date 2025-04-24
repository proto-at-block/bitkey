use std::collections::HashMap;
use std::collections::HashSet;
use std::str::FromStr;
use std::sync::Arc;

use axum::body::Body;
use bdk_utils::bdk::bitcoin::Txid;
use bdk_utils::{
    bdk::{
        bitcoin::{
            key::Secp256k1,
            psbt::{PartiallySignedTransaction, Psbt},
        },
        database::AnyDatabase,
        wallet::{AddressIndex, AddressInfo},
        FeeRate, SignOptions, Wallet,
    },
    error::BdkUtilError,
    signature::sign_message,
    ElectrumRpcUris, TransactionBroadcasterTrait,
};
use http::{HeaderMap, Method, StatusCode};
use mockall::mock;
use notification::NotificationPayloadType;
use promotion_code::{entities::CodeKey, routes::CodeRedemptionWebhookResponse};
use rand::thread_rng;
use recovery::routes::{
    inheritance::{
        CancelInheritanceClaimRequest, CancelInheritanceClaimResponse,
        CompleteInheritanceClaimResponse, CreateInheritanceClaimRequest,
        CreateInheritanceClaimResponse, InheritancePackage, LockInheritanceClaimResponse,
        UploadInheritancePackagesRequest, UploadInheritancePackagesResponse,
    },
    relationship::{CreateRelationshipRequest, GetPromotionCodeForInviteCodeResponse},
    INHERITANCE_ENABLED_FLAG_KEY,
};
use rstest::rstest;
use serde_json::{json, Value};
use time::{Duration, OffsetDateTime};
use tokio::join;
use types::{
    account::{
        bitcoin::Network, entities::Account, identifiers::AccountId, keys::FullAccountAuthKeys,
        AccountType,
    },
    recovery::{
        inheritance::{
            claim::{
                InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimCompleted,
                InheritanceClaimId, InheritanceClaimPending, InheritanceCompletionMethod,
            },
            router::{
                BenefactorInheritanceClaimView, BeneficiaryInheritanceClaimView,
                BeneficiaryInheritanceClaimViewPending,
            },
        },
        social::relationship::RecoveryRelationshipId,
        trusted_contacts::TrustedContactRole,
    },
};

use crate::tests::{
    gen_services, gen_services_with_overrides,
    lib::{create_full_account, create_new_authkeys},
    recovery::{
        inheritance::setup_benefactor_and_beneficiary_account,
        shared::{
            assert_notifications, try_accept_recovery_relationship_invitation,
            try_create_relationship, try_endorse_recovery_relationship, CodeOverride,
        },
    },
    requests::{axum::TestClient, CognitoAuthentication},
    TestContext,
};
use crate::{Bootstrap, GenServiceOverrides};

use super::BenefactorBeneficiarySetup;

enum InheritanceClaimActor {
    Benefactor,
    Beneficiary,
    External,
}

pub(super) async fn try_start_inheritance_claim(
    context: &TestContext,
    client: &TestClient,
    beneficiary_account_id: &AccountId,
    recovery_relationship_id: &RecoveryRelationshipId,
    auth_keys: &FullAccountAuthKeys,
    expected_status_code: StatusCode,
) -> Option<CreateInheritanceClaimResponse> {
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary_account_id)
        .expect("Invalid keys for account");
    let create_response = client
        .start_inheritance_claim(
            &beneficiary_account_id.to_string(),
            &CreateInheritanceClaimRequest {
                recovery_relationship_id: recovery_relationship_id.to_owned(),
                auth: InheritanceClaimAuthKeys::FullAccount(auth_keys.to_owned()),
            },
            &keys,
        )
        .await;

    assert_eq!(
        create_response.status_code, expected_status_code,
        "{:?}",
        create_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        let create_body = create_response.body.unwrap();
        return Some(create_body);
    }

    None
}

pub(super) async fn try_cancel_inheritance_claim(
    context: &TestContext,
    client: &TestClient,
    account_id: &AccountId,
    auth_account_id: &AccountId,
    inheritance_claim_id: &InheritanceClaimId,
    expected_status_code: StatusCode,
) -> Option<CancelInheritanceClaimResponse> {
    let keys = context
        .get_authentication_keys_for_account_id(auth_account_id)
        .expect("Invalid keys for account");
    let cancel_response = client
        .cancel_inheritance_claim(
            &account_id.to_string(),
            &inheritance_claim_id.to_string(),
            &CancelInheritanceClaimRequest {},
            &auth_account_id.to_string(),
            &keys,
        )
        .await;

    assert_eq!(
        cancel_response.status_code, expected_status_code,
        "{:?}",
        cancel_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        let cancel_body = cancel_response.body.unwrap();
        return Some(cancel_body);
    }

    None
}

#[rstest]
#[case::create_with_full_account(AccountType::Full, false, StatusCode::OK)]
#[case::create_with_lite_account(AccountType::Lite, false, StatusCode::FORBIDDEN)]
#[case::create_with_existing_claim(AccountType::Full, true, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn start_inheritance_claim_test(
    #[case] beneficiary_account_type: AccountType,
    #[case] create_existing_inheritance_claim: bool,
    #[case] expected_status_code: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.clone().router).await;

    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        ..
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        beneficiary_account_type,
    )
    .await;

    let rotate_to_inheritance_keys = match &beneficiary {
        Account::Full(f) => f.active_auth_keys().unwrap().to_owned(),
        Account::Lite(_) => {
            let k = create_new_authkeys(&mut context);
            FullAccountAuthKeys {
                app_pubkey: k.app.public_key,
                hardware_pubkey: k.hw.public_key,
                recovery_pubkey: Some(k.recovery.public_key),
            }
        }
        Account::Software(_) => unimplemented!(),
    };
    if create_existing_inheritance_claim {
        try_start_inheritance_claim(
            &context,
            &client,
            beneficiary.get_id(),
            &recovery_relationship_id,
            &rotate_to_inheritance_keys,
            StatusCode::OK,
        )
        .await;
    }

    // act
    try_start_inheritance_claim(
        &context,
        &client,
        beneficiary.get_id(),
        &recovery_relationship_id,
        &rotate_to_inheritance_keys,
        expected_status_code,
    )
    .await;

    // assert
    if expected_status_code.is_success() {
        let benefactor_claims_resp = client
            .get_inheritance_claims(
                &benefactor.get_id().to_string(),
                &context
                    .get_authentication_keys_for_account_id(benefactor.get_id())
                    .expect("Invalid keys for account"),
            )
            .await;

        assert_eq!(benefactor_claims_resp.status_code, StatusCode::OK);
        let benefactor_claims = benefactor_claims_resp.body.unwrap().claims_as_benefactor;
        assert_eq!(benefactor_claims.len(), 1);

        let beneficiary_claims_resp = client
            .get_inheritance_claims(
                &beneficiary.get_id().to_string(),
                &context
                    .get_authentication_keys_for_account_id(beneficiary.get_id())
                    .expect("Invalid keys for account"),
            )
            .await;

        assert_eq!(beneficiary_claims_resp.status_code, StatusCode::OK);
        let beneficiary_claims = beneficiary_claims_resp.body.unwrap().claims_as_beneficiary;
        assert_eq!(beneficiary_claims.len(), 1);

        // Check whether the notifications were created
        let (
            benefactor_expected_scheduled_notifications_types,
            beneficiary_expected_scheduled_notifications_types,
        ) = (
            vec![
                // The initiated schedule gets split into 1 that gets sent immediately
                // and a schedule that starts in 7 days
                NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending,
                NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                NotificationPayloadType::InheritanceClaimPeriodInitiated,
                NotificationPayloadType::InheritanceClaimPeriodAlmostOver,
                NotificationPayloadType::InheritanceClaimPeriodCompleted,
            ],
            vec![
                NotificationPayloadType::InheritanceClaimPeriodAlmostOver,
                NotificationPayloadType::InheritanceClaimPeriodCompleted,
            ],
        );

        let (
            benefactor_expected_customer_notifications_types,
            beneficiary_expected_customer_notifications_types,
        ) = (
            vec![
                NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                NotificationPayloadType::InheritanceClaimPeriodInitiated,
            ],
            vec![
                NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                NotificationPayloadType::InheritanceClaimPeriodInitiated,
            ],
        );

        join!(
            assert_notifications(
                &bootstrap,
                benefactor.get_id(),
                benefactor_expected_customer_notifications_types,
                benefactor_expected_scheduled_notifications_types
            ),
            assert_notifications(
                &bootstrap,
                beneficiary.get_id(),
                beneficiary_expected_customer_notifications_types,
                beneficiary_expected_scheduled_notifications_types
            ),
        );
    }
}

pub(super) async fn try_package_upload(
    context: &TestContext,
    client: &TestClient,
    beneficiary_account_id: &AccountId,
    recovery_relationships: &[RecoveryRelationshipId],
    expected_status_code: StatusCode,
) -> Option<UploadInheritancePackagesResponse> {
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary_account_id)
        .expect("Invalid keys for account");

    let create_response = client
        .package_upload(
            &beneficiary_account_id.to_string(),
            &UploadInheritancePackagesRequest {
                packages: recovery_relationships
                    .iter()
                    .map(|r| InheritancePackage {
                        recovery_relationship_id: r.clone(),
                        sealed_dek: "RANDOM_SEALED_DEK".to_string(),
                        sealed_mobile_key: "RANDOM_SEALED_MOBILE_KEY".to_string(),
                    })
                    .collect(),
            },
            &keys,
        )
        .await;

    assert_eq!(
        create_response.status_code, expected_status_code,
        "{:?}",
        create_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        let create_body = create_response.body.unwrap();
        return Some(create_body);
    }

    None
}

#[rstest]
#[case::upload_package(false, StatusCode::OK)]
#[case::relationship_doesnt_exist(true, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn package_upload_test(
    #[case] invalid_relationships: bool,
    #[case] expected_status_code: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let benefactor_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;

    let mut relationship_ids = Vec::new();
    if invalid_relationships {
        relationship_ids.extend(vec![RecoveryRelationshipId::from_str(
            "urn:wallet-recovery-relationship:01J7P50S2SPMZJPQTERANGC0FE",
        )
        .unwrap()]);
    } else {
        let beneficiary_account = Account::Full(
            create_full_account(
                &mut context,
                &bootstrap.services,
                Network::BitcoinSignet,
                None,
            )
            .await,
        );

        let create_body = try_create_relationship(
            &context,
            &client,
            &benefactor_account.id,
            &TrustedContactRole::Beneficiary,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            StatusCode::OK,
            1,
            0,
        )
        .await
        .unwrap();

        let recovery_relationship_id = create_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id
            .clone();

        try_accept_recovery_relationship_invitation(
            &context,
            &client,
            &benefactor_account.id,
            beneficiary_account.get_id(),
            &TrustedContactRole::Beneficiary,
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            StatusCode::OK,
            1,
        )
        .await;

        try_endorse_recovery_relationship(
            &context,
            &client,
            &benefactor_account.id,
            &TrustedContactRole::Beneficiary,
            &recovery_relationship_id,
            "RANDOM_CERT",
            StatusCode::OK,
        )
        .await;

        relationship_ids.push(recovery_relationship_id.clone());
    }

    // act
    try_package_upload(
        &context,
        &client,
        &benefactor_account.id,
        &relationship_ids,
        expected_status_code,
    )
    .await;

    // assert
    if expected_status_code.is_success() {
        let packages = bootstrap
            .services
            .inheritance_service
            .get_packages_by_relationship_id(&relationship_ids)
            .await
            .unwrap();
        assert_eq!(packages.len(), relationship_ids.len());
    }
}

#[rstest]
#[case::cancel_with_benefactor(InheritanceClaimActor::Benefactor, false, StatusCode::OK)]
#[case::cancel_with_beneficiary(InheritanceClaimActor::Beneficiary, false, StatusCode::OK)]
#[case::cancel_with_random_account(
    InheritanceClaimActor::External,
    false,
    StatusCode::UNAUTHORIZED
)]
#[case::cancel_with_benefactor_and_canceled_claim(
    InheritanceClaimActor::Benefactor,
    true,
    StatusCode::OK
)]
#[case::cancel_with_beneficiary_and_canceled_claim(
    InheritanceClaimActor::Beneficiary,
    true,
    StatusCode::OK
)]
#[case::cancel_with_external_and_canceled_claim(
    InheritanceClaimActor::External,
    true,
    StatusCode::UNAUTHORIZED
)]
#[tokio::test]
async fn cancel_inheritance_claim(
    #[case] cancel_role: InheritanceClaimActor,
    #[case] precancel_claim: bool,
    #[case] expected_status_code: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.clone().router).await;

    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        ..
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;
    let external_account = Account::Full(
        create_full_account(
            &mut context,
            &bootstrap.services,
            Network::BitcoinSignet,
            None,
        )
        .await,
    );
    let rotate_to_inheritance_keys = if let Account::Full(f) = &beneficiary {
        f.active_auth_keys().unwrap().to_owned()
    } else {
        panic!("This test only supports Full accounts")
    };
    let BeneficiaryInheritanceClaimView::Pending(pending_claim) = try_start_inheritance_claim(
        &context,
        &client,
        beneficiary.get_id(),
        &recovery_relationship_id,
        &rotate_to_inheritance_keys,
        StatusCode::OK,
    )
    .await
    .expect("Created inheritance claim")
    .claim
    else {
        panic!("Expected a pending claim");
    };
    let pending_claim_id = pending_claim.common_fields.id;
    let account_id = match cancel_role {
        InheritanceClaimActor::Benefactor => benefactor.get_id(),
        InheritanceClaimActor::Beneficiary => beneficiary.get_id(),
        InheritanceClaimActor::External => external_account.get_id(),
    };
    if precancel_claim {
        try_cancel_inheritance_claim(
            &context,
            &client,
            beneficiary.get_id(),
            beneficiary.get_id(),
            &pending_claim_id,
            StatusCode::OK,
        )
        .await;
    }

    // act
    try_cancel_inheritance_claim(
        &context,
        &client,
        if matches!(cancel_role, InheritanceClaimActor::External) {
            beneficiary.get_id()
        } else {
            account_id
        },
        account_id,
        &pending_claim_id,
        expected_status_code,
    )
    .await;

    // assert
    if expected_status_code.is_success() {
        let benefactor_claims_resp = client
            .get_inheritance_claims(
                &benefactor.get_id().to_string(),
                &context
                    .get_authentication_keys_for_account_id(benefactor.get_id())
                    .expect("Invalid keys for account"),
            )
            .await;

        assert_eq!(benefactor_claims_resp.status_code, StatusCode::OK);
        let benefactor_claims = benefactor_claims_resp.body.unwrap().claims_as_benefactor;
        assert_eq!(benefactor_claims.len(), 1);
        assert!(matches!(
            benefactor_claims[0],
            BenefactorInheritanceClaimView::Canceled(_)
        ));

        let beneficiary_claims_resp = client
            .get_inheritance_claims(
                &beneficiary.get_id().to_string(),
                &context
                    .get_authentication_keys_for_account_id(beneficiary.get_id())
                    .expect("Invalid keys for account"),
            )
            .await;

        assert_eq!(beneficiary_claims_resp.status_code, StatusCode::OK);
        let beneficiary_claims = beneficiary_claims_resp.body.unwrap().claims_as_beneficiary;
        assert_eq!(beneficiary_claims.len(), 1);
        assert!(matches!(
            beneficiary_claims[0],
            BeneficiaryInheritanceClaimView::Canceled(_)
        ));

        let claims = bootstrap
            .services
            .inheritance_service
            .repository
            .fetch_claims_for_recovery_relationship_id(&recovery_relationship_id)
            .await
            .expect("no claims found");
        assert_eq!(claims.len(), 1);

        // Check whether the notifications were created
        let (
            benefactor_expected_scheduled_notifications_types,
            beneficiary_expected_scheduled_notifications_types,
        ) = (
            vec![
                // The initiated schedule gets split into 1 that gets sent immediately
                // and a schedule that starts in 7 days
                NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending,
                NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                NotificationPayloadType::InheritanceClaimPeriodInitiated,
                NotificationPayloadType::InheritanceClaimPeriodAlmostOver,
                NotificationPayloadType::InheritanceClaimPeriodCompleted,
            ],
            vec![
                NotificationPayloadType::InheritanceClaimPeriodAlmostOver,
                NotificationPayloadType::InheritanceClaimPeriodCompleted,
            ],
        );

        let (
            benefactor_expected_customer_notifications_types,
            beneficiary_expected_customer_notifications_types,
        ) = (
            vec![
                NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                NotificationPayloadType::InheritanceClaimPeriodInitiated,
                NotificationPayloadType::InheritanceClaimCanceled,
            ],
            vec![
                NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                NotificationPayloadType::InheritanceClaimPeriodInitiated,
                NotificationPayloadType::InheritanceClaimCanceled,
            ],
        );

        join!(
            assert_notifications(
                &bootstrap,
                benefactor.get_id(),
                benefactor_expected_customer_notifications_types,
                benefactor_expected_scheduled_notifications_types
            ),
            assert_notifications(
                &bootstrap,
                beneficiary.get_id(),
                beneficiary_expected_customer_notifications_types,
                beneficiary_expected_scheduled_notifications_types
            ),
        );
    }
}

#[tokio::test]
async fn test_lock_inheritance_claim_success() {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        ..
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;

    let secp = Secp256k1::new();
    let (app_auth_seckey, app_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let (hardware_auth_seckey, hardware_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let (_recovery_auth_seckey, recovery_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let auth_keys = FullAccountAuthKeys::new(
        app_auth_pubkey,
        hardware_auth_pubkey,
        Some(recovery_auth_pubkey),
    );

    let pending_claim = create_lockable_claim(
        &mut context,
        bootstrap,
        &client,
        &benefactor,
        &beneficiary,
        &recovery_relationship_id,
        &auth_keys,
    )
    .await;

    let challenge = create_challenge(auth_keys);
    let app_signature = sign_message(&secp, &challenge, &app_auth_seckey);
    let hardware_signature = sign_message(&secp, &challenge, &hardware_auth_seckey);

    // act
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{}/lock",
        beneficiary.get_id(),
        pending_claim.common_fields.id
    );
    let body = json!({
        "recovery_relationship_id": recovery_relationship_id,
        "challenge": challenge,
        "app_signature": app_signature,
        "hardware_signature": hardware_signature,
    })
    .to_string();

    let response = client
        .make_request::<CreateRelationshipRequest>(&uri, &Method::PUT, Body::from(body))
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::OK);

    let Account::Full(benefactor_account) = benefactor else {
        panic!("Expected a full account")
    };

    let expected_descriptor_keyset = benefactor_account
        .active_descriptor_keyset()
        .unwrap()
        .into_multisig_descriptor()
        .unwrap();
    let expected_claim_id = pending_claim.common_fields.id;

    let expected_response_json = json!({
        "claim": {
            "status": "LOCKED",
            "id": expected_claim_id,
            "recovery_relationship_id": recovery_relationship_id,
            "sealed_dek": "RANDOM_SEALED_DEK",
            "sealed_mobile_key": "RANDOM_SEALED_MOBILE_KEY",
            "benefactor_descriptor_keyset": expected_descriptor_keyset,
        }
    });
    let actual_response_json: Value =
        serde_json::from_str(&response.body_string).expect("should be valid json");

    assert_eq!(actual_response_json, expected_response_json);
}

fn create_challenge(auth_keys: FullAccountAuthKeys) -> String {
    let challenge = format!(
        "LockInheritanceClaim{}{}{}",
        auth_keys.hardware_pubkey,
        auth_keys.app_pubkey,
        auth_keys
            .recovery_pubkey
            .map(|k| k.to_string())
            .unwrap_or("".to_string())
    );
    challenge
}

async fn complete_claim_delay_period(
    bootstrap: Bootstrap,
    pending_claim_view: &BeneficiaryInheritanceClaimViewPending,
) {
    let repository = bootstrap.services.inheritance_service.repository;
    let pending_claim = repository
        .fetch_inheritance_claim(&pending_claim_view.common_fields.id)
        .await
        .expect("fetch claim");
    let pending_claim_past_delay_end_time = InheritanceClaimPending {
        common_fields: pending_claim.common_fields().clone(),
        delay_end_time: OffsetDateTime::now_utc() - Duration::days(1),
    };

    repository
        .persist_inheritance_claim(&InheritanceClaim::Pending(
            pending_claim_past_delay_end_time,
        ))
        .await
        .expect("persist claim");
}

#[tokio::test]
async fn test_lock_inheritance_claim_lite_account_forbidden() {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let BenefactorBeneficiarySetup {
        beneficiary,
        recovery_relationship_id,
        ..
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Lite,
    )
    .await;

    // act
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{}/lock",
        beneficiary.get_id(),
        InheritanceClaimId::gen().expect("fake claim id")
    );
    let body = json!({
        "recovery_relationship_id": recovery_relationship_id,
        "challenge": "fake_challenge",
        "app_signature": "fake_app_signature",
        "hardware_signature": "fake_hardware_signature",
    })
    .to_string();

    let response = client
        .make_request::<CreateRelationshipRequest>(&uri, &Method::PUT, Body::from(body))
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::FORBIDDEN);
}

mock! {
    TransactionBroadcaster { }
    impl TransactionBroadcasterTrait for TransactionBroadcaster {
        fn broadcast(
            &self,
            wallet: Wallet<AnyDatabase>,
            transaction: &mut Psbt,
            rpc_uris: &ElectrumRpcUris
        ) -> Result<(), BdkUtilError>;
    }
}
#[tokio::test]
async fn test_complete_inheritance_claim_lite_account_forbidden() {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let BenefactorBeneficiarySetup { beneficiary, .. } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Lite,
    )
    .await;
    let keys = create_new_authkeys(&mut context);

    // act
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{}/complete",
        beneficiary.get_id(),
        InheritanceClaimId::gen().expect("fake claim id")
    );
    let body = json!({ "psbt": "cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA" }).to_string();

    let response = client
        .make_request_with_auth::<CompleteInheritanceClaimResponse>(
            &uri,
            beneficiary.get_id().to_string().as_str(),
            &Method::PUT,
            Body::from(body),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::FORBIDDEN);
}
#[tokio::test]
async fn test_complete_inheritance_claim_success() {
    // arrange
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(1)
        .returning(|_, _, _| Ok(()));
    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        benefactor_wallet,
        beneficiary_wallet,
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;
    let claim_id = create_locked_claim(
        &mut context,
        bootstrap,
        &client,
        &benefactor,
        &beneficiary,
        &recovery_relationship_id,
    )
    .await;
    let (psbt, _) = build_sweep_psbt(&benefactor_wallet, beneficiary_wallet.as_ref(), 5.0, true);

    // act
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{claim_id}/complete",
        beneficiary.get_id()
    );
    let body = json!({ "psbt": psbt.to_string() }).to_string();
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary.get_id())
        .expect("Invalid keys for account");
    let response = client
        .make_request_with_auth::<CompleteInheritanceClaimResponse>(
            &uri,
            &beneficiary.get_id().to_string(),
            &Method::PUT,
            Body::from(body),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::OK);

    let expected_response_json_without_psbt = json!({
        "claim": {
            "status": "COMPLETED",
            "id": claim_id,
            "recovery_relationship_id": recovery_relationship_id,
        }
    });
    let actual_response_json: Value =
        serde_json::from_str(&response.body_string).expect("should be valid json");
    let mut actual_response_json_without_psbt = actual_response_json.clone();
    actual_response_json_without_psbt["claim"]
        .as_object_mut()
        .unwrap()
        .remove("psbt_txid");

    let actual_psbt_txid = actual_response_json["claim"]["psbt_txid"]
        .as_str()
        .expect("psbt_txid field missing");
    let actual_psbt_txid = Txid::from_str(actual_psbt_txid).expect("psbt_txid should be valid");

    assert_eq!(
        actual_response_json_without_psbt,
        expected_response_json_without_psbt
    );

    assert_eq!(actual_psbt_txid, psbt.unsigned_tx.txid());
}

#[tokio::test]
async fn test_complete_without_psbt_inheritance_claim_success() {
    // arrange
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .never()
        .returning(|_, _, _| Ok(()));
    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        ..
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;
    let claim_id = create_locked_claim(
        &mut context,
        bootstrap,
        &client,
        &benefactor,
        &beneficiary,
        &recovery_relationship_id,
    )
    .await;

    // act
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{claim_id}/complete-without-psbt",
        beneficiary.get_id()
    );
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary.get_id())
        .expect("Invalid keys for account");
    let response = client
        .make_request_with_auth::<CompleteInheritanceClaimResponse>(
            &uri,
            &beneficiary.get_id().to_string(),
            &Method::PUT,
            Body::empty(),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::OK);

    let expected_response_json = json!({
        "claim": {
            "status": "COMPLETED",
            "id": claim_id,
            "recovery_relationship_id": recovery_relationship_id,
            "psbt_txid": null,
        }
    });
    let actual_response_json: Value =
        serde_json::from_str(&response.body_string).expect("should be valid json");
    assert_eq!(actual_response_json, expected_response_json);
}

#[tokio::test]
async fn test_complete_inheritance_claim_rbf_success() {
    // arrange
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(2)
        .returning(|_, _, _| Ok(()));
    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        benefactor_wallet,
        beneficiary_wallet,
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;
    let claim_id = create_locked_claim(
        &mut context,
        bootstrap,
        &client,
        &benefactor,
        &beneficiary,
        &recovery_relationship_id,
    )
    .await;
    let (psbt, _) = build_sweep_psbt(&benefactor_wallet, beneficiary_wallet.as_ref(), 5.0, true);
    let auth = &CognitoAuthentication::Wallet {
        is_app_signed: true,
        is_hardware_signed: true,
    };

    // complete claim
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{claim_id}/complete",
        beneficiary.get_id()
    );
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary.get_id())
        .expect("Invalid keys for account");
    let _ = client
        .make_request_with_auth::<CompleteInheritanceClaimResponse>(
            &uri,
            &beneficiary.get_id().to_string(),
            &Method::PUT,
            Body::from(json!({ "psbt": psbt.to_string() }).to_string()),
            auth,
            &keys,
        )
        .await;

    let (rbf_psbt, _) =
        build_sweep_psbt(&benefactor_wallet, beneficiary_wallet.as_ref(), 7.0, true);

    // act
    let rbf_response = client
        .make_request_with_auth::<CompleteInheritanceClaimResponse>(
            &uri,
            &beneficiary.get_id().to_string(),
            &Method::PUT,
            Body::from(json!({ "psbt": rbf_psbt.to_string() }).to_string()),
            auth,
            &keys,
        )
        .await;

    // assert
    assert_eq!(rbf_response.status_code, StatusCode::OK);

    let actual_response_json: Value =
        serde_json::from_str(&rbf_response.body_string).expect("should be valid json");

    let actual_psbt_txid = actual_response_json["claim"]["psbt_txid"]
        .as_str()
        .expect("psbt_txid field missing");
    let actual_psbt_txid = Txid::from_str(actual_psbt_txid).expect("psbt_txid should be valid");

    assert_eq!(actual_psbt_txid, rbf_psbt.unsigned_tx.txid());
}

#[tokio::test]
async fn test_complete_inheritance_claim_sanctions_failure() {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        benefactor_wallet,
        beneficiary_wallet,
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;
    let claim_id = create_locked_claim(
        &mut context,
        bootstrap,
        &client,
        &benefactor,
        &beneficiary,
        &recovery_relationship_id,
    )
    .await;
    let (psbt, beneficiary_address) =
        build_sweep_psbt(&benefactor_wallet, beneficiary_wallet.as_ref(), 5.0, true);

    // add beneficiary to blocked list
    let blocked_hash_set = HashSet::from([beneficiary_address.to_string()]);
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock.expect_broadcast().times(0);
    let overrides = GenServiceOverrides::new()
        .broadcaster(Arc::new(broadcaster_mock))
        .blocked_addresses(blocked_hash_set);
    let (_, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    // act
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{claim_id}/complete",
        beneficiary.get_id()
    );
    let body = json!({ "psbt": psbt.to_string() }).to_string();
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary.get_id())
        .expect("Invalid keys for account");
    let response = client
        .make_request_with_auth::<CompleteInheritanceClaimResponse>(
            &uri,
            &beneficiary.get_id().to_string(),
            &Method::PUT,
            Body::from(body),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(
        response.status_code,
        StatusCode::UNAVAILABLE_FOR_LEGAL_REASONS
    );
}

#[tokio::test]
async fn test_complete_inheritance_claim_unsigned_psbt_fails() {
    // arrange
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock.expect_broadcast().times(0);
    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        benefactor_wallet,
        beneficiary_wallet,
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;
    let claim_id = create_locked_claim(
        &mut context,
        bootstrap,
        &client,
        &benefactor,
        &beneficiary,
        &recovery_relationship_id,
    )
    .await;
    let (psbt, _) = build_sweep_psbt(&benefactor_wallet, beneficiary_wallet.as_ref(), 5.0, false);

    // act
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{claim_id}/complete",
        beneficiary.get_id()
    );
    let body = json!({ "psbt": psbt.to_string() }).to_string();
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary.get_id())
        .expect("Invalid keys for account");
    let response = client
        .make_request_with_auth::<CompleteInheritanceClaimResponse>(
            &uri,
            &beneficiary.get_id().to_string(),
            &Method::PUT,
            Body::from(body),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::INTERNAL_SERVER_ERROR);
    assert!(response
        .body_string
        .contains("Input does not only have one signature"));
}

async fn create_locked_claim(
    context: &mut TestContext,
    bootstrap: Bootstrap,
    client: &TestClient,
    benefactor: &Account,
    beneficiary: &Account,
    recovery_relationship_id: &RecoveryRelationshipId,
) -> InheritanceClaimId {
    let secp = Secp256k1::new();
    let (app_auth_seckey, app_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let (hardware_auth_seckey, hardware_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let (_recovery_auth_seckey, recovery_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let auth_keys = FullAccountAuthKeys::new(
        app_auth_pubkey,
        hardware_auth_pubkey,
        Some(recovery_auth_pubkey),
    );

    let pending_claim = create_lockable_claim(
        context,
        bootstrap,
        client,
        benefactor,
        beneficiary,
        recovery_relationship_id,
        &auth_keys,
    )
    .await;

    let challenge = create_challenge(auth_keys);
    let app_signature = sign_message(&secp, &challenge, &app_auth_seckey);
    let hardware_signature = sign_message(&secp, &challenge, &hardware_auth_seckey);

    // lock claim
    let uri = format!(
        "/api/accounts/{}/recovery/inheritance/claims/{}/lock",
        beneficiary.get_id(),
        pending_claim.common_fields.id
    );
    let body = json!({
        "recovery_relationship_id": recovery_relationship_id,
        "challenge": challenge,
        "app_signature": app_signature,
        "hardware_signature": hardware_signature,
    })
    .to_string();
    let _ = client
        .make_request::<LockInheritanceClaimResponse>(&uri, &Method::PUT, Body::from(body))
        .await;
    pending_claim.common_fields.id
}

fn build_sweep_psbt(
    benefactor_wallet: &Wallet<AnyDatabase>,
    beneficiary_wallet: Option<&Wallet<AnyDatabase>>,
    fee: f32,
    sign: bool,
) -> (PartiallySignedTransaction, AddressInfo) {
    let beneficiary_address = beneficiary_wallet
        .expect("Beneficiary wallet not created")
        .get_address(AddressIndex::New)
        .expect("Could not get address");

    let mut builder = benefactor_wallet.build_tx();
    builder
        .drain_wallet()
        .drain_to(beneficiary_address.script_pubkey())
        .fee_rate(FeeRate::from_sat_per_vb(fee));

    let (mut psbt, _) = builder.finish().expect("Could not build psbt");

    if sign {
        let _ = benefactor_wallet.sign(
            &mut psbt,
            SignOptions {
                remove_partial_sigs: false,
                ..SignOptions::default()
            },
        );
    }
    (psbt, beneficiary_address)
}

async fn create_lockable_claim(
    context: &mut TestContext,
    bootstrap: Bootstrap,
    client: &TestClient,
    benefactor: &Account,
    beneficiary: &Account,
    recovery_relationship_id: &RecoveryRelationshipId,
    auth_keys: &FullAccountAuthKeys,
) -> BeneficiaryInheritanceClaimViewPending {
    let BeneficiaryInheritanceClaimView::Pending(pending_claim) = try_start_inheritance_claim(
        context,
        client,
        beneficiary.get_id(),
        recovery_relationship_id,
        auth_keys,
        StatusCode::OK,
    )
    .await
    .expect("Created inheritance claim")
    .claim
    else {
        panic!("Expected a pending claim");
    };

    complete_claim_delay_period(bootstrap, &pending_claim).await;

    try_package_upload(
        context,
        client,
        benefactor.get_id(),
        &[recovery_relationship_id.clone()],
        StatusCode::OK,
    )
    .await;
    pending_claim
}

#[rstest::rstest]
#[case::incomplete_benefactor(true, InheritanceClaimActor::Benefactor, StatusCode::BAD_REQUEST)]
#[case::incomplete_beneficiary(true, InheritanceClaimActor::Beneficiary, StatusCode::BAD_REQUEST)]
#[case::complete_benefactor(false, InheritanceClaimActor::Benefactor, StatusCode::OK)]
#[case::complete_beneficiary(false, InheritanceClaimActor::Beneficiary, StatusCode::OK)]
#[tokio::test]
async fn test_delete_relationship_with_claim(
    #[case] is_claim_incomplete: bool,
    #[case] actor: InheritanceClaimActor,
    #[case] expected_status_code: StatusCode,
) {
    // arrange
    let overrides = GenServiceOverrides::new().feature_flags(HashMap::from([(
        INHERITANCE_ENABLED_FLAG_KEY.to_string(),
        true.to_string(),
    )]));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let inheritance_repository = &bootstrap.services.inheritance_repository.clone();
    let BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        benefactor_wallet,
        beneficiary_wallet,
    } = setup_benefactor_and_beneficiary_account(
        &mut context,
        &bootstrap,
        &client,
        AccountType::Full,
    )
    .await;

    let claim_id = create_locked_claim(
        &mut context,
        bootstrap,
        &client,
        &benefactor,
        &beneficiary,
        &recovery_relationship_id,
    )
    .await;
    if !is_claim_incomplete {
        let claim = inheritance_repository
            .fetch_inheritance_claim(&claim_id)
            .await
            .expect("Claim not found");
        let (psbt, _) =
            build_sweep_psbt(&benefactor_wallet, beneficiary_wallet.as_ref(), 5.0, true);
        inheritance_repository
            .persist_inheritance_claim(&InheritanceClaim::Completed(InheritanceClaimCompleted {
                common_fields: claim.common_fields().to_owned(),
                completion_method: InheritanceCompletionMethod::WithPsbt {
                    txid: psbt.unsigned_tx.txid(),
                },
                completed_at: OffsetDateTime::now_utc(),
            }))
            .await
            .expect("Failed to persist claim");
    }

    let (deleter_account, auth_key_type, auth_keys) = match actor {
        InheritanceClaimActor::Benefactor => (
            &benefactor,
            CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            context
                .get_authentication_keys_for_account_id(benefactor.get_id())
                .expect("Invalid keys for benefactor"),
        ),
        InheritanceClaimActor::Beneficiary => (
            &beneficiary,
            CognitoAuthentication::Recovery,
            context
                .get_authentication_keys_for_account_id(beneficiary.get_id())
                .expect("Invalid keys for beneficiary"),
        ),
        InheritanceClaimActor::External => unreachable!(),
    };

    // act
    let delete_response = client
        .delete_recovery_relationship(
            &deleter_account.get_id().to_string(),
            &recovery_relationship_id.to_string(),
            &auth_key_type,
            &auth_keys,
        )
        .await;

    // assert
    assert_eq!(delete_response.status_code, expected_status_code);
}

#[rstest]
#[case::for_beneficiary(
    TrustedContactRole::Beneficiary,
    InheritanceClaimActor::Beneficiary,
    true
)]
#[case::for_benefactor(
    TrustedContactRole::Beneficiary,
    InheritanceClaimActor::Beneficiary,
    true
)]
#[case::not_created_for_beneficiary(
    TrustedContactRole::SocialRecoveryContact,
    InheritanceClaimActor::Beneficiary,
    false
)]
#[case::not_created_for_benefactor(
    TrustedContactRole::SocialRecoveryContact,
    InheritanceClaimActor::Benefactor,
    false
)]
#[tokio::test]
async fn generate_promotional_codes_upon_creating_recovery_relationship(
    #[case] role: TrustedContactRole,
    #[case] actor: InheritanceClaimActor,
    #[case] should_have_promotion_code: bool,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let benefactor_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;

    let beneficiary_account = Account::Full(
        create_full_account(
            &mut context,
            &bootstrap.services,
            Network::BitcoinSignet,
            None,
        )
        .await,
    );
    let create_body = try_create_relationship(
        &context,
        &client,
        &benefactor_account.id,
        &role,
        &CognitoAuthentication::Wallet {
            is_app_signed: true,
            is_hardware_signed: true,
        },
        StatusCode::OK,
        1,
        0,
    )
    .await
    .unwrap();

    // act
    let account_id = match actor {
        InheritanceClaimActor::Beneficiary => beneficiary_account.get_id().to_owned(),
        InheritanceClaimActor::Benefactor => benefactor_account.id,
        InheritanceClaimActor::External => panic!("External actor not supported"),
    };
    let uri = format!(
        "/api/accounts/{}/recovery/relationship-invitations/{}/promotion-code",
        account_id, create_body.invitation.code,
    );
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");
    let response = client
        .make_request_with_auth::<GetPromotionCodeForInviteCodeResponse>(
            &uri,
            &account_id.to_string(),
            &Method::GET,
            Body::empty(),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::OK);
    assert_eq!(
        response
            .body
            .expect("Response body is empty")
            .code
            .is_some(),
        should_have_promotion_code
    );
}

#[rstest]
#[case::with_valid_api_key(Some("FAKE_API_KEY"), StatusCode::OK)]
#[case::with_invalid_api_key(Some("abc"), StatusCode::UNAUTHORIZED)]
#[case::without_api_key(None, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn mark_promotion_code_as_redeemed(
    #[case] api_key: Option<&str>,
    #[case] expected_status_code: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let benefactor_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    try_create_relationship(
        &context,
        &client,
        &benefactor_account.id,
        &TrustedContactRole::Beneficiary,
        &CognitoAuthentication::Wallet {
            is_app_signed: true,
            is_hardware_signed: true,
        },
        StatusCode::OK,
        1,
        0,
    )
    .await
    .unwrap();

    let code_key = CodeKey::inheritance_benefactor(benefactor_account.id);
    let promotion_code = bootstrap
        .services
        .promotion_code_service
        .get(&code_key)
        .await
        .expect("Failed to get promotion code")
        .expect("Promotion code not found")
        .code;

    // act
    let request = json!({ "code": promotion_code }).to_string();
    let mut headers = HeaderMap::new();
    if let Some(api_key) = api_key {
        headers.insert(
            "Authorization",
            format!("Bearer {}", api_key).parse().unwrap(),
        );
    }
    let response = client
        .make_request_with_headers::<CodeRedemptionWebhookResponse>(
            "/api/promotion-code-redemptions",
            &Method::POST,
            headers,
            Body::from(request),
        )
        .await;

    let code = bootstrap
        .services
        .promotion_code_service
        .get(&code_key)
        .await
        .expect("Failed to get promotion code")
        .expect("Promotion code not found");

    // assert
    assert_eq!(response.status_code, expected_status_code);
    assert_eq!(code.is_redeemed, expected_status_code == StatusCode::OK);
}
