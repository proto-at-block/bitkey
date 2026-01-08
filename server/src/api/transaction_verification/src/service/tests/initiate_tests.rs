use rstest::rstest;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use bdk_utils::bdk::bitcoin::ScriptBuf;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
use bdk_utils::bdk::{FeeRate, Wallet};
use exchange_rate::currency_conversion::sats_for;
use exchange_rate::service::Service as ExchangeRateService;
use feature_flags::flag::ContextKey;
use std::{collections::HashMap, str::FromStr};
use types::account::entities::TransactionVerificationPolicy;
use types::account::money::Money;
use types::currencies::CurrencyCode::USD;
use types::exchange_rate::local_rate_provider::LocalRateProvider;
use types::transaction_verification::entities::BitcoinDisplayUnit::Satoshi;
use types::transaction_verification::service::{
    InitiateVerificationResultDiscriminants, StaticWalletProvider, VerificationWalletProvider,
};

use super::setup_account_with_transaction_verification_policy;
use crate::error::TransactionVerificationError;
use crate::service::tests::{
    construct_test_transaction_verification_service,
    construct_test_transaction_verification_service_with,
};
use screener::screening::SANCTION_TEST_FLAG_KEY;

#[rstest]
#[case::with_policy_never(
    Some(TransactionVerificationPolicy::Never),
    false,
    Ok(InitiateVerificationResultDiscriminants::SignedWithoutVerification)
)]
#[case::without_prompt_user(
    Some(TransactionVerificationPolicy::Always),
    false,
    Ok(InitiateVerificationResultDiscriminants::VerificationRequired)
)]
#[case::prompt_user(
    Some(TransactionVerificationPolicy::Always),
    true,
    Ok(InitiateVerificationResultDiscriminants::VerificationRequested)
)]
#[tokio::test]
async fn test_initiate_verification(
    #[case] policy: Option<TransactionVerificationPolicy>,
    #[case] should_prompt_user: bool,
    #[case] expected_result: Result<
        InitiateVerificationResultDiscriminants,
        TransactionVerificationError,
    >,
) {
    let service = construct_test_transaction_verification_service().await;
    let account = setup_account_with_transaction_verification_policy(policy).await;

    let psbt = Psbt::from_str("cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA").unwrap();
    let wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
    let network = wallet.network();
    let result = service
        .initiate(
            &account,
            VerificationWalletProvider::Static(StaticWalletProvider {
                wallet: Box::new(wallet),
            }),
            psbt,
            network,
            USD,
            Satoshi,
            should_prompt_user,
            None,
        )
        .await;

    if let Ok(r) = result {
        let discriminate = InitiateVerificationResultDiscriminants::from(r);
        assert_eq!(discriminate, expected_result.unwrap());
    } else {
        let actual_err = result.err().unwrap();
        let expected_err = expected_result.err().unwrap();

        assert_eq!(actual_err.to_string(), expected_err.to_string());
    }
}

#[rstest]
#[case::at_threshold(0, Ok(InitiateVerificationResultDiscriminants::VerificationRequired))]
#[case::below_threshold(-1, Ok(InitiateVerificationResultDiscriminants::SignedWithoutVerification))]
#[tokio::test]
async fn test_psbt_threshold(
    #[case] threshold_offset: i64,
    #[case] expected_result: Result<
        InitiateVerificationResultDiscriminants,
        TransactionVerificationError,
    >,
) {
    // The LocalProvider's exchange rate is hardcoded LOCAL_ONE_BTC_IN_FIAT
    let threshold = Money {
        amount: 1000,
        currency_code: USD,
    };
    let threshold_sats = sats_for(
        &ExchangeRateService::new(),
        LocalRateProvider::new(),
        &threshold,
    )
    .await
    .unwrap();
    let service = construct_test_transaction_verification_service().await;
    let account = setup_account_with_transaction_verification_policy(Some(
        TransactionVerificationPolicy::Threshold(threshold),
    ))
    .await;

    let source_wallet_unboxed = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
    let network = source_wallet_unboxed.network();
    let source_wallet = Box::new(source_wallet_unboxed);
    let destination_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
    let destination_address = destination_wallet.get_address(AddressIndex::New).unwrap();
    let sats_to_send = (threshold_sats as i64 + threshold_offset) as u64;
    let psbt = make_psbt(
        source_wallet.as_ref(),
        destination_address.script_pubkey(),
        sats_to_send,
    );

    let result = service
        .initiate(
            &account,
            VerificationWalletProvider::Static(StaticWalletProvider {
                wallet: source_wallet,
            }),
            psbt,
            network,
            USD,
            Satoshi,
            false,
            None,
        )
        .await;

    if let Ok(r) = result {
        let discriminate = InitiateVerificationResultDiscriminants::from(r);
        assert_eq!(discriminate, expected_result.unwrap());
    } else {
        let actual_err = result.err().unwrap();
        let expected_err = expected_result.err().unwrap();

        assert_eq!(actual_err.to_string(), expected_err.to_string());
    }
}

