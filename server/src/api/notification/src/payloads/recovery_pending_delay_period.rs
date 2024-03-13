use std::collections::HashMap;

use account::entities::Factor;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};

use crate::{
    clients::iterable::IterableCampaignType, email::EmailPayload,
    entities::NotificationCompositeKey, push::AndroidChannelId, push::SNSPushPayload,
    sms::SmsPayload, NotificationError, NotificationMessage,
};

const LOST_FACTOR_FIELD: &str = "lostFactor";
const DURATION_FIELD: &str = "duration";
const END_DATE_FIELD: &str = "endDate";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct RecoveryPendingDelayPeriodPayload {
    #[serde(with = "rfc3339")]
    pub initiation_time: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
    pub lost_factor: Factor,
}

impl TryFrom<(NotificationCompositeKey, RecoveryPendingDelayPeriodPayload)>
    for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, RecoveryPendingDelayPeriodPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();
        let duration = payload.delay_end_time - OffsetDateTime::now_utc();

        let formatted_duration = if duration.whole_hours() > 18 {
            // Call anything above 18 hours 1 day
            let whole_days = duration.whole_days().max(1);
            format!(
                "{} day{}",
                whole_days,
                if whole_days != 1 { "s" } else { "" }
            )
        } else if duration.whole_minutes() > 45 {
            // Call anything above 45 minutes 1 hour
            let whole_hours = duration.whole_hours().max(1);
            format!(
                "{} hour{}",
                whole_hours,
                if whole_hours != 1 { "s" } else { "" }
            )
        } else {
            let whole_minutes = duration.whole_minutes();
            format!(
                "{} minute{}",
                whole_minutes,
                if whole_minutes != 1 { "s" } else { "" }
            )
        };

        let message = match payload.lost_factor {
            Factor::App => format!("Your Bitkey wallet will be ready on your new phone in {}. If you didn't request this, please cancel immediately in your Bitkey app.", formatted_duration),
            Factor::Hw => format!("Your new Bitkey hardware device will be ready to use in {}. If you didn't request a new device, please cancel this immediately in your Bitkey app.", formatted_duration),
        };

        let calendar_end_date = payload.delay_end_time.to_calendar_date();
        let formatted_end_date = format!(
            "{} {} {}",
            calendar_end_date.2, calendar_end_date.1, calendar_end_date.0
        );

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: match payload.lost_factor {
                    Factor::App => IterableCampaignType::RecoveryPendingDelayPeriodLostApp,
                    Factor::Hw => IterableCampaignType::RecoveryPendingDelayPeriodLostHw,
                },
                data_fields: HashMap::from([
                    (
                        LOST_FACTOR_FIELD.to_string(),
                        payload.lost_factor.to_string(),
                    ),
                    (DURATION_FIELD.to_string(), formatted_duration),
                    (END_DATE_FIELD.to_string(), formatted_end_date),
                ]),
            }),
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload {
                message,
                unsupported_country_codes: None,
            }),
        })
    }
}

#[cfg(test)]
mod tests {
    use account::entities::Factor;
    use time::{Duration, OffsetDateTime};
    use types::account::identifiers::AccountId;

    use crate::identifiers::NotificationId;
    use crate::payloads::recovery_pending_delay_period::RecoveryPendingDelayPeriodPayload;
    use crate::NotificationMessage;

    #[test]
    fn test_notification_message_push_payload() {
        // Push notification at the start of the delay window
        let start_time = OffsetDateTime::now_utc();
        let end_time = start_time + Duration::days(1) + Duration::minutes(1);
        let composite_key = (
            AccountId::gen().expect("Valid AccountId"),
            NotificationId::gen_scheduled(),
        );
        let payload = RecoveryPendingDelayPeriodPayload {
            initiation_time: start_time,
            delay_end_time: end_time,
            lost_factor: Factor::Hw,
        };

        let notification_message: NotificationMessage =
            (composite_key.clone(), payload).try_into().unwrap();
        let push_payload = notification_message.push_payload.unwrap();
        assert!(push_payload.message.contains("1 day"));

        // Push notification at the middle of the delay window
        let start_time = OffsetDateTime::now_utc();
        let end_time = start_time + Duration::hours(12) + Duration::minutes(1);
        let payload = RecoveryPendingDelayPeriodPayload {
            initiation_time: start_time,
            delay_end_time: end_time,
            lost_factor: Factor::Hw,
        };

        let notification_message: NotificationMessage =
            (composite_key, payload).try_into().unwrap();
        let push_payload = notification_message.push_payload.unwrap();
        assert!(push_payload.message.contains("12 hours"));
    }
}
