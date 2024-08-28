// Re-export from crypto crate
pub use crypto::frost::{
    dkg::{KeygenError, SharePackage},
    KeyCommitments, ShareDetails,
};

use std::sync::{Arc, Mutex};

use crypto::frost::dkg::{
    aggregate_shares, equality_check, generate_share_package, share_agg_params,
};
use crypto::frost::{Participant, ParticipantIndex, ShareAggParams, APP_PARTICIPANT_INDEX};

pub struct ShareGenerator {
    inner: Mutex<ShareGeneratorState>,
}

impl Default for ShareGenerator {
    fn default() -> Self {
        Self::new()
    }
}

impl ShareGenerator {
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(ShareGeneratorState::new()),
        }
    }

    pub fn generate(&self) -> Result<Arc<SharePackage>, KeygenError> {
        let mut inner = self.inner.lock().unwrap();

        inner.generate().map(Arc::new)
    }

    pub fn aggregate(
        &self,
        peer_share_package: Arc<SharePackage>,
        peer_key_commitments: Arc<KeyCommitments>,
    ) -> Result<Arc<ShareDetails>, KeygenError> {
        let inner = self.inner.lock().unwrap();

        inner
            .aggregate(peer_share_package.as_ref(), peer_key_commitments.as_ref())
            .map(Arc::new)
    }
}

struct ShareGeneratorState {
    app_id: ParticipantIndex,
    share_agg_params: Option<ShareAggParams>,
}

impl ShareGeneratorState {
    // TODO: Change initializer to instantiate using a long-term public key.
    pub fn new() -> Self {
        Self {
            app_id: APP_PARTICIPANT_INDEX,
            share_agg_params: None,
        }
    }

    /// Returns a SharePackage to send the Server.
    fn generate(&mut self) -> Result<SharePackage, KeygenError> {
        let share_package = generate_share_package(Participant::App, Participant::Server)?;
        self.share_agg_params = share_agg_params(&share_package).map(Some)?;

        Ok(share_package)
    }

    /// Aggregates the peer's share package and generates the key share.
    /// MUST be run after `generate`.
    fn aggregate(
        &self,
        peer_share_package: &SharePackage,
        peer_key_commitments: &KeyCommitments,
    ) -> Result<ShareDetails, KeygenError> {
        if self.share_agg_params.is_none() {
            return Err(KeygenError::MissingShareAggParams);
        }

        let share_details =
            aggregate_shares(self.share_agg_params.as_ref().unwrap(), peer_share_package)?;

        equality_check(peer_key_commitments, share_details)
    }
}

#[cfg(test)]
mod tests {
    use crypto::frost::{
        dkg::{aggregate_shares, generate_share_package, share_agg_params, KeygenError},
        Participant::{App, Server},
    };
    use std::sync::Arc;

    use crate::ShareGenerator;

    #[test]
    fn test_aggregate_without_agg_params() {
        let share_generator = ShareGenerator::new();

        // Simulate server
        let peer_share_package = generate_share_package(Server, App).unwrap();
        let share_agg_params = share_agg_params(&peer_share_package).unwrap();
        let share_details = aggregate_shares(&share_agg_params, &peer_share_package).unwrap();

        // Attempt to aggregate without generating a share package.
        assert_eq!(
            share_generator.aggregate(
                Arc::new(peer_share_package),
                Arc::new(share_details.key_commitments)
            ),
            Err(KeygenError::MissingShareAggParams)
        )
    }
}
