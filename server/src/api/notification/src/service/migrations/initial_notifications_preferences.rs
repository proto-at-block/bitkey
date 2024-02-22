use std::collections::{HashMap, HashSet};

use account::entities::Touchpoint;
use async_trait::async_trait;
use migration::{Migration, MigrationError};
use tracing::error;
use types::notification::{NotificationChannel, NotificationsPreferences};

use crate::{
    clients::{
        error::NotificationClientsError,
        iterable::{IterableUserId, TOUCHPOINT_ID_KEY, USER_SCOPE_KEY},
    },
    service::{Service, UpdateNotificationsPreferencesInput},
};

pub(crate) struct InitialNotificationsPreferences<'a> {
    service: &'a Service,
}

impl<'a> InitialNotificationsPreferences<'a> {
    pub fn new(service: &'a Service) -> Self {
        Self { service }
    }
}

#[async_trait]
impl<'a> Migration for InitialNotificationsPreferences<'a> {
    fn name(&self) -> &str {
        "20240212_initial_notifications_preferences"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        let initial_notifications_preferences = &NotificationsPreferences {
            account_security: HashSet::from([
                NotificationChannel::Push,
                NotificationChannel::Email,
                NotificationChannel::Sms,
            ]),
            money_movement: HashSet::from([NotificationChannel::Push]),
            product_marketing: HashSet::from([
                NotificationChannel::Push,
                NotificationChannel::Email,
            ]),
        };

        let initial_notifications_preferences_without_email = &initial_notifications_preferences
            .with_email_notification_categories(HashSet::default());

        for account in self
            .service
            .account_service
            .fetch_accounts()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?
        {
            let email_touchpoint_details = account
                .get_common_fields()
                .touchpoints
                .iter()
                .filter_map(|touchpoint| match touchpoint {
                    Touchpoint::Email {
                        id,
                        email_address,
                        active: true,
                    } => Some((id, email_address)),
                    _ => None,
                })
                .next();

            // Can only subscribe to email notifications if Iterable user exists, and Iterable user
            // only exists if the account has an active email touchpoint.
            let notifications_preferences = if email_touchpoint_details.is_some() {
                initial_notifications_preferences
            } else {
                initial_notifications_preferences_without_email
            };

            if account.get_common_fields().notifications_preferences != *notifications_preferences {
                let result = self
                    .service
                    .update_notifications_preferences(UpdateNotificationsPreferencesInput {
                        account_id: account.get_id(),
                        notifications_preferences,
                    })
                    .await;

                if matches!(&result, Err(e) if *e == NotificationClientsError::IterableUpdateUserSubscriptionsNonexistentUserError.into())
                    && email_touchpoint_details.is_some()
                {
                    error!(
                        "Iterable user did not exist for account with email; account_id: {:?}",
                        account.get_id()
                    );

                    // Ensure iterable "account" user exists
                    self.service
                        .iterable_client
                        .update_user(
                            IterableUserId::Account(account.get_id()),
                            email_touchpoint_details.unwrap().1.to_owned(),
                            Some(HashMap::from([
                                (
                                    TOUCHPOINT_ID_KEY,
                                    email_touchpoint_details.unwrap().0.to_string().as_str(),
                                ),
                                (USER_SCOPE_KEY, "account"),
                            ])),
                        )
                        .await
                        .map_err(|err| {
                            MigrationError::UpdateNotificationsPreferences(err.to_string())
                        })?;

                    // Retry update
                    self.service
                        .update_notifications_preferences(UpdateNotificationsPreferencesInput {
                            account_id: account.get_id(),
                            notifications_preferences,
                        })
                        .await
                        .map_err(|err| {
                            MigrationError::UpdateNotificationsPreferences(err.to_string())
                        })?;
                } else {
                    result.map_err(|err| {
                        MigrationError::UpdateNotificationsPreferences(err.to_string())
                    })?;
                }
            }
        }

        Ok(())
    }
}
