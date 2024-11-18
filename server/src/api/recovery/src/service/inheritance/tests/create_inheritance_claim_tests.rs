use rstest::rstest;
use time::Duration;

use types::account::bitcoin::Network;
use types::account::entities::Account;
use types::recovery::inheritance::claim::InheritanceClaimAuthKeys;

use crate::service::inheritance::tests::{
    create_pending_inheritance_claim, setup_accounts_with_network,
};

fn get_auth_keys(account: &Account) -> InheritanceClaimAuthKeys {
    match account {
        Account::Full(full_account) => InheritanceClaimAuthKeys::FullAccount(
            full_account
                .active_auth_keys()
                .expect("Account has active auth keys")
                .to_owned(),
        ),
        _ => panic!("Account is not a full account"),
    }
}

// Tests
#[rstest]
#[case::test_account(Network::BitcoinSignet, Duration::minutes(1))]
#[case::non_test_account(Network::BitcoinMain, Duration::days(180))]
#[tokio::test]
async fn test_create_pending_claim(#[case] network: Network, #[case] offset: Duration) {
    // arrange
    let (benefactor_account, beneficiary_account) = setup_accounts_with_network(network).await;
    let current_auth_keys = get_auth_keys(&beneficiary_account);

    // act
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &current_auth_keys,
        None,
    )
    .await;

    // assert
    assert_eq!(
        pending_claim.common_fields.created_at + offset,
        pending_claim.delay_end_time
    );
}
