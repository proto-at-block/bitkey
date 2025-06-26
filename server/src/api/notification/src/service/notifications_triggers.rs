use errors::ApiError;
use futures::future::try_join_all;
use time::OffsetDateTime;
use tracing::instrument;
use types::{
    account::{entities::CommonAccountFields, identifiers::AccountId},
    notification::{NotificationsTrigger, NotificationsTriggerType},
};

use super::Service;
use crate::{
    payloads::security_hub::SecurityHubPayload,
    schedule::ScheduleNotificationType,
    service::{ScheduleNotificationsInput, UpdateNotificationsTriggersInput},
    NotificationPayloadBuilder,
};

impl Service {
    #[instrument(skip(self))]
    pub async fn update_notifications_triggers(
        &self,
        input: UpdateNotificationsTriggersInput<'_>,
    ) -> Result<(), ApiError> {
        let account = self.account_repo.fetch(input.account_id).await?;

        let now = OffsetDateTime::now_utc();
        let old_triggers = &account.get_common_fields().notifications_triggers;

        let (new_triggers, mut notifications_triggers): (Vec<_>, Vec<_>) = input
            .trigger_types
            .into_iter()
            .map(
                |tt| match old_triggers.iter().find(|t| t.trigger_type == tt) {
                    Some(existing) => NotificationsTrigger::new(tt, existing.created_at, now),
                    None => NotificationsTrigger::new(tt, now, now),
                },
            )
            .partition(|t| t.created_at == now);

        notifications_triggers.extend(new_triggers.iter().cloned());

        let updated_account = account.update(CommonAccountFields {
            notifications_triggers,
            ..account.get_common_fields().clone()
        })?;

        self.account_repo.persist(&updated_account).await?;

        try_join_all(
            new_triggers
                .iter()
                .map(|t| handle_notifications(self, input.account_id, t)),
        )
        .await?;

        Ok(())
    }
}

async fn handle_notifications(
    service: &Service,
    account_id: &AccountId,
    state: &NotificationsTrigger,
) -> Result<(), ApiError> {
    match state.trigger_type {
        NotificationsTriggerType::SecurityHubWalletAtRisk => {
            service
                .schedule_notifications(ScheduleNotificationsInput {
                    account_id: account_id.clone(),
                    notification_type: ScheduleNotificationType::SecurityHubWalletAtRisk,
                    payload: NotificationPayloadBuilder::default()
                        .security_hub_payload(Some(SecurityHubPayload {
                            created_at: state.created_at,
                            trigger_type: state.trigger_type.clone(),
                        }))
                        .build()
                        .map_err(|_| {
                            ApiError::GenericInternalApplicationError(
                                "failed to build notification payload".to_string(),
                            )
                        })?,
                })
                .await
        }
    }
    .map_err(ApiError::from)
}
