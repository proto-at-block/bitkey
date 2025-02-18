use std::str::FromStr;

use super::{
    error::ServiceError, fetch_relationships_and_claim, filter_endorsed_relationship, Service,
};

use bdk_utils::is_addressed_to_wallet;
use bdk_utils::{bdk::bitcoin::Address, generate_electrum_rpc_uris};
use feature_flags::flag::ContextKey;
use tracing::instrument;
use types::account::entities::Account;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimCommonFields, InheritanceClaimId, InheritanceDestination,
};
pub struct UpdateInheritanceProcessWithDestinationInput<'a> {
    pub beneficiary_account: &'a Account,
    pub inheritance_claim_id: InheritanceClaimId,
    pub destination: InheritanceDestination,
    pub context_key: ContextKey,
}

impl Service {
    /// This function updates the destination of a pending inheritance claim
    /// The beneficiary must be the one to update the destination.
    /// A pending claim must exist between the benefactor and beneficiary.
    /// There must also be a valid recovery relationship between the benefactor and beneficiary.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary account and inheritance claim id
    ///
    /// # Returns
    ///
    /// * The updated inheritance claim
    ///     
    #[instrument(skip(self, input))]
    async fn update_claim(
        &self,
        input: UpdateInheritanceProcessWithDestinationInput<'_>,
    ) -> Result<InheritanceClaim, ServiceError> {
        // Get relationships for the beneficiary account and the claim by id
        let (relationships, claim) = fetch_relationships_and_claim(
            self,
            input.beneficiary_account.get_id(),
            &input.inheritance_claim_id,
        )
        .await?;

        filter_endorsed_relationship(
            relationships.customers,
            &claim.common_fields().recovery_relationship_id,
        )?;

        // For now, FullAccount beneiciaries can only send to their own Bitkey wallets
        match input.beneficiary_account {
            Account::Full(full_account) => {
                // Destination must be a valid destination
                let spending_keyset = full_account
                    .active_spending_keyset()
                    .ok_or(ServiceError::NoActiveDescriptorKeySet)?;

                let rpc_uris = generate_electrum_rpc_uris(
                    &self.feature_flags_service,
                    Some(input.context_key),
                );

                let descriptor_keyset = full_account
                    .active_descriptor_keyset()
                    .ok_or(ServiceError::NoActiveDescriptorKeySet)?;
                let wallet = descriptor_keyset
                    .generate_wallet(false, &rpc_uris)
                    .map_err(ServiceError::BdkUtils)?;

                let destination_address_script_pubkey =
                    Address::from_str(input.destination.destination_address())
                        .map_err(ServiceError::InvalidAddress)?
                        .require_network(spending_keyset.network.into())
                        .map_err(ServiceError::InvalidAddress)?
                        .script_pubkey();
                if !is_addressed_to_wallet(&wallet, &destination_address_script_pubkey)? {
                    return Err(ServiceError::UnownedDestination);
                }
            }
            _ => unimplemented!(),
        }

        let updated_common_fields = InheritanceClaimCommonFields {
            destination: Some(input.destination),
            ..claim.common_fields().to_owned()
        };
        let updated_claim = self
            .repository
            .persist_inheritance_claim(&claim.with_common_fields(&updated_common_fields))
            .await?;

        Ok(updated_claim)
    }
}
