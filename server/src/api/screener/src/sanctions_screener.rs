use serde::Deserialize;
use std::collections::HashSet;

#[derive(Clone, Debug, Deserialize)]
pub struct SanctionedAddresses(pub HashSet<String>);

#[derive(Clone, Debug, Deserialize)]
pub struct Screener {
    /// HashSet of sanctioned wallet addresses.
    sanctioned_addresses: SanctionedAddresses,
}

impl Screener {
    pub(crate) fn new() -> Self {
        Self {
            sanctioned_addresses: SanctionedAddresses(HashSet::new()),
        }
    }

    pub(crate) fn set_sanctioned_addresses(&mut self, addresses: SanctionedAddresses) {
        self.sanctioned_addresses = addresses;
    }

    pub(crate) fn find_sanctioned_addresses(&self, addresses: &[String]) -> Vec<String> {
        addresses
            .iter()
            .filter(|address| self.sanctioned_addresses.0.contains(*address))
            .map(|address| address.to_string())
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn do_are_chain_addresses_blocked_works() {
        let mut screener = Screener::new();
        let blocklist: HashSet<String> = vec![
            "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC",
            "1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVo",
        ]
        .into_iter()
        .map(|s| s.to_string())
        .collect();
        screener.set_sanctioned_addresses(SanctionedAddresses(blocklist));

        let cases = vec![
            (
                // Match a single address found in the list
                vec![
                    "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC".to_string(),
                    "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsX".to_string(),
                ],
                vec!["1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC".to_string()],
            ),
            (
                // Match a multiple address found in the list
                vec![
                    "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC".to_string(),
                    "1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVo".to_string(),
                ],
                vec![
                    "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC".to_string(),
                    "1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVo".to_string(),
                ],
            ),
            (
                // Match no addresses found in list; also tests case sensitivity
                vec![
                    "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsc".to_string(),
                    "1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVO".to_string(),
                ],
                vec![],
            ),
        ];

        for (addresses, expected) in cases {
            let result = screener.find_sanctioned_addresses(&addresses);
            assert_eq!(
                result, expected,
                "expected {:?} for addresses {:?}",
                expected, addresses
            );
        }
    }
}
