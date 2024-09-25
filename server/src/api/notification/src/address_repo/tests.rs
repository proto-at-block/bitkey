use crate::address_repo::ddb::service::Service as AddressRepoDDB;
use crate::address_repo::memory::Service as AddressRepoMemory;
use crate::address_repo::{AddressAndKeysetId, AddressWatchlistTrait};
use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::secp256k1::rand::thread_rng;
use bdk_utils::bdk::bitcoin::secp256k1::Secp256k1;
use bdk_utils::bdk::bitcoin::{Address, Network, PublicKey};
use database::ddb::{self, Repository};
use http_server::config;
use rstest::{fixture, rstest};
use types::account::identifiers::{AccountId, KeysetId};

use super::ddb::repository::AddressRepository;

fn memory_repo() -> impl AddressWatchlistTrait {
    AddressRepoMemory::default()
}

async fn ddb_repo() -> impl AddressWatchlistTrait {
    let conn = config::extract::<ddb::Config>(Some("test"))
        .unwrap()
        .to_connection()
        .await;
    let repo = AddressRepository::new(conn);
    AddressRepoDDB::create(repo).await.unwrap()
}

fn random_address(network: Network) -> AddressAndKeysetId {
    let secp = Secp256k1::new();
    let (_, pk) = secp.generate_keypair(&mut thread_rng());

    // Force Address to be of type Address<NetworkUnchecked> for AddressAndKeysetId to be
    // deserializable.
    // [W-5648]: Use `as_unchecked` once it's available in BDK.
    let addr_string = Address::p2pkh(&PublicKey::new(pk), network).to_string();
    let unchecked_address: Address<NetworkUnchecked> = addr_string.parse().unwrap();
    AddressAndKeysetId::new(unchecked_address, KeysetId::gen().unwrap())
}

#[fixture]
fn mainnet_address() -> AddressAndKeysetId {
    random_address(Network::Bitcoin)
}

#[fixture]
fn testnet_address() -> AddressAndKeysetId {
    random_address(Network::Testnet)
}

#[rstest]
#[tokio::test]
async fn test_first_insert_succeeds(
    #[values(testnet_address(), mainnet_address())] addr1: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) {
    let acct_id = AccountId::gen().unwrap();

    repo.insert(&[addr1.clone()], &acct_id).await.unwrap();
    assert_eq!(
        acct_id,
        repo.get(&[addr1.address.clone()])
            .await
            .unwrap()
            .get(&addr1.address)
            .unwrap()
            .account_id
    );
}

#[rstest]
#[tokio::test]
async fn test_two_separate_inserts_succeeds(
    #[values(testnet_address(), mainnet_address())] addr1: AddressAndKeysetId,
    #[values(testnet_address(), mainnet_address())] addr2: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) {
    let acct_id = AccountId::gen().unwrap();

    repo.insert(&[addr1.clone()], &acct_id).await.unwrap();
    repo.insert(&[addr2.clone()], &acct_id).await.unwrap();
    assert_eq!(
        acct_id,
        repo.get(&[addr1.address.clone()])
            .await
            .unwrap()
            .get(&addr1.address)
            .unwrap()
            .account_id
    );
    assert_eq!(
        acct_id,
        repo.get(&[addr2.address.clone()])
            .await
            .unwrap()
            .get(&addr2.address)
            .unwrap()
            .account_id
    );
}

#[rstest]
#[tokio::test]
async fn test_batch_insert_succeeds(
    #[values(testnet_address(), mainnet_address())] addr1: AddressAndKeysetId,
    #[values(testnet_address(), mainnet_address())] addr2: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) {
    let acct_id = AccountId::gen().unwrap();

    repo.insert(&[addr1.clone(), addr2.clone()], &acct_id)
        .await
        .unwrap();
    assert_eq!(
        acct_id,
        repo.get(&[addr1.address.clone()])
            .await
            .unwrap()
            .get(&addr1.address)
            .unwrap()
            .account_id
    );
    assert_eq!(
        acct_id,
        repo.get(&[addr2.address.clone()])
            .await
            .unwrap()
            .get(&addr2.address)
            .unwrap()
            .account_id
    );
}

#[rstest]
#[tokio::test]
async fn test_get_without_insert_returns_none(
    #[values(testnet_address(), mainnet_address())] addr1: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] repo: impl AddressWatchlistTrait,
) {
    assert!(repo
        .get(&[addr1.address.clone()])
        .await
        .unwrap()
        .get(&addr1.address)
        .is_none());
}

