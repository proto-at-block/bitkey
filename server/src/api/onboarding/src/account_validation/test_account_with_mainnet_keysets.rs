use account::service::Service as AccountService;
use async_trait::async_trait;
use recovery::repository::RecoveryRepository;
use types::account::bitcoin::Network;

use super::{error::AccountValidationError, AccountValidationRequest, Rule};
use crate::routes::Config;

pub(crate) struct TestAccountsWithMainnetKeysetsRule;

#[async_trait]
impl Rule for TestAccountsWithMainnetKeysetsRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        config: &Config,
        _: &AccountService,
        _: &RecoveryRepository,
    ) -> Result<(), AccountValidationError> {
        // This check only applies to creating or upgrading to full accounts
        let (is_test_account, spending_network) = match request {
            AccountValidationRequest::CreateFullAccount {
                is_test_account,
                spending_network,
                ..
            } => (is_test_account, spending_network),
            AccountValidationRequest::UpgradeAccount {
                is_test_account,
                spending_network,
                ..
            } => (is_test_account, spending_network),
            AccountValidationRequest::CreateFullAccountV2 {
                spend,
                is_test_account,
                ..
            } => (is_test_account, &spend.network.into()),
            AccountValidationRequest::UpgradeAccountV2 {
                is_test_account,
                spend_network,
                ..
            } => (is_test_account, spend_network),
            AccountValidationRequest::CreateLiteAccount { .. }
            | AccountValidationRequest::CreateSoftwareAccount { .. } => {
                return Ok(());
            }
        };

        if !config.allow_test_accounts_with_mainnet_keysets
            && *is_test_account
            && *spending_network == Network::BitcoinMain
        {
            return Err(AccountValidationError::InvalidNetworkForTestAccount);
        }

        Ok(())
    }
}
