use crate::tests;
use account::entities::{Account, Network};
use http::StatusCode;
use recovery::routes::{
    FetchSocialChallengeResponse, RespondToSocialChallengeRequest,
    RespondToSocialChallengeResponse, StartChallengeTrustedContactRequest,
    StartSocialChallengeRequest, StartSocialChallengeResponse, VerifySocialChallengeCodeRequest,
    VerifySocialChallengeCodeResponse,
};
use types::{
    account::identifiers::AccountId,
    recovery::social::{
        challenge::{SocialChallengeId, TrustedContactChallengeRequest},
        relationship::RecoveryRelationshipId,
    },
};

use super::{
    gen_services,
    lib::{create_account, create_lite_account},
    recovery_relationship_integration_tests::{
        try_accept_recovery_relationship_invitation, try_create_recovery_relationship,
        try_endorse_recovery_relationship, AccountType, CodeOverride,
    },
    requests::{axum::TestClient, CognitoAuthentication},
};

const CUSTOMER_IDENTITY_PUBKEY: &str = "CustyIdentityPubkey";
const CUSTOMER_EPHEMERAL_PUBKEY: &str = "CustyEphemeralPubkey";
const CUSTOMER_RECOVERY_PUBKEY: &str = "CustyRecoveryPubkey";
const TRUSTED_CONTACT_RECOVERY_PUBKEY: &str = "TrustedContactRecoveryPubkey";
const SHARED_SECRET_CIPHERTEXT: &str = "SharedSecretCiphertext";

