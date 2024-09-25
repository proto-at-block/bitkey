use std::str::FromStr;

use http::StatusCode;
use rstest::rstest;

use account::entities::Account;
use recovery::routes::{
    CancelInheritanceClaimRequest, CancelInheritanceClaimResponse, CreateInheritanceClaimRequest,
    CreateInheritanceClaimResponse, InheritancePackage, UploadInheritancePackagesRequest,
    UploadInheritancePackagesResponse,
};
use types::{
    account::{bitcoin::Network, identifiers::AccountId, keys::FullAccountAuthKeys, AccountType},
    recovery::{
        inheritance::{
            claim::{InheritanceClaimAuthKeys, InheritanceClaimId},
            router::{BenefactorInheritanceClaimView, BeneficiaryInheritanceClaimView},
        },
        social::relationship::RecoveryRelationshipId,
        trusted_contacts::TrustedContactRole,
    },
};

use super::shared::{
    try_accept_recovery_relationship_invitation, try_create_relationship,
    try_endorse_recovery_relationship, CodeOverride,
};
use crate::{
    tests::{
        gen_services,
        lib::{create_full_account, create_lite_account, create_new_authkeys},
        requests::{axum::TestClient, CognitoAuthentication},
        TestContext,
    },
    Bootstrap,
};

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

async fn setup_benefactor_and_beneficiary_account(
    context: &mut TestContext,
    bootstrap: &Bootstrap,
    client: &TestClient,
    beneficiary_account_type: AccountType,
) -> (Account, Account, RecoveryRelationshipId) {
    let benefactor_account = Account::Full(
        create_full_account(context, &bootstrap.services, Network::BitcoinSignet, None).await,
    );
    let beneficiary_account = match beneficiary_account_type {
        AccountType::Full { .. } => Account::Full(
            create_full_account(context, &bootstrap.services, Network::BitcoinSignet, None).await,
        ),
        AccountType::Lite => {
            Account::Lite(create_lite_account(context, &bootstrap.services, None, true).await)
        }
        AccountType::Software => unimplemented!(),
    };

    let create_body = try_create_relationship(
        context,
        client,
        benefactor_account.get_id(),
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
        context,
        client,
        benefactor_account.get_id(),
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
        context,
        client,
        benefactor_account.get_id(),
        &TrustedContactRole::Beneficiary,
        &recovery_relationship_id,
        "RANDOM_CERT",
        StatusCode::OK,
    )
    .await;

    (
        benefactor_account,
        beneficiary_account,
        recovery_relationship_id,
    )
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

    let (benefactor, beneficiary, recovery_relationship_id) =
        setup_benefactor_and_beneficiary_account(
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
    }
}

pub(super) async fn try_package_upload(
    context: &TestContext,
    client: &TestClient,
    beneficiary_account_id: &AccountId,
    recovery_relationships: &Vec<RecoveryRelationshipId>,
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

    let (benefactor, beneficiary, recovery_relationship_id) =
        setup_benefactor_and_beneficiary_account(
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
        unimplemented!()
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
    }
}
