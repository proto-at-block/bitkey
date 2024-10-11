use http::StatusCode;
use notification::NotificationPayloadType;
use recovery::routes::relationship::UpdateRecoveryRelationshipRequest;
use recovery::routes::relationship::UpdateRecoveryRelationshipResponse;
use time::OffsetDateTime;
use types::account::bitcoin::Network;
use types::account::entities::Account;
use types::recovery::trusted_contacts::TrustedContactRole;

use super::shared::assert_notifications;
use super::shared::AccountType;
use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::update_recovery_relationship_invitation_expiration;
use crate::tests::lib::{create_full_account, create_lite_account};
use crate::tests::recovery::shared::{
    assert_relationship_counts, try_accept_recovery_relationship_invitation,
    try_create_recovery_relationship, try_endorse_recovery_relationship, CodeOverride,
};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::CognitoAuthentication;

#[derive(Debug)]
struct CreateRecoveryRelationshipTestVector {
    customer_account_type: AccountType,
    auth: CognitoAuthentication,
    expected_status_code: StatusCode,
}

async fn create_recovery_relationship_test(vector: CreateRecoveryRelationshipTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.clone().router).await;

    let customer_account = match vector.customer_account_type {
        AccountType::Full { .. } => Account::Full(
            create_full_account(
                &mut context,
                &bootstrap.services,
                Network::BitcoinSignet,
                None,
            )
            .await,
        ),
        AccountType::Lite => {
            Account::Lite(create_lite_account(&mut context, &bootstrap.services, None, true).await)
        }
    };

    try_create_recovery_relationship(
        &context,
        &client,
        customer_account.get_id(),
        &vector.auth,
        vector.expected_status_code,
        1,
        0,
    )
    .await;

    // Check whether the notifications were created
    let expected_scheduled_notifications_types =
        vec![NotificationPayloadType::RecoveryRelationshipInvitationPending];

    let expected_customer_notifications_types = vec![];

    if vector.expected_status_code.is_success() {
        assert_notifications(
            &bootstrap,
            customer_account.get_id(),
            expected_customer_notifications_types,
            expected_scheduled_notifications_types,
        )
        .await;
    }
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
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_create_recovery_relationship_no_hw_signature: CreateRecoveryRelationshipTestVector {
        customer_account_type: AccountType::Full,
        auth: CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: false },
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_create_recovery_relationship_lite_account: CreateRecoveryRelationshipTestVector {
        customer_account_type: AccountType::Lite,
        auth: CognitoAuthentication::Recovery,
        expected_status_code: StatusCode::UNAUTHORIZED,
    },
}