#[tokio::test]
async fn initiate_rejects_blocked_addresses() {
    let feature_flags_service =
        feature_flags::config::Config::new_with_overrides(Default::default())
            .to_service()
            .await
            .unwrap();
    let service =
        construct_test_transaction_verification_service_with(true, feature_flags_service).await;
    let account = setup_account_with_transaction_verification_policy(None).await;

    let source_wallet = Box::new(
        get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0,
    );
    let destination_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
    let destination_address = destination_wallet.get_address(AddressIndex::New).unwrap();
    let network = source_wallet.network();
    let psbt = make_psbt(
        source_wallet.as_ref(),
        destination_address.script_pubkey(),
        1_000,
    );

    let result = service
        .initiate(
            &account,
            VerificationWalletProvider::Static(StaticWalletProvider {
                wallet: source_wallet,
            }),
            psbt,
            network,
            USD,
            Satoshi,
            true,
            None,
        )
        .await;

    assert!(matches!(
        result,
        Err(TransactionVerificationError::OutputsBelongToSanctionedIndividuals)
    ));
}

#[tokio::test]
async fn initiate_rejects_blocked_addresses_via_flag() {
    let feature_flags_service =
        feature_flags::config::Config::new_with_overrides(HashMap::from([(
            SANCTION_TEST_FLAG_KEY.to_string(),
            true.to_string(),
        )]))
        .to_service()
        .await
        .unwrap();
    let service =
        construct_test_transaction_verification_service_with(false, feature_flags_service).await;
    let account = setup_account_with_transaction_verification_policy(None).await;

    let source_wallet = Box::new(
        get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0,
    );
    let destination_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
    let destination_address = destination_wallet.get_address(AddressIndex::New).unwrap();
    let network = source_wallet.network();
    let psbt = make_psbt(
        source_wallet.as_ref(),
        destination_address.script_pubkey(),
        1_000,
    );

    let result = service
        .initiate(
            &account,
            VerificationWalletProvider::Static(StaticWalletProvider {
                wallet: source_wallet,
            }),
            psbt,
            network,
            USD,
            Satoshi,
            true,
            Some(ContextKey::Account(
                "accountid".to_string(),
                Default::default(),
            )),
        )
        .await;

    assert!(matches!(
        result,
        Err(TransactionVerificationError::OutputsBelongToSanctionedIndividuals)
    ));
}

#[tokio::test]
async fn initiate_rejects_invalid_script_pubkeys() {
    let feature_flags_service =
        feature_flags::config::Config::new_with_overrides(Default::default())
            .to_service()
            .await
            .unwrap();
    let service =
        construct_test_transaction_verification_service_with(false, feature_flags_service).await;
    let account = setup_account_with_transaction_verification_policy(None).await;
    let source_wallet = Box::new(
        get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0,
    );
    let network = source_wallet.network();
    let invalid_script = ScriptBuf::from_hex("001161458e330389cd0437ee9fe3641d70cc18").unwrap();
    let mut builder = source_wallet.build_tx();
    builder
        .add_recipient(invalid_script, 1_000)
        .fee_rate(FeeRate::from_sat_per_vb(5.0));
    let (psbt, _) = builder.finish().unwrap();

    let result = service
        .initiate(
            &account,
            VerificationWalletProvider::Static(StaticWalletProvider {
                wallet: source_wallet,
            }),
            psbt,
            network,
            USD,
            Satoshi,
            true,
            None,
        )
        .await;

    assert!(matches!(
        result,
        Err(TransactionVerificationError::InvalidScriptPubKeys)
    ));
}

fn make_psbt(wallet: &Wallet<AnyDatabase>, recipient: ScriptBuf, sats: u64) -> Psbt {
    let mut builder = wallet.build_tx();
    builder
        .add_recipient(recipient, sats)
        .fee_rate(FeeRate::from_sat_per_kvb(5.0));
    let (psbt, _) = builder.finish().unwrap();
    psbt
}
