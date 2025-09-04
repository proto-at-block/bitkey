use crate::clients::iterable::IterableCampaignType;
use std::collections::HashMap;
use types::privileged_action::shared::PrivilegedActionType;

const VERIFICATION_URL_DATA_KEY: &str = "verificationURL";

#[derive(Debug, Clone)]
pub struct CampaignType {
    pub pending: IterableCampaignType,
    pub completed: IterableCampaignType,
    pub canceled: IterableCampaignType,
}

pub struct OutOfBandNotificationConfig {
    pub url_path: &'static str,
    pub campaign_types: CampaignType,
}

impl OutOfBandNotificationConfig {
    pub fn build_verification_url(&self, base_url: &str, web_auth_token: &str) -> String {
        format!(
            "{}{}?web_auth_token={}",
            base_url, self.url_path, web_auth_token
        )
    }

    pub fn build_data_fields(&self, verification_url: String) -> HashMap<String, String> {
        HashMap::from([(VERIFICATION_URL_DATA_KEY.to_string(), verification_url)])
    }
}

impl From<PrivilegedActionType> for OutOfBandNotificationConfig {
    fn from(action_type: PrivilegedActionType) -> Self {
        match action_type {
            PrivilegedActionType::LoosenTransactionVerificationPolicy => {
                OutOfBandNotificationConfig {
                    url_path: "/privileged-action",
                    campaign_types: CampaignType {
                        pending: IterableCampaignType::PrivilegedActionPendingOutOfBandVerification,
                        completed:
                            IterableCampaignType::PrivilegedActionCompletedOutOfBandVerification,
                        canceled:
                            IterableCampaignType::PrivilegedActionCanceledOutOfBandVerification,
                    },
                }
            }
            _ => {
                panic!("Unsupported action type: {:?}", action_type)
            }
        }
    }
}

pub(crate) enum DelayNotifyStatus {
    Canceled,
    Completed,
    Pending(String),
}

pub(crate) struct DelayNotifyEmailDataFields {
    pub email_subject: String,
    pub email_header: String,
    pub email_body: String,
}

impl DelayNotifyEmailDataFields {
    pub(crate) fn new(status: DelayNotifyStatus, notification_summary: String) -> Self {
        let (email_header, email_body) = match status {
            DelayNotifyStatus::Canceled => (
                format!("Your request to {} has been canceled.", notification_summary),
                "If you didn't cancel this request, please return to your Bitkey app to take further action.".to_string(),
            ),
            DelayNotifyStatus::Completed => (
                format!("Your request to {} is ready to be completed.", notification_summary),
                "Please open your Bitkey app to complete this request.".to_string(),
            ),
            DelayNotifyStatus::Pending(formatted_duration) => (format!("Your request to {} will be ready to be completed in {}. If you didn't request this, please cancel immediately in your Bitkey app.", notification_summary, formatted_duration), "If you didn't request this, please cancel immediately in your Bitkey app.".to_string()),
        };
        DelayNotifyEmailDataFields {
            email_subject: email_header.clone(),
            email_header,
            email_body,
        }
    }
}

impl From<DelayNotifyEmailDataFields> for HashMap<String, String> {
    fn from(data_fields: DelayNotifyEmailDataFields) -> Self {
        HashMap::from([
            ("emailSubject".to_string(), data_fields.email_subject),
            ("emailHeader".to_string(), data_fields.email_header),
            ("emailBody".to_string(), data_fields.email_body),
        ])
    }
}

pub struct DelayAndNotifyNotificationConfig {
    pub campaign_types: CampaignType,
}

impl From<PrivilegedActionType> for DelayAndNotifyNotificationConfig {
    fn from(action_type: PrivilegedActionType) -> Self {
        match action_type {
            PrivilegedActionType::ResetFingerprint
            | PrivilegedActionType::ActivateTouchpoint
            | PrivilegedActionType::ConfigurePrivilegedActionDelays => {
                DelayAndNotifyNotificationConfig {
                    campaign_types: CampaignType {
                        pending: IterableCampaignType::PrivilegedActionDelayNotifyUpdate,
                        completed: IterableCampaignType::PrivilegedActionDelayNotifyUpdate,
                        canceled: IterableCampaignType::PrivilegedActionDelayNotifyUpdate,
                    },
                }
            }
            _ => {
                panic!("Unsupported action type: {:?}", action_type)
            }
        }
    }
}
