use std::collections::HashSet;
use std::env;
use std::str::FromStr;

use crate::sanctions_screener::{SanctionedAddresses, Screener};
use crate::{Config, ScreenerMode};
use csv::ReaderBuilder;
use s3_utils::{read_file_to_memory, ObjectPath};
use serde::Deserialize;
use tracing::error;

pub trait SanctionsScreener {
    fn should_block_transaction(&self, addresses: &[String]) -> bool;
}

#[derive(Clone, Debug, Deserialize)]
pub struct Service {
    screener: Screener,
}

impl Service {
    pub async fn new_and_load_data(
        with_overrides: Option<HashSet<String>>,
        config: Config,
    ) -> Self {
        let mut screener = Screener::new();

        match config.screener {
            ScreenerMode::Test => {
                if let Some(overrides) = with_overrides {
                    screener.set_sanctioned_addresses(SanctionedAddresses(overrides));
                }

                Self { screener }
            }
            ScreenerMode::S3 => {
                // If SQ_SDN_URI is undefined, use hardcoded addresses. If it is malformed, we should
                // panic.
                let object_path = env::var("SQ_SDN_URI").ok().map(|sdn_uri| {
                    ObjectPath::from_str(&sdn_uri).expect("Invalid formatted SQ_SDN_URI")
                });
                let mut sanctioned_addresses = match object_path {
                    Some(path) => {
                        let bytes = read_file_to_memory(&path).await;
                        extract_sanctioned_addresses(&bytes)
                    }
                    None => HashSet::new(),
                };

                if let Some(overrides) = with_overrides {
                    sanctioned_addresses.extend(overrides);
                }

                screener.set_sanctioned_addresses(SanctionedAddresses(sanctioned_addresses));

                Self { screener }
            }
        }
    }
}

impl SanctionsScreener for Service {
    /// Returns true if any of the addresses belong to a sanctioned wallet.
    fn should_block_transaction(&self, destination_addresses: &[String]) -> bool {
        !self
            .screener
            .find_sanctioned_addresses(destination_addresses)
            .is_empty()
    }
}

fn extract_sanctioned_addresses(bytes: &[u8]) -> HashSet<String> {
    let mut addresses = HashSet::new();

    let mut reader = ReaderBuilder::new().has_headers(true).from_reader(bytes);

    for result in reader.records() {
        match result {
            Ok(record) => {
                // Bitcoin address is always in the 13th column.
                // If it exist, insert it into the addresses HashSet. Else, skip it.
                match record.get(13) {
                    Some(address) => addresses.insert(address.to_string()),
                    None => continue,
                };
            }
            Err(e) => {
                error!("Error reading record: {}", e);
            }
        }
    }

    addresses
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_load_data_local() {
        let service = Service::new_and_load_data(
            Some(HashSet::from(["addr1".to_string()])),
            Config {
                screener: ScreenerMode::Test,
            },
        )
        .await;
        let blocked_addresses = service
            .screener
            .find_sanctioned_addresses(&["addr1".to_string()]);

        assert!(!blocked_addresses.is_empty());
    }

    #[test]
    fn test_extract_sanctioned_addresses() {
        let result = extract_sanctioned_addresses(b"SDN List as of 11/3/23,SDN Name,Entity Type,Sanctions Program,,,,,,,,Identifying Info,Digital Currency Type,Wallet Address\r\nOFAC Listed,\"YAN, Xiaobing\",individual,SDNTK,-0-,-0-,-0-,-0-,-0-,-0-,-0-,\"DOB 25 Mar 1977; POB Wuhan City, Hubei, China; citizen China; Gender Male; Digital Currency Address - XBT 12QtD5BFwRsdNsAZY76UVE1xyCGNTojH9h; alt. Digital Currency Address - XBT 1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVo; alt. Digital Currency Address - XBT 13f59kUM5FU8MfTG7DCEugYarDhSD7XCoC; alt. Digital Currency Address - XBT 1P3ZfGFLezzYGg9k5SVzQmnjyh7nrUmF2y; alt. Digital Currency Address - XBT 1EpMiZkQVekM5ij12nMiEwttFPcDK9XhX6; alt. Digital Currency Address - XBT 1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC; Chinese Commercial Code 7346 2556 0365; Citizen's Card Number 421002197703250019 (China); a.k.a. 'ZHOU, William'; a.k.a. 'YAN, Steven'.\",XBT,12QtD5BFwRsdNsAZY76UVE1xyCGNTojH9h\r\nOFAC Listed,\"YAN, Xiaobing\",individual,SDNTK,,,,,,,,,XBT,1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVo\r\nOFAC Listed,\"YAN, Xiaobing\",individual,SDNTK,,,,,,,,,XBT,13f59kUM5FU8MfTG7DCEugYarDhSD7XCoC\r\nOFAC Listed,\"YAN, Xiaobing\",individual,SDNTK,,,,,,,,,XBT,1P3ZfGFLezzYGg9k5SVzQmnjyh7nrUmF2y\r\nOFAC Listed,\"YAN, Xiaobing\",individual,SDNTK,,,,,,,,,XBT,1EpMiZkQVekM5ij12nMiEwttFPcDK9XhX6\r\nOFAC Listed,\"YAN, Xiaobing\",individual,SDNTK,,,,,,,,,XBT,1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC");
        let expected: HashSet<String> = vec![
            "12QtD5BFwRsdNsAZY76UVE1xyCGNTojH9h",
            "1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVo",
            "13f59kUM5FU8MfTG7DCEugYarDhSD7XCoC",
            "1P3ZfGFLezzYGg9k5SVzQmnjyh7nrUmF2y",
            "1EpMiZkQVekM5ij12nMiEwttFPcDK9XhX6",
            "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC",
        ]
        .into_iter()
        .map(|s| s.to_string())
        .collect();

        assert_eq!(result, expected)
    }
}
