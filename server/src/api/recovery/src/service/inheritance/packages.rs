use database::ddb::DatabaseError;

use tracing::instrument;

use types::recovery::{
    inheritance::package::Package, social::relationship::RecoveryRelationshipId,
};

use super::{error::ServiceError, Service};

impl Service {
    #[instrument(skip(self, input))]
    pub async fn upload_packages(&self, input: Vec<Package>) -> Result<Vec<Package>, ServiceError> {
        let package_rows = self
            .repository
            .persist_packages(input)
            .await
            .map_err(|e| match e {
                DatabaseError::DependantObjectNotFound(_) => ServiceError::InvalidRelationship,
                _ => ServiceError::from(e),
            })?;

        Ok(package_rows)
    }

    #[instrument(skip(self, relationship_ids))]
    pub async fn get_packages_by_relationship_id(
        &self,
        relationship_ids: &Vec<RecoveryRelationshipId>,
    ) -> Result<Vec<Package>, ServiceError> {
        let packages = self
            .repository
            .fetch_packages_by_relationship_id(relationship_ids)
            .await?
            .into_iter()
            .map(|row| row.try_into().map_err(|_| ServiceError::InvalidPackage))
            .collect::<Result<Vec<_>, _>>()?;

        Ok(packages)
    }
}
