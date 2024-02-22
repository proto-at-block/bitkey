use crate::{create_bootstrap_with_overrides, Bootstrap, GenServiceOverrides};

mod account_keysets_integration_tests;
mod analytics_integration_tests;
mod auth_tests;
mod bdk_configuration_tests;
mod blockchain_polling_integration_tests;
mod cloud_recovery_integration_tests;
mod currency_exchange_integration_tests;
mod exchange_rate_integration_tests;
mod lib;
mod mobile_pay_tests;
mod notification_integration_tests;
mod onboarding_integration_tests;
mod recovery_integration_tests;
mod recovery_relationship_integration_tests;
mod register_watch_address_integration_tests;
mod requests;
mod scheduled_notifications_integration_tests;
mod send_customer_notifications_integration_tests;
mod social_challenge_integration_tests;
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

// Run standard bootstrap with that uses Profile to instantiate test versions of dependencies
async fn gen_services() -> Bootstrap {
    gen_services_with_overrides(GenServiceOverrides::default()).await
}

// Run standard bootstrap (see gen_services()), but optionally override dependencies. Useful for
// injecting test doubles during testing.
async fn gen_services_with_overrides(overrides: GenServiceOverrides) -> Bootstrap {
    let _ = env_logger::builder().is_test(true).try_init();
    create_bootstrap_with_overrides(Some("test"), overrides)
        .await
        .unwrap()
}
