use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::update_recovery_relationship_invitation_expiration;
use crate::tests::lib::{create_account, create_lite_account};
use crate::tests::requests::axum::TestClient;
use account::entities::Account;
use account::entities::Network;
use http::StatusCode;
use recovery::routes::OutboundInvitation;
use recovery::routes::UpdateRecoveryRelationshipResponse;
use recovery::routes::{CreateRecoveryRelationshipRequest, UpdateRecoveryRelationshipRequest};
use recovery::routes::{
    CreateRecoveryRelationshipResponse, EndorseRecoveryRelationshipsRequest,
    EndorseRecoveryRelationshipsResponse,
};
use time::OffsetDateTime;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{
    RecoveryRelationshipEndorsement, RecoveryRelationshipId,
};

use super::requests::CognitoAuthentication;

const TRUSTED_CONTACT_ALIAS: &str = "Trusty";
const CUSTOMER_ALIAS: &str = "Custy";
const CUSTOMER_ENROLLMENT_PUBKEY: &str = "CustyEnrollmentPubkey";
const TRUSTED_CONTACT_IDENTITY_PUBKEY: &str = "TrustyIdentityPubkey";
const TRUSTED_CONTACT_ENROLLMENT_PUBKEY: &str = "TrustyEnrollmentPubkey";

#[derive(Debug)]
pub(super) enum CodeOverride {
    None,
    HumanError,
    Mismatch,
}

impl CodeOverride {
    pub(super) fn apply(&self, code: &str) -> String {
        match self {
            Self::None => code.to_string(),
            Self::HumanError => {
                let lower = code.to_lowercase();
                lower.replace('0', "o").replace('1', "l")
            }
            Self::Mismatch => "BADC0DEE".to_string(),
        }
    }
}

async fn assert_relationship_counts(
    client: &TestClient,
    account_id: &AccountId,
    num_invitations: usize,
    num_unendorsed_trusted_contacts: usize,
    num_endorsed_trusted_contacts: usize,
    num_customers: usize,
) {
    let get_response = client
        .get_recovery_relationships(&account_id.to_string())
        .await;

    assert_eq!(
        get_response.status_code,
        StatusCode::OK,
        "{:?}",
        get_response.body_string
    );

    let get_body = get_response.body.unwrap();

    assert_eq!(get_body.invitations.len(), num_invitations);
    assert_eq!(
        get_body.unendorsed_trusted_contacts.len(),
        num_unendorsed_trusted_contacts
    );
    assert_eq!(
        get_body.endorsed_trusted_contacts.len(),
        num_endorsed_trusted_contacts
    );
    assert_eq!(get_body.customers.len(), num_customers);

    get_body.unendorsed_trusted_contacts.iter().for_each(|tc| {
        assert_eq!(
            tc.info.trusted_contact_identity_pubkey,
            TRUSTED_CONTACT_IDENTITY_PUBKEY
        );
    });
}

pub(super) async fn try_create_recovery_relationship(
    client: &TestClient,
    customer_account_id: &AccountId,
    auth: &CognitoAuthentication,
    expected_status_code: StatusCode,
    expected_num_invitations: usize,
    expected_num_trusted_contacts: usize,
) -> Option<CreateRecoveryRelationshipResponse> {
    let create_response = client
        .create_recovery_relationship(
            &customer_account_id.to_string(),
            &CreateRecoveryRelationshipRequest {
                trusted_contact_alias: TRUSTED_CONTACT_ALIAS.to_string(),
                customer_enrollment_pubkey: CUSTOMER_ENROLLMENT_PUBKEY.to_string(),
            },
            auth,
        )
        .await;

    assert_eq!(
        create_response.status_code, expected_status_code,
        "{:?}",
        create_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        let create_body = create_response.body.unwrap();
        assert_eq!(
            create_body.invitation.trusted_contact_alias,
            TRUSTED_CONTACT_ALIAS
        );

        assert_relationship_counts(
            client,
            customer_account_id,
            expected_num_invitations,
            expected_num_trusted_contacts,
            0,
            0,
        )
        .await;

        return Some(create_body);
    }

    None
}

