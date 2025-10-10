use isocountry::CountryCode;
use notification::clients::twilio::{TwilioClient, TwilioMode};
use notification::sms::SmsPayload;
use tracing::instrument;
use types::account::entities::Touchpoint;

use crate::error::WorkerError;

pub struct SendSMS {
    pub twilio: TwilioClient,
}

impl SendSMS {
    pub async fn new(twilio_mode: TwilioMode) -> Self {
        Self {
            twilio: TwilioClient::new(twilio_mode),
        }
    }

    #[instrument(skip(self))]
    pub async fn send(
        &self,
        touchpoint: &Touchpoint,
        payload: &SmsPayload,
    ) -> Result<(), WorkerError> {
        let Touchpoint::Phone {
            phone_number,
            country_code,
            ..
        } = touchpoint
        else {
            return Err(WorkerError::IncorrectTouchpointType);
        };

        self.twilio
            .create_message(
                country_code.to_owned(),
                phone_number.to_owned(),
                payload.message(),
            )
            .await?;

        Ok(())
    }

    #[instrument(skip(self))]
    pub fn is_supported_country_code(&self, country_code: CountryCode) -> bool {
        self.twilio.is_supported_sms_country_code(country_code)
    }
}
