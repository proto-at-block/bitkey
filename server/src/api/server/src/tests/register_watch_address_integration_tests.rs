use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::secp256k1::rand::thread_rng;
use bdk_utils::bdk::bitcoin::secp256k1::Secp256k1;
use bdk_utils::bdk::bitcoin::{Address, Network, PublicKey};
use database::ddb::{Config as DDBConfig, Repository};
use http::StatusCode;
use http_server::config;
use notification::address_repo::ddb::repository::AddressRepository;
use notification::address_repo::ddb::service::Service as AddressRepoDDB;
use notification::address_repo::memory::Service as AddressRepoMemory;
use notification::address_repo::{AddressAndKeysetId, AddressWatchlistTrait};
use rstest::{fixture, rstest};
use types::account::bitcoin::Network::BitcoinSignet;
use types::account::identifiers::{AccountId, KeysetId};

use crate::tests::gen_services_with_overrides;
use crate::tests::lib::create_full_account;
use crate::tests::requests::axum::TestClient;
use crate::GenServiceOverrides;

fn memory_repo() -> impl AddressWatchlistTrait + Clone {
    AddressRepoMemory::default()
}

async fn ddb_repo() -> impl AddressWatchlistTrait + Clone {
    let conn = config::extract::<DDBConfig>(Some("test"))
        .unwrap()
        .to_connection()
        .await;
    let repo = AddressRepository::new(conn);
    AddressRepoDDB::create(repo).await.unwrap()
}

struct TestContext<A: AddressWatchlistTrait + Clone + 'static> {
    address_repo: A,
    known_account_id_1: AccountId,
    known_account_id_2: AccountId,
    client: TestClient,
    services: crate::Services,
}

impl<A: AddressWatchlistTrait + Clone + 'static> TestContext<A> {
    async fn set_up(address_repo: A, enable_notifications: bool) -> Self {
        let overrides = GenServiceOverrides::new().address_repo(Box::new(address_repo.clone()));
        let (mut ctx, bootstrap) = gen_services_with_overrides(overrides).await;
        let client = TestClient::new(bootstrap.router).await;
        let account_1 =
            create_full_account(&mut ctx, &bootstrap.services, BitcoinSignet, None).await;
        let account_2 =
            create_full_account(&mut ctx, &bootstrap.services, BitcoinSignet, None).await;

        let test_ctx = Self {
            address_repo,
            client,
            known_account_id_1: account_1.id.clone(),
            known_account_id_2: account_2.id.clone(),
            services: bootstrap.services,
        };

        if enable_notifications {
            test_ctx
                .enable_money_movement_notifications(&account_1.id)
                .await;
            test_ctx
                .enable_money_movement_notifications(&account_2.id)
                .await;
        }

        test_ctx
    }

    async fn enable_money_movement_notifications(&self, account_id: &AccountId) {
        use notification::service::{
            FetchNotificationsPreferencesInput, UpdateNotificationsPreferencesInput,
        };
        use types::notification::{NotificationCategory, NotificationChannel};

        // Fetch current notification preferences
        let current_notification_preferences = self
            .services
            .notification_service
            .fetch_notifications_preferences(FetchNotificationsPreferencesInput { account_id })
            .await
            .expect("Failed to fetch notification preferences in test");

        // Enable money movement notifications for push channel
        self.services
            .notification_service
            .update_notifications_preferences(UpdateNotificationsPreferencesInput {
                account_id,
                notifications_preferences: &current_notification_preferences.with_enabled(
                    NotificationCategory::MoneyMovement,
                    NotificationChannel::Push,
                ),
                key_proof: None,
            })
            .await
            .expect("Failed to enable money movement notifications in test");
    }
}