pub(super) async fn try_accept_recovery_relationship_invitation(
    client: &TestClient,
    customer_account_id: &AccountId,
    trusted_contact_account_id: &AccountId,
    auth: &CognitoAuthentication,
    invitation: &OutboundInvitation,
    code_override: CodeOverride,
    expected_status_code: StatusCode,
) -> Option<UpdateRecoveryRelationshipResponse> {
    let accept_response = client
        .update_recovery_relationship(
            &trusted_contact_account_id.to_string(),
            &invitation.recovery_relationship_id.to_string(),
            &UpdateRecoveryRelationshipRequest::Accept {
                code: code_override.apply(&invitation.code),
                customer_alias: CUSTOMER_ALIAS.to_string(),
                trusted_contact_identity_pubkey: TRUSTED_CONTACT_IDENTITY_PUBKEY.to_string(),
                trusted_contact_enrollment_pubkey: TRUSTED_CONTACT_ENROLLMENT_PUBKEY.to_string(),
                trusted_contact_identity_pubkey_mac: "RANDOM_MAC".to_string(),
                enrollment_key_confirmation: "RANDOM_KEY_CONFIRMATION".to_string(),
            },
            auth,
        )
        .await;

    assert_eq!(
        accept_response.status_code, expected_status_code,
        "{:?}",
        accept_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        let accept_body: UpdateRecoveryRelationshipResponse = accept_response.body.unwrap();

        let UpdateRecoveryRelationshipResponse::Accept { customer } = &accept_body else {
            panic!();
        };

        assert_eq!(customer.customer_alias, CUSTOMER_ALIAS);

        assert_relationship_counts(client, customer_account_id, 0, 1, 0, 0).await;
        assert_relationship_counts(client, trusted_contact_account_id, 0, 0, 0, 1).await;

        return Some(accept_body);
    }

    None
}

pub(super) async fn try_endorse_recovery_relationship(
    client: &TestClient,
    customer_account_id: &AccountId,
    recovery_relationship_id: &RecoveryRelationshipId,
    endorsement_key_certificate: &str,
    expected_status_code: StatusCode,
) -> Option<EndorseRecoveryRelationshipsResponse> {
    let endorse_response = client
        .endorse_recovery_relationship(
            &customer_account_id.to_string(),
            &EndorseRecoveryRelationshipsRequest {
                endorsements: vec![RecoveryRelationshipEndorsement {
                    recovery_relationship_id: recovery_relationship_id.to_owned(),
                    endorsement_key_certificate: endorsement_key_certificate.to_string(),
                }],
            },
        )
        .await;

    assert_eq!(
        endorse_response.status_code, expected_status_code,
        "{:?}",
        endorse_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        let endorse_body: EndorseRecoveryRelationshipsResponse = endorse_response.body.unwrap();
        assert_relationship_counts(client, customer_account_id, 0, 0, 1, 0).await;
        return Some(endorse_body);
    }
    None
}

#[derive(Debug)]
pub(super) enum AccountType {
    Full,
    Lite,
}

#[derive(Debug)]
struct CreateRecoveryRelationshipTestVector {
    customer_account_type: AccountType,
    auth: CognitoAuthentication,
    expected_status_code: StatusCode,
}

async fn create_recovery_relationship_test(vector: CreateRecoveryRelationshipTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = match vector.customer_account_type {
        AccountType::Full { .. } => {
            Account::Full(create_account(&bootstrap.services, Network::BitcoinSignet, None).await)
        }
        AccountType::Lite => {
            Account::Lite(create_lite_account(&bootstrap.services, None, true).await)
        }
    };

    try_create_recovery_relationship(
        &client,
        customer_account.get_id(),
        &vector.auth,
        vector.expected_status_code,
        1,
        0,
    )
    .await;
}

