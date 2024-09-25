use crate::tests::lib::{create_full_account, create_lite_account};
use crate::tests::recovery::shared::AccountType;
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::CognitoAuthentication;
use crate::tests::{gen_services, TestContext};
use crate::Bootstrap;
use account::entities::Account;
use axum::body::Body;
use http::{Method, StatusCode};
use recovery::routes::{CreateRelationshipRequest, GetRelationshipsRequest};
use regex::Regex;
use rstest::rstest;
use serde_json::json;
use types::account::bitcoin::Network;
use types::recovery::trusted_contacts::TrustedContactRole;
use types::recovery::trusted_contacts::TrustedContactRole::Beneficiary;
use types::recovery::trusted_contacts::TrustedContactRole::SocialRecoveryContact;

const VALID_CREATE_RESPONSE_PATTERN: &str = r#"\{"invitation":\{"recovery_relationship_id":"urn:wallet-recovery-relationship:[^"]+","trusted_contact_alias":"some_alias","trusted_contact_roles":__TC_ROLES__,"code":"[a-zA-Z0-9]+","code_bit_length":\d+,"expires_at":"[^"]+"\}\}"#;

#[rstest]
#[case::full_account_socrec(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: true },
    vec![SocialRecoveryContact],
    StatusCode::OK,
    VALID_CREATE_RESPONSE_PATTERN
)]
#[case::full_account_beneficiary(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: true },
    vec![Beneficiary],
    StatusCode::OK,
    VALID_CREATE_RESPONSE_PATTERN
)]
#[case::full_account_multi_role(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: true },
    vec![Beneficiary, SocialRecoveryContact],
    StatusCode::OK,
    VALID_CREATE_RESPONSE_PATTERN
)]
#[case::full_account_no_app_sig(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: false, is_hardware_signed: true },
    vec![SocialRecoveryContact],
    StatusCode::FORBIDDEN,
    "valid signature over access token required by both app and hw auth keys"
)]
#[case::full_account_no_hw_sig(
    AccountType::Full,
    CognitoAuthentication::Wallet{ is_app_signed: true, is_hardware_signed: false },
    vec![SocialRecoveryContact],
    StatusCode::FORBIDDEN,
    "valid signature over access token required by both app and hw auth keys"
)]
#[case::lite_account(
    AccountType::Full,
    CognitoAuthentication::Recovery,
    vec![SocialRecoveryContact],
    StatusCode::UNAUTHORIZED,
    ""
)]
#[tokio::test]
async fn test_create_relationship(
    #[case] customer_account_type: AccountType,
    #[case] auth: CognitoAuthentication,
    #[case] tc_roles: Vec<TrustedContactRole>,
    #[case] expected_status_code: StatusCode,
    #[case] expected_response_pattern: &str,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let customer_account =
        create_customer_account(customer_account_type, &mut context, &bootstrap).await;
    let keys = context
        .get_authentication_keys_for_account_id(customer_account.get_id())
        .expect("Invalid keys for account");
    let trusted_contact_alias = "some_alias";
    let pake_pubkey = "2ec146ad388773e9b40c520fae98d99273878c9a9ea062a1283235e9e1191417";

    // act
    let uri = format!("/api/accounts/{}/relationships", customer_account.get_id());
    let body = json!({
        "protected_customer_enrollment_pake_pubkey": pake_pubkey,
        "trusted_contact_alias": trusted_contact_alias,
        "trusted_contact_roles": tc_roles
    })
    .to_string();
    let response = client
        .make_request_with_auth::<CreateRelationshipRequest>(
            &uri,
            &customer_account.get_id().to_string(),
            &Method::POST,
            Body::from(body),
            &auth,
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, expected_status_code);

    let re = get_expected_response_regex(&tc_roles, expected_response_pattern);

    assert!(
        re.is_match(&response.body_string),
        "Expected pattern: {}\nReceived response: {}",
        expected_response_pattern,
        &response.body_string,
    );
}

