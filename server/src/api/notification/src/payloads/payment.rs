use serde::{Deserialize, Serialize};
use types::account::identifiers::AccountId;

use crate::{
    entities::NotificationCompositeKey, push::AndroidChannelId, push::SNSPushPayload,
    NotificationError, NotificationMessage,
};

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct PaymentPayload {
    pub account_id: AccountId,
}

impl TryFrom<(NotificationCompositeKey, PaymentPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(v: (NotificationCompositeKey, PaymentPayload)) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        Ok(NotificationMessage {
            composite_key,
            account_id: payload.account_id,
            email_payload: None,
            push_payload: Some(SNSPushPayload {
                message: "You've received bitcoin.".to_owned(),
                android_channel_id: AndroidChannelId::Transactions,
                ..Default::default()
            }),
            sms_payload: None,
        })
    }
}

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct PendingPaymentPayload {
    pub account_id: AccountId,
}

impl TryFrom<(NotificationCompositeKey, PendingPaymentPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(v: (NotificationCompositeKey, PendingPaymentPayload)) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        Ok(NotificationMessage {
            composite_key,
            account_id: payload.account_id,
            email_payload: None,
            push_payload: Some(SNSPushPayload {
                message: "Your bitcoin deposit is now processing.".to_owned(),
                android_channel_id: AndroidChannelId::Transactions,
                ..Default::default()
            }),
            sms_payload: None,
        })
    }
}
