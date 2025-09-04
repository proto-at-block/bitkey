use std::collections::HashMap;
use std::collections::HashSet;
use std::env;
use std::fmt;
use std::fmt::Display;
use std::sync::Arc;
use std::time::Duration;

use futures::future::join_all;
use futures::lock::Mutex;
use reqwest::Client;

use reqwest_middleware::ClientBuilder;
use reqwest_middleware::ClientWithMiddleware;

use reqwest_retry::policies::ExponentialBackoff;
use reqwest_retry::RetryTransientMiddleware;

use serde::Deserialize;
use serde::Serialize;
use strum::IntoEnumIterator;
use tokio::time::sleep;
use tracing::info;
use tracing::{error, instrument};

use strum_macros::EnumString;
use types::account::identifiers::AccountId;
use types::account::identifiers::TouchpointId;
use types::notification::NotificationCategory;

use urlencoding::encode as urlencode;

use crate::clients::error::NotificationClientsError;

const ITERABLE_API_URL: &str = "https://api.iterable.com/api/";
const USER_ID_QUERY_PARAM: &str = "userId";
const API_KEY_HEADER: &str = "Api-Key";
const CACHE_CONTROL_HEADER: &str = "Cache-Control";
const NO_CACHE_HEADER_VALUE: &str = "no-cache";
const MAX_RETRY_ATTEMPTS: u64 = 5;
const RETRY_INITIAL_BACKOFF_MILLIS: u64 = 1000;
const UNKNOWN_ERROR_CODE: &str = "unknown";

// https://support.iterable.com/hc/en-us/articles/208499956-Handling-Anonymous-Users#manually-creating-a-placeholder-email
pub const PLACEHOLDER_EMAIL_ADDRESS: &str = "bitkey@placeholder.email";
pub const ACCOUNT_ID_KEY: &str = "bitkeyAccountId";
pub const TOUCHPOINT_ID_KEY: &str = "bitkeyTouchpointId";
pub const USER_SCOPE_KEY: &str = "bitkeyUserScope";

#[derive(Debug)]
pub enum IterableUserId<'a> {
    Account(&'a AccountId),
    Touchpoint(&'a TouchpointId),
}

impl Display for IterableUserId<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let user_id_str = match self {
            IterableUserId::Account(account_id) => account_id.to_string(),
            IterableUserId::Touchpoint(touchpoint_id) => touchpoint_id.to_string(),
        };
        write!(f, "{}", user_id_str)
    }
}

#[derive(Deserialize, Clone)]
pub struct Config {
    pub iterable: IterableMode,
}

impl From<Config> for IterableClient {
    fn from(config: Config) -> Self {
        Self::from_config(config)
    }
}

