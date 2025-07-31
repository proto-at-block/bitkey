use std::collections::HashMap;

use account::service::FetchAccountInput;
use axum::body::Body;
use bdk_utils::{
    bdk::bitcoin::{hashes::sha256, key::Secp256k1, secp256k1::Message},
    signature::sign_message,
};

use http::{Method, StatusCode};
use recovery::routes::INHERITANCE_ENABLED_FLAG_KEY;
use recovery::{
    entities::{RecoveryDestination, RecoveryStatus},
    routes::delay_notify::{
        AuthenticationKey, CompleteDelayNotifyResponse, RotateAuthenticationKeysRequest,
    },
    service::inheritance::get_inheritance_claims::GetInheritanceClaimsInput,
};
use rstest::rstest;
use serde_json::json;
use time::{Duration, OffsetDateTime};
use types::{
    account::{
        entities::{Account, Factor},
        AccountType,
    },
    recovery::inheritance::{claim::InheritanceClaim, router::BeneficiaryInheritanceClaimView},
};

use crate::{
    tests::{
        gen_services_with_overrides,
        lib::{create_new_authkeys, create_plain_keys, generate_delay_and_notify_recovery},
        recovery::inheritance::{
            inheritance_integration_tests::try_start_inheritance_claim,
            setup_benefactor_and_beneficiary_account, BenefactorBeneficiarySetup,
        },
        requests::{axum::TestClient, CognitoAuthentication},
    },
    GenServiceOverrides,
};

#[rstest]
#[case::app_factor_enabled(Factor::App, true)]
#[case::hw_factor_enabled(Factor::Hw, true)]
#[case::hw_factor_disabled(Factor::Hw, false)]
#[case::app_factor_disabled(Factor::App, false)]
#[tokio::test]
async fn test_recreate_inheritance_claim_after_completing_delay_notify(
    #[case] delay_notify_factor: Factor,
    #[case] is_inheritance_flag_enabled: bool,
) {
    // arrange
    let overrides = GenServiceOverrides::new().feature_flags(HashMap::from([(
        INHERITANCE_ENABLED_FLAG_KEY.to_string(),
        is_inheritance_flag_enabled.to_string(),
    )]));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.clone().router).await;

    let BenefactorBeneficiarySetup {
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
    let beneficiary_full_account = match &beneficiary {
        Account::Full(f) => f,
        _ => panic!("Beneficiary is not a full account"),
    };
    let BeneficiaryInheritanceClaimView::Pending(initial_claim) = try_start_inheritance_claim(
        &context,
        &client,
        beneficiary.get_id(),
        &recovery_relationship_id,
        beneficiary_full_account
            .active_auth_keys()
            .expect("Active auth keys not set for beneficiary"),
        StatusCode::OK,
    )
    .await
    .expect("Created inheritance claim")
    .claim
    else {
        panic!("Expected a pending claim");
    };
    let initial_claim_id = initial_claim.common_fields.id;

    let (app_auth_seckey, app_auth_pubkey) = create_plain_keys();
    let (hardware_auth_seckey, hardware_auth_pubkey) = create_plain_keys();
    let (_recovery_auth_seckey, recovery_auth_pubkey) = create_plain_keys();
    let recovery = generate_delay_and_notify_recovery(
        beneficiary.get_id().to_owned(),
        RecoveryDestination {
            source_auth_keys_id: beneficiary.get_common_fields().active_auth_keys_id.clone(),
            app_auth_pubkey,
            hardware_auth_pubkey,
            recovery_auth_pubkey: Some(recovery_auth_pubkey),
        },
        OffsetDateTime::now_utc() - Duration::minutes(1),
        RecoveryStatus::Pending,
        delay_notify_factor,
    );
    bootstrap
        .services
        .recovery_service
        .create(&recovery)
        .await
        .unwrap();

    let challenge = format!(
        "CompleteDelayNotify{}{}{}",
        hardware_auth_pubkey, app_auth_pubkey, recovery_auth_pubkey
    );
    let app_signature = sign_message(&Secp256k1::new(), &challenge, &app_auth_seckey);
    let hardware_signature = sign_message(&Secp256k1::new(), &challenge, &hardware_auth_seckey);

    // act
    let uri = format!(
        "/api/accounts/{}/delay-notify/complete",
        beneficiary.get_id()
    );
    let body = json!({ "challenge": challenge, "app_signature": app_signature, "hardware_signature": hardware_signature }).to_string();
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary.get_id())
        .expect("Invalid keys for account");
    let response = client
        .make_request_with_auth::<CompleteDelayNotifyResponse>(
            &uri,
            &beneficiary.get_id().to_string(),
            &Method::POST,
            Body::from(body),
            &CognitoAuthentication::Wallet {
                is_app_signed: false,
                is_hardware_signed: false,
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, StatusCode::OK);

    let claims = bootstrap
        .services
        .inheritance_service
        .get_inheritance_claims(GetInheritanceClaimsInput {
            account_id: beneficiary.get_id(),
        })
        .await
        .expect("Get inheritance claims for beneficiary");
    assert_eq!(
        claims.claims_as_beneficiary.len(),
        if is_inheritance_flag_enabled { 2 } else { 1 }
    );
    let pending_claims = claims
        .claims_as_beneficiary
        .iter()
        .filter(|c| matches!(c, InheritanceClaim::Pending(_)))
        .collect::<Vec<_>>();
    assert_eq!(pending_claims.len(), 1);
    let InheritanceClaim::Pending(claim) = pending_claims[0] else {
        panic!("Expected a pending claim");
    };
    if is_inheritance_flag_enabled {
        assert_ne!(claim.common_fields.id, initial_claim_id);
        assert_ne!(claim.common_fields.auth_keys, initial_claim.auth_keys);
        assert_eq!(
            claim.common_fields.recovery_relationship_id,
            initial_claim.common_fields.recovery_relationship_id
        );
        assert_eq!(
            claim.delay_end_time,
            initial_claim.pending_common_fields.delay_end_time
        );
    } else {
        assert_eq!(claim.common_fields.id, initial_claim_id);
        assert_eq!(claim.common_fields.auth_keys, initial_claim.auth_keys);
        assert_eq!(
            claim.common_fields.recovery_relationship_id,
            initial_claim.common_fields.recovery_relationship_id
        );
        assert_eq!(
            claim.delay_end_time,
            initial_claim.pending_common_fields.delay_end_time
        );
    }
}

