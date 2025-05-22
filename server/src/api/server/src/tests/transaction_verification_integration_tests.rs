use crate::tests::gen_services;
use crate::tests::lib::create_default_account_with_predefined_wallet;
use crate::tests::requests::axum::TestClient;
use transaction_verification::routes::PutTransactionVerificationPolicyRequest;
use types::account::entities::TransactionVerificationPolicy;
use types::account::money::Money;
use types::currencies::CurrencyCode::USD;

#[tokio::test]
async fn update_transaction_verification_policy_test() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;

    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();
    let resp = client
        .get_transaction_verification_policy(&account.id, &keys)
        .await;

    let expected_policy = TransactionVerificationPolicy::Never;
    assert_eq!(resp.body.unwrap().policy, expected_policy);

    // Create a new transaction verification policy
    let new_policy = TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    });

    // Update the transaction verification policy
    client
        .update_transaction_verification_policy(
            &account.id,
            true, // app signed only
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: new_policy.clone(),
            },
        )
        .await;

    let resp = client
        .get_transaction_verification_policy(&account.id, &keys)
        .await;

    assert_eq!(resp.body.unwrap().policy, new_policy);
}
