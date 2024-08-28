use super::recovery_relationship_integration_tests::{
    try_accept_recovery_relationship_invitation, try_create_recovery_relationship,
    try_endorse_recovery_relationship, AccountType, CodeOverride,
};
use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::{create_account, create_lite_account};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::CognitoAuthentication;
use account::{
    entities::{Account, Network},
    spend_limit::{Money, SpendingLimit},
};
use http::StatusCode;
use mobile_pay::routes::MobilePaySetupRequest;
use rand::Rng;
use recovery::{
    routes::{
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
use types::{
    account::identifiers::AccountId,
    currencies::CurrencyCode,
    recovery::social::{
        challenge::{SocialChallengeId, TrustedContactChallengeRequest},
        relationship::RecoveryRelationshipId,
    },
};

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

#[derive(Debug)]
struct StartSocialChallengeTestVector {
    customer_account_type: AccountType,
    expected_status_code: StatusCode,
}

async fn start_social_challenge_test(vector: StartSocialChallengeTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = match vector.customer_account_type {
        AccountType::Full { .. } => {
            let account = create_account(
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
        vector.expected_status_code,
    )
    .await;

    if vector.expected_status_code.is_success() {
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

        let other_account = create_account(
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

tests! {
    runner = start_social_challenge_test,
    test_create_social_challenge_full_account: StartSocialChallengeTestVector {
        customer_account_type: AccountType::Full,
        expected_status_code: StatusCode::OK,
    },
    test_create_social_challenge_lite_account: StartSocialChallengeTestVector {
        customer_account_type: AccountType::Lite,
        expected_status_code: StatusCode::INTERNAL_SERVER_ERROR,
    },
}

#[derive(Debug)]
struct VerifySocialChallengeTestVector {
    is_trusted_contact: bool,
    is_trusted_contact_endorsed_by_customer: bool,
    override_counter: bool,
    expected_status_code: StatusCode,
}

async fn verify_social_challenge_test(vector: VerifySocialChallengeTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let tc_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let other_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let create_relationship_body = try_create_recovery_relationship(
        &mut context,
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
        if vector.is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &CognitoAuthentication::Recovery,
        &create_relationship_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await;

    if vector.is_trusted_contact_endorsed_by_customer {
        try_endorse_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
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
        if vector.is_trusted_contact_endorsed_by_customer {
            StatusCode::OK
        } else {
            StatusCode::BAD_REQUEST
        },
    )
    .await;

    // If the trusted contact isn't endorsed, there's no point in continuing
    if !vector.is_trusted_contact_endorsed_by_customer {
        return;
    }

    let start_body = start_body.unwrap();
    let mut rng = rand::thread_rng();
    let counter = if vector.override_counter {
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
        vector.expected_status_code,
    )
    .await;
}

tests! {
    runner = verify_social_challenge_test,
    test_verify_social_challenge_by_tc: VerifySocialChallengeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: true,
        override_counter: false,
        expected_status_code: StatusCode::OK,
    },
    test_verify_social_challenge_by_tc_without_endorsement: VerifySocialChallengeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: false,
        override_counter: false,
        expected_status_code: StatusCode::OK,
    },
    test_verify_social_challenge_by_not_tc: VerifySocialChallengeTestVector {
        is_trusted_contact: false,
        is_trusted_contact_endorsed_by_customer: true,
        override_counter: false,
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_verify_social_challenge_by_not_tc_without_endorsement: VerifySocialChallengeTestVector {
        is_trusted_contact: false,
        is_trusted_contact_endorsed_by_customer: false,
        override_counter: false,
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_verify_social_challenge_wrong_counter: VerifySocialChallengeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: true,
        override_counter: true,
        expected_status_code: StatusCode::NOT_FOUND,
    },
    test_verify_social_challenge_wrong_counter_without_endorsement: VerifySocialChallengeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: false,
        override_counter: true,
        expected_status_code: StatusCode::NOT_FOUND,
    },
}

#[derive(Debug)]
struct RespondToSocialChallengeTestVector {
    is_trusted_contact: bool,
    is_customer_endorsed: bool,
    expected_status_code: StatusCode,
}

async fn respond_to_social_challenge_test(vector: RespondToSocialChallengeTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(
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
        if vector.is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &CognitoAuthentication::Recovery,
        &create_relationship_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
        1,
    )
    .await;

    if vector.is_customer_endorsed {
        try_endorse_recovery_relationship(
            &context,
            &client,
            &customer_account.id,
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
        if vector.is_customer_endorsed {
            StatusCode::OK
        } else {
            StatusCode::BAD_REQUEST
        },
    )
    .await;

    // If the customer isn't endorsed, there's no point in continuing
    if !vector.is_customer_endorsed {
        return;
    }

    let start_body = start_body.unwrap();
    let verify_body = try_verify_social_challenge(
        &client,
        if vector.is_trusted_contact {
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
        vector.expected_status_code,
    )
    .await;

    if vector.expected_status_code.is_success() {
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
            vector.expected_status_code,
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

tests! {
    runner = respond_to_social_challenge_test,
    test_respond_to_social_challenge_by_tc: RespondToSocialChallengeTestVector {
        is_trusted_contact: true,
        is_customer_endorsed: true,
        expected_status_code: StatusCode::OK,
    },
    test_respond_to_social_challenge_by_tc_without_endorsement: RespondToSocialChallengeTestVector {
        is_trusted_contact: true,
        is_customer_endorsed: false,
        expected_status_code: StatusCode::BAD_REQUEST,
    },
    test_respond_to_social_challenge_by_not_tc: RespondToSocialChallengeTestVector {
        is_trusted_contact: false,
        is_customer_endorsed: true,
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_respond_to_social_challenge_by_not_tc_without_endorsement: RespondToSocialChallengeTestVector {
        is_trusted_contact: false,
        is_customer_endorsed: false,
        expected_status_code: StatusCode::BAD_REQUEST,
    },
}