#[rstest]
#[tokio::test]
async fn test_second_insert_same_acct_succeeds(
    #[values(testnet_address(), mainnet_address())] addr1: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) {
    let acct_id = AccountId::gen().unwrap();

    repo.insert(&[addr1.clone()], &acct_id).await.unwrap();
    assert!(repo.insert(&[addr1.clone()], &acct_id).await.is_ok());
}

#[rstest]
#[tokio::test]
async fn test_second_insert_diff_acct_errors(
    #[values(testnet_address(), mainnet_address())] addr1: AddressAndKeysetId,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) {
    let acct_id1 = AccountId::gen().unwrap();
    let acct_id2 = AccountId::gen().unwrap();

    repo.insert(&[addr1.clone()], &acct_id1).await.unwrap();
    assert!(repo.insert(&[addr1.clone()], &acct_id2).await.is_err());
}

#[rstest]
#[tokio::test]
async fn test_large_batch_insert_succeeds<F>(
    #[values(testnet_address, mainnet_address)] addr_gen: F,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) where
    F: Fn() -> AddressAndKeysetId,
{
    let acct_id = AccountId::gen().unwrap();
    let addrs: Vec<AddressAndKeysetId> = (1..=1000).map(|_| addr_gen()).collect();

    repo.insert(&addrs, &acct_id).await.unwrap();

    for addr in addrs {
        assert_eq!(
            acct_id,
            repo.get(&[addr.address.clone()])
                .await
                .unwrap()
                .get(&addr.address)
                .unwrap()
                .account_id
        );
    }
}

#[rstest]
#[tokio::test]
async fn test_large_batch_get_succeeds<F>(
    #[values(testnet_address, mainnet_address)] addr_gen: F,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) where
    F: Fn() -> AddressAndKeysetId,
{
    let acct_id = AccountId::gen().unwrap();
    let addrs: Vec<AddressAndKeysetId> = (1..=1000).map(|_| addr_gen()).collect();

    repo.insert(&addrs, &acct_id).await.unwrap();

    let stored_addresses = repo
        .get(
            &addrs
                .iter()
                .map(|item| item.address.clone())
                .collect::<Vec<Address<NetworkUnchecked>>>(),
        )
        .await
        .unwrap();
    for addr in addrs {
        assert_eq!(
            acct_id,
            stored_addresses.get(&addr.address).unwrap().account_id
        );
    }
}

#[rstest]
#[tokio::test]
async fn test_batch_get_mixed_known_and_unknown_repo_superset_succeeds<F>(
    #[values(testnet_address, mainnet_address)] addr_gen: F,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) where
    F: Fn() -> AddressAndKeysetId,
{
    let acct_id = AccountId::gen().unwrap();
    let known_addrs: Vec<AddressAndKeysetId> = (1..=10).map(|_| addr_gen()).collect();

    repo.insert(&known_addrs, &acct_id).await.unwrap();

    let get_addrs: Vec<Address<NetworkUnchecked>> = known_addrs
        .into_iter()
        .map(|item| item.address)
        .step_by(2)
        .collect();

    let stored_addresses = repo.get(&get_addrs).await.unwrap();

    // all expected items are in the returned hashmap
    for addr in &get_addrs {
        assert_eq!(acct_id, stored_addresses.get(addr).unwrap().account_id);
    }

    // no additional items are in the returned hashmap
    assert_eq!(get_addrs.len(), stored_addresses.len());
}

#[rstest]
#[tokio::test]
async fn test_batch_get_mixed_known_and_unknown_query_superset_succeeds<F>(
    #[values(testnet_address, mainnet_address)] addr_gen: F,
    #[values(memory_repo(), ddb_repo().await)] mut repo: impl AddressWatchlistTrait,
) where
    F: Fn() -> AddressAndKeysetId,
{
    let acct_id = AccountId::gen().unwrap();
    let superset: Vec<AddressAndKeysetId> = (1..=10).map(|_| addr_gen()).collect();
    let subset: Vec<AddressAndKeysetId> = superset.clone().into_iter().step_by(2).collect();

    repo.insert(&subset, &acct_id).await.unwrap();

    let stored_addresses = repo
        .get(
            &superset
                .iter()
                .map(|item| item.address.clone())
                .collect::<Vec<Address<NetworkUnchecked>>>(),
        )
        .await
        .unwrap();

    // all expected items are in the returned hashmap
    for addr in &subset {
        assert_eq!(
            acct_id,
            stored_addresses
                .get(&addr.address.clone())
                .unwrap()
                .account_id
        );
    }

    // no additional items are in the returned hashmap
    assert_eq!(subset.len(), stored_addresses.len());
}
