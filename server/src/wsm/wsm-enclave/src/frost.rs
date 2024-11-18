use crypto::frost::{
    dkg::{aggregate_shares, equality_check, generate_share_packages, KeygenError, SharePackage},
    KeyCommitments, Participant, ShareDetails,
};

struct ShareGenerator {
    app_share_package: SharePackage,
}

impl ShareGenerator {
    pub fn new(app_share_package: SharePackage) -> Self {
        Self { app_share_package }
    }

    pub fn generate(self) -> Result<(PendingEqualityChecker, SharePackage), KeygenError> {
        let mut share_packages = generate_share_packages()?;

        let server_share_package = share_packages
            .pop()
            .expect("Server share package not found");
        let app_share_package = share_packages.pop().expect("App share package not found");

        // Since secp does verification as part of aggregation, we assume that the app's package is
        // being verified in this call.
        let share_details = aggregate_shares(
            Participant::Server,
            &[&server_share_package, &self.app_share_package],
        )?;

        Ok((PendingEqualityChecker { share_details }, app_share_package))
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