fn gen_random_address() -> AddressAndKeysetId {
    let secp = Secp256k1::new();
    let (_, pk) = secp.generate_keypair(&mut thread_rng());

    // Force Address to be of type Address<NetworkUnchecked> for AddressAndKeysetId to be
    // deserializable.
    // [W-5648]: Use `as_unchecked` once it's available in BDK.
    let addr_string = Address::p2pkh(&PublicKey::new(pk), Network::Bitcoin).to_string();
    let unchecked_address: Address<NetworkUnchecked> = addr_string.parse().unwrap();
    AddressAndKeysetId::new(unchecked_address, KeysetId::gen().unwrap())
}

#[fixture]
fn random_address() -> AddressAndKeysetId {
    gen_random_address()
}

#[fixture]
fn random_address2() -> AddressAndKeysetId {
    gen_random_address()
}

async fn get_acct_for_addr(
    repo: &impl AddressWatchlistTrait,
    addr: &Address<NetworkUnchecked>,
) -> AccountId {
    repo.get(&[addr.clone()])
        .await
        .unwrap()
        .get(addr)
        .unwrap()
        .clone()
        .account_id
}

#[rstest]
#[tokio::test]
async fn test_register_watch_address_with_unknown_account_id_404(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = AccountId::gen().ok().unwrap();

    let response = test_ctx
        .client
        .register_watch_address(&acct_id, &vec![random_address.clone()].into())
        .await;

    assert_eq!(StatusCode::NOT_FOUND, response.status_code);
}

#[rstest]
#[tokio::test]
async fn test_register_watch_address_with_known_account_id_first_insert_200(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;
    let response = test_ctx
        .client
        .register_watch_address(&acct_id, &vec![random_address.clone()].into())
        .await;

    assert_eq!(StatusCode::OK, response.status_code);
    assert_eq!(
        acct_id,
        get_acct_for_addr(&test_ctx.address_repo, &random_address.address).await
    );
}

#[rstest]
#[tokio::test]
async fn test_register_watch_address_with_known_account_id_multiple_insert_200(
    random_address: AddressAndKeysetId,
    random_address2: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;

    let response = test_ctx
        .client
        .register_watch_address(
            &acct_id,
            &vec![random_address.clone(), random_address2.clone()].into(),
        )
        .await;

    assert_eq!(StatusCode::OK, response.status_code);

    let known = test_ctx
        .address_repo
        .get(&[
            random_address.address.clone(),
            random_address2.address.clone(),
        ])
        .await
        .unwrap();

    assert_eq!(
        acct_id,
        known.get(&random_address.address).unwrap().account_id
    );
    assert_eq!(
        acct_id,
        known.get(&random_address2.address).unwrap().account_id
    );
}

#[rstest]
#[tokio::test]
async fn test_register_watch_address_with_known_account_id_second_insert_200(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;

    let _ = test_ctx
        .client
        .register_watch_address(&acct_id, &vec![random_address.clone()].into())
        .await;

    let response = test_ctx
        .client
        .register_watch_address(&acct_id, &vec![random_address.clone()].into())
        .await;

    assert_eq!(StatusCode::OK, response.status_code);
    assert_eq!(
        acct_id,
        get_acct_for_addr(&test_ctx.address_repo, &random_address.address).await
    );
}

#[rstest]
#[tokio::test]
async fn test_register_watch_address_with_different_account_id_second_insert_500(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let first_acct_id = test_ctx.known_account_id_1;
    let second_acct_id = test_ctx.known_account_id_2;

    let _ = test_ctx
        .client
        .register_watch_address(&first_acct_id, &vec![random_address.clone()].into())
        .await;

    let response = test_ctx
        .client
        .register_watch_address(&second_acct_id, &vec![random_address.clone()].into())
        .await;

    assert_eq!(StatusCode::INTERNAL_SERVER_ERROR, response.status_code);
}

#[rstest]
#[tokio::test]
async fn test_register_watch_address_unauth_401(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;

    let response = test_ctx
        .client
        .register_watch_address_unauth(&acct_id, &vec![random_address.clone()].into())
        .await;

    assert_eq!(StatusCode::UNAUTHORIZED, response.status_code);
}

