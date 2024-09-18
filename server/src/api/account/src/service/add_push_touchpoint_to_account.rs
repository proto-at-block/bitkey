use aws_config::BehaviorVersion;
use aws_sdk_sns::{error::ProvideErrorMetadata, Client as SNSClient};
use tracing::{event, Level};

use crate::{
    entities::{Account, Touchpoint, TouchpointPlatform},
    error::AccountError,
};

use super::{AddPushTouchpointToAccountInput, Service};

impl Service {
    pub async fn add_push_touchpoint_for_account(
        &self,
        input: AddPushTouchpointToAccountInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        let mut account = self.account_repo.fetch(input.account_id).await?;

        let device_token = input.device_token.clone();
        if let Some(touchpoint) = account.get_common_fields().touchpoints.iter().find(|t| {
            if let Touchpoint::Push {
                platform: _,
                arn: _,
                device_token,
            } = t
            {
                *device_token == input.device_token
            } else {
                false
            }
        }) {
            return Ok(touchpoint.to_owned());
        }

        // Prevent inactive devices from registering device tokens as soon as their access token is revoked
        if self
            .userpool_service
            .is_access_token_revoked(input.access_token)
            .await?
        {
            return Err(AccountError::UnauthorizedDeviceTokenRegistration);
        }

        let device_arn = generate_device_arn(
            input.use_local_sns,
            input.platform,
            input.device_token,
        )
        .await
        .map_err(|e| {
            event!(
                Level::INFO,
                "Failed to generate device ARN with error: {} and device token: {} and platform: {}",
                e.to_string(),
                &device_token,
                &input.platform
            );
            e
        })?;
        let new_touchpoint = Touchpoint::Push {
            platform: input.platform,
            arn: device_arn.to_string(),
            device_token,
        };
        match &mut account {
            Account::Full(full_account) => full_account
                .common_fields
                .touchpoints
                .push(new_touchpoint.clone()),
            Account::Lite(lite_account) => lite_account
                .common_fields
                .touchpoints
                .push(new_touchpoint.clone()),
            Account::Software(software_account) => software_account
                .common_fields
                .touchpoints
                .push(new_touchpoint.clone()),
        };

        self.account_repo.persist(&account).await?;
        Ok(new_touchpoint)
    }
}

pub async fn generate_device_arn(
    use_local_sns: bool,
    platform: TouchpointPlatform,
    device_token: String,
) -> Result<String, AccountError> {
    if use_local_sns {
        return Ok("test-arn".to_string());
    }

    let platform_arn = platform.get_platform_arn().map_err(|e| {
        event!(
            Level::ERROR,
            "Couldn't find platform ARN for {platform} due to error: {e}"
        );
        AccountError::PlatformArnNotFound
    })?;

    let sdk_config = aws_config::load_defaults(BehaviorVersion::latest()).await;
    let sns_client = SNSClient::new(&sdk_config);

    let res = sns_client
        .create_platform_endpoint()
        .set_platform_application_arn(Some(platform_arn))
        .set_token(device_token.into())
        .send()
        .await
        .map_err(|err| {
            event!(
                Level::ERROR,
                "Couldn't create SNS channel for Push Notification: {}",
                err
            );
            let endpoint_err = err.into_service_error();
            event!(
                Level::ERROR,
                "Couldn't create SNS channel for Push Notification: {endpoint_err:?} with message: {}",
                endpoint_err.message().unwrap_or("Unknown message"),
            );
            AccountError::CreatePushNotificationChannel
        })?;
    res.endpoint_arn
        .ok_or(AccountError::CreatePushNotificationChannel)
}
