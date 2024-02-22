use std::collections::HashMap;

use futures::future::try_join_all;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipEndorsed, RecoveryRelationshipEndorsement,
    RecoveryRelationshipId,
};

use super::{error::ServiceError, Service};

pub struct EndorseRecoveryRelationshipsInput<'a> {
    pub customer_account_id: &'a AccountId,
    pub endorsements: Vec<RecoveryRelationshipEndorsement>,
}

impl Service {
    /// This function allows the customer to endorse the recovery relationship after it has been accepted by the TC.
    /// This ensures that in the future, the customer will be able to trust that Trusted Contact keys haven't been
    /// tampered with in transit.
    ///
    /// # Arguments
    ///
    /// * `customer_account_id` - The customer account that is trying to endorse the recovery relationship
    /// * `trusted_contacts_to_endorse` - The list of trusted contacts to be endorsed. They include the recovery_relationship_id and the endorsement_key_cert
    pub async fn endorse_recovery_relationships(
        &self,
        input: EndorseRecoveryRelationshipsInput<'_>,
    ) -> Result<Vec<RecoveryRelationship>, ServiceError> {
        let recovery_relationships_for_account = self
            .repository
            .fetch_recovery_relationships_for_account(input.customer_account_id)
            .await?;

        let endorsed_trusted_contacts = recovery_relationships_for_account
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
            if let Some(RecoveryRelationship::Endorsed(c)) =
                endorsed_trusted_contacts.get(&endorsement.recovery_relationship_id)
            {
                if c.endorsement_key_certificate != endorsement.endorsement_key_certificate {
                    return Err(ServiceError::RelationshipAlreadyEstablished);
                }
                // If the endorsement key certificate is the same, we can skip this relationship
                continue;
            }

            let Some(RecoveryRelationship::Unendorsed(c)) =
                unendorsed_relationships.get(&endorsement.recovery_relationship_id)
            else {
                return Err(ServiceError::InvitationNonEndorsable);
            };

            to_update.push(RecoveryRelationship::Endorsed(
                RecoveryRelationshipEndorsed {
                    common_fields: c.common_fields.to_owned(),
                    connection_fields: c.connection_fields.to_owned(),
                    endorsement_key_certificate: endorsement.endorsement_key_certificate.to_owned(),
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