#[derive(Deserialize, Default, Clone)]
pub struct ClientConfig {
    pub comms_verification_campaign_id: usize,
    pub recovery_relationship_benefactor_invitation_pending_campaign_id: usize,
    pub recovery_pending_delay_period_lost_app_campaign_id: usize,
    pub recovery_pending_delay_period_lost_hw_campaign_id: usize,
    pub recovery_completed_delay_period_lost_app_campaign_id: usize,
    pub recovery_completed_delay_period_lost_hw_campaign_id: usize,
    pub recovery_canceled_delay_period_lost_app_campaign_id: usize,
    pub recovery_canceled_delay_period_lost_hw_campaign_id: usize,
    pub recovery_relationship_invitation_accepted_received_by_benefactor_campaign_id: usize,
    pub recovery_relationship_invitation_accepted_received_by_beneficiary_campaign_id: usize,
    pub recovery_relationship_invitation_accepted_received_by_protected_customer_campaign_id: usize,
    pub recovery_relationship_deleted_campaign_id: usize,
    pub recovery_relationship_invitation_deleted_by_benefactor_received_by_benefactor_campaign_id:
        usize,
    pub recovery_relationship_invitation_deleted_by_benefactor_received_by_beneficiary_campaign_id:
        usize,
    pub recovery_relationship_invitation_deleted_by_beneficiary_received_by_benefactor_campaign_id:
        usize,
    pub recovery_relationship_invitation_deleted_by_beneficiary_received_by_beneficiary_campaign_id:
        usize,
    pub inheritance_claim_almost_over_as_benefactor_campaign_id: usize,
    pub inheritance_claim_almost_over_as_beneficiary_campaign_id: usize,
    pub inheritance_claim_canceled_by_beneficiary_received_by_beneficiary_campaign_id: usize,
    pub inheritance_claim_canceled_by_beneficiary_received_by_benefactor_campaign_id: usize,
    pub inheritance_claim_canceled_by_benefactor_received_by_beneficiary_campaign_id: usize,
    pub inheritance_claim_canceled_by_benefactor_received_by_benefactor_campaign_id: usize,
    pub inheritance_claim_initiated_as_benefactor_campaign_id: usize,
    pub inheritance_claim_initiated_as_beneficiary_campaign_id: usize,
    pub inheritance_claim_period_completed_by_benefactor_campaign_id: usize,
    pub inheritance_claim_period_completed_by_beneficiary_campaign_id: usize,
    pub privileged_action_canceled_out_of_band_verification_campaign_id: usize,
    pub privileged_action_completed_out_of_band_verification_campaign_id: usize,
    pub privileged_action_pending_out_of_band_verification_campaign_id: usize,
    pub privileged_action_delay_notify_update_campaign_id: usize,
    pub social_challenge_response_received_campaign_id: usize,
    pub marketing_channel_id: usize,
    pub transactional_channel_id: usize,
    pub account_security_message_type_id: usize,
    pub money_movement_message_type_id: usize,
    pub product_marketing_message_type_id: usize,
    pub transaction_verification_campaign_id: usize,
    pub security_hub_campaign_id: usize,
}

#[derive(Deserialize, EnumString, Clone)]
#[serde(rename_all = "lowercase", tag = "mode")]
pub enum IterableMode {
    Test,
    Environment(ClientConfig),
}

impl From<IterableMode> for IterableClient {
    fn from(value: IterableMode) -> Self {
        Self::from_mode(value)
    }
}

#[derive(Clone)]
pub enum IterableClient {
    Real {
        endpoint: reqwest::Url,
        client: ClientWithMiddleware,
        api_key: String,
        config: ClientConfig,
    },
    Test(Arc<Mutex<HashMap<String, HashSet<NotificationCategory>>>>),
}

