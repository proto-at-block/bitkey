use rstest::rstest;

use types::account::bitcoin::Network;
use types::recovery::inheritance::claim::InheritanceClaimCanceledBy;

use crate::service::inheritance::has_incompleted_claim::HasIncompletedClaimInput;
use crate::service::inheritance::tests::{
    construct_test_inheritance_service, create_canceled_claim, create_completed_claim,
    create_locked_claim, create_pending_inheritance_claim, get_auth_keys,
    setup_accounts_with_network,
};

enum InheritanceClaimState {
    Pending,
    Locked,
    Completed,
    Cancelled,
}

// Tests
#[rstest]
#[case::with_pending_claim(InheritanceClaimState::Pending, true)]
#[case::with_locked_claim(InheritanceClaimState::Locked, true)]
#[case::with_completed_claim(InheritanceClaimState::Completed, false)]
#[case::with_cancelled_claim(InheritanceClaimState::Cancelled, false)]
#[tokio::test]
async fn test_has_incomplete_claim(
    #[case] claim_state: InheritanceClaimState,
    #[case] has_incomplete_claim: bool,
) {
    // arrange

    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinSignet).await;
    let current_auth_keys = get_auth_keys(&beneficiary_account);

    // act
    let recovery_relationship_id = match claim_state {
        InheritanceClaimState::Pending => {
            create_pending_inheritance_claim(
                &benefactor_account,
                &beneficiary_account,
                &current_auth_keys,
                None,
            )
            .await
            .common_fields
            .recovery_relationship_id
        }
        InheritanceClaimState::Locked => {
            create_locked_claim(&benefactor_account, &beneficiary_account)
                .await
                .common_fields
                .recovery_relationship_id
        }
        InheritanceClaimState::Completed => {
            let locked_claim = create_locked_claim(&benefactor_account, &beneficiary_account).await;
            create_completed_claim(&locked_claim)
                .await
                .common_fields
                .recovery_relationship_id
        }
        InheritanceClaimState::Cancelled => {
            let pending_claim = create_pending_inheritance_claim(
                &benefactor_account,
                &beneficiary_account,
                &current_auth_keys,
                None,
            )
            .await;
            create_canceled_claim(&pending_claim, InheritanceClaimCanceledBy::Beneficiary)
                .await
                .common_fields
                .recovery_relationship_id
        }
    };
    let benefactor_account_has_incomplete_claim = inheritance_service
        .has_incompleted_claim(HasIncompletedClaimInput {
            actor_account_id: &benefactor_account.id,
            recovery_relationship_id: &recovery_relationship_id,
        })
        .await
        .expect("Inable to fetch incomplete inheritance claims");
    let beneficiary_account_has_incomplete_claim = inheritance_service
        .has_incompleted_claim(HasIncompletedClaimInput {
            actor_account_id: &beneficiary_account.id,
            recovery_relationship_id: &recovery_relationship_id,
        })
        .await
        .expect("Inable to fetch incomplete inheritance claims");

    // assert
    assert_eq!(
        benefactor_account_has_incomplete_claim.as_benefactor,
        has_incomplete_claim
    );
    assert_eq!(
        beneficiary_account_has_incomplete_claim.as_beneficiary,
        has_incomplete_claim
    );
}