#[rstest]
#[tokio::test]
async fn test_delete_all_addresses_success(
    random_address: AddressAndKeysetId,
    random_address2: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;

    // Register multiple addresses
    let _ = test_ctx
        .client
        .register_watch_address(
            &acct_id,
            &vec![random_address.clone(), random_address2.clone()].into(),
        )
        .await;

    // Verify they exist
    let results = test_ctx
        .address_repo
        .get(&[
            random_address.address.clone(),
            random_address2.address.clone(),
        ])
        .await
        .unwrap();
    assert_eq!(results.len(), 2);

    // Delete all addresses
    let response = test_ctx.client.delete_all_watch_addresses(&acct_id).await;

    assert_eq!(StatusCode::OK, response.status_code);

    // Verify they no longer exist
    let results = test_ctx
        .address_repo
        .get(&[
            random_address.address.clone(),
            random_address2.address.clone(),
        ])
        .await
        .unwrap();
    assert!(results.is_empty());
}

#[rstest]
#[tokio::test]
async fn test_delete_all_addresses_empty_account(
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;

    // Delete all addresses for account with no addresses
    let response = test_ctx.client.delete_all_watch_addresses(&acct_id).await;

    assert_eq!(StatusCode::OK, response.status_code);
}

#[rstest]
#[tokio::test]
async fn test_delete_all_addresses_only_deletes_own_account(
    random_address: AddressAndKeysetId,
    random_address2: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id1 = test_ctx.known_account_id_1;
    let acct_id2 = test_ctx.known_account_id_2;

    // Register addresses for both accounts
    let _ = test_ctx
        .client
        .register_watch_address(&acct_id1, &vec![random_address.clone()].into())
        .await;
    let _ = test_ctx
        .client
        .register_watch_address(&acct_id2, &vec![random_address2.clone()].into())
        .await;

    // Delete all addresses for account 1
    let response = test_ctx.client.delete_all_watch_addresses(&acct_id1).await;

    assert_eq!(StatusCode::OK, response.status_code);

    // Verify only account 1's address was deleted
    let results = test_ctx
        .address_repo
        .get(&[
            random_address.address.clone(),
            random_address2.address.clone(),
        ])
        .await
        .unwrap();
    assert!(!results.contains_key(&random_address.address)); // Account 1's address deleted
    assert!(results.contains_key(&random_address2.address)); // Account 2's address remains
}

#[rstest]
#[tokio::test]
async fn test_delete_all_addresses_with_unknown_account_id_404(
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let unknown_acct_id = AccountId::gen().unwrap();

    let response = test_ctx
        .client
        .delete_all_watch_addresses(&unknown_acct_id)
        .await;

    assert_eq!(StatusCode::NOT_FOUND, response.status_code);
}

#[rstest]
#[tokio::test]
async fn test_delete_and_re_register_address(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;

    // Register address
    let register_response = test_ctx
        .client
        .register_watch_address(&acct_id, &vec![random_address.clone()].into())
        .await;
    assert_eq!(StatusCode::OK, register_response.status_code);

    // Delete all addresses
    let delete_response = test_ctx.client.delete_all_watch_addresses(&acct_id).await;
    assert_eq!(StatusCode::OK, delete_response.status_code);

    // Re-register the same address (should work)
    let re_register_response = test_ctx
        .client
        .register_watch_address(&acct_id, &vec![random_address.clone()].into())
        .await;
    assert_eq!(StatusCode::OK, re_register_response.status_code);

    // Verify it exists again
    assert_eq!(
        acct_id,
        get_acct_for_addr(&test_ctx.address_repo, &random_address.address).await
    );
}

