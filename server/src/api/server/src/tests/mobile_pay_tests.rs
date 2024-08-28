use http::StatusCode;
use time::UtcOffset;
use ulid::Ulid;

use account::service::{FetchAccountInput, FetchAndUpdateSpendingLimitInput};
use account::spend_limit::{Money, SpendingLimit};

use external_identifier::ExternalIdentifier;
use mobile_pay::routes::{MobilePaySetupRequest, MobilePaySetupResponse};
use types::account::identifiers::AccountId;
use types::currencies::CurrencyCode::{USD, XXX};

use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::create_default_account_with_predefined_wallet;

use super::requests::axum::TestClient;

#[tokio::test]
async fn mobile_pay_setup_and_deactivation_succeeds_with_valid_request() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();
    assert_eq!(account.spending_limit, None);

    let spend_limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
        },
        ..Default::default()
    };
    let request = build_mobile_pay_request(spend_limit.clone());
    let response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    assert_eq!(response.body.unwrap(), MobilePaySetupResponse {});

    // Check if spending limit is persisted in Account
    let response = client.get_mobile_pay(&account.id, &keys).await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let resp_body = response.body.unwrap();
    let mobile_pay_config = resp_body.mobile_pay().unwrap();
    assert_eq!(mobile_pay_config.limit, spend_limit.clone());

    let disable_spend_limit = SpendingLimit {
        active: false,
        ..spend_limit.clone()
    };
    // Deactivate Mobile Pay
    let request = build_mobile_pay_request(disable_spend_limit.clone());
    let response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(response.status_code, StatusCode::OK);

    // Check if Mobile Pay is turned off.
    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account.id,
        })
        .await
        .unwrap();
    assert_eq!(account.spending_limit, Some(disable_spend_limit));

    // Deactivate Mobile Pay
    bootstrap
        .services
        .account_service
        .fetch_and_update_spend_limit(FetchAndUpdateSpendingLimitInput {
            account_id: &account.id,
            new_spending_limit: None,
        })
        .await
        .unwrap();
    let get_response = client.get_mobile_pay(&account.id, &keys).await;
    assert_eq!(get_response.status_code, StatusCode::OK);
    let resp_body = get_response.body.unwrap();
    assert!(resp_body.mobile_pay().is_none());
}

struct SetupMobilePayWithTimezoneTestVector {
    time_zone_offset: UtcOffset,
    expected_status_code: StatusCode,
}

async fn setup_mobile_pay_with_timezone_test(vector: SetupMobilePayWithTimezoneTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, ..) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let request = build_mobile_pay_request(SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
        },
        time_zone_offset: vector.time_zone_offset,
        ..Default::default()
    });
    let response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(
        response.status_code, vector.expected_status_code,
        "{}",
        response.body_string
    );
}

tests! {
    runner = setup_mobile_pay_with_timezone_test,
    test_mobilepay_with_timezone_at_lower_boundary_timezone: SetupMobilePayWithTimezoneTestVector {
        time_zone_offset: UtcOffset::from_hms(-12, 0, 0).unwrap(),
        expected_status_code: StatusCode::OK
    },
    test_mobilepay_with_timezone_utc: SetupMobilePayWithTimezoneTestVector {
        time_zone_offset: UtcOffset::UTC,
        expected_status_code: StatusCode::OK
    },
    test_mobilepay_with_timezone_at_upper_boundary_timezone: SetupMobilePayWithTimezoneTestVector {
        time_zone_offset: UtcOffset::from_hms(14, 0, 0).unwrap(),
        expected_status_code: StatusCode::OK
    },
}

#[tokio::test]
async fn mobile_pay_setup_fails_without_jwt() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, ..) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;

    let request = build_mobile_pay_request(SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
        },
        ..Default::default()
    });
    let response = client
        .setup_mobile_pay_unauthenticated(&account.id, &request)
        .await;
    assert_eq!(response.status_code, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn mobile_pay_setup_fails_with_unsupported_currency_code() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, ..) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let request = build_mobile_pay_request(SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: XXX,
        },
        ..Default::default()
    });
    let response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(response.status_code, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn mobile_pay_setup_fails_with_non_existent_account() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let request = build_mobile_pay_request(SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
        },
        ..Default::default()
    });
    let response = client
        .put_mobile_pay(&AccountId::new(Ulid::new()).unwrap(), &request, &keys)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::NOT_FOUND,
        "{}",
        response.body_string
    );
}

