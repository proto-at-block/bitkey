use crate::tests::lib::{
    create_lite_account, create_nontest_default_account_with_predefined_wallet,
};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::CognitoAuthentication;
use crate::tests::TestContext;
use crate::Bootstrap;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use http::StatusCode;
use notification::service::FetchForAccountInput;
use notification::NotificationPayloadType;
use recovery::routes::relationship::UpdateRecoveryRelationshipResponse;
use recovery::routes::relationship::{
    CreateRecoveryRelationshipRequest, UpdateRecoveryRelationshipRequest,
};
use recovery::routes::relationship::{CreateRelationshipRequest, OutboundInvitation};
use recovery::routes::relationship::{
    CreateRelationshipResponse, EndorseRecoveryRelationshipsRequest,
    EndorseRecoveryRelationshipsResponse,
};
use time::Duration;
use types::account::entities::Account;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{
    RecoveryRelationshipEndorsement, RecoveryRelationshipId,
};
use types::recovery::trusted_contacts::TrustedContactRole;

const TRUSTED_CONTACT_ALIAS: &str = "Trusty";
const CUSTOMER_ALIAS: &str = "Custy";
const PROTECTED_CUSTOMER_ENROLLMENT_PAKE_PUBKEY: &str =
    "003abf297a64bac071986e41c4dddf8160fe245f9f889699c9c57c35fa6d56f3";
const TRUSTED_CONTACT_ENROLLMENT_PAKE_PUBKEY: &str =
    "004abf297a64bac071986e41c4dddf8160fe245f9f889699c9c57c35fa6d56f4";

#[derive(Debug)]
pub(super) enum CodeOverride {
    None,
    Mismatch,
}

impl CodeOverride {
    pub(super) fn apply(&self, code: &str) -> String {
        match self {
            Self::None => code.to_string(),
            Self::Mismatch => "deadbeef".to_string(),
        }
    }
}

#[derive(Debug)]
pub(super) enum AccountType {
    Full,
    Lite,
}

pub(super) async fn assert_relationship_counts(
    client: &TestClient,
    account_id: &AccountId,
    num_invitations: usize,
    num_unendorsed_trusted_contacts: usize,
    num_endorsed_trusted_contacts: usize,
    num_customers: usize,
    trusted_contact_role: &TrustedContactRole,
) {
    let get_response = client
        .get_relationships(&account_id.to_string(), None)
        .await;

    assert_eq!(
        get_response.status_code,
        StatusCode::OK,
        "{:?}",
        get_response.body_string
    );

    let get_body = get_response.body.unwrap();

    let invitations_count = get_body
        .invitations
        .iter()
        .filter(|inv| {
            inv.recovery_relationship_info
                .trusted_contact_roles
                .contains(trusted_contact_role)
        })
        .count();
    let unendorsed_trusted_contacts_count = get_body
        .unendorsed_trusted_contacts
        .iter()
        .filter(|tc| {
            tc.recovery_relationship_info
                .trusted_contact_roles
                .contains(trusted_contact_role)
        })
        .count();
    let endorsed_trusted_contacts_count = get_body
        .endorsed_trusted_contacts
        .iter()
        .filter(|tc| {
            tc.recovery_relationship_info
                .trusted_contact_roles
                .contains(trusted_contact_role)
        })
        .count();

    assert_eq!(invitations_count, num_invitations);
    assert_eq!(
        unendorsed_trusted_contacts_count,
        num_unendorsed_trusted_contacts
    );
    assert_eq!(
        endorsed_trusted_contacts_count,
        num_endorsed_trusted_contacts
    );
    assert_eq!(get_body.customers.len(), num_customers);

    get_body.unendorsed_trusted_contacts.iter().for_each(|tc| {
        assert_eq!(
            tc.trusted_contact_enrollment_pake_pubkey,
            TRUSTED_CONTACT_ENROLLMENT_PAKE_PUBKEY
        );
    });
}

