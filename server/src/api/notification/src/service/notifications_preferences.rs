use account::entities::{Account, CommonAccountFields, Touchpoint};
use errors::ApiError;
use repository::consent::ConsentRepository;
use time::{Duration, OffsetDateTime};
use tracing::instrument;
use types::{
    consent::Consent,
    notification::{NotificationsPreferences, NotificationsPreferencesState},
};

use crate::clients::iterable::IterableUserId;

use super::{FetchNotificationsPreferencesInput, Service, UpdateNotificationsPreferencesInput};

const SYNC_FROM_ITERABLE_DELAY: Duration = Duration::minutes(5);

impl Service {
    #[instrument(skip(self))]
    pub async fn fetch_notifications_preferences(
        &self,
        input: FetchNotificationsPreferencesInput<'_>,
    ) -> Result<NotificationsPreferences, ApiError> {
        let mut notifications_preferences_state = self
            .account_repo
            .fetch(input.account_id)
            .await
            .map_err(ApiError::from)
            .map(|account| {
                account
                    .get_common_fields()
                    .notifications_preferences_state
                    .clone()
            })?;

        // Give iterable time to reach consistency after our most recent update before treating it as SOT
        if notifications_preferences_state.email_updated_at
            < OffsetDateTime::now_utc() - SYNC_FROM_ITERABLE_DELAY
        {
            // Sync email subscriptions from Iterable since the customer may have unsubscribed out of band
            let email_notification_categories = self
                .iterable_client
                .get_subscribed_notification_categories(IterableUserId::Account(input.account_id))
                .await?;

            notifications_preferences_state = notifications_preferences_state
                .with_email_notification_categories(email_notification_categories);
        }

        Ok(notifications_preferences_state.into())
    }

    #[instrument(skip(self))]
    pub async fn update_notifications_preferences(
        &self,
        input: UpdateNotificationsPreferencesInput<'_>,
    ) -> Result<(), ApiError> {
        let account = self
            .account_repo
            .fetch(input.account_id)
            .await
            .map_err(ApiError::from)?;

        let notifications_preferences_state =
            &account.get_common_fields().notifications_preferences_state;

        if !notifications_preferences_state
            .account_security
            .is_subset(&input.notifications_preferences.account_security)
            && !input
                .key_proof
                .map_or(false, |kp| kp.app_signed && kp.hw_signed)
        {
            return Err(ApiError::GenericForbidden(
                "valid signature over access token required by both app and hw auth keys"
                    .to_string(),
            ));
        }

        if !account.get_common_fields().onboarding_complete {
            self.iterable_client
                .set_initial_subscribed_notification_categories(
                    IterableUserId::Account(input.account_id),
                    input
                        .notifications_preferences
                        .get_email_notification_categories(),
                )
                .await?;
        } else {
            self.iterable_client
                .set_subscribed_notification_categories(
                    IterableUserId::Account(input.account_id),
                    input
                        .notifications_preferences
                        .get_email_notification_categories(),
                )
                .await?;
        }

        let updated_account = &account
            .update(CommonAccountFields {
                notifications_preferences_state: notifications_preferences_state
                    .update(input.notifications_preferences),
                ..account.get_common_fields().clone()
            })
            .map_err(ApiError::from)?;

        self.account_repo
            .persist(updated_account)
            .await
            .map_err(ApiError::from)?;

        capture_consents(
            &self.consent_repo,
            &account,
            notifications_preferences_state,
            &updated_account
                .get_common_fields()
                .notifications_preferences_state,
        )
        .await?;

        Ok(())
    }
}

async fn capture_consents(
    repo: &ConsentRepository,
    account: &Account,
    old_preferences: &NotificationsPreferencesState,
    new_preferences: &NotificationsPreferencesState,
) -> Result<(), ApiError> {
    let email_address = account
        .get_common_fields()
        .touchpoints
        .iter()
        .filter_map(|touchpoint| {
            if let Touchpoint::Email {
                email_address,
                active: true,
                ..
            } = touchpoint
            {
                Some(email_address.clone())
            } else {
                None
            }
        })
        .next();

    // Realistically this will almost always be exactly 1 consent being recorded.

    let diff = old_preferences.diff(new_preferences);

    for (category, channel) in diff.subscribes {
        let consent = Consent::new_notification_opt_in(
            account.get_id(),
            email_address.clone(),
            category,
            channel,
        );
        repo.persist(&consent).await?;
    }

    for (category, channel) in diff.unsubscribes {
        let consent = Consent::new_notification_opt_out(
            account.get_id(),
            email_address.clone(),
            category,
            channel,
        );
        repo.persist(&consent).await?;
    }

    Ok(())
}