#[tokio::test]
async fn mobile_pay_disable() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();
    assert_eq!(account.spending_limit, None);

    let spend_limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
        },
        ..Default::default()
    };
    let request = build_mobile_pay_request(spend_limit.clone());
    let response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    assert_eq!(response.body.unwrap(), MobilePaySetupResponse {});

    // Test delete endpoint
    let response = client.delete_mobile_pay(&account.id, &keys).await;
    assert_eq!(response.status_code, StatusCode::OK);

    // Check if Mobile Pay is turned off.
    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account.id,
        })
        .await
        .unwrap();

    let disabled_spend_limit = SpendingLimit {
        active: false,
        ..spend_limit
    };
    assert_eq!(account.spending_limit, Some(disabled_spend_limit));
}

pub(crate) fn build_mobile_pay_request(limit: SpendingLimit) -> MobilePaySetupRequest {
    MobilePaySetupRequest { limit }
}

#[cfg(test)]
mod get_mobile_pay_tests {
    use std::str::FromStr;

    use http::StatusCode;
    use time::{OffsetDateTime, UtcOffset};

    use account::spend_limit::{Money, SpendingLimit};
    use bdk_utils::bdk::bitcoin::absolute::LockTime;
    use bdk_utils::bdk::bitcoin::psbt::Psbt;
    use bdk_utils::bdk::bitcoin::{Address, ScriptBuf, Transaction, TxOut};
    use bdk_utils::constants::ONE_BTC_IN_SATOSHIS;
    use bdk_utils::error::BdkUtilError;
    use bdk_utils::{AttributableWallet, SpkWithDerivationPaths};
    use mobile_pay::daily_spend_record::entities::DailySpendingRecord;
    use mobile_pay::routes::MobilePaySetupResponse;
    use types::currencies::CurrencyCode::{EUR, USD};
    use types::exchange_rate::local_rate_provider::LOCAL_ONE_BTC_IN_FIAT;

    use crate::tests;
    use crate::tests::gen_services;
    use crate::tests::lib::create_default_account_with_predefined_wallet;
    use crate::tests::mobile_pay_tests::build_mobile_pay_request;
    use crate::tests::requests::axum::TestClient;

    struct DummyWallet {
        is_mine_pubkeys: Vec<ScriptBuf>,
    }

    impl DummyWallet {
        fn new(is_mine_pubkeys: Vec<ScriptBuf>) -> Self {
            Self { is_mine_pubkeys }
        }
    }
    impl AttributableWallet for DummyWallet {
        fn is_addressed_to_self(&self, _psbt: &Psbt) -> Result<bool, BdkUtilError> {
            todo!()
        }

        fn all_inputs_are_from_self(&self, _psbt: &Psbt) -> Result<bool, BdkUtilError> {
            todo!()
        }

        fn is_my_psbt_address(&self, spk: &SpkWithDerivationPaths) -> Result<bool, BdkUtilError> {
            Ok(self.is_mine_pubkeys.contains(&spk.script_pubkey))
        }
    }

    #[derive(Clone, Debug)]
    struct GetMobilePayTestVector {
        spending_limit: SpendingLimit,
    }

    // Let o = 10_000_000 (number of sats in 1 BTC)
    // Let r = 22678 (`LocalRateProvider`'s 1 BTC in USD)
    // Let a be amount in USD
    // Let s be the amount in sats
    // Formula for $s = \frac{o}{r} * \frac{1}{100} * a$
    // Formula for $a = s * 100 * \frac{r}{o}$
    const SPENDING_LIMIT_SATS: u64 = 1_000_000;
    const SPENDING_LIMIT_USD: f64 =
        (SPENDING_LIMIT_SATS as f64) * 100.0 * (LOCAL_ONE_BTC_IN_FIAT / ONE_BTC_IN_SATOSHIS as f64);
    const USD_SPENDING_LIMIT: SpendingLimit = SpendingLimit {
        active: true,
        amount: Money {
            amount: SPENDING_LIMIT_USD as u64,
            currency_code: USD,
        },
        time_zone_offset: UtcOffset::UTC,
    };

    tests! {
        runner = get_mobile_pay_balance,
        test_get_mobile_pay_balance_with_fiat_spending_limit: GetMobilePayTestVector {
            spending_limit: USD_SPENDING_LIMIT,
        },
    }

