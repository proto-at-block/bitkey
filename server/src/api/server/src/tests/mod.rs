use account::service::tests::TestAuthenticationKeys;
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use std::collections::HashMap;
use types::account::identifiers::AccountId;

use crate::{create_bootstrap_with_overrides, Bootstrap, GenServiceOverrides};

mod account_keysets_integration_tests;
mod analytics_integration_tests;
mod auth_tests;
mod bdk_configuration_tests;
mod blockchain_polling_integration_tests;
mod cloud_recovery_integration_tests;
mod currency_exchange_integration_tests;
mod exchange_rate_integration_tests;
mod experimentation_integration_tests;
mod export_tools_integration_tests;
mod lib;
mod mempool_polling_integration_tests;
mod mobile_pay_tests;
mod notification_integration_tests;
mod onboarding_integration_tests;
mod privileged_actions_integration_tests;
mod recovery;
mod register_watch_address_integration_tests;
mod requests;
mod scheduled_notifications_integration_tests;
mod send_customer_notifications_integration_tests;
mod transaction_integration_tests;

#[macro_export]
macro_rules! tests {
    (runner = $runner_name:ident, $($test_name:ident: $input:expr,)*) => {
        $(
        #[tokio::test]
        async fn $test_name() {
            $runner_name($input).await
        }
        )*
    };
}

#[derive(Debug, Default)]
pub struct TestContext {
    authentication_keys: HashMap<PublicKey, TestAuthenticationKeys>,
    account_authentication_keys: HashMap<AccountId, TestAuthenticationKeys>,
}

impl TestContext {
    pub fn new() -> Self {
        TestContext::default()
    }

    pub fn add_authentication_keys(&mut self, keys: TestAuthenticationKeys) {
        self.authentication_keys
            .insert(keys.app.public_key, keys.clone());
        self.authentication_keys
            .insert(keys.hw.public_key, keys.clone());
        self.authentication_keys
            .insert(keys.recovery.public_key, keys);
    }

    pub fn get_authentication_keys_for_account_id(
        &self,
        account_id: &AccountId,
    ) -> Option<TestAuthenticationKeys> {
        self.account_authentication_keys
            .get(account_id)
            .map(|k| k.to_owned())
    }

    pub fn associate_with_account(&mut self, account_id: &AccountId, pubkey: PublicKey) {
        let keys = self.authentication_keys.get(&pubkey).unwrap().to_owned();
        self.account_authentication_keys
            .insert(account_id.to_owned(), keys);
    }
}

// Run standard bootstrap with that uses Profile to instantiate test versions of dependencies
async fn gen_services() -> (TestContext, Bootstrap) {
    gen_services_with_overrides(GenServiceOverrides::default()).await
}

// Run standard bootstrap (see gen_services()), but optionally override dependencies. Useful for
// injecting test doubles during testing.
async fn gen_services_with_overrides(overrides: GenServiceOverrides) -> (TestContext, Bootstrap) {
    let _ = env_logger::builder().is_test(true).try_init();
    (
        TestContext::new(),
        create_bootstrap_with_overrides(Some("test"), overrides)
            .await
            .unwrap(),
    )
}