tests! {
    runner = create_recovery_relationship_test,
    test_create_recovery_relationship: CreateRecoveryRelationshipTestVector {
        customer_account_type: AccountType::Full,
        auth: CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::OK,
    },
    test_create_recovery_relationship_no_app_signature: CreateRecoveryRelationshipTestVector {
        customer_account_type: AccountType::Full,
        auth: CognitoAuthentication::Wallet{ is_app_signed: false, is_hardware_signed: true },
        expected_status_code: StatusCode::BAD_REQUEST,
    },
    test_create_recovery_relationship_no_hw_signature: CreateRecoveryRelationshipTestVector {
        customer_account_type: AccountType::Full,
        auth: CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: false },
        expected_status_code: StatusCode::BAD_REQUEST,
    },
    test_create_recovery_relationship_lite_account: CreateRecoveryRelationshipTestVector {
        customer_account_type: AccountType::Lite,
        auth: CognitoAuthentication::Recovery,
        expected_status_code: StatusCode::UNAUTHORIZED,
    },
}

#[tokio::test]
async fn test_reissue_recovery_relationship_invitation() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let create_response = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    let other_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = create_lite_account(&bootstrap.services, None, true).await;

    // Another account can't reissue
    let reissue_response = client
        .update_recovery_relationship(
            &other_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
        )
        .await;
    assert_eq!(
        reissue_response.status_code,
        StatusCode::FORBIDDEN,
        "{:?}",
        reissue_response.body_string
    );

    // A TC can't reissue
    let reissue_response = client
        .update_recovery_relationship(
            &tc_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Recovery,
        )
        .await;
    assert_eq!(
        reissue_response.status_code,
        StatusCode::FORBIDDEN,
        "{:?}",
        reissue_response.body_string
    );

    // Missing signature fails to reissue
    let reissue_response = client
        .update_recovery_relationship(
            &customer_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: false,
            },
        )
        .await;
    assert_eq!(
        reissue_response.status_code,
        StatusCode::BAD_REQUEST,
        "{:?}",
        reissue_response.body_string
    );

    // Successful reissue
    let reissue_response = client
        .update_recovery_relationship(
            &customer_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
        )
        .await;
    assert_eq!(
        reissue_response.status_code,
        StatusCode::OK,
        "{:?}",
        reissue_response.body_string
    );

    let UpdateRecoveryRelationshipResponse::Reissue { invitation } = reissue_response.body.unwrap()
    else {
        panic!();
    };

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &tc_account.id,
        &CognitoAuthentication::Recovery,
        &invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await;

    // Reissue after accept fails
    let reissue_response = client
        .update_recovery_relationship(
            &customer_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
        )
        .await;
    assert_eq!(
        reissue_response.status_code,
        StatusCode::CONFLICT,
        "{:?}",
        reissue_response.body_string
    );

    try_endorse_recovery_relationship(
        &client,
        &customer_account.id,
        &create_response.invitation.recovery_relationship_id,
        "RANDOM_CERT",
        StatusCode::OK,
    )
    .await;

    // Reissue after endorse
    let reissue_response = client
        .update_recovery_relationship(
            &customer_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
        )
        .await;
    assert_eq!(
        reissue_response.status_code,
        StatusCode::CONFLICT,
        "{:?}",
        reissue_response.body_string
    );
}

#[derive(Debug)]
struct AcceptRecoveryRelationshipInvitationTestVector {
    tc_account_type: AccountType,
    customer_is_tc: bool,
    tc_auth: CognitoAuthentication,
    code_override: CodeOverride,
    override_expires_at: Option<OffsetDateTime>,
    expected_status_code: StatusCode,
}

async fn accept_recovery_relationship_invitation_test(
    vector: AcceptRecoveryRelationshipInvitationTestVector,
) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;

    let tc_account = if !vector.customer_is_tc {
        match vector.tc_account_type {
            AccountType::Full => Account::Full(
                create_account(&bootstrap.services, Network::BitcoinSignet, None).await,
            ),
            AccountType::Lite => {
                Account::Lite(create_lite_account(&bootstrap.services, None, true).await)
            }
        }
    } else {
        Account::Full(customer_account.clone())
    };

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    if let Some(override_expiration) = vector.override_expires_at {
        update_recovery_relationship_invitation_expiration(
            &bootstrap.services,
            &create_body.invitation.recovery_relationship_id,
            override_expiration,
        )
        .await;
    }

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        tc_account.get_id(),
        &vector.tc_auth,
        &create_body.invitation,
        vector.code_override,
        vector.expected_status_code,
    )
    .await;
}