#[tokio::test]
async fn test_reissue_recovery_relationship_invitation() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let customer_account_keys = context
        .get_authentication_keys_for_account_id(&customer_account.id)
        .expect("Invalid keys for account");
    let create_response = try_create_recovery_relationship(
        &context,
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

    let other_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let other_account_keys = context
        .get_authentication_keys_for_account_id(&other_account.id)
        .expect("Invalid keys for other account");
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let tc_account_keys = context
        .get_authentication_keys_for_account_id(&tc_account.id)
        .expect("Invalid keys for tc account");

    // Another account can't reissue
    let reissue_response = client
        .update_recovery_relationship(
            &other_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &other_account_keys,
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
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Recovery,
            &tc_account_keys,
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
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: false,
            },
            &customer_account_keys,
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
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &customer_account_keys,
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
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await;

    // Reissue after accept fails
    let reissue_response = client
        .update_recovery_relationship(
            &customer_account.id.to_string(),
            &create_response
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &customer_account_keys,
        )
        .await;
    assert_eq!(
        reissue_response.status_code,
        StatusCode::CONFLICT,
        "{:?}",
        reissue_response.body_string
    );

    try_endorse_recovery_relationship(
        &context,
        &client,
        &customer_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &create_response
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
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
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Reissue,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &customer_account_keys,
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;

    let tc_account = if !vector.customer_is_tc {
        match vector.tc_account_type {
            AccountType::Full => Account::Full(
                create_full_account(
                    &mut context,
                    &bootstrap.services,
                    Network::BitcoinSignet,
                    None,
                )
                .await,
            ),
            AccountType::Lite => Account::Lite(
                create_lite_account(&mut context, &bootstrap.services, None, true).await,
            ),
        }
    } else {
        Account::Full(customer_account.clone())
    };

    let create_body = try_create_recovery_relationship(
        &context,
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
            &create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id,
            override_expiration,
        )
        .await;
    }

    try_accept_recovery_relationship_invitation(
        &context,
        &client,
        &customer_account.id,
        tc_account.get_id(),
        &TrustedContactRole::SocialRecoveryContact,
        &vector.tc_auth,
        &create_body.invitation,
        vector.code_override,
        vector.expected_status_code,
        1,
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
    redo_endorsed_relationship: bool,
    expected_status_code: StatusCode,
}

async fn endorse_recovery_relationship_test(vector: EndorseRecoveryRelationshipTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let tc_account =
        Account::Lite(create_lite_account(&mut context, &bootstrap.services, None, true).await);

    let create_body = try_create_recovery_relationship(
        &context,
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
            &context,
            &client,
            &customer_account.id,
            tc_account.get_id(),
            &TrustedContactRole::SocialRecoveryContact,
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            vector.expected_status_code,
            1,
        )
        .await;
    }

    try_endorse_recovery_relationship(
        &context,
        &client,
        &customer_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &create_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
        "RANDOM_CERT",
        vector.expected_status_code,
    )
    .await;
    let get_response = client
        .get_recovery_relationships(&customer_account.id.to_string())
        .await;
    assert_eq!(
        get_response.status_code,
        StatusCode::OK,
        "{:?}",
        get_response.body_string
    );

    let get_body = get_response.body.unwrap();
    get_body.endorsed_trusted_contacts.iter().for_each(|tc| {
        assert_eq!(tc.delegated_decryption_pubkey_certificate, "RANDOM_CERT");
    });

    if vector.redo_endorsed_relationship {
        try_endorse_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
            &TrustedContactRole::SocialRecoveryContact,
            &create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id,
            "RANDOM_CERT_2",
            vector.expected_status_code,
        )
        .await;
        let get_response = client
            .get_recovery_relationships(&customer_account.id.to_string())
            .await;

        assert_eq!(
            get_response.status_code,
            StatusCode::OK,
            "{:?}",
            get_response.body_string
        );

        let get_body = get_response.body.unwrap();
        get_body.endorsed_trusted_contacts.iter().for_each(|tc| {
            assert_eq!(tc.delegated_decryption_pubkey_certificate, "RANDOM_CERT_2");
        });
    }
}

