use http::StatusCode;
use mobile_pay::routes::MobilePaySetupRequest;
use rand::Rng;
use recovery::{
    routes::social_challenge::{
        FetchSocialChallengeResponse, RespondToSocialChallengeRequest,
        RespondToSocialChallengeResponse, StartChallengeTrustedContactRequest,
        StartSocialChallengeRequest, StartSocialChallengeResponse, VerifySocialChallengeRequest,
        VerifySocialChallengeResponse,
    },
    service::social::challenge::{
        clear_social_challenges::ClearSocialChallengesInput,
        fetch_social_challenge::CountSocialChallengesInput,
    },
};
use time::UtcOffset;
use types::account::bitcoin::Network;
use types::account::{entities::Account, money::Money, spend_limit::SpendingLimit};
use types::recovery::trusted_contacts::TrustedContactRole;
use types::{
    account::identifiers::AccountId,
    currencies::CurrencyCode,
    recovery::social::{
        challenge::{SocialChallengeId, TrustedContactChallengeRequest},
        relationship::RecoveryRelationshipId,
    },
};

use super::shared::{
    try_accept_recovery_relationship_invitation, try_create_recovery_relationship,
    try_endorse_recovery_relationship, AccountType, CodeOverride,
};
use rstest::rstest;

use crate::tests::gen_services;
use crate::tests::lib::{create_full_account, create_lite_account};
use crate::tests::recovery::shared::try_create_relationship;
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::CognitoAuthentication;

const PROTECTED_CUSTOMER_RECOVERY_PAKE_PUBKEY: &str =
    "005abf297a64bac071986e41c4dddf8160fe245f9f889699c9c57c35fa6d56f5";
const TRUSTED_CONTACT_RECOVERY_PAKE_PUBKEY: &str =
    "006abf297a64bac071986e41c4dddf8160fe245f9f889699c9c57c35fa6d56f6";
const RECOVERY_PAKE_CONFIRMATION: &str = "RecoveryPakeConfirmation";

async fn try_start_social_challenge(
    client: &TestClient,
    customer_account_id: &AccountId,
    trusted_contacts: Vec<StartChallengeTrustedContactRequest>,
    expected_status_code: StatusCode,
) -> Option<StartSocialChallengeResponse> {
    let create_resp = client
        .start_social_challenge(
            &customer_account_id.to_string(),
            &StartSocialChallengeRequest { trusted_contacts },
        )
        .await;

    assert_eq!(
        create_resp.status_code, expected_status_code,
        "{:?}",
        create_resp.body_string
    );

    if expected_status_code.is_success() {
        let body_ref = create_resp.body.as_ref().unwrap();

        assert_eq!(body_ref.social_challenge.responses.len(), 0);

        Some(create_resp.body.unwrap())
    } else {
        None
    }
}

async fn try_verify_social_challenge(
    client: &TestClient,
    trusted_contact_account_id: &AccountId,
    recovery_relationship_id: &RecoveryRelationshipId,
    counter: u32,
    expected_status_code: StatusCode,
) -> Option<VerifySocialChallengeResponse> {
    let verify_resp = client
        .verify_social_challenge(
            &trusted_contact_account_id.to_string(),
            &VerifySocialChallengeRequest {
                recovery_relationship_id: recovery_relationship_id.to_owned(),
                counter,
            },
        )
        .await;

    assert_eq!(
        verify_resp.status_code, expected_status_code,
        "{:?}",
        verify_resp.body_string
    );

    if expected_status_code.is_success() {
        let body_ref = verify_resp.body.as_ref().unwrap();
        assert_eq!(
            body_ref
                .social_challenge
                .protected_customer_recovery_pake_pubkey,
            PROTECTED_CUSTOMER_RECOVERY_PAKE_PUBKEY
        );
        Some(verify_resp.body.unwrap())
    } else {
        None
    }
}

