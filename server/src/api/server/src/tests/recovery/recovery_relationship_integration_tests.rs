use http::StatusCode;

use notification::NotificationPayloadType;
use recovery::routes::relationship::UpdateRecoveryRelationshipRequest;
use recovery::routes::relationship::UpdateRecoveryRelationshipResponse;
use time::OffsetDateTime;
use tokio::join;
use types::account::bitcoin::Network;
use types::account::entities::Account;
use types::recovery::trusted_contacts::TrustedContactRole;

use super::shared::AccountType;
use rstest::rstest;

use crate::tests::gen_services;
use crate::tests::lib::create_phone_touchpoint;
use crate::tests::lib::update_recovery_relationship_invitation_expiration;
use crate::tests::lib::{create_full_account, create_lite_account};
use crate::tests::recovery::shared::assert_notifications;
use crate::tests::recovery::shared::try_create_relationship;
use crate::tests::recovery::shared::{
    assert_relationship_counts, try_accept_recovery_relationship_invitation,
    try_create_recovery_relationship, try_endorse_recovery_relationship, CodeOverride,
};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::CognitoAuthentication;

#[rstest]
#[case::success(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: true },
    StatusCode::OK,
)]
#[case::no_app_signature(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: false, is_hardware_signed: true },
    StatusCode::FORBIDDEN,
)]
#[case::no_hw_signature(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: false },
    StatusCode::FORBIDDEN,
)]
#[case::lite_account(
    AccountType::Lite,
    CognitoAuthentication::Recovery,
    StatusCode::UNAUTHORIZED
)]
#[tokio::test]
async fn test_create_recovery_relationship(
    #[case] customer_account_type: AccountType,
    #[case] auth: CognitoAuthentication,
    #[case] expected_status_code: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.clone().router).await;

    let customer_account = match customer_account_type {
        AccountType::Full => Account::Full(
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
        &auth,
        expected_status_code,
        1,
        0,
    )
    .await;
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

#[rstest]
#[case::customer_type_forbidden(
    AccountType::Full,
    false,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: false },
    CodeOverride::None,
    None,
    StatusCode::FORBIDDEN,
)]
#[case::tc_type_success(
    AccountType::Lite,
    false,
    CognitoAuthentication::Recovery,
    CodeOverride::None,
    None,
    StatusCode::OK
)]
#[case::bad_code(
    AccountType::Lite,
    false,
    CognitoAuthentication::Recovery,
    CodeOverride::Mismatch,
    None,
    StatusCode::BAD_REQUEST
)]
#[case::expired(
    AccountType::Lite,
    false,
    CognitoAuthentication::Recovery,
    CodeOverride::None,
    Some(OffsetDateTime::now_utc()),
    StatusCode::CONFLICT
)]
#[case::customer_is_tc(
    AccountType::Full,
    true,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: true },
    CodeOverride::None,
    None,
    StatusCode::FORBIDDEN,
)]
#[tokio::test]
async fn test_accept_recovery_relationship_invitation(
    #[case] tc_account_type: AccountType,
    #[case] customer_is_tc: bool,
    #[case] tc_auth: CognitoAuthentication,
    #[case] code_override: CodeOverride,
    #[case] override_expires_at: Option<OffsetDateTime>,
    #[case] expected_status_code: StatusCode,
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

    let tc_account = if !customer_is_tc {
        match tc_account_type {
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

    if let Some(override_expiration) = override_expires_at {
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
        &tc_auth,
        &create_body.invitation,
        code_override,
        expected_status_code,
        1,
    )
    .await;
}

#[rstest]
#[case::success(true, false, StatusCode::OK)]
#[case::redo_endorsement(true, true, StatusCode::OK)]
#[case::invitation_not_accepted(false, false, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn test_endorse_recovery_relationship(
    #[case] accept_recovery_relationship: bool,
    #[case] redo_endorsed_relationship: bool,
    #[case] expected_status_code: StatusCode,
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

    if accept_recovery_relationship {
        try_accept_recovery_relationship_invitation(
            &context,
            &client,
            &customer_account.id,
            tc_account.get_id(),
            &TrustedContactRole::SocialRecoveryContact,
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            expected_status_code,
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
        expected_status_code,
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

    if redo_endorsed_relationship {
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
            expected_status_code,
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

#[derive(Debug, PartialEq)]
enum RelationshipRole {
    Customer,
    TrustedContact,
    Unrelated,
}

#[rstest]
#[case::customer_delete_invitation(
    AccountType::Lite,
    RelationshipRole::Customer,
    false,
    false,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::OK,
)]
#[case::unrelated_delete_forbidden(
    AccountType::Lite,
    RelationshipRole::Unrelated,
    false,
    false,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::FORBIDDEN,
)]
#[case::customer_delete_accepted(
    AccountType::Lite,
    RelationshipRole::Customer,
    true,
    false,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::OK,
)]
#[case::customer_delete_endorsed(
    AccountType::Lite,
    RelationshipRole::Customer,
    true,
    true,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::OK,
)]
#[case::tc_delete_full_account_forbidden(
    AccountType::Full,
    RelationshipRole::TrustedContact,
    true,
    false,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::FORBIDDEN,
)]
#[case::tc_delete_endorsed_full_forbidden(
    AccountType::Full,
    RelationshipRole::TrustedContact,
    true,
    true,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::FORBIDDEN,
)]
#[case::tc_delete_lite_account(
    AccountType::Lite,
    RelationshipRole::TrustedContact,
    true,
    false,
    CognitoAuthentication::Recovery,
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::OK
)]
#[case::tc_delete_endorsed_lite(
    AccountType::Lite,
    RelationshipRole::TrustedContact,
    true,
    true,
    CognitoAuthentication::Recovery,
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::OK
)]
#[case::unrelated_delete_accepted_forbidden(
    AccountType::Full,
    RelationshipRole::Unrelated,
    true,
    false,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::FORBIDDEN,
)]
#[case::unrelated_delete_endorsed_forbidden(
    AccountType::Full,
    RelationshipRole::Unrelated,
    true,
    true,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::FORBIDDEN,
)]
#[case::no_keyproof(
    AccountType::Lite,
    RelationshipRole::Customer,
    false,
    false,
    CognitoAuthentication::Wallet { is_app_signed: false, is_hardware_signed: false },
    TrustedContactRole::SocialRecoveryContact,
    StatusCode::OK,
)]
#[case::customer_delete_beneficiary(
    AccountType::Full,
    RelationshipRole::Customer,
    true,
    true,
    CognitoAuthentication::Wallet { is_app_signed: true, is_hardware_signed: true },
    TrustedContactRole::Beneficiary,
    StatusCode::OK,
)]
#[case::tc_delete_beneficiary(
    AccountType::Full,
    RelationshipRole::TrustedContact,
    true,
    true,
    CognitoAuthentication::Recovery,
    TrustedContactRole::Beneficiary,
    StatusCode::OK
)]
#[tokio::test]
async fn test_delete_recovery_relationship(
    #[case] tc_account_type: AccountType,
    #[case] deleter: RelationshipRole,
    #[case] accepted: bool,
    #[case] endorsed: bool,
    #[case] deleter_auth: CognitoAuthentication,
    #[case] trusted_contact_role: TrustedContactRole,
    #[case] expected_status_code: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

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
    let tc_account = match tc_account_type {
        AccountType::Full => Account::Full(
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
    // Setup touchpoints for both accounts to receive notifications
    join!(
        create_phone_touchpoint(&bootstrap.services, &customer_account.id, true),
        create_phone_touchpoint(&bootstrap.services, tc_account.get_id(), true),
    );

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

    let create_body = if trusted_contact_role == TrustedContactRole::SocialRecoveryContact {
        try_create_recovery_relationship(
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
        .unwrap()
    } else {
        try_create_relationship(
            &context,
            &client,
            &customer_account.id,
            &trusted_contact_role,
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            StatusCode::OK,
            1,
            0,
        )
        .await
        .unwrap()
    };

    if accepted {
        try_accept_recovery_relationship_invitation(
            &context,
            &client,
            &customer_account.id,
            tc_account.get_id(),
            &trusted_contact_role,
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            StatusCode::OK,
            1,
        )
        .await;
    }

    if endorsed {
        try_endorse_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
            &trusted_contact_role,
            &create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id,
            "RANDOM_CERT",
            StatusCode::OK,
        )
        .await;
    }

    let (deleter_account_id, keys) = match deleter {
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
            &deleter_auth,
            keys,
        )
        .await;

    assert_eq!(
        delete_response.status_code, expected_status_code,
        "{:?}",
        delete_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        assert_relationship_counts(
            &client,
            &customer_account.id,
            0,
            0,
            0,
            0,
            &trusted_contact_role,
        )
        .await;
        assert_relationship_counts(
            &client,
            tc_account.get_id(),
            0,
            0,
            0,
            0,
            &trusted_contact_role,
        )
        .await;

        let expected_customer_notifications = if accepted || endorsed {
            vec![
                NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                NotificationPayloadType::RecoveryRelationshipDeleted,
            ]
        } else {
            vec![]
        };
        if trusted_contact_role == TrustedContactRole::SocialRecoveryContact {
            assert_notifications(
                &bootstrap,
                &customer_account.id,
                expected_customer_notifications,
                if accepted {
                    vec![NotificationPayloadType::RecoveryRelationshipInvitationAccepted]
                } else {
                    vec![]
                },
            )
            .await;
            assert_notifications(&bootstrap, tc_account.get_id(), vec![], vec![]).await;
        } else {
            let expected_tc_notifications = if accepted || endorsed {
                vec![
                    NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                    NotificationPayloadType::RecoveryRelationshipDeleted,
                ]
            } else {
                vec![]
            };
            assert_notifications(
                &bootstrap,
                &customer_account.id,
                expected_customer_notifications,
                vec![
                    NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending,
                    NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                ],
            )
            .await;
            assert_notifications(
                &bootstrap,
                tc_account.get_id(),
                expected_tc_notifications,
                vec![],
            )
            .await;
        }
    }
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
    for i in 0..=20 {
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
            if i != 20 {
                StatusCode::OK
            } else {
                StatusCode::CONFLICT
            },
            i + 1,
        )
        .await;
    }

    for i in 0..=20 {
        let customer_account = create_full_account(
            &mut context,
            &bootstrap.services,
            Network::BitcoinSignet,
            None,
        )
        .await;
        let create_body = try_create_relationship(
            &context,
            &client,
            &customer_account.id,
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

        try_accept_recovery_relationship_invitation(
            &context,
            &client,
            &customer_account.id,
            &tc_account.id,
            &TrustedContactRole::Beneficiary,
            &CognitoAuthentication::Recovery,
            &create_body.invitation,
            CodeOverride::None,
            if i != 20 {
                StatusCode::OK
            } else {
                StatusCode::CONFLICT
            },
            i + 21,
        )
        .await;
    }
}
