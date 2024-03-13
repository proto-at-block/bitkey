use account::entities::{Account, CommonAccountFields, Touchpoint};
use errors::ApiError;
use repository::consent::Repository as ConsentRepository;
use tracing::instrument;
use types::{consent::Consent, notification::NotificationsPreferences};

use crate::clients::iterable::IterableUserId;

use super::{FetchNotificationsPreferencesInput, Service, UpdateNotificationsPreferencesInput};

impl Service {
    #[instrument(skip(self))]
    pub async fn fetch_notifications_preferences(
        &self,
        input: FetchNotificationsPreferencesInput<'_>,
    ) -> Result<NotificationsPreferences, ApiError> {
        let notifications_preferences = self
            .account_repo
            .fetch(input.account_id)
            .await
            .map_err(ApiError::from)
            .map(|account| {
                account
                    .get_common_fields()
                    .notifications_preferences
                    .clone()
            })?;

        // Get email subscriptions from Iterable since the customer may have unsubscribed out of band
        let email_notification_categories = self
            .iterable_client
            .get_subscribed_notification_categories(IterableUserId::Account(input.account_id))
            .await?;

        Ok(notifications_preferences
            .with_email_notification_categories(email_notification_categories))
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

        if !account
            .get_common_fields()
            .notifications_preferences
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

        let updated_account = account
            .update(CommonAccountFields {
                notifications_preferences: input.notifications_preferences.clone(),
                ..account.get_common_fields().clone()
            })
            .map_err(ApiError::from)?;

        self.account_repo
            .persist(&updated_account)
            .await
            .map_err(ApiError::from)?;

        capture_consents(
            &self.consent_repo,
            &account,
            &account.get_common_fields().notifications_preferences,
            &updated_account
                .get_common_fields()
                .notifications_preferences,
        )
        .await?;

        Ok(())
    }
}

async fn capture_consents(
    repo: &ConsentRepository,
    account: &Account,
    old_preferences: &NotificationsPreferences,
    new_preferences: &NotificationsPreferences,
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