#[rstest]
#[case::flag_disabled(false)]
#[case::flag_enabled(true)]
#[tokio::test]
async fn test_rotate_authentication_keys_with_inheritance_claim(
    #[case] is_inheritance_flag_enabled: bool,
) {
    // arrange
    let overrides = GenServiceOverrides::new().feature_flags(HashMap::from([(
        INHERITANCE_ENABLED_FLAG_KEY.to_string(),
        is_inheritance_flag_enabled.to_string(),
    )]));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.clone().router).await;

    let BenefactorBeneficiarySetup {
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
    let beneficiary_full_account = match &beneficiary {
        Account::Full(f) => f,
        _ => panic!("Beneficiary is not a full account"),
    };
    let BeneficiaryInheritanceClaimView::Pending(pending_claim) = try_start_inheritance_claim(
        &context,
        &client,
        beneficiary.get_id(),
        &recovery_relationship_id,
        beneficiary_full_account
            .active_auth_keys()
            .expect("Active auth keys not set for beneficiary"),
        StatusCode::OK,
    )
    .await
    .expect("Created inheritance claim")
    .claim
    else {
        panic!("Expected a pending claim");
    };
    let initial_claim_id = pending_claim.common_fields.id;

    let beneficiary_account_id = beneficiary.get_id();
    let active_auth_key_id = &beneficiary_full_account.common_fields.active_auth_keys_id;
    let new_auth_keys = create_new_authkeys(&mut context);

    let secp = Secp256k1::new();
    let (new_auth_app_seckey, new_auth_app_pubkey) =
        (new_auth_keys.app.secret_key, new_auth_keys.app.public_key);
    let app_signature = {
        let message = Message::from_hashed_data::<sha256::Hash>(
            beneficiary_account_id.to_string().as_bytes(),
        );
        secp.sign_ecdsa(&message, &new_auth_app_seckey).to_string()
    };
    let keys = context
        .get_authentication_keys_for_account_id(beneficiary_account_id)
        .expect("Keys not found for account");
    let (existing_auth_hardware_seckey, existing_auth_hardware_pubkey) =
        (keys.hw.secret_key, keys.hw.public_key);
    let hardware_signature = {
        let message = Message::from_hashed_data::<sha256::Hash>(
            beneficiary_account_id.to_string().as_bytes(),
        );
        secp.sign_ecdsa(&message, &existing_auth_hardware_seckey)
            .to_string()
    };
    let (new_auth_recovery_seckey, new_auth_recovery_pubkey) = (
        new_auth_keys.recovery.secret_key,
        new_auth_keys.recovery.public_key,
    );
    let recovery_signature = {
        let message = Message::from_hashed_data::<sha256::Hash>(
            beneficiary_account_id.to_string().as_bytes(),
        );
        secp.sign_ecdsa(&message, &new_auth_recovery_seckey)
            .to_string()
    };

    //act
    let response = client
        .rotate_authentication_keys(
            &mut context,
            &beneficiary_account_id.to_string(),
            &RotateAuthenticationKeysRequest {
                application: AuthenticationKey {
                    key: new_auth_app_pubkey,
                    signature: app_signature,
                },
                hardware: AuthenticationKey {
                    key: existing_auth_hardware_pubkey,
                    signature: hardware_signature,
                },
                recovery: Some(AuthenticationKey {
                    key: new_auth_recovery_pubkey,
                    signature: recovery_signature,
                }),
            },
            &keys,
        )
        .await;

    // assert
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let beneficiary_full_account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: beneficiary.get_id(),
        })
        .await
        .expect("Fetch beneficiary full account");
    assert_ne!(
        beneficiary_full_account.common_fields.active_auth_keys_id,
        *active_auth_key_id
    );
    let auth = beneficiary_full_account
        .active_auth_keys()
        .expect("Auth keys should be present");
    assert_eq!(auth.app_pubkey, new_auth_app_pubkey);
    assert_eq!(auth.hardware_pubkey, existing_auth_hardware_pubkey);
    assert_eq!(auth.recovery_pubkey, Some(new_auth_recovery_pubkey));
    let claims = bootstrap
        .services
        .inheritance_service
        .get_inheritance_claims(GetInheritanceClaimsInput {
            account_id: beneficiary.get_id(),
        })
        .await
        .expect("Get inheritance claims for beneficiary");
    assert_eq!(
        claims.claims_as_beneficiary.len(),
        if is_inheritance_flag_enabled { 2 } else { 1 }
    );
    let pending_claims = claims
        .claims_as_beneficiary
        .iter()
        .filter(|c| matches!(c, InheritanceClaim::Pending(_)))
        .collect::<Vec<_>>();
    assert_eq!(pending_claims.len(), 1);
    let InheritanceClaim::Pending(claim) = pending_claims[0] else {
        panic!("Expected a pending claim");
    };
    if is_inheritance_flag_enabled {
        assert_ne!(claim.common_fields.id, initial_claim_id);
        assert_ne!(claim.common_fields.auth_keys, pending_claim.auth_keys);
        assert_eq!(
            claim.common_fields.recovery_relationship_id,
            pending_claim.common_fields.recovery_relationship_id
        );
        assert_eq!(
            claim.delay_end_time,
            pending_claim.pending_common_fields.delay_end_time
        );
    } else {
        assert_eq!(claim.common_fields.id, initial_claim_id);
        assert_eq!(claim.common_fields.auth_keys, pending_claim.auth_keys);
        assert_eq!(
            claim.common_fields.recovery_relationship_id,
            pending_claim.common_fields.recovery_relationship_id
        );
        assert_eq!(
            claim.delay_end_time,
            pending_claim.pending_common_fields.delay_end_time
        );
    }
}
