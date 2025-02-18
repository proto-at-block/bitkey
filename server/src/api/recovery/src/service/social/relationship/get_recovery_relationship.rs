use tracing::instrument;
use types::recovery::social::relationship::RecoveryRelationship;
use types::recovery::social::relationship::RecoveryRelationshipId;

use super::{error::ServiceError, Service};

impl Service {
    /// This function fetches the recovery relationship for a given id.
    ///
    /// # Arguments
    ///
    /// * `recovery_relationship_id` - The id of the recovery relationship to fetch
    ///
    /// # Returns
    ///
    /// * The recovery relationship
    #[instrument(skip(self))]
    pub async fn get_recovery_relationship(
        &self,
        recovery_relationship_id: &RecoveryRelationshipId,
    ) -> Result<RecoveryRelationship, ServiceError> {
        let relationship = self
            .repository
            .fetch_recovery_relationship(recovery_relationship_id)
            .await?;

        Ok(relationship)
    }
}
