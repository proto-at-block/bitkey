use notification::{
    payloads::social_challenge_response_received::SocialChallengeResponseReceivedPayload,
    service::SendNotificationInput, NotificationPayloadBuilder, NotificationPayloadType,
};
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    recovery::{
        social::{
            challenge::{SocialChallenge, SocialChallengeId, SocialChallengeResponse},
            relationship::RecoveryRelationship,
        },
        trusted_contacts::TrustedContactRole,
    },
};

use super::{error::ServiceError, Service};

pub struct RespondToSocialChallengeInput<'a> {
    pub trusted_contact_account_id: &'a AccountId,
    pub social_challenge_id: &'a SocialChallengeId,
    pub trusted_contact_recovery_pake_pubkey: &'a str,
    pub recovery_pake_confirmation: &'a str,
    pub resealed_dek: &'a str,
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn respond_to_social_challenge(
        &self,
        input: RespondToSocialChallengeInput<'_>,
    ) -> Result<SocialChallenge, ServiceError> {
        let prev_challenge = self
            .repository
            .fetch_social_challenge(input.social_challenge_id)
            .await?;
        let customer_account_id = &prev_challenge.customer_account_id;
        let Some(RecoveryRelationship::Endorsed(endorsed_relationship)) = self
            .repository
            .fetch_optional_recovery_relationship_for_account_ids(
                customer_account_id,
                input.trusted_contact_account_id,
                &TrustedContactRole::SocialRecoveryContact,
            )
            .await?
        else {
            return Err(ServiceError::AccountNotTrustedContact);
        };

        let challenge = self
            .repository
            .persist_social_challenge(&prev_challenge.with_response(SocialChallengeResponse {
                recovery_relationship_id: endorsed_relationship.common_fields.id,
                trusted_contact_recovery_pake_pubkey:
                    input.trusted_contact_recovery_pake_pubkey.to_owned(),
                recovery_pake_confirmation: input.recovery_pake_confirmation.to_owned(),
                resealed_dek: input.resealed_dek.to_owned(),
            }))
            .await?;

        self.notification_service
            .send_notification(SendNotificationInput {
                account_id: customer_account_id,
                payload_type: NotificationPayloadType::SocialChallengeResponseReceived,
                payload: &NotificationPayloadBuilder::default()
                    .social_challenge_response_received_payload(Some(
                        SocialChallengeResponseReceivedPayload {
                            trusted_contact_alias: endorsed_relationship
                                .common_fields
                                .trusted_contact_info
                                .alias
                                .clone(),
                        },
                    ))
                    .build()?,
                only_touchpoints: None,
            })
            .await?;

        Ok(challenge)
    }
}
