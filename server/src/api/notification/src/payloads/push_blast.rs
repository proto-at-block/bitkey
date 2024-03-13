use serde::{Deserialize, Serialize};
use types::account::identifiers::AccountId;

use crate::{
    entities::NotificationCompositeKey, push::SNSPushPayload, NotificationError,
    NotificationMessage,
};

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct PushBlastPayload {
    pub account_id: AccountId,
    pub message: String,
}

impl TryFrom<(NotificationCompositeKey, PushBlastPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(v: (NotificationCompositeKey, PushBlastPayload)) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();
        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: None,
            push_payload: Some(SNSPushPayload {
                message: payload.message,
                ..Default::default()
            }),
            sms_payload: None,
        })
    }
}