const VALID_GET_RESPONSE_PATTERN: &str = r#"\{"invitations":\[\{"recovery_relationship_id":"urn:wallet-recovery-relationship:[^"]+","trusted_contact_alias":"some_alias","trusted_contact_roles":__TC_ROLES__,"code":"[a-zA-Z0-9]+","code_bit_length":\d+,"expires_at":"[^"]+"\}\],"unendorsed_trusted_contacts":\[\],"endorsed_trusted_contacts":\[\],"customers":\[\]\}"#;
const EMPTY_GET_RESPONSE_PATTERN: &str = r#"\{"invitations":\[\],"unendorsed_trusted_contacts":\[\],"endorsed_trusted_contacts":\[\],"customers":\[\]\}"#;

#[rstest]
#[case::get_socrec_relations_exists(
    vec![SocialRecoveryContact],
    SocialRecoveryContact,
    StatusCode::OK,
    VALID_GET_RESPONSE_PATTERN
)]
#[case::get_beneficiary_relations_exists(
    vec![Beneficiary],
    Beneficiary,
    StatusCode::OK,
    VALID_GET_RESPONSE_PATTERN
)]
#[case::get_beneficiary_relations_multi_roles(
    vec![SocialRecoveryContact, Beneficiary],
    Beneficiary,
    StatusCode::OK,
    VALID_GET_RESPONSE_PATTERN
)]
#[case::get_beneficiary_relations_none_exists(
    vec![SocialRecoveryContact],
    Beneficiary,
    StatusCode::OK,
    EMPTY_GET_RESPONSE_PATTERN
)]
#[tokio::test]
async fn test_get_relationships(
    #[case] existing_tc_roles: Vec<TrustedContactRole>,
    #[case] target_tc_role: TrustedContactRole,
    #[case] expected_status_code: StatusCode,
    #[case] expected_response_pattern: &str,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let customer_account =
        create_customer_account(AccountType::Full, &mut context, &bootstrap).await;
    let keys = context
        .get_authentication_keys_for_account_id(customer_account.get_id())
        .expect("Invalid keys for account");

    let body = json!({
        "protected_customer_enrollment_pake_pubkey": "2ec146ad388773e9b40c520fae98d99273878c9a9ea062a1283235e9e1191417",
        "trusted_contact_alias": "some_alias",
        "trusted_contact_roles": existing_tc_roles
    }).to_string();

    client
        .make_request_with_auth::<CreateRelationshipRequest>(
            &format!("/api/accounts/{}/relationships", customer_account.get_id()),
            &customer_account.get_id().to_string(),
            &Method::POST,
            Body::from(body),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
            &keys,
        )
        .await;

    // act
    let uri = format!(
        "/api/accounts/{}/relationships?trusted_contact_role={}",
        customer_account.get_id(),
        target_tc_role
    );
    let response = client
        .make_request_with_auth::<GetRelationshipsRequest>(
            &uri,
            &customer_account.get_id().to_string(),
            &Method::GET,
            Body::empty(),
            &CognitoAuthentication::Recovery,
            &keys,
        )
        .await;

    // assert
    assert_eq!(response.status_code, expected_status_code);

    let re = get_expected_response_regex(&existing_tc_roles, expected_response_pattern);
    assert!(
        re.is_match(&response.body_string),
        "Expected pattern: {}\nReceived response: {}",
        expected_response_pattern,
        &response.body_string,
    );
}

async fn create_customer_account(
    customer_account_type: AccountType,
    context: &mut TestContext,
    bootstrap: &Bootstrap,
) -> Account {
    match customer_account_type {
        AccountType::Full { .. } => Account::Full(
            create_full_account(context, &bootstrap.services, Network::BitcoinSignet, None).await,
        ),
        AccountType::Lite => {
            Account::Lite(create_lite_account(context, &bootstrap.services, None, true).await)
        }
    }
}

fn get_expected_response_regex(
    tc_roles: &Vec<TrustedContactRole>,
    expected_response_pattern: &str,
) -> Regex {
    let tc_roles_json = serde_json::to_string(&tc_roles)
        .unwrap()
        .replace('[', r"\[")
        .replace(']', r"\]");
    let expected_response_pattern =
        expected_response_pattern.replace("__TC_ROLES__", &tc_roles_json);
    Regex::new(expected_response_pattern.as_str()).unwrap()
}
