use serde::{Deserialize, Serialize};
use serde_json::json;

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "snake_case")]
pub enum AndroidChannelId {
    General,
    Transactions,
    RecoveryAccountSecurity,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SNSPushPayload {
    pub title: String,
    pub message: String,
    pub android_channel_id: AndroidChannelId,
    pub android_color: String,
}

impl Default for SNSPushPayload {
    fn default() -> SNSPushPayload {
        SNSPushPayload {
            title: "".to_owned(),
            message: "".to_owned(),
            android_channel_id: AndroidChannelId::General,
            android_color: "#000000".to_owned(),
        }
    }
}

impl SNSPushPayload {
    pub fn to_sns_message(&self) -> String {
        // https://docs.aws.amazon.com/sns/latest/dg/sns-send-custom-platform-specific-payloads-mobile-devices.html
        // https://developer.apple.com/library/archive/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/PayloadKeyReference.html#//apple_ref/doc/uid/TP40008194-CH17-SW5
        // https://firebase.google.com/docs/cloud-messaging/http-server-ref
        let ios = json!({
            "aps": {
                "alert": {
                    "title": self.title,
                    "body": self.message,
                },
                "badge": 1,
            }
        });

        let android = json!({
            "notification": {
                "title": self.title,
                "body": self.message,
                "android_channel_id": self.android_channel_id,
                "color": self.android_color
            }
        });

        let message = json!({
            "default": self.message,
            "APNS": json_to_string(&ios),
            "GCM": json_to_string(&android),
        });

        json_to_string(&message)
    }
}

#[derive(Debug)]
pub enum Push {
    Alert { text: String, badge: Option<i32> },
    Silent { badge: Option<i32> },
}

impl Push {
    pub fn to_sns_payload(&self) -> String {
        let (ios, android) = match self {
            Push::Alert { text, badge } => {
                let ios = json!({
                    "aps": {
                      "alert": text,
                      "badge": badge,
                    }
                });

                let android = json!({
                  "data": {
                    "message": text,
                    "badge": badge,
                  }
                });

                (ios, android)
            }
            Push::Silent { badge } => {
                let ios = json!({
                    "aps": {
                        "content-available": 1,
                        "badge": badge,
                    }
                });

                let android = json!({
                  "data": {}
                });

                (ios, android)
            }
        };

        let payload = json!({
            "default": "",
            "APNS": json_to_string(&ios),
            "APNS_SANDBOX": json_to_string(&ios),
            "GCM": json_to_string(&android),
        });

        json_to_string(&payload)
    }
}

fn json_to_string<S: Serialize>(s: &S) -> String {
    serde_json::to_string(s).unwrap()
}

#[cfg(test)]
mod tests {
    use crate::push::AndroidChannelId;
    use crate::push::SNSPushPayload;
    #[test]
    fn test_to_sns_message() {
        let payload = SNSPushPayload {
            title: "This is a notification title".to_owned(),
            message: "This is a notification message".to_owned(),
            android_channel_id: AndroidChannelId::Transactions,
            android_color: "#ffffff".to_owned(),
        };
        assert_eq!(
            payload.to_sns_message(),
            "{\"APNS\":\"{\\\"aps\\\":{\\\"alert\\\":{\\\"body\\\":\\\"This is a notification message\\\",\\\"title\\\":\\\"This is a notification title\\\"},\\\"badge\\\":1}}\",\"GCM\":\"{\\\"notification\\\":{\\\"android_channel_id\\\":\\\"transactions\\\",\\\"body\\\":\\\"This is a notification message\\\",\\\"color\\\":\\\"#ffffff\\\",\\\"title\\\":\\\"This is a notification title\\\"}}\",\"default\":\"This is a notification message\"}",
        );
    }
}
