use repository::recovery::social::fetch::RecoveryRelationshipsForAccount;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::RecoveryRelationship;

use super::{error::ServiceError, Service};

pub struct GetRecoveryRelationshipsInput<'a> {
    pub account_id: &'a AccountId,
}

pub struct GetRecoveryRelationshipsOutput {
    pub invitations: Vec<RecoveryRelationship>,
    pub endorsed_trusted_contacts: Vec<RecoveryRelationship>,
    pub unendorsed_trusted_contacts: Vec<RecoveryRelationship>,
    pub customers: Vec<RecoveryRelationship>,
}

impl From<RecoveryRelationshipsForAccount> for GetRecoveryRelationshipsOutput {
    fn from(relationships: RecoveryRelationshipsForAccount) -> Self {
        Self {
            invitations: relationships.invitations,
            endorsed_trusted_contacts: relationships.endorsed_trusted_contacts,
            unendorsed_trusted_contacts: relationships.unendorsed_trusted_contacts,
            customers: relationships.customers,
        }
    }
}

impl Service {
    /// This function fetches the recovery relationships for a given account. It returns a list of
    /// invitations, trusted contacts, and customers. Both the trusted contacts and customers are
    /// able to call this endpoint.
    ///
    /// # Arguments
    ///
    /// * `account_id` - The account to fetch the recovery relationships for
    pub async fn get_recovery_relationships(
        &self,
        input: GetRecoveryRelationshipsInput<'_>,
    ) -> Result<GetRecoveryRelationshipsOutput, ServiceError> {
        let relationships = self
            .repository
            .fetch_recovery_relationships_for_account(input.account_id)
            .await?;

        Ok(relationships.into())
    }
}