#[rstest]
#[tokio::test]
async fn test_delete_all_addresses_twice_is_idempotent(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id = test_ctx.known_account_id_1;

    // Register an address
    let _ = test_ctx
        .client
        .register_watch_address(&acct_id, &vec![random_address.clone()].into())
        .await;

    // Delete all addresses first time
    let delete_response1 = test_ctx.client.delete_all_watch_addresses(&acct_id).await;
    assert_eq!(StatusCode::OK, delete_response1.status_code);

    // Delete all addresses second time (should be idempotent)
    let delete_response2 = test_ctx.client.delete_all_watch_addresses(&acct_id).await;
    assert_eq!(StatusCode::OK, delete_response2.status_code);
}

#[rstest]
#[tokio::test]
async fn test_batch_delete_operations(
    random_address: AddressAndKeysetId,
    random_address2: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id1 = test_ctx.known_account_id_1;
    let acct_id2 = test_ctx.known_account_id_2;

    // Register addresses for both accounts
    let _ = test_ctx
        .client
        .register_watch_address(
            &acct_id1,
            &vec![random_address.clone(), random_address2.clone()].into(),
        )
        .await;

    let third_address = gen_random_address();
    let _ = test_ctx
        .client
        .register_watch_address(&acct_id2, &vec![third_address.clone()].into())
        .await;

    // Verify all addresses exist
    let results1 = test_ctx
        .address_repo
        .get(&[
            random_address.address.clone(),
            random_address2.address.clone(),
        ])
        .await
        .unwrap();
    assert_eq!(results1.len(), 2);

    let results2 = test_ctx
        .address_repo
        .get(&[third_address.address.clone()])
        .await
        .unwrap();
    assert_eq!(results2.len(), 1);

    // Delete all addresses for account 1 - should not affect account 2
    let delete_response = test_ctx.client.delete_all_watch_addresses(&acct_id1).await;
    assert_eq!(StatusCode::OK, delete_response.status_code);

    // Verify account 1's addresses deleted, account 2's remain
    let results1 = test_ctx
        .address_repo
        .get(&[
            random_address.address.clone(),
            random_address2.address.clone(),
        ])
        .await
        .unwrap();
    assert!(results1.is_empty());

    let results2 = test_ctx
        .address_repo
        .get(&[third_address.address.clone()])
        .await
        .unwrap();
    assert_eq!(results2.len(), 1);
}

#[rstest]
#[tokio::test]
async fn test_cross_account_security(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, true).await;
    let acct_id1 = test_ctx.known_account_id_1;
    let acct_id2 = test_ctx.known_account_id_2;

    // Register address with account 1
    let _ = test_ctx
        .client
        .register_watch_address(&acct_id1, &vec![random_address.clone()].into())
        .await;

    // Verify address exists for account 1
    assert_eq!(
        acct_id1,
        get_acct_for_addr(&test_ctx.address_repo, &random_address.address).await
    );

    // Try delete all with account 2 - should return 200 but not affect account 1
    let delete_all_response = test_ctx.client.delete_all_watch_addresses(&acct_id2).await;
    assert_eq!(StatusCode::OK, delete_all_response.status_code);

    // Address should still exist for account 1
    assert_eq!(
        acct_id1,
        get_acct_for_addr(&test_ctx.address_repo, &random_address.address).await
    );
}

#[rstest]
#[tokio::test]
async fn test_register_watch_address_with_disabled_notifications_no_insert(
    random_address: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait + Clone + 'static,
) {
    let test_ctx = TestContext::set_up(repo, false).await;

    // Try to register address with account that has notifications disabled
    let response = test_ctx
        .client
        .register_watch_address(
            &test_ctx.known_account_id_1,
            &vec![random_address.clone()].into(),
        )
        .await;

    assert_eq!(StatusCode::OK, response.status_code);

    // Address should not exist in the repo
    let results = test_ctx
        .address_repo
        .get(&[random_address.address.clone()])
        .await
        .unwrap();
    assert!(
        results.is_empty(),
        "Address should not be stored when notifications are disabled"
    );
}
