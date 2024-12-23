use bdk_utils::bdk::{database::AnyDatabase, Wallet};
use http::StatusCode;
use types::{
    account::{entities::Account, AccountType},
    recovery::{
        social::relationship::RecoveryRelationshipId, trusted_contacts::TrustedContactRole,
    },
};

use crate::{
    tests::{
        lib::{create_default_account_with_predefined_wallet, create_phone_touchpoint},
        requests::{axum::TestClient, CognitoAuthentication},
        TestContext,
    },
    Bootstrap,
};

use super::shared::{
    create_beneficiary_account, try_accept_recovery_relationship_invitation,
    try_create_relationship, try_endorse_recovery_relationship, CodeOverride,
};

mod inheritance_integration_tests;
mod inheritance_recovery_scenarios_integration_tests;

pub(crate) struct BenefactorBeneficiarySetup {
    pub benefactor: Account,
    pub beneficiary: Account,
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub benefactor_wallet: Wallet<AnyDatabase>,
    pub beneficiary_wallet: Option<Wallet<AnyDatabase>>,
}

pub(crate) async fn setup_benefactor_and_beneficiary_account(
    context: &mut TestContext,
    bootstrap: &Bootstrap,
    client: &TestClient,
    beneficiary_account_type: AccountType,
) -> BenefactorBeneficiarySetup {
    let (benefactor_account, benefactor_wallet) =
        create_default_account_with_predefined_wallet(context, client, &bootstrap.services).await;
    let benefactor = Account::Full(benefactor_account);
    create_phone_touchpoint(&bootstrap.services, benefactor.get_id(), true).await;

    let (beneficiary, beneficiary_wallet) =
        create_beneficiary_account(beneficiary_account_type, context, bootstrap, client).await;
    create_phone_touchpoint(&bootstrap.services, beneficiary.get_id(), true).await;

    let create_body = try_create_relationship(
        context,
        client,
        benefactor.get_id(),
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
        benefactor.get_id(),
        beneficiary.get_id(),
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
        benefactor.get_id(),
        &TrustedContactRole::Beneficiary,
        &recovery_relationship_id,
        "RANDOM_CERT",
        StatusCode::OK,
    )
    .await;

    BenefactorBeneficiarySetup {
        benefactor,
        beneficiary,
        recovery_relationship_id,
        benefactor_wallet,
        beneficiary_wallet,
    }
}