tests! {
    runner = accept_recovery_relationship_invitation_test,
    test_accept_recovery_relationship_invitation_by_customer_type: AcceptRecoveryRelationshipInvitationTestVector {
        tc_account_type: AccountType::Full,
        customer_is_tc: false,
        tc_auth: CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: false },
        code_override: CodeOverride::None,
        override_expires_at: None,
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_accept_recovery_relationship_invitation_by_tc_type: AcceptRecoveryRelationshipInvitationTestVector {
        tc_account_type: AccountType::Lite,
        customer_is_tc: false,
        tc_auth: CognitoAuthentication::Recovery,
        code_override: CodeOverride::None,
        override_expires_at: None,
        expected_status_code: StatusCode::OK,
    },
    test_accept_recovery_relationship_invitation_with_human_error: AcceptRecoveryRelationshipInvitationTestVector {
        tc_account_type: AccountType::Lite,
        customer_is_tc: false,
        tc_auth: CognitoAuthentication::Recovery,
        code_override: CodeOverride::HumanError,
        override_expires_at: None,
        expected_status_code: StatusCode::OK,
    },
    test_accept_recovery_relationship_invitation_bad_code: AcceptRecoveryRelationshipInvitationTestVector {
        tc_account_type: AccountType::Lite,
        customer_is_tc: false,
        tc_auth: CognitoAuthentication::Recovery,
        code_override: CodeOverride::Mismatch,
        override_expires_at: None,
        expected_status_code: StatusCode::BAD_REQUEST,
    },
    test_accept_recovery_relationship_invitation_expired: AcceptRecoveryRelationshipInvitationTestVector {
        tc_account_type: AccountType::Lite,
        customer_is_tc: false,
        tc_auth: CognitoAuthentication::Recovery,
        code_override: CodeOverride::None,
        override_expires_at: Some(OffsetDateTime::now_utc()),
        expected_status_code: StatusCode::CONFLICT,
    },
    test_accept_recovery_relationship_invitation_customer_is_tc: AcceptRecoveryRelationshipInvitationTestVector {
        tc_account_type: AccountType::Full,
        customer_is_tc: true,
        tc_auth: CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: true },
        code_override: CodeOverride::None,
        override_expires_at: None,
        expected_status_code: StatusCode::FORBIDDEN,
    },
}

#[derive(Debug)]
struct EndorseRecoveryRelationshipTestVector {
    accept_recovery_relationship: bool,
    expected_status_code: StatusCode,
}

async fn endorse_recovery_relationship_test(vector: EndorseRecoveryRelationshipTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = Account::Lite(create_lite_account(&bootstrap.services, None, true).await);

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    if vector.accept_recovery_relationship {
        try_accept_recovery_relationship_invitation(
            &client,
            &customer_account.id,
            tc_account.get_id(),
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            vector.expected_status_code,
        )
        .await;
    }

    try_endorse_recovery_relationship(
        &client,
        &customer_account.id,
        &create_body.invitation.recovery_relationship_id,
        "RANDOM_CERT",
        vector.expected_status_code,
    )
    .await;
}

tests! {
    runner = endorse_recovery_relationship_test,
    test_endorse_recovery_relationship: EndorseRecoveryRelationshipTestVector {
        accept_recovery_relationship: true,
        expected_status_code: StatusCode::OK,
    },
    test_endorse_recovery_relationship_invitation: EndorseRecoveryRelationshipTestVector {
        accept_recovery_relationship: false,
        expected_status_code: StatusCode::BAD_REQUEST,
    },
}

#[derive(Debug)]
enum RelationshipRole {
    Customer,
    TrustedContact,
    Unrelated,
}

#[derive(Debug)]
struct DeleteRecoveryRelationshipTestVector {
    tc_account_type: AccountType,
    deleter: RelationshipRole,
    accepted: bool,
    endorsed: bool,
    deleter_auth: CognitoAuthentication,
    expected_status_code: StatusCode,
}