    async fn get_mobile_pay_balance(vector: GetMobilePayTestVector) {
        let payee_script_pubkey = Address::from_str("bc1qvh30c5k24q4z2h6e88tvsv7x3xyj7m4g37e498")
            .unwrap()
            .assume_checked()
            .script_pubkey();
        let change_script_pubkey =
            Address::from_str("bc1qwqdg6squsna38e46795at95yu9atm8azzmyvckulcc7kytlcckxswvvzej")
                .unwrap()
                .assume_checked()
                .script_pubkey();

        let (mut context, bootstrap) = gen_services().await;
        let client = TestClient::new(bootstrap.router).await;
        let (account, _) = create_default_account_with_predefined_wallet(
            &mut context,
            &client,
            &bootstrap.services,
        )
        .await;
        let keys = context
            .get_authentication_keys_for_account_id(&account.id)
            .unwrap();

        // Set up mobile pay.
        let setup_mobile_pay_request = build_mobile_pay_request(vector.spending_limit.clone());
        let mobile_pay_setup_response = client
            .put_mobile_pay(&account.id, &setup_mobile_pay_request, &keys)
            .await;
        assert_eq!(mobile_pay_setup_response.status_code, StatusCode::OK);

        // We don't want to use a real wallet here, so we mock it out with DummyWallet
        let mut spending_record =
            DailySpendingRecord::try_new(&account.id, OffsetDateTime::now_utc().date()).unwrap();
        let PAYEE_AMOUNT_SATS: u64 = 860_000;
        let CHANGE_AMOUNT_SATS: u64 = 306_249;

        let psbt = Psbt::from_unsigned_tx(Transaction {
            version: 0,
            lock_time: LockTime::ZERO,
            input: Vec::new(),
            output: vec![
                // payee output
                TxOut {
                    value: PAYEE_AMOUNT_SATS,
                    script_pubkey: payee_script_pubkey,
                },
                // change output
                TxOut {
                    value: CHANGE_AMOUNT_SATS,
                    script_pubkey: change_script_pubkey.clone(),
                },
            ],
        })
        .unwrap();

        spending_record
            .update_with_psbt(&DummyWallet::new(vec![change_script_pubkey.clone()]), &psbt);
        let _ = bootstrap
            .services
            .daily_spend_record_service
            .save_daily_spending_record(spending_record)
            .await;

        let get_mobile_pay_response = client.get_mobile_pay(&account.id, &keys).await;
        let resp_body = get_mobile_pay_response.body.unwrap();
        let mobile_pay_config = resp_body.mobile_pay().unwrap();
        assert!(mobile_pay_config.limit.active);
        assert_eq!(
            mobile_pay_config.available.amount,
            SPENDING_LIMIT_SATS - PAYEE_AMOUNT_SATS
        );
        assert_eq!(mobile_pay_config.spent.amount, PAYEE_AMOUNT_SATS);
        assert_eq!(mobile_pay_config.limit, vector.spending_limit);
    }

    #[tokio::test]
    async fn test_lowering_spending_limit_should_not_underflow() {
        let payee_script_pubkey = Address::from_str("bc1qvh30c5k24q4z2h6e88tvsv7x3xyj7m4g37e498")
            .unwrap()
            .assume_checked()
            .script_pubkey();
        let change_script_pubkey =
            Address::from_str("bc1qwqdg6squsna38e46795at95yu9atm8azzmyvckulcc7kytlcckxswvvzej")
                .unwrap()
                .assume_checked()
                .script_pubkey();

        let (mut context, bootstrap) = gen_services().await;
        let client = TestClient::new(bootstrap.router).await;
        let (account, _) = create_default_account_with_predefined_wallet(
            &mut context,
            &client,
            &bootstrap.services,
        )
        .await;
        let keys = context
            .get_authentication_keys_for_account_id(&account.id)
            .unwrap();

        // Fake spending 1000 sats cosigned transaction
        let mut spending_record =
            DailySpendingRecord::try_new(&account.id, OffsetDateTime::now_utc().date()).unwrap();
        let psbt = Psbt::from_unsigned_tx(Transaction {
            version: 0,
            lock_time: LockTime::ZERO,
            input: Vec::new(),
            output: vec![
                // payee output
                TxOut {
                    value: 50000,
                    script_pubkey: payee_script_pubkey,
                },
                // change output
                TxOut {
                    value: 0,
                    script_pubkey: change_script_pubkey.clone(),
                },
            ],
        })
        .unwrap();

        spending_record
            .update_with_psbt(&DummyWallet::new(vec![change_script_pubkey.clone()]), &psbt);
        let _ = bootstrap
            .services
            .daily_spend_record_service
            .save_daily_spending_record(spending_record)
            .await;

        // Set up mobile pay with 1000 sats limit.
        let limit = SpendingLimit {
            active: true,
            amount: Money {
                amount: 1_000,
                currency_code: USD,
            },
            time_zone_offset: UtcOffset::UTC,
        };
        let setup_mobile_pay_request = build_mobile_pay_request(limit.clone());
        let mobile_pay_setup_response = client
            .put_mobile_pay(&account.id, &setup_mobile_pay_request, &keys)
            .await;
        assert_eq!(mobile_pay_setup_response.status_code, StatusCode::OK);

        let get_mobile_pay_response = client.get_mobile_pay(&account.id, &keys).await;
        let resp_body = get_mobile_pay_response.body.unwrap();
        let mobile_pay_config = resp_body.mobile_pay().unwrap();
        assert!(mobile_pay_config.limit.active);
        assert_eq!(mobile_pay_config.available.amount, 0);
        assert_eq!(mobile_pay_config.spent.amount, 50000);
        assert_eq!(mobile_pay_config.limit, limit);
    }