async fn try_respond_to_social_challenge(
    client: &TestClient,
    trusted_contact_account_id: &AccountId,
    social_challenge_id: &SocialChallengeId,
    expected_status_code: StatusCode,
) -> Option<RespondToSocialChallengeResponse> {
    let respond_resp = client
        .respond_to_social_challenge(
            &trusted_contact_account_id.to_string(),
            &social_challenge_id.to_string(),
            &RespondToSocialChallengeRequest {
                trusted_contact_recovery_pake_pubkey: TRUSTED_CONTACT_RECOVERY_PAKE_PUBKEY
                    .to_owned(),
                recovery_pake_confirmation: RECOVERY_PAKE_CONFIRMATION.to_owned(),
                resealed_dek: "".to_owned(),
            },
        )
        .await;

    assert_eq!(
        respond_resp.status_code, expected_status_code,
        "{:?}",
        respond_resp.body_string
    );

    if expected_status_code.is_success() {
        Some(respond_resp.body.unwrap())
    } else {
        None
    }
}

async fn try_fetch_social_challenge(
    client: &TestClient,
    customer_account_id: &AccountId,
    social_challenge_id: &SocialChallengeId,
    expected_status_code: StatusCode,
    expected_num_responses: usize,
) -> Option<FetchSocialChallengeResponse> {
    let fetch_resp = client
        .fetch_social_challenge(
            &customer_account_id.to_string(),
            &social_challenge_id.to_string(),
        )
        .await;

    assert_eq!(
        fetch_resp.status_code, expected_status_code,
        "{:?}",
        fetch_resp.body_string
    );

    if expected_status_code.is_success() {
        let body_ref = fetch_resp.body.as_ref().unwrap();

        assert_eq!(
            body_ref.social_challenge.responses.len(),
            expected_num_responses
        );

        body_ref.social_challenge.responses.iter().for_each(|r| {
            assert_eq!(
                r.trusted_contact_recovery_pake_pubkey,
                TRUSTED_CONTACT_RECOVERY_PAKE_PUBKEY
            );
            assert_eq!(r.recovery_pake_confirmation, RECOVERY_PAKE_CONFIRMATION);
        });

        Some(fetch_resp.body.unwrap())
    } else {
        None
    }
}

#[rstest]
#[case::create_social_challenge_full_account(AccountType::Full, StatusCode::OK)]
#[case::create_social_challenge_lite_account(AccountType::Lite, StatusCode::INTERNAL_SERVER_ERROR)]
#[tokio::test]
async fn test_start_social_challenge(
    #[case] customer_account_type: AccountType,
    #[case] expected_status_code: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = match customer_account_type {
        AccountType::Full => {
            let account = create_full_account(
                &mut context,
                &bootstrap.services,
                Network::BitcoinSignet,
                None,
            )
            .await;
            let keys = context
                .get_authentication_keys_for_account_id(&account.id)
                .expect("Invalid keys for account");

            let mobile_pay_resp = client
                .put_mobile_pay(
                    &account.id,
                    &MobilePaySetupRequest {
                        limit: SpendingLimit {
                            active: true,
                            amount: Money {
                                amount: 100,
                                currency_code: CurrencyCode::USD,
                            },
                            time_zone_offset: UtcOffset::UTC,
                        },
                    },
                    &keys,
                )
                .await;
            assert!(mobile_pay_resp.status_code.is_success());

            Account::Full(account)
        }
        AccountType::Lite => {
            Account::Lite(create_lite_account(&mut context, &bootstrap.services, None, true).await)
        }
    };

    let start_body = try_start_social_challenge(
        &client,
        customer_account.get_id(),
        vec![],
        expected_status_code,
    )
    .await;

    if expected_status_code.is_success() {
        // Customer can fetch the challege
        try_fetch_social_challenge(
            &client,
            customer_account.get_id(),
            &start_body
                .as_ref()
                .unwrap()
                .social_challenge
                .social_challenge_id,
            StatusCode::OK,
            0,
        )
        .await;

        let other_account = create_full_account(
            &mut context,
            &bootstrap.services,
            Network::BitcoinSignet,
            None,
        )
        .await;

        // Non-customer cannot fetch the challege
        try_fetch_social_challenge(
            &client,
            &other_account.id,
            &start_body
                .as_ref()
                .unwrap()
                .social_challenge
                .social_challenge_id,
            StatusCode::FORBIDDEN,
            0,
        )
        .await;

        // Mobile pay is disabled
        let keys = context
            .get_authentication_keys_for_account_id(customer_account.get_id())
            .expect("Invalid keys for account");
        let mobile_pay_resp = client
            .get_mobile_pay(customer_account.get_id(), &keys)
            .await;
        assert!(mobile_pay_resp.status_code.is_success());
        let body = mobile_pay_resp.body.unwrap();
        assert!(!body.mobile_pay().unwrap().limit.active);
    }
}

