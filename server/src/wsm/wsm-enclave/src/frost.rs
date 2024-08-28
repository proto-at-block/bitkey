use crypto::frost::{
    dkg::{
        aggregate_shares, equality_check, generate_share_package, share_agg_params, KeygenError,
        SharePackage,
    },
    KeyCommitments, Participant, ShareDetails,
};

struct ShareGenerator {
    app_share_package: SharePackage,
}

impl ShareGenerator {
    pub fn new(app_share_package: SharePackage) -> Self {
        Self { app_share_package }
    }

    pub fn generate(self) -> Result<PendingEqualityChecker, KeygenError> {
        let share_package = generate_share_package(Participant::Server, Participant::App)?;
        let share_agg_params = share_agg_params(&share_package)?;
        // Since secp does verification as part of aggregation, we assume that the app's package is
        // being verified in this call.
        let share_details = aggregate_shares(&share_agg_params, &self.app_share_package)?;

        Ok(PendingEqualityChecker { share_details })
    }
}

struct PendingEqualityChecker {
    share_details: ShareDetails,
}

impl PendingEqualityChecker {
    pub fn verify_equality(
        self,
        peer_key_commitments: &KeyCommitments,
    ) -> Result<ShareDetails, KeygenError> {
        equality_check(peer_key_commitments, self.share_details)
    }
}