#[derive(Serialize, Deserialize, Debug, Clone, Eq, PartialEq)]
pub enum IterableCampaignType {
    CommsVerification,
    InheritanceClaimAlmostOverAsBenefactor,
    InheritanceClaimAlmostOverAsBeneficiary,
    InheritanceClaimCanceledByBeneficiaryReceivedByBeneficiary,
    InheritanceClaimCanceledByBeneficiaryReceivedByBenefactor,
    InheritanceClaimCanceledByBenefactorReceivedByBeneficiary,
    InheritanceClaimCanceledByBenefactorReceivedByBenefactor,
    InheritanceClaimInitiatedAsBenefactor,
    InheritanceClaimInitiatedAsBeneficiary,
    InheritanceClaimPeriodCompletedAsBenefactor,
    InheritanceClaimPeriodCompletedAsBeneficiary,
    PrivilegedActionCanceledOutOfBandVerification,
    PrivilegedActionCompletedOutOfBandVerification,
    PrivilegedActionDelayNotifyUpdate,
    PrivilegedActionPendingOutOfBandVerification,
    RecoveryPendingDelayPeriodLostApp,
    RecoveryPendingDelayPeriodLostHw,
    RecoveryCompletedDelayPeriodLostApp,
    RecoveryCompletedDelayPeriodLostHw,
    RecoveryCanceledDelayPeriodLostApp,
    RecoveryCanceledDelayPeriodLostHw,
    RecoveryRelationshipBenefactorInvitationPending,
    RecoveryRelationshipInvitationAcceptedReceivedByProtectedCustomer,
    RecoveryRelationshipInvitationAcceptedReceivedByBeneficiary,
    RecoveryRelationshipInvitationAcceptedReceivedByBenefactor,
    RecoveryRelationshipDeletedReceivedByProtectedCustomer,
    RecoveryRelationshipDeletedByBenefactorReceivedByBenefactor,
    RecoveryRelationshipDeletedByBenefactorReceivedByBeneficiary,
    RecoveryRelationshipDeletedByBeneficiaryReceivedByBenefactor,
    RecoveryRelationshipDeletedByBeneficiaryReceivedByBeneficiary,
    SocialChallengeResponseReceived,
    TransactionVerification,
    SecurityHub,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ErrorResponse {
    code: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SendTargetedEmailRequest {
    recipient_user_id: String,
    campaign_id: usize,
    data_fields: HashMap<String, String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdateUserRequest<'a> {
    user_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    email: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    data_fields: Option<HashMap<&'a str, &'a str>>,
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
struct GetUserResponseDataFields {
    #[serde(default)]
    user_list_ids: HashSet<usize>,
    #[serde(default)]
    unsubscribed_channel_ids: HashSet<usize>,
    #[serde(default)]
    unsubscribed_message_type_ids: HashSet<usize>,
    #[serde(default)]
    subscribed_message_type_ids: HashSet<usize>,
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
#[allow(dead_code)]
struct GetUserResponseUser {
    email: String,
    user_id: String,
    data_fields: GetUserResponseDataFields,
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
struct GetUserResponse {
    user: GetUserResponseUser,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdateUserSubscriptionsRequest {
    user_id: String,
    user_list_ids: HashSet<usize>,
    unsubscribed_channel_ids: HashSet<usize>,
    unsubscribed_message_type_ids: HashSet<usize>,
    subscribed_message_type_ids: HashSet<usize>,
}

impl IterableClient {
    fn from_mode(mode: IterableMode) -> Self {
        match mode {
            IterableMode::Environment(client_config) => Self::Real {
                endpoint: reqwest::Url::parse(ITERABLE_API_URL).unwrap(),
                client: ClientBuilder::new(Client::new())
                    .with(RetryTransientMiddleware::new_with_policy(
                        ExponentialBackoff::builder().build_with_max_retries(5),
                    ))
                    .build(),
                api_key: env::var("ITERABLE_API_KEY")
                    .expect("ITERABLE_API_KEY environment variable not set"),
                config: client_config,
            },
            IterableMode::Test => Self::Test(Arc::new(Mutex::new(HashMap::new()))),
        }
    }

    fn from_config(config: Config) -> Self {
        Self::from_mode(config.iterable)
    }

    #[instrument(skip(self))]
    pub async fn send_targeted_email(
        &self,
        recipient_user_id: IterableUserId<'_>,
        campaign_type: IterableCampaignType,
        data_fields: HashMap<String, String>,
    ) -> Result<(), NotificationClientsError> {
        match self {
            Self::Real {
                endpoint,
                client,
                api_key,
                config,
            } => {
                let campaign_id = match campaign_type {
                    IterableCampaignType::CommsVerification => {
                        config.comms_verification_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimAlmostOverAsBenefactor => {
                        config.inheritance_claim_almost_over_as_benefactor_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimAlmostOverAsBeneficiary => {
                        config.inheritance_claim_almost_over_as_beneficiary_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimCanceledByBeneficiaryReceivedByBeneficiary => {
                        config.inheritance_claim_canceled_by_beneficiary_received_by_beneficiary_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimCanceledByBenefactorReceivedByBeneficiary => {
                        config.inheritance_claim_canceled_by_benefactor_received_by_beneficiary_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimCanceledByBenefactorReceivedByBenefactor => {
                        config.inheritance_claim_canceled_by_benefactor_received_by_benefactor_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimCanceledByBeneficiaryReceivedByBenefactor => {
                        config.inheritance_claim_canceled_by_beneficiary_received_by_benefactor_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimInitiatedAsBenefactor => {
                        config.inheritance_claim_initiated_as_benefactor_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimInitiatedAsBeneficiary => {
                        config.inheritance_claim_initiated_as_beneficiary_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimPeriodCompletedAsBenefactor => {
                        config.inheritance_claim_period_completed_by_benefactor_campaign_id
                    }
                    IterableCampaignType::InheritanceClaimPeriodCompletedAsBeneficiary => {
                        config.inheritance_claim_period_completed_by_beneficiary_campaign_id
                    }
                    IterableCampaignType::PrivilegedActionCanceledOutOfBandVerification => {
                        config.privileged_action_canceled_out_of_band_verification_campaign_id
                    }
                    IterableCampaignType::PrivilegedActionCompletedOutOfBandVerification => {
                        config.privileged_action_completed_out_of_band_verification_campaign_id
                    }
                    IterableCampaignType::PrivilegedActionDelayNotifyUpdate => {
                        config.privileged_action_delay_notify_update_campaign_id
                    }
                    IterableCampaignType::PrivilegedActionPendingOutOfBandVerification => {
                        config.privileged_action_pending_out_of_band_verification_campaign_id
                    }
                    IterableCampaignType::RecoveryPendingDelayPeriodLostApp => {
                        config.recovery_pending_delay_period_lost_app_campaign_id
                    }
                    IterableCampaignType::RecoveryPendingDelayPeriodLostHw => {
                        config.recovery_pending_delay_period_lost_hw_campaign_id
                    }
                    IterableCampaignType::RecoveryCompletedDelayPeriodLostApp => {
                        config.recovery_completed_delay_period_lost_app_campaign_id
                    }
                    IterableCampaignType::RecoveryCompletedDelayPeriodLostHw => {
                        config.recovery_completed_delay_period_lost_hw_campaign_id
                    }
                    IterableCampaignType::RecoveryCanceledDelayPeriodLostApp => {
                        config.recovery_canceled_delay_period_lost_app_campaign_id
                    }
                    IterableCampaignType::RecoveryCanceledDelayPeriodLostHw => {
                        config.recovery_canceled_delay_period_lost_hw_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipBenefactorInvitationPending => {
                        config.recovery_relationship_benefactor_invitation_pending_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipInvitationAcceptedReceivedByBenefactor => {
                        config.recovery_relationship_invitation_accepted_received_by_benefactor_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipInvitationAcceptedReceivedByBeneficiary => {
                        config.recovery_relationship_invitation_accepted_received_by_beneficiary_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipInvitationAcceptedReceivedByProtectedCustomer => {
                        config.recovery_relationship_invitation_accepted_received_by_protected_customer_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipDeletedReceivedByProtectedCustomer => {
                        config.recovery_relationship_deleted_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipDeletedByBenefactorReceivedByBenefactor => {
                        config.recovery_relationship_invitation_deleted_by_benefactor_received_by_benefactor_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipDeletedByBenefactorReceivedByBeneficiary => {
                        config.recovery_relationship_invitation_deleted_by_benefactor_received_by_beneficiary_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipDeletedByBeneficiaryReceivedByBenefactor => {
                        config.recovery_relationship_invitation_deleted_by_beneficiary_received_by_benefactor_campaign_id
                    }
                    IterableCampaignType::RecoveryRelationshipDeletedByBeneficiaryReceivedByBeneficiary => {
                        config.recovery_relationship_invitation_deleted_by_beneficiary_received_by_beneficiary_campaign_id
                    }
                    IterableCampaignType::SocialChallengeResponseReceived => {
                        config.social_challenge_response_received_campaign_id
                    }
                    IterableCampaignType::TransactionVerification => {
                        config.transaction_verification_campaign_id
                    }
                    IterableCampaignType::SecurityHub => {
                        config.security_hub_campaign_id
                    }
                };

                let request = SendTargetedEmailRequest {
                    recipient_user_id: recipient_user_id.to_string(),
                    campaign_id,
                    data_fields,
                };

                let response = client
                    .post(endpoint.join("email/target").unwrap())
                    .header(API_KEY_HEADER, api_key.to_owned())
                    .json(&request)
                    .send()
                    .await
                    .map_err(|e| {
                        error!("received error sending targeted email: {e:?}");
                        NotificationClientsError::IterableSendTargetedEmailError
                    })?;

                let status_code = response.status();
                if !status_code.is_success() {
                    let error_code = match response.json::<ErrorResponse>().await {
                        Ok(error_response) => error_response.code,
                        Err(_) => UNKNOWN_ERROR_CODE.to_string(),
                    };
                    error!("received error sending targeted email; status_code: {status_code:?}; code: {error_code:?}");
                    Err(NotificationClientsError::IterableSendTargetedEmailError)
                } else {
                    Ok(())
                }
            }
            Self::Test(_) => Ok(()),
        }
    }

    #[instrument(skip(self))]
    pub async fn update_user(
        &self,
        user_id: IterableUserId<'_>,
        email_address: String,
        data_fields: Option<HashMap<&str, &str>>,
    ) -> Result<(), NotificationClientsError> {
        match self {
            Self::Real {
                endpoint,
                client,
                api_key,
                ..
            } => {
                let request = UpdateUserRequest {
                    user_id: user_id.to_string(),
                    email: Some(email_address),
                    data_fields,
                };

                let response = client
                    .post(endpoint.join("users/update").unwrap())
                    .header(API_KEY_HEADER, api_key.to_owned())
                    .json(&request)
                    .send()
                    .await
                    .map_err(|e| {
                        error!("received error updating user: {e:?}");
                        NotificationClientsError::IterableUpdateUserError
                    })?;

                let status_code = response.status();
                if !status_code.is_success() {
                    let error_code = match response.json::<ErrorResponse>().await {
                        Ok(error_response) => error_response.code,
                        Err(_) => UNKNOWN_ERROR_CODE.to_string(),
                    };

                    if error_code == "InvalidEmailAddressError" {
                        return Err(NotificationClientsError::IterableInvalidEmailAddressError);
                    }

                    error!("received error updating user; status_code: {status_code:?}; code: {error_code:?}");
                    Err(NotificationClientsError::IterableUpdateUserError)
                } else {
                    Ok(())
                }
            }
            Self::Test(_) => Ok(()),
        }
    }

    #[instrument(skip(self))]
    async fn get_user(
        &self,
        user_id: String,
    ) -> Result<Option<GetUserResponse>, NotificationClientsError> {
        match self {
            Self::Real {
                endpoint,
                client,
                api_key,
                ..
            } => {
                for attempt in 0..MAX_RETRY_ATTEMPTS {
                    let response = client
                        .get(endpoint.join("users/byUserId").unwrap())
                        .header(API_KEY_HEADER, api_key.to_owned())
                        .header(CACHE_CONTROL_HEADER, NO_CACHE_HEADER_VALUE)
                        .query(&[(USER_ID_QUERY_PARAM, user_id.clone())])
                        .send()
                        .await
                        .map_err(|e| {
                            error!("received error getting user: {e:?}");
                            NotificationClientsError::IterableGetUserError
                        })?;

                    let status_code = response.status();
                    if !status_code.is_success() {
                        let error_code = match response.json::<ErrorResponse>().await {
                            Ok(error_response) => error_response.code,
                            Err(_) => UNKNOWN_ERROR_CODE.to_string(),
                        };

                        if error_code == "error.users.noUserWithIdExists" {
                            return Ok(None);
                        }

                        error!("received error getting user; status_code: {status_code:?}; code: {error_code:?}");
                        return Err(NotificationClientsError::IterableGetUserError);
                    }

                    // If we request the user too quickly after creating it (which we do in order to update subs),
                    // Iterable returns a 200 but with an empty user object. Do some retries with backoff here.
                    match response.json::<GetUserResponse>().await {
                        Ok(response) => return Ok(Some(response)),
                        Err(e) => {
                            info!("received error deserializing user; retrying: {e:?}");

                            if attempt < MAX_RETRY_ATTEMPTS - 1 {
                                sleep(Duration::from_millis(
                                    (attempt + 1) * RETRY_INITIAL_BACKOFF_MILLIS,
                                ))
                                .await;
                            }
                        }
                    }
                }

                Err(NotificationClientsError::IterableGetUserError)
            }
            Self::Test(_) => Ok(Default::default()),
        }
    }

    #[instrument(skip(self))]
    pub async fn get_subscribed_notification_categories(
        &self,
        user_id: IterableUserId<'_>,
    ) -> Result<HashSet<NotificationCategory>, NotificationClientsError> {
        match self {
            Self::Real { config, .. } => match self.get_user(user_id.to_string()).await? {
                Some(get_user_response) => Ok(NotificationCategory::iter()
                    .filter(|category| {
                        let (channel_id, message_type_id) = match category {
                            NotificationCategory::AccountSecurity => (
                                config.transactional_channel_id,
                                config.account_security_message_type_id,
                            ),
                            NotificationCategory::MoneyMovement => (
                                config.transactional_channel_id,
                                config.money_movement_message_type_id,
                            ),
                            NotificationCategory::ProductMarketing => (
                                config.marketing_channel_id,
                                config.product_marketing_message_type_id,
                            ),
                        };

                        // If the Iterable user is unsubscribed to the parent channel (out of band),
                        // we represent the nested message types as unsubscribed
                        get_user_response
                            .user
                            .data_fields
                            .subscribed_message_type_ids
                            .contains(&message_type_id)
                            && !get_user_response
                                .user
                                .data_fields
                                .unsubscribed_channel_ids
                                .contains(&channel_id)
                    })
                    .collect()),
                None => Ok(Default::default()),
            },
            Self::Test(store) => Ok(store
                .lock()
                .await
                .get(&user_id.to_string())
                .cloned()
                .unwrap_or_default()),
        }
    }

    #[instrument(skip(self))]
    pub async fn set_subscribed_notification_categories(
        &self,
        user_id: IterableUserId<'_>,
        notification_categories: HashSet<NotificationCategory>,
    ) -> Result<(), NotificationClientsError> {
        match self {
            Self::Real {
                endpoint,
                client,
                api_key,
                config,
                ..
            } => match self.get_user(user_id.to_string()).await? {
                Some(get_user_response) => {
                    let user = get_user_response.user;

                    let (
                        channels_to_subscribe,
                        message_types_to_subscribe,
                        message_types_to_unsubscribe,
                    ) = NotificationCategory::iter().fold(
                        (HashSet::new(), HashSet::new(), HashSet::new()),
                        |(
                            mut channels_to_subscribe,
                            mut message_types_to_subscribe,
                            mut message_types_to_unsubscribe,
                        ),
                         category| {
                            let (channel_id, message_type_id) = match category {
                                NotificationCategory::AccountSecurity => (
                                    config.transactional_channel_id,
                                    config.account_security_message_type_id,
                                ),
                                NotificationCategory::MoneyMovement => (
                                    config.transactional_channel_id,
                                    config.money_movement_message_type_id,
                                ),
                                NotificationCategory::ProductMarketing => (
                                    config.marketing_channel_id,
                                    config.product_marketing_message_type_id,
                                ),
                            };

                            // If the Iterable user is unsubscribed to the parent channel (out of band),
                            // we need to re-subscribe them to that as well as the child
                            if notification_categories.contains(&category) {
                                channels_to_subscribe.insert(channel_id);
                                message_types_to_subscribe.insert(message_type_id);
                            } else {
                                message_types_to_unsubscribe.insert(message_type_id);
                            }

                            (
                                channels_to_subscribe,
                                message_types_to_subscribe,
                                message_types_to_unsubscribe,
                            )
                        },
                    );

                    let request = UpdateUserSubscriptionsRequest {
                        user_id: user_id.to_string(),
                        user_list_ids: user.data_fields.user_list_ids,
                        unsubscribed_channel_ids: user
                            .data_fields
                            .unsubscribed_channel_ids
                            .into_iter()
                            .filter(|id| !channels_to_subscribe.contains(id))
                            .collect(),
                        unsubscribed_message_type_ids: user
                            .data_fields
                            .unsubscribed_message_type_ids
                            .union(&message_types_to_unsubscribe)
                            .cloned()
                            .filter(|id| !message_types_to_subscribe.contains(id))
                            .collect(),
                        subscribed_message_type_ids: user
                            .data_fields
                            .subscribed_message_type_ids
                            .union(&message_types_to_subscribe)
                            .cloned()
                            .filter(|id| !message_types_to_unsubscribe.contains(id))
                            .collect(),
                    };

                    let response = client
                        .post(endpoint.join("users/updateSubscriptions").unwrap())
                        .header(API_KEY_HEADER, api_key.to_owned())
                        .json(&request)
                        .send()
                        .await
                        .map_err(|e| {
                            error!("received error updating user subscriptions: {e:?}");
                            NotificationClientsError::IterableUpdateUserSubscriptionsError
                        })?;

                    let status_code = response.status();
                    if !status_code.is_success() {
                        let error_code = match response.json::<ErrorResponse>().await {
                            Ok(error_response) => error_response.code,
                            Err(_) => UNKNOWN_ERROR_CODE.to_string(),
                        };
                        error!("received error updating user subscriptions; status_code: {status_code:?}; code: {error_code:?}");
                        Err(NotificationClientsError::IterableUpdateUserSubscriptionsError)
                    } else {
                        Ok(())
                    }
                }
                None => {
                    error!("attempted to update user subscriptions for nonexistent user");
                    Err(NotificationClientsError::IterableUpdateUserSubscriptionsNonexistentUserError)
                }
            },
            Self::Test(store) => {
                store
                    .lock()
                    .await
                    .insert(user_id.to_string(), notification_categories);
                Ok(())
            }
        }
    }

    // This is a special case for the initial subscription of a user to a set of notification categories.
    // It assumes the Iterable user has no existing subscriptions. This prevents us from running into
    // consistency issues if we try to observe the Iterable user too soon after creating it (such as during
    // onboarding).
    #[instrument(skip(self))]
    pub async fn set_initial_subscribed_notification_categories(
        &self,
        user_id: IterableUserId<'_>,
        notification_categories: HashSet<NotificationCategory>,
    ) -> Result<(), NotificationClientsError> {
        match self {
            Self::Real {
                endpoint,
                client,
                api_key,
                config,
                ..
            } => {
                let requests = notification_categories.iter().map(|category| {
                    let message_type_id = match category {
                        NotificationCategory::AccountSecurity => {
                            config.account_security_message_type_id
                        }
                        NotificationCategory::MoneyMovement => {
                            config.money_movement_message_type_id
                        }
                        NotificationCategory::ProductMarketing => {
                            config.product_marketing_message_type_id
                        }
                    };

                    client
                        .patch(
                            endpoint
                                .join(&format!(
                                    "subscriptions/{}/{}/byUserId/{}",
                                    "messageType",
                                    message_type_id,
                                    urlencode(&user_id.to_string())
                                ))
                                .unwrap(),
                        )
                        .header(API_KEY_HEADER, api_key.to_owned())
                        .send()
                });

                for result in join_all(requests).await {
                    let response = result.map_err(|e| {
                        error!("received error subscribing user: {e:?}");
                        NotificationClientsError::IterableSubscribeUserError
                    })?;
                    let status_code = response.status();
                    if !status_code.is_success() {
                        let error_code = match response.json::<ErrorResponse>().await {
                            Ok(error_response) => error_response.code,
                            Err(_) => UNKNOWN_ERROR_CODE.to_string(),
                        };
                        error!("received error subscribing user; status_code: {status_code:?}; code: {error_code:?}");
                        return Err(NotificationClientsError::IterableSubscribeUserError);
                    }
                }
                Ok(())
            }
            Self::Test(store) => {
                let mut store = store.lock().await;
                let mut categories = store.get(&user_id.to_string()).cloned().unwrap_or_default();
                categories.extend(notification_categories);
                store.insert(user_id.to_string(), categories);
                Ok(())
            }
        }
    }

    #[instrument(skip(self))]
    pub async fn wait_for_touchpoint_user(
        &self,
        user_id: IterableUserId<'_>,
    ) -> Result<(), NotificationClientsError> {
        match self {
            Self::Real { config, .. } => {
                for attempt in 0..MAX_RETRY_ATTEMPTS {
                    if let Some(user) = self.get_user(user_id.to_string()).await? {
                        if !user
                            .user
                            .data_fields
                            .subscribed_message_type_ids
                            .contains(&config.account_security_message_type_id)
                        {
                            if attempt < MAX_RETRY_ATTEMPTS - 1 {
                                sleep(Duration::from_millis(
                                    (attempt + 1) * RETRY_INITIAL_BACKOFF_MILLIS,
                                ))
                                .await;
                            }
                        } else {
                            return Ok(());
                        }
                    } else {
                        break;
                    }
                }

                return Err(NotificationClientsError::IterableWaitForTouchpointUserError);
            }
            Self::Test(_) => Ok(()),
        }
    }
}