    #[tokio::test]
    async fn changing_mobile_pay_currencies_preserves_balance() {
        let payee_script_pubkey = Address::from_str("bc1qvh30c5k24q4z2h6e88tvsv7x3xyj7m4g37e498")
            .unwrap()
            .assume_checked()
            .script_pubkey();
        let change_script_pubkey =
            Address::from_str("bc1qwqdg6squsna38e46795at95yu9atm8azzmyvckulcc7kytlcckxswvvzej")
                .unwrap()
                .assume_checked()
                .script_pubkey();

        let (mut context, bootstrap) = gen_services().await;
        let client = TestClient::new(bootstrap.router).await;
        let (account, ..) = create_default_account_with_predefined_wallet(
            &mut context,
            &client,
            &bootstrap.services,
        )
        .await;
        let keys = context
            .get_authentication_keys_for_account_id(&account.id)
            .unwrap();

        // Fake spending 1000 sats cosigned transaction
        let mut spending_record =
            DailySpendingRecord::try_new(&account.id, OffsetDateTime::now_utc().date()).unwrap();
        let psbt = Psbt::from_unsigned_tx(Transaction {
            version: 0,
            lock_time: LockTime::ZERO,
            input: Vec::new(),
            output: vec![
                // payee output
                TxOut {
                    value: 1_100,
                    script_pubkey: payee_script_pubkey,
                },
                // change output
                TxOut {
                    value: 100,
                    script_pubkey: change_script_pubkey.clone(),
                },
            ],
        })
        .unwrap();
        spending_record
            .update_with_psbt(&DummyWallet::new(vec![change_script_pubkey.clone()]), &psbt);

        let _ = bootstrap
            .services
            .daily_spend_record_service
            .save_daily_spending_record(spending_record)
            .await;

        // Setup Mobile Pay with USD
        let request = build_mobile_pay_request(SpendingLimit {
            active: true,
            amount: Money {
                amount: 400,
                currency_code: USD,
            },
            ..Default::default()
        });
        let response = client.put_mobile_pay(&account.id, &request, &keys).await;
        assert_eq!(
            response.status_code,
            StatusCode::OK,
            "{}",
            response.body_string
        );
        assert_eq!(response.body.unwrap(), MobilePaySetupResponse {});

        let get_mobile_pay_response = client.get_mobile_pay(&account.id, &keys).await;
        let resp_body = get_mobile_pay_response.body.unwrap();
        let mobile_pay_config = resp_body.mobile_pay().unwrap();
        let spent_amount_sats = mobile_pay_config.spent.amount;

        // Simulate mobile pay config deletion when changing currencies.
        let response = client.delete_mobile_pay(&account.id, &keys).await;
        assert_eq!(
            response.status_code,
            StatusCode::OK,
            "{}",
            response.body_string
        );

        // Setup Mobile Pay with EUR
        let destination_currency_code = EUR;
        let request = build_mobile_pay_request(SpendingLimit {
            active: true,
            amount: Money {
                amount: 400,
                currency_code: destination_currency_code.clone(),
            },
            ..Default::default()
        });
        let response = client.put_mobile_pay(&account.id, &request, &keys).await;
        assert_eq!(
            response.status_code,
            StatusCode::OK,
            "{}",
            response.body_string
        );

        // Check that we persist the spent amount in sats.
        let get_mobile_pay_response = client.get_mobile_pay(&account.id, &keys).await;
        let resp_body = get_mobile_pay_response.body.unwrap();
        let mobile_pay_config = resp_body.mobile_pay().unwrap();
        assert!(mobile_pay_config.limit.active);
        assert_eq!(mobile_pay_config.spent.amount, spent_amount_sats);
        assert_eq!(
            mobile_pay_config.limit.amount.currency_code,
            destination_currency_code
        )
    }

    #[tokio::test]
    async fn returns_200_with_none_if_customer_never_set_up_mobile_pay() {
        let (mut context, bootstrap) = gen_services().await;
        let client = TestClient::new(bootstrap.router).await;
        let (account, ..) = create_default_account_with_predefined_wallet(
            &mut context,
            &client,
            &bootstrap.services,
        )
        .await;
        let keys = context
            .get_authentication_keys_for_account_id(&account.id)
            .unwrap();

        let response = client.get_mobile_pay(&account.id, &keys).await;
        assert_eq!(response.status_code, StatusCode::OK);
        let resp_body = response.body.unwrap();
        assert!(resp_body.mobile_pay().is_none());
    }
}