async fn try_start_social_challenge(
    client: &TestClient,
    customer_account_id: &AccountId,
    trusted_contacts: Vec<StartChallengeTrustedContactRequest>,
    expected_status_code: StatusCode,
) -> Option<StartSocialChallengeResponse> {
    let create_resp = client
        .start_social_challenge(
            &customer_account_id.to_string(),
            &StartSocialChallengeRequest {
                customer_identity_pubkey: CUSTOMER_IDENTITY_PUBKEY.to_owned(),
                customer_ephemeral_pubkey: CUSTOMER_EPHEMERAL_PUBKEY.to_owned(),
                trusted_contacts,
            },
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

async fn try_verify_social_challenge_code(
    client: &TestClient,
    trusted_contact_account_id: &AccountId,
    recovery_relationship_id: &RecoveryRelationshipId,
    code: &str,
    expected_status_code: StatusCode,
) -> Option<VerifySocialChallengeCodeResponse> {
    let verify_resp = client
        .verify_social_challenge_code(
            &trusted_contact_account_id.to_string(),
            &VerifySocialChallengeCodeRequest {
                recovery_relationship_id: recovery_relationship_id.to_owned(),
                code: code.to_owned(),
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
            body_ref.social_challenge.customer_identity_pubkey,
            CUSTOMER_IDENTITY_PUBKEY
        );
        assert_eq!(
            body_ref.social_challenge.customer_ephemeral_pubkey,
            CUSTOMER_EPHEMERAL_PUBKEY
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
                shared_secret_ciphertext: SHARED_SECRET_CIPHERTEXT.to_owned(),
                trusted_contact_recovery_pubkey: TRUSTED_CONTACT_RECOVERY_PUBKEY.to_owned(),
                recovery_key_confirmation: "".to_owned(),
                recovery_sealed_pkek: "".to_owned(),
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
            assert_eq!(r.shared_secret_ciphertext, SHARED_SECRET_CIPHERTEXT);
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

        let other_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;

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
struct VerifySocialChallengeCodeTestVector<'a> {
    is_trusted_contact: bool,
    is_trusted_contact_endorsed_by_customer: bool,
    override_code: Option<&'a str>,
    expected_status_code: StatusCode,
}

async fn verify_social_challenge_code_test(vector: VerifySocialChallengeCodeTestVector<'_>) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = create_lite_account(&bootstrap.services, None, true).await;
    let other_account = create_lite_account(&bootstrap.services, None, true).await;

    let create_relationship_body = try_create_recovery_relationship(
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
        if vector.is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &CognitoAuthentication::Recovery,
        &create_relationship_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await;

    if vector.is_trusted_contact_endorsed_by_customer {
        try_endorse_recovery_relationship(
            &client,
            &customer_account.id,
            &create_relationship_body.invitation.recovery_relationship_id,
            "ENDORSEMENT_CERT",
            StatusCode::OK,
        )
        .await;
    }

    // TODO(BKR-919): Add endorsement of recovery relationship once it's implemented
    let start_body = try_start_social_challenge(
        &client,
        &customer_account.id,
        vec![StartChallengeTrustedContactRequest {
            recovery_relationship_id: create_relationship_body
                .invitation
                .recovery_relationship_id
                .clone(),
            challenge_request: TrustedContactChallengeRequest {
                customer_recovery_pubkey: CUSTOMER_RECOVERY_PUBKEY.to_owned(),
                enrollment_sealed_pkek: "".to_owned(),
            },
        }],
        StatusCode::OK,
    )
    .await
    .unwrap();

    try_verify_social_challenge_code(
        &client,
        &tc_account.id,
        &create_relationship_body.invitation.recovery_relationship_id,
        vector
            .override_code
            .unwrap_or(&start_body.social_challenge.code),
        vector.expected_status_code,
    )
    .await;
}

tests! {
    runner = verify_social_challenge_code_test,
    test_verify_social_challenge_code_by_tc: VerifySocialChallengeCodeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: true,
        override_code: None,
        expected_status_code: StatusCode::OK,
    },
    test_verify_social_challenge_code_by_tc_without_endorsement: VerifySocialChallengeCodeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: false,
        override_code: None,
        expected_status_code: StatusCode::OK,
    },
    test_verify_social_challenge_code_by_not_tc: VerifySocialChallengeCodeTestVector {
        is_trusted_contact: false,
        is_trusted_contact_endorsed_by_customer: true,
        override_code: None,
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_verify_social_challenge_code_by_not_tc_without_endorsement: VerifySocialChallengeCodeTestVector {
        is_trusted_contact: false,
        is_trusted_contact_endorsed_by_customer: false,
        override_code: None,
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_verify_social_challenge_code_wrong_code: VerifySocialChallengeCodeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: true,
        override_code: Some("000000"),
        expected_status_code: StatusCode::NOT_FOUND,
    },
    test_verify_social_challenge_code_wrong_code_without_endorsement: VerifySocialChallengeCodeTestVector {
        is_trusted_contact: true,
        is_trusted_contact_endorsed_by_customer: false,
        override_code: Some("000000"),
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
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;
    let tc_account = create_lite_account(&bootstrap.services, None, true).await;
    let other_account = create_lite_account(&bootstrap.services, None, true).await;

    let create_relationship_body = try_create_recovery_relationship(
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
        if vector.is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &CognitoAuthentication::Recovery,
        &create_relationship_body.invitation,
        CodeOverride::None,
        StatusCode::OK,
    )
    .await;

    if vector.is_customer_endorsed {
        try_endorse_recovery_relationship(
            &client,
            &customer_account.id,
            &create_relationship_body.invitation.recovery_relationship_id,
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
                .recovery_relationship_id
                .clone(),
            challenge_request: TrustedContactChallengeRequest {
                customer_recovery_pubkey: CUSTOMER_RECOVERY_PUBKEY.to_owned(),
                enrollment_sealed_pkek: "".to_owned(),
            },
        }],
        StatusCode::OK,
    )
    .await
    .unwrap();

    let verify_body = try_verify_social_challenge_code(
        &client,
        if vector.is_trusted_contact {
            &tc_account.id
        } else {
            &other_account.id
        },
        &create_relationship_body.invitation.recovery_relationship_id,
        &start_body.social_challenge.code,
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
        expected_status_code: StatusCode::OK,
    },
    test_respond_to_social_challenge_by_not_tc: RespondToSocialChallengeTestVector {
        is_trusted_contact: false,
        is_customer_endorsed: true,
        expected_status_code: StatusCode::FORBIDDEN,
    },
    test_respond_to_social_challenge_by_not_tc_without_endorsement: RespondToSocialChallengeTestVector {
        is_trusted_contact: false,
        is_customer_endorsed: false,
        expected_status_code: StatusCode::FORBIDDEN,
    },
}