pub(super) async fn try_create_recovery_relationship(
    context: &TestContext,
    client: &TestClient,
    customer_account_id: &AccountId,
    auth: &CognitoAuthentication,
    expected_status_code: StatusCode,
    expected_num_invitations: usize,
    expected_num_trusted_contacts: usize,
) -> Option<CreateRelationshipResponse> {
    let keys = context
        .get_authentication_keys_for_account_id(customer_account_id)
        .expect("Invalid keys for account");
    let create_response = client
        .create_recovery_relationship(
            &customer_account_id.to_string(),
            &CreateRecoveryRelationshipRequest {
                trusted_contact_alias: TRUSTED_CONTACT_ALIAS.to_string(),
                protected_customer_enrollment_pake_pubkey:
                    PROTECTED_CUSTOMER_ENROLLMENT_PAKE_PUBKEY.to_string(),
            },
            auth,
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
        assert_eq!(
            create_body
                .invitation
                .recovery_relationship_info
                .trusted_contact_alias,
            TRUSTED_CONTACT_ALIAS
        );
        assert_eq!(
            create_body
                .invitation
                .recovery_relationship_info
                .trusted_contact_roles,
            vec![TrustedContactRole::SocialRecoveryContact]
        );

        assert_relationship_counts(
            client,
            customer_account_id,
            expected_num_invitations,
            expected_num_trusted_contacts,
            0,
            0,
            &TrustedContactRole::SocialRecoveryContact,
        )
        .await;

        return Some(create_body);
    }

    None
}

pub(super) async fn try_create_relationship(
    context: &TestContext,
    client: &TestClient,
    benefactor_account_id: &AccountId,
    trusted_contact_role: &TrustedContactRole,
    auth: &CognitoAuthentication,
    expected_status_code: StatusCode,
    expected_num_invitations: usize,
    expected_num_trusted_contacts: usize,
) -> Option<CreateRelationshipResponse> {
    let keys = context
        .get_authentication_keys_for_account_id(benefactor_account_id)
        .expect("Invalid keys for account");
    let create_response = client
        .create_relationship(
            &benefactor_account_id.to_string(),
            &CreateRelationshipRequest {
                trusted_contact_alias: TRUSTED_CONTACT_ALIAS.to_string(),
                protected_customer_enrollment_pake_pubkey:
                    PROTECTED_CUSTOMER_ENROLLMENT_PAKE_PUBKEY.to_string(),
                trusted_contact_roles: vec![trusted_contact_role.to_owned()],
            },
            auth,
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
        assert_eq!(
            create_body
                .invitation
                .recovery_relationship_info
                .trusted_contact_alias,
            TRUSTED_CONTACT_ALIAS
        );
        assert_eq!(
            create_body
                .invitation
                .recovery_relationship_info
                .trusted_contact_roles,
            vec![trusted_contact_role.to_owned()]
        );

        assert_relationship_counts(
            client,
            benefactor_account_id,
            expected_num_invitations,
            expected_num_trusted_contacts,
            0,
            0,
            trusted_contact_role,
        )
        .await;

        return Some(create_body);
    }

    None
}

pub(super) async fn try_accept_recovery_relationship_invitation(
    context: &TestContext,
    client: &TestClient,
    customer_account_id: &AccountId,
    trusted_contact_account_id: &AccountId,
    trusted_contact_role: &TrustedContactRole,
    auth: &CognitoAuthentication,
    invitation: &OutboundInvitation,
    code_override: CodeOverride,
    expected_status_code: StatusCode,
    tc_expected_num_customers: usize,
) -> Option<UpdateRecoveryRelationshipResponse> {
    let keys = context
        .get_authentication_keys_for_account_id(trusted_contact_account_id)
        .expect("Invalid keys for account");
    let accept_response = client
        .update_recovery_relationship(
            &trusted_contact_account_id.to_string(),
            &invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string(),
            &UpdateRecoveryRelationshipRequest::Accept {
                code: code_override.apply(&invitation.code),
                customer_alias: CUSTOMER_ALIAS.to_string(),
                trusted_contact_enrollment_pake_pubkey: TRUSTED_CONTACT_ENROLLMENT_PAKE_PUBKEY
                    .to_string(),
                enrollment_pake_confirmation: "RANDOM_PAKE_CONFIRMATION".to_string(),
                sealed_delegated_decryption_pubkey: "SEALED_PUBKEY".to_string(),
            },
            auth,
            &keys,
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

        assert_relationship_counts(
            client,
            customer_account_id,
            0,
            1,
            0,
            0,
            trusted_contact_role,
        )
        .await;
        assert_relationship_counts(
            client,
            trusted_contact_account_id,
            0,
            0,
            0,
            tc_expected_num_customers,
            trusted_contact_role,
        )
        .await;

        return Some(accept_body);
    }

    None
}

