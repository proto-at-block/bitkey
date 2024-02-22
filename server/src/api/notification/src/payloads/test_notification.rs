use serde::{Deserialize, Serialize};

use crate::{
    entities::NotificationCompositeKey, push::SNSPushPayload, NotificationError,
    NotificationMessage,
};

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
pub struct TestNotificationPayload {}

impl TryFrom<(NotificationCompositeKey, TestNotificationPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, TestNotificationPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, _payload) = v;
        let (account_id, _) = composite_key.clone();
        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: None,
            push_payload: Some(SNSPushPayload {
                message: "Test Notification Received".to_owned(),
                ..Default::default()
            }),
            sms_payload: None,
        })
    }
}
