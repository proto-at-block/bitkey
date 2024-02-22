use std::collections::HashMap;

use errors::ApiError;
use notification::clients::iterable::{
    IterableClient, IterableUserId, ACCOUNT_ID_KEY, PLACEHOLDER_EMAIL_ADDRESS, USER_SCOPE_KEY,
};
use notification::service::Service as NotificationService;
use notification::service::{
    FetchNotificationsPreferencesInput, UpdateNotificationsPreferencesInput,
};
use types::{
    account::identifiers::AccountId,
    notification::{NotificationCategory, NotificationChannel},
};

pub mod account_validation;
pub(crate) mod metrics;
pub mod routes;

async fn enable_account_security_notifications(
    notification_service: &NotificationService,
    account_id: &AccountId,
    notification_channel: NotificationChannel,
) -> Result<(), ApiError> {
    let current_notifications_preferences = notification_service
        .fetch_notifications_preferences(FetchNotificationsPreferencesInput { account_id })
        .await?;
    if !current_notifications_preferences
        .account_security
        .contains(&notification_channel)
    {
        notification_service
            .update_notifications_preferences(UpdateNotificationsPreferencesInput {
                account_id,
                notifications_preferences: &current_notifications_preferences
                    .with_enabled(NotificationCategory::AccountSecurity, notification_channel),
            })
            .await?;
    }
    Ok(())
}

async fn create_account_iterable_users(
    iterable_client: &IterableClient,
    account_id: &AccountId,
) -> Result<(), ApiError> {
    // Account-scoped Iterable user; this is the primary target for sending emails. The email address on
    // this user only gets updated when a new email address is activated for the account.
    iterable_client
        .update_user(
            IterableUserId::Account(account_id),
            PLACEHOLDER_EMAIL_ADDRESS.to_string(),
            Some(HashMap::from([(USER_SCOPE_KEY, "account")])),
        )
        .await?;

    // Touchpoint-scoped Iterable user; this user is only used for sending the verification OTP. Since this
    // happens before an email address is activated, we can't use the account-scoped Iterable user. So this
    // operates as sort of a staging user for the account's pending email address change. This allows us to
    // send an OTP to a new email address while there's still an existing active one on the account-scoped
    // user.
    iterable_client
        .update_user(
            IterableUserId::Touchpoint(account_id),
            PLACEHOLDER_EMAIL_ADDRESS.to_string(),
            Some(HashMap::from([
                (ACCOUNT_ID_KEY, account_id.to_string().as_str()),
                (USER_SCOPE_KEY, "touchpoint"),
            ])),
        )
        .await?;

    Ok(())
}
