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
}

impl<A: AddressWatchlistTrait + Clone + 'static> TestContext<A> {
    async fn set_up(address_repo: A) -> Self {
        let overrides = GenServiceOverrides::new().address_repo(Box::new(address_repo.clone()));
        let (mut ctx, bootstrap) = gen_services_with_overrides(overrides).await;
        let client = TestClient::new(bootstrap.router).await;
        let account_1 =
            create_full_account(&mut ctx, &bootstrap.services, BitcoinSignet, None).await;
        let account_2 =
            create_full_account(&mut ctx, &bootstrap.services, BitcoinSignet, None).await;

        Self {
            address_repo,
            client,
            known_account_id_1: account_1.id,
            known_account_id_2: account_2.id,
        }
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
    let test_ctx = TestContext::set_up(repo).await;
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
    let test_ctx = TestContext::set_up(repo).await;
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
    let test_ctx = TestContext::set_up(repo).await;
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
    let test_ctx = TestContext::set_up(repo).await;
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
    let test_ctx = TestContext::set_up(repo).await;
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
    let test_ctx = TestContext::set_up(repo).await;
    let acct_id = test_ctx.known_account_id_1;

    let response = test_ctx
        .client
        .register_watch_address_unauth(&acct_id, &vec![random_address.clone()].into())
        .await;

    assert_eq!(StatusCode::UNAUTHORIZED, response.status_code);
}