async fn delete_recovery_relationship_test(vector: DeleteRecoveryRelationshipTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = match vector.tc_account_type {
        AccountType::Full { .. } => {
            Account::Full(create_account(&bootstrap.services, Network::BitcoinSignet, None).await)
        }
        AccountType::Lite => {
            Account::Lite(create_lite_account(&bootstrap.services, None, true).await)
        }
    };
    let unrelated_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    if vector.accepted {
        try_accept_recovery_relationship_invitation(
            &client,
            &customer_account.id,
            tc_account.get_id(),
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            StatusCode::OK,
        )
        .await;
    }

    if vector.endorsed {
        try_endorse_recovery_relationship(
            &client,
            &customer_account.id,
            &create_body.invitation.recovery_relationship_id,
            "RANDOM_CERT",
            StatusCode::OK,
        )
        .await;
    }

    let deleter_account_id = match vector.deleter {
        RelationshipRole::Customer => &customer_account.id,
        RelationshipRole::TrustedContact => tc_account.get_id(),
        RelationshipRole::Unrelated => &unrelated_account.id,
    };

    let delete_response = client
        .delete_recovery_relationship(
            &deleter_account_id.to_string(),
            &create_body.invitation.recovery_relationship_id.to_string(),
            &vector.deleter_auth,
        )
        .await;

    assert_eq!(
        delete_response.status_code, vector.expected_status_code,
        "{:?}",
        delete_response.body_string
    );

    if vector.expected_status_code == StatusCode::OK {
        assert_relationship_counts(&client, &customer_account.id, 0, 0, 0, 0).await;
        assert_relationship_counts(&client, tc_account.get_id(), 0, 0, 0, 0).await;
    }
}

tests! {
    runner = delete_recovery_relationship_test,
    test_delete_recovery_relationship_invitation: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Lite,
        deleter: RelationshipRole::Customer,
        accepted: false,
        endorsed: false,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::OK,
    },
    test_delete_recovery_relationship_invitation_unrelated_deleter: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Lite,
        deleter: RelationshipRole::Unrelated,
        accepted: false,
        endorsed: false,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_delete_recovery_relationship_invitation_accepted_customer_deleter: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Lite,
        deleter: RelationshipRole::Customer,
        accepted: true,
        endorsed: false,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::OK,
    },
    test_delete_recovery_relationship_invitation_accepted_and_endorsed_customer_deleter: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Lite,
        deleter: RelationshipRole::Customer,
        accepted: true,
        endorsed: true,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::OK,
    },
    test_delete_recovery_relationship_invitation_accepted_tc_deleter_customer_type: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Full,
        deleter: RelationshipRole::TrustedContact,
        accepted: true,
        endorsed: false,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_delete_recovery_relationship_invitation_accepted_and_endorsed_tc_deleter_customer_type: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Full,
        deleter: RelationshipRole::TrustedContact,
        accepted: true,
        endorsed: true,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_delete_recovery_relationship_invitation_accepted_tc_deleter_tc_type: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Lite,
        deleter: RelationshipRole::TrustedContact,
        accepted: true,
        endorsed: false,
        deleter_auth: CognitoAuthentication::Recovery,
        expected_status_code: StatusCode::OK,
    },
    test_delete_recovery_relationship_invitation_accepted_and_endorsed_tc_deleter_tc_type: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Lite,
        deleter: RelationshipRole::TrustedContact,
        accepted: true,
        endorsed: true,
        deleter_auth: CognitoAuthentication::Recovery,
        expected_status_code: StatusCode::OK,
    },
    test_delete_recovery_relationship_invitation_accepted_unrelated_deleter: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Full,
        deleter: RelationshipRole::Unrelated,
        accepted: true,
        endorsed: false,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_delete_recovery_relationship_invitation_accepted_and_endorsed_unrelated_deleter: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Full,
        deleter: RelationshipRole::Unrelated,
        accepted: true,
        endorsed: true,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_delete_recovery_relationship_no_keyproof: DeleteRecoveryRelationshipTestVector {
        tc_account_type: AccountType::Lite,
        deleter: RelationshipRole::Customer,
        accepted: false,
        endorsed: false,
        deleter_auth: CognitoAuthentication::Wallet { is_app_signed: false, is_hardware_signed: false },
        expected_status_code: StatusCode::OK,
    },
}

