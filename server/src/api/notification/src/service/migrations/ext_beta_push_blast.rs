use std::collections::HashSet;

use account::entities::{Touchpoint, TouchpointPlatform};
use async_trait::async_trait;
use migration::{Migration, MigrationError};
use time::macros::datetime;

use crate::{
    payloads::push_blast::PushBlastPayload,
    service::{SendNotificationInput, Service},
    NotificationPayloadBuilder, NotificationPayloadType,
};

pub(crate) struct ExtBetaPushBlast<'a> {
    service: &'a Service,
}

impl<'a> ExtBetaPushBlast<'a> {
    pub fn new(service: &'a Service) -> Self {
        Self { service }
    }
}

#[async_trait]
impl<'a> Migration for ExtBetaPushBlast<'a> {
    fn name(&self) -> &str {
        // TODO: for real blast, update name
        "20240228_extbetapushblast_test01"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        // TODO: for real blast, replace with beta app platforms
        let target_platforms =
            HashSet::from([TouchpointPlatform::ApnsTeam, TouchpointPlatform::FcmTeam]);

        // TODO: for real blast, replace with appropriate message
        let message = "Ext beta push blast test message";

        for account in self
            .service
            .account_service
            .fetch_accounts()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?
        {
            // TODO: for real blast, reverse or remove this condition
            if !account.get_common_fields().properties.is_test_account {
                // Skip if account is not a test account
                continue;
            }

            // TODO: for real blast, filter only known integration email addresses
            if !account.get_common_fields().touchpoints.iter().any(|t| matches!(t, Touchpoint::Email { email_address, .. } if email_address.ends_with("@squareup.com") || email_address.ends_with("@block.xyz"))) {
                // Skip non-employee email addresses
                continue;
            }

            let touchpoints: Vec<_> = account.get_common_fields().touchpoints.iter().filter(|t| matches!(t, Touchpoint::Push { platform, .. } if target_platforms.contains(platform))).collect();
            if touchpoints.is_empty() {
                // Skip if account does not have touchpoints on the right platforms
                continue;
            }

            if self
                .service
                .notification_repo
                .fetch_customer_for_account_id_and_payload_type(
                    account.get_id(),
                    Some(NotificationPayloadType::PushBlast),
                )
                .await
                .map_err(|e| MigrationError::ExtBetaPushBlast(e.to_string()))?
                .iter()
                .any(|c| {
                    // TODO: for real blast, update to start of day we run migration
                    c.created_at > datetime!(2024-02-28 0:00 -8)
                })
            {
                // Skip if account already received a push blast since the start of the day we run this migration
                continue;
            }

            self.service
                .send_notification(SendNotificationInput {
                    account_id: account.get_id(),
                    payload_type: NotificationPayloadType::PushBlast,
                    payload: &NotificationPayloadBuilder::default()
                        .push_blast_payload(Some(PushBlastPayload {
                            account_id: account.get_id().to_owned(),
                            message: message.to_string(),
                        }))
                        .build()
                        .map_err(|e| MigrationError::ExtBetaPushBlast(e.to_string()))?,
                    only_touchpoints: Some(
                        touchpoints
                            .into_iter()
                            .map(|t| t.to_owned().into())
                            .collect(),
                    ),
                })
                .await
                .map_err(|e| MigrationError::ExtBetaPushBlast(e.to_string()))?;
        }

        Ok(())
    }
}