tests! {
    runner = endorse_recovery_relationship_test,
    test_endorse_recovery_relationship: EndorseRecoveryRelationshipTestVector {
        accept_recovery_relationship: true,
        redo_endorsed_relationship: false,
        expected_status_code: StatusCode::OK,
    },
    test_redo_endorsement_recovery_relationship: EndorseRecoveryRelationshipTestVector {
        accept_recovery_relationship: true,
        redo_endorsed_relationship: true,
        expected_status_code: StatusCode::OK,
    },
    test_endorse_recovery_relationship_invitation: EndorseRecoveryRelationshipTestVector {
        accept_recovery_relationship: false,
        redo_endorsed_relationship: false,
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let customer_keys = context
        .get_authentication_keys_for_account_id(&customer_account.id)
        .expect("Invalid keys for account");
    let tc_account = match vector.tc_account_type {
        AccountType::Full { .. } => Account::Full(
            create_full_account(
                &mut context,
                &bootstrap.services,
                Network::BitcoinSignet,
                None,
            )
            .await,
        ),
        AccountType::Lite => {
            Account::Lite(create_lite_account(&mut context, &bootstrap.services, None, true).await)
        }
    };
    let tc_account_keys = context
        .get_authentication_keys_for_account_id(tc_account.get_id())
        .expect("Invalid keys for account");
    let unrelated_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let unrelated_account_keys = context
        .get_authentication_keys_for_account_id(&unrelated_account.id)
        .expect("Invalid keys for account");

    let create_body = try_create_recovery_relationship(
        &context,
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
            &context,
            &client,
            &customer_account.id,
            tc_account.get_id(),
            &TrustedContactRole::SocialRecoveryContact,
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            StatusCode::OK,
            1,
        )
        .await;
    }

    if vector.endorsed {
        try_endorse_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
            &TrustedContactRole::SocialRecoveryContact,
            &create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id,
            "RANDOM_CERT",
            StatusCode::OK,
        )
        .await;
    }

    let (deleter_account_id, keys) = match vector.deleter {
        RelationshipRole::Customer => (&customer_account.id, &customer_keys),
        RelationshipRole::TrustedContact => (tc_account.get_id(), &tc_account_keys),
        RelationshipRole::Unrelated => (&unrelated_account.id, &unrelated_account_keys),
    };

    let delete_response = client
        .delete_recovery_relationship(
            &deleter_account_id.to_string(),
            &create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &vector.deleter_auth,
            keys,
        )
        .await;

    assert_eq!(
        delete_response.status_code, vector.expected_status_code,
        "{:?}",
        delete_response.body_string
    );

    if vector.expected_status_code == StatusCode::OK {
        assert_relationship_counts(
            &client,
            &customer_account.id,
            0,
            0,
            0,
            0,
            &TrustedContactRole::SocialRecoveryContact,
        )
        .await;
        assert_relationship_counts(
            &client,
            tc_account.get_id(),
            0,
            0,
            0,
            0,
            &TrustedContactRole::SocialRecoveryContact,
        )
        .await;
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let customer_keys = context
        .get_authentication_keys_for_account_id(&customer_account.id)
        .expect("Invalid keys for account");

    let create_body = try_create_recovery_relationship(
        &context,
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
            &create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &CognitoAuthentication::Recovery,
            &customer_keys,
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
        &create_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
        OffsetDateTime::now_utc(),
    )
    .await;

    let delete_response = client
        .delete_recovery_relationship(
            &customer_account.id.to_string(),
            &create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &CognitoAuthentication::Wallet {
                is_app_signed: false,
                is_hardware_signed: false,
            },
            &customer_keys,
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let other_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &context,
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
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await
    .unwrap();
    let repeat_response = try_accept_recovery_relationship_invitation(
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await
    .unwrap();

    // Test idempotency
    assert_eq!(initial_response, repeat_response);

    try_accept_recovery_relationship_invitation(
        &context,
        &client,
        &customer_account.id,
        &other_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::CONFLICT,
        1,
    )
    .await;
}

#[tokio::test]
async fn test_accept_already_accepted_and_endorsed_recovery_relationship() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let other_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &context,
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
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await
    .unwrap();

    let initial_response = try_endorse_recovery_relationship(
        &context,
        &client,
        &customer_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &create_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
        "RANDOM_CERT",
        StatusCode::OK,
    )
    .await
    .unwrap();
    let repeat_response = try_endorse_recovery_relationship(
        &context,
        &client,
        &customer_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &create_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
        "RANDOM_CERT",
        StatusCode::OK,
    )
    .await
    .unwrap();

    // Test idempotency
    assert_eq!(initial_response, repeat_response);

    try_accept_recovery_relationship_invitation(
        &context,
        &client,
        &customer_account.id,
        &other_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::CONFLICT,
        1,
    )
    .await;
}

#[tokio::test]
async fn test_accept_recovery_relationship_for_existing_customer() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &context,
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
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await;

    let create_body = try_create_recovery_relationship(
        &context,
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
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::CONFLICT,
        1,
    )
    .await;
}

#[tokio::test]
async fn test_get_recovery_relationship_invitation_for_code() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let create_body = try_create_recovery_relationship(
        &context,
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
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
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

#[tokio::test]
async fn test_relationships_count_caps() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    for i in 0..=3 {
        try_create_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            if i != 3 {
                StatusCode::OK
            } else {
                StatusCode::CONFLICT
            },
            i + 1,
            0,
        )
        .await;
    }

    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    for i in 0..=10 {
        let customer_account = create_full_account(
            &mut context,
            &bootstrap.services,
            Network::BitcoinSignet,
            None,
        )
        .await;
        let create_body = try_create_recovery_relationship(
            &context,
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
            &context,
            &client,
            &customer_account.id,
            &tc_account.id,
            &TrustedContactRole::SocialRecoveryContact,
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            if i != 10 {
                StatusCode::OK
            } else {
                StatusCode::CONFLICT
            },
            i + 1,
        )
        .await;
    }
}
