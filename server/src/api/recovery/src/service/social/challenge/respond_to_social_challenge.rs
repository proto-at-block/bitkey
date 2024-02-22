use notification::{
    payloads::social_challenge_response_received::SocialChallengeResponseReceivedPayload,
    service::SendNotificationInput, NotificationPayloadBuilder, NotificationPayloadType,
};
use types::{
    account::identifiers::AccountId,
    recovery::social::{
        challenge::{SocialChallenge, SocialChallengeId, SocialChallengeResponse},
        relationship::RecoveryRelationship,
    },
};

use super::{error::ServiceError, Service};

pub struct RespondToSocialChallengeInput<'a> {
    pub trusted_contact_account_id: &'a AccountId,
    pub social_challenge_id: &'a SocialChallengeId,
    #[deprecated]
    pub shared_secret_ciphertext: &'a str,
    pub trusted_contact_recovery_pubkey: &'a str,
    pub recovery_key_confirmation: &'a str,
    pub recovery_sealed_pkek: &'a str,
}

impl Service {
    pub async fn respond_to_social_challenge(
        &self,
        input: RespondToSocialChallengeInput<'_>,
    ) -> Result<SocialChallenge, ServiceError> {
        let prev_challenge = self
            .repository
            .fetch_social_challenge(input.social_challenge_id)
            .await?;
        let customer_account_id = &prev_challenge.customer_account_id;

        //TODO(BKR-919): Fix this once we update the social challenge
        let common_fields = match self
            .repository
            .fetch_optional_recovery_relationship_for_account_ids(
                customer_account_id,
                input.trusted_contact_account_id,
            )
            .await?
        {
            Some(RecoveryRelationship::Unendorsed(r)) => r.common_fields,
            Some(RecoveryRelationship::Endorsed(r)) => r.common_fields,
            _ => return Err(ServiceError::AccountNotTrustedContact),
        };

        let challenge = self
            .repository
            .persist_social_challenge(&prev_challenge.with_response(SocialChallengeResponse {
                recovery_relationship_id: common_fields.id,
                shared_secret_ciphertext: input.shared_secret_ciphertext.to_owned(),
                trusted_contact_recovery_pubkey: input.trusted_contact_recovery_pubkey.to_owned(),
                recovery_key_confirmation: input.recovery_key_confirmation.to_owned(),
                recovery_sealed_pkek: input.recovery_sealed_pkek.to_owned(),
            }))
            .await?;

        self.notification_service
            .send_notification(SendNotificationInput {
                account_id: customer_account_id,
                payload_type: NotificationPayloadType::SocialChallengeResponseReceived,
                payload: &NotificationPayloadBuilder::default()
                    .social_challenge_response_received_payload(Some(
                        SocialChallengeResponseReceivedPayload {
                            trusted_contact_alias: common_fields.trusted_contact_alias.clone(),
                        },
                    ))
                    .build()?,
                only_touchpoints: None,
            })
            .await?;

        Ok(challenge)
    }
}