pub(super) async fn try_endorse_recovery_relationship(
    context: &TestContext,
    client: &TestClient,
    customer_account_id: &AccountId,
    trusted_contact_role: &TrustedContactRole,
    recovery_relationship_id: &RecoveryRelationshipId,
    endorsement_key_certificate: &str,
    expected_status_code: StatusCode,
) -> Option<EndorseRecoveryRelationshipsResponse> {
    let keys = context
        .get_authentication_keys_for_account_id(customer_account_id)
        .expect("Invalid keys for account");
    let endorse_response = client
        .endorse_recovery_relationship(
            &customer_account_id.to_string(),
            &EndorseRecoveryRelationshipsRequest {
                endorsements: vec![RecoveryRelationshipEndorsement {
                    recovery_relationship_id: recovery_relationship_id.to_owned(),
                    delegated_decryption_pubkey_certificate: endorsement_key_certificate
                        .to_string(),
                }],
            },
            &keys,
        )
        .await;

    assert_eq!(
        endorse_response.status_code, expected_status_code,
        "{:?}",
        endorse_response.body_string
    );

    if expected_status_code == StatusCode::OK {
        let endorse_body: EndorseRecoveryRelationshipsResponse = endorse_response.body.unwrap();

        assert!(endorse_body
            .endorsed_trusted_contacts
            .iter()
            .map(|tc| tc
                .recovery_relationship_info
                .recovery_relationship_id
                .to_string())
            .collect::<Vec<String>>()
            .contains(&recovery_relationship_id.to_string()),);

        assert_relationship_counts(
            client,
            customer_account_id,
            0,
            0,
            1,
            0,
            trusted_contact_role,
        )
        .await;

        return Some(endorse_body);
    }
    None
}

pub async fn create_beneficiary_account(
    beneficiary_account_type: types::account::AccountType,
    context: &mut TestContext,
    bootstrap: &Bootstrap,
    client: &TestClient,
) -> (Account, Option<Wallet<AnyDatabase>>) {
    let (acct, wallet) =
        create_nontest_default_account_with_predefined_wallet(context, client, &bootstrap.services)
            .await;
    match beneficiary_account_type {
        types::account::AccountType::Full { .. } => (Account::Full(acct), Some(wallet)),
        types::account::AccountType::Lite => (
            Account::Lite(create_lite_account(context, &bootstrap.services, None, true).await),
            None,
        ),
        types::account::AccountType::Software => {
            panic!("Software account type not implemented in tests");
        }
    }
}

pub async fn assert_notifications(
    bootstrap: &Bootstrap,
    account_id: &AccountId,
    expected_customer_notifications_types: Vec<NotificationPayloadType>,
    expected_scheduled_notifications_types: Vec<NotificationPayloadType>,
) {
    let scheduled_notifications_types = {
        let mut notifications = bootstrap
            .services
            .notification_service
            .fetch_scheduled_for_account(FetchForAccountInput {
                account_id: account_id.clone(),
            })
            .await
            .unwrap();

        notifications.sort_by_key(|n| {
            // Get rid of jitter
            let jitter = n
                .schedule
                .as_ref()
                .and_then(|s| s.jitter)
                .unwrap_or(Duration::seconds(0));

            n.execution_date_time - jitter
        });
        notifications
            .iter()
            .map(|n| n.payload_type)
            .collect::<Vec<NotificationPayloadType>>()
    };

    let customer_notifications_types = {
        let mut notifications = bootstrap
            .services
            .notification_service
            .fetch_customer_for_account(FetchForAccountInput {
                account_id: account_id.clone(),
            })
            .await
            .unwrap();

        notifications.sort_by_key(|n| n.created_at);
        notifications
            .iter()
            .map(|n| n.payload_type)
            .collect::<Vec<NotificationPayloadType>>()
    };

    assert_eq!(
        customer_notifications_types,
        expected_customer_notifications_types
    );

    assert_eq!(
        scheduled_notifications_types,
        expected_scheduled_notifications_types
    );
}