async fn verify_social_challenge_test(
    is_trusted_contact: bool,
    is_trusted_contact_endorsed_by_customer: bool,
    override_counter: bool,
    expected_status_code: StatusCode,
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
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let other_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let create_relationship_body = try_create_recovery_relationship(
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
        if is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_relationship_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await;

    if is_trusted_contact_endorsed_by_customer {
        try_endorse_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
            &TrustedContactRole::SocialRecoveryContact,
            &create_relationship_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id,
            "ENDORSEMENT_CERT",
            StatusCode::OK,
        )
        .await;
    }

    let start_body = try_start_social_challenge(
        &client,
        &customer_account.id,
        vec![StartChallengeTrustedContactRequest {
            recovery_relationship_id: create_relationship_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .clone(),
            challenge_request: TrustedContactChallengeRequest {
                protected_customer_recovery_pake_pubkey: PROTECTED_CUSTOMER_RECOVERY_PAKE_PUBKEY
                    .to_owned(),
                sealed_dek: "".to_owned(),
            },
        }],
        if is_trusted_contact_endorsed_by_customer {
            StatusCode::OK
        } else {
            StatusCode::BAD_REQUEST
        },
    )
    .await;

    // If the trusted contact isn't endorsed, there's no point in continuing
    if !is_trusted_contact_endorsed_by_customer {
        return;
    }

    let start_body = start_body.unwrap();
    let mut rng = rand::thread_rng();
    let counter = if override_counter {
        rng.gen::<u32>()
    } else {
        start_body.social_challenge.counter
    };
    try_verify_social_challenge(
        &client,
        &tc_account.id,
        &create_relationship_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
        counter,
        expected_status_code,
    )
    .await;
}

#[rstest]
#[case::by_tc(true, true, false, StatusCode::OK)]
#[case::by_tc_without_endorsement(true, false, false, StatusCode::OK)]
#[case::by_not_tc(false, true, false, StatusCode::FORBIDDEN)]
#[case::by_not_tc_without_endorsement(false, false, false, StatusCode::FORBIDDEN)]
#[case::wrong_counter(true, true, true, StatusCode::NOT_FOUND)]
#[case::wrong_counter_without_endorsement(true, false, true, StatusCode::NOT_FOUND)]
#[tokio::test]
async fn test_verify_social_challenge(
    #[case] is_trusted_contact: bool,
    #[case] is_trusted_contact_endorsed_by_customer: bool,
    #[case] override_counter: bool,
    #[case] expected_status_code: StatusCode,
) {
    verify_social_challenge_test(
        is_trusted_contact,
        is_trusted_contact_endorsed_by_customer,
        override_counter,
        expected_status_code,
    )
    .await
}