#[tokio::test]
async fn test_customer_deletes_expired_invitation_with_no_keyproof() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    let delete_response = client
        .delete_recovery_relationship(
            &customer_account.id.to_string(),
            &create_body.invitation.recovery_relationship_id.to_string(),
            &CognitoAuthentication::Recovery,
        )
        .await;

    assert_eq!(
        delete_response.status_code,
        StatusCode::FORBIDDEN,
        "{:?}",
        delete_response.body_string
    );

    update_recovery_relationship_invitation_expiration(
        &bootstrap.services,
        &create_body.invitation.recovery_relationship_id,
        OffsetDateTime::now_utc(),
    )
    .await;

    let delete_response = client
        .delete_recovery_relationship(
            &customer_account.id.to_string(),
            &create_body.invitation.recovery_relationship_id.to_string(),
            &CognitoAuthentication::Wallet {
                is_app_signed: false,
                is_hardware_signed: false,
            },
        )
        .await;

    assert_eq!(
        delete_response.status_code,
        StatusCode::OK,
        "{:?}",
        delete_response.body_string
    );
}

#[tokio::test]
async fn test_accept_already_accepted_recovery_relationship() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = create_lite_account(&bootstrap.services, None, true).await;
    let other_account = create_lite_account(&bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    let initial_response = try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &tc_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await
    .unwrap();
    let repeat_response = try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &tc_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await
    .unwrap();

    // Test idempotency
    assert_eq!(initial_response, repeat_response);

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &other_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::CONFLICT,
    )
    .await;
}

#[tokio::test]
async fn test_accept_already_accepted_and_endorsed_recovery_relationship() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = create_lite_account(&bootstrap.services, None, true).await;
    let other_account = create_lite_account(&bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &tc_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await
    .unwrap();

    let initial_response = try_endorse_recovery_relationship(
        &client,
        &customer_account.id,
        &create_body.invitation.recovery_relationship_id,
        "RANDOM_CERT",
        StatusCode::OK,
    )
    .await
    .unwrap();
    let repeat_response = try_endorse_recovery_relationship(
        &client,
        &customer_account.id,
        &create_body.invitation.recovery_relationship_id,
        "RANDOM_CERT",
        StatusCode::OK,
    )
    .await
    .unwrap();

    // Test idempotency
    assert_eq!(initial_response, repeat_response);

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &other_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::CONFLICT,
    )
    .await;
}

#[tokio::test]
async fn test_accept_recovery_relationship_for_existing_customer() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = create_lite_account(&bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &tc_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await;

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
        &CognitoAuthentication::Wallet {
            is_app_signed: true,
            is_hardware_signed: true,
        },
        StatusCode::OK,
        1,
        1,
    )
    .await
    .unwrap();

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &tc_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::CONFLICT,
    )
    .await;
}

#[tokio::test]
async fn test_get_recovery_relationship_invitation_for_code() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = create_lite_account(&bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &client,
        &customer_account.id,
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

    let get_response = client
        .get_recovery_relationship_invitation_for_code(
            &tc_account.id.to_string(),
            &create_body.invitation.code,
        )
        .await;

    assert_eq!(
        get_response.status_code,
        StatusCode::OK,
        "{:?}",
        get_response.body_string
    );

    try_accept_recovery_relationship_invitation(
        &client,
        &customer_account.id,
        &tc_account.id,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await;

    let get_response = client
        .get_recovery_relationship_invitation_for_code(
            &tc_account.id.to_string(),
            &create_body.invitation.code,
        )
        .await;

    assert_eq!(
        get_response.status_code,
        StatusCode::NOT_FOUND,
        "{:?}",
        get_response.body_string
    );
}
