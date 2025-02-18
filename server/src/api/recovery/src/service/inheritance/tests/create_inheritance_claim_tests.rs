use rstest::rstest;
use time::Duration;

use types::account::bitcoin::Network;

use crate::service::inheritance::tests::{
    create_pending_inheritance_claim, get_auth_keys, setup_accounts_with_network,
};

// Tests
#[rstest]
#[case::test_account(Network::BitcoinSignet, Duration::minutes(10))]
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
