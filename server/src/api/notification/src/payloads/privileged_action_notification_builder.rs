use crate::clients::iterable::IterableCampaignType;
use std::collections::HashMap;
use types::privileged_action::shared::PrivilegedActionType;

const VERIFICATION_URL_DATA_KEY: &str = "verificationURL";
#[derive(Debug, Clone)]
pub struct OutOfBandIterableCampaignType {
    pub pending: IterableCampaignType,
    pub completed: IterableCampaignType,
    pub canceled: IterableCampaignType,
}

pub struct OutOfBandNotificationConfig {
    pub url_path: &'static str,
    pub campaign_types: OutOfBandIterableCampaignType,
}

impl From<PrivilegedActionType> for OutOfBandNotificationConfig {
    fn from(action_type: PrivilegedActionType) -> Self {
        match action_type {
            PrivilegedActionType::LoosenTransactionVerificationPolicy => {
                OutOfBandNotificationConfig {
                    url_path: "/privileged-action",
                    campaign_types: OutOfBandIterableCampaignType {
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
