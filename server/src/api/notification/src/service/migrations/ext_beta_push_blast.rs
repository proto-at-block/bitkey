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
        "20240321_extbetapushblast"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        let target_platforms = HashSet::from([TouchpointPlatform::Apns, TouchpointPlatform::Fcm]);

        let message = "The Bitkey Beta is winding down in the coming weeks. Return to the Bitkey app or check your email to learn more.";

        for account in self
            .service
            .account_service
            .fetch_accounts()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?
        {
            if account.get_common_fields().properties.is_test_account {
                // Skip if account is a test account to reduce volume
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
                .any(|c| c.created_at > datetime!(2024-03-21 0:00 -8))
            {
                // Skip if account already received this push blast
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