async fn respond_to_social_challenge_test(
    is_trusted_contact: bool,
    is_customer_endorsed: bool,
    expected_status_code: StatusCode,
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
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let other_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let create_relationship_body = try_create_recovery_relationship(
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
        if is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &TrustedContactRole::SocialRecoveryContact,
        &CognitoAuthentication::Recovery,
        &create_relationship_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await;

    if is_customer_endorsed {
        try_endorse_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
            &TrustedContactRole::SocialRecoveryContact,
            &create_relationship_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id,
            "ENDORSEMENT_CERT",
            StatusCode::OK,
        )
        .await;
    }

    let start_body = try_start_social_challenge(
        &client,
        &customer_account.id,
        vec![StartChallengeTrustedContactRequest {
            recovery_relationship_id: create_relationship_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .clone(),
            challenge_request: TrustedContactChallengeRequest {
                protected_customer_recovery_pake_pubkey: PROTECTED_CUSTOMER_RECOVERY_PAKE_PUBKEY
                    .to_owned(),
                sealed_dek: "".to_owned(),
            },
        }],
        if is_customer_endorsed {
            StatusCode::OK
        } else {
            StatusCode::BAD_REQUEST
        },
    )
    .await;

    // If the customer isn't endorsed, there's no point in continuing
    if !is_customer_endorsed {
        return;
    }

    let start_body = start_body.unwrap();
    let verify_body = try_verify_social_challenge(
        &client,
        if is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &create_relationship_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
        start_body.social_challenge.counter,
        StatusCode::OK,
    )
    .await
    .unwrap();

    try_respond_to_social_challenge(
        &client,
        &tc_account.id,
        &verify_body.social_challenge.social_challenge_id,
        expected_status_code,
    )
    .await;

    if expected_status_code.is_success() {
        // Fetched challenge reflects response
        try_fetch_social_challenge(
            &client,
            &customer_account.id,
            &start_body.social_challenge.social_challenge_id,
            StatusCode::OK,
            1,
        )
        .await;

        try_respond_to_social_challenge(
            &client,
            &tc_account.id,
            &verify_body.social_challenge.social_challenge_id,
            expected_status_code,
        )
        .await;

        // Second response from same contact overwrites the first
        try_fetch_social_challenge(
            &client,
            &customer_account.id,
            &start_body.social_challenge.social_challenge_id,
            StatusCode::OK,
            1,
        )
        .await;
    }

    let num_social_challenges = bootstrap
        .services
        .social_challenge_service
        .count_social_challenges(CountSocialChallengesInput {
            customer_account_id: &customer_account.id,
        })
        .await
        .unwrap();
    assert_eq!(num_social_challenges, 1);
    bootstrap
        .services
        .social_challenge_service
        .clear_social_challenges(ClearSocialChallengesInput {
            customer_account_id: &customer_account.id,
        })
        .await
        .unwrap();
    let num_social_challenges = bootstrap
        .services
        .social_challenge_service
        .count_social_challenges(CountSocialChallengesInput {
            customer_account_id: &customer_account.id,
        })
        .await
        .unwrap();
    assert_eq!(num_social_challenges, 0);
}

#[rstest]
#[case::by_tc(true, true, StatusCode::OK)]
#[case::by_tc_without_endorsement(true, false, StatusCode::BAD_REQUEST)]
#[case::by_not_tc(false, true, StatusCode::FORBIDDEN)]
#[case::by_not_tc_without_endorsement(false, false, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn test_respond_to_social_challenge(
    #[case] is_trusted_contact: bool,
    #[case] is_customer_endorsed: bool,
    #[case] expected_status_code: StatusCode,
) {
    respond_to_social_challenge_test(
        is_trusted_contact,
        is_customer_endorsed,
        expected_status_code,
    )
    .await
}

#[tokio::test]
async fn test_start_social_challenge_with_beneficiary() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let tc_account = create_full_account(
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

    let _initial_response = try_accept_recovery_relationship_invitation(
        &context,
        &client,
        &customer_account.id,
        &tc_account.id,
        &TrustedContactRole::Beneficiary,
        &CognitoAuthentication::Recovery,
        &create_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await
    .unwrap();

    try_endorse_recovery_relationship(
        &context,
        &client,
        &customer_account.id,
        &TrustedContactRole::Beneficiary,
        &create_body
            .invitation
            .recovery_relationship_info
            .recovery_relationship_id,
        "ENDORSEMENT_CERT",
        StatusCode::OK,
    )
    .await;

    try_start_social_challenge(
        &client,
        &customer_account.id,
        vec![StartChallengeTrustedContactRequest {
            recovery_relationship_id: create_body
                .invitation
                .recovery_relationship_info
                .recovery_relationship_id
                .clone(),
            challenge_request: TrustedContactChallengeRequest {
                protected_customer_recovery_pake_pubkey: PROTECTED_CUSTOMER_RECOVERY_PAKE_PUBKEY
                    .to_owned(),
                sealed_dek: "".to_owned(),
            },
        }],
        StatusCode::BAD_REQUEST,
    )
    .await;
}
