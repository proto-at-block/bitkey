use std::collections::HashMap;

use futures::future::try_join_all;
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipEndorsed, RecoveryRelationshipEndorsement,
    RecoveryRelationshipId,
};

use super::{error::ServiceError, Service};

/// The input for the `endorse_recovery_relationships` function
///
/// # Fields
///
/// * `customer_account_id` - The customer account that is trying to endorse the recovery relationship
/// * `trusted_contacts_to_endorse` - The list of Recovery Contacts to be endorsed. They include the recovery_relationship_id and the endorsement_key_certificate
pub struct EndorseRecoveryRelationshipsInput<'a> {
    pub customer_account_id: &'a AccountId,
    pub endorsements: Vec<RecoveryRelationshipEndorsement>,
}

impl Service {
    /// This function allows the customer to endorse the recovery relationship after it has been accepted by the RC.
    /// This ensures that in the future, the customer will be able to trust that Recovery Contact keys haven't been
    /// tampered with in transit.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the customer account id and the list of endorsements to be endorsed
    ///
    /// # Returns
    ///
    /// * The list of endorsed recovery relationships
    #[instrument(skip(self, input))]
    pub async fn endorse_recovery_relationships(
        &self,
        input: EndorseRecoveryRelationshipsInput<'_>,
    ) -> Result<Vec<RecoveryRelationship>, ServiceError> {
        let recovery_relationships_for_account = self
            .repository
            .fetch_recovery_relationships_for_account(input.customer_account_id)
            .await?;

        let endorsed_relationships = recovery_relationships_for_account
            .endorsed_trusted_contacts
            .clone()
            .into_iter()
            .map(|r| match &r {
                RecoveryRelationship::Endorsed(c) => (c.common_fields.id.to_owned(), r),
                _ => unreachable!(),
            })
            .collect::<HashMap<RecoveryRelationshipId, RecoveryRelationship>>();
        let unendorsed_relationships: HashMap<RecoveryRelationshipId, RecoveryRelationship> =
            recovery_relationships_for_account
                .unendorsed_trusted_contacts
                .into_iter()
                .map(|r| match &r {
                    RecoveryRelationship::Unendorsed(c) => (c.common_fields.id.to_owned(), r),
                    _ => unreachable!(),
                })
                .collect::<HashMap<RecoveryRelationshipId, RecoveryRelationship>>();

        let mut to_update = Vec::new();
        for endorsement in input.endorsements {
            let (common_fields, connection_fields) =
                if let Some(RecoveryRelationship::Endorsed(c)) =
                    endorsed_relationships.get(&endorsement.recovery_relationship_id)
                {
                    (c.common_fields.to_owned(), c.connection_fields.to_owned())
                } else if let Some(RecoveryRelationship::Unendorsed(c)) =
                    unendorsed_relationships.get(&endorsement.recovery_relationship_id)
                {
                    (c.common_fields.to_owned(), c.connection_fields.to_owned())
                } else {
                    return Err(ServiceError::InvitationNonEndorsable);
                };

            to_update.push(RecoveryRelationship::Endorsed(
                RecoveryRelationshipEndorsed {
                    common_fields,
                    connection_fields,
                    delegated_decryption_pubkey_certificate: endorsement
                        .delegated_decryption_pubkey_certificate
                        .to_owned(),
                },
            ));
        }

        let updated_relationships = try_join_all(
            to_update
                .iter()
                .map(|r| self.repository.persist_recovery_relationship(r)),
        )
        .await?;
        Ok(updated_relationships)
    }
}
