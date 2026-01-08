use serde::Deserialize;
use std::collections::HashSet;

#[derive(Clone, Debug, Deserialize)]
pub(crate) struct Sanctions(pub HashSet<String>);

impl Sanctions {
    pub(crate) fn new() -> Self {
        Self(HashSet::new())
    }

    pub(crate) fn set_addresses(&mut self, addresses: HashSet<String>) {
        self.0 = addresses;
    }

    pub(crate) fn find_addresses(&self, addresses: &[String]) -> Vec<String> {
        addresses
            .iter()
            .filter(|address| self.0.contains(*address))
            .map(|address| address.to_string())
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn do_are_chain_addresses_blocked_works() {
        let mut sanctions = Sanctions::new();
        let blocklist: HashSet<String> = vec![
            "1JREJdZupiFhE7ZzQPtASuMCvvpXC7wRsC",
            "1Kuf2Rd8mDyAViwBozGTNYnvWL8uYFrkVo",
        ]
        .into_iter()
        .map(|s| s.to_string())
        .collect();
        sanctions.set_addresses(blocklist);

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
            let result = sanctions.find_addresses(&addresses);
            assert_eq!(
                result, expected,
                "expected {:?} for addresses {:?}",
                expected, addresses
            );
        }
    }
}
