use account::entities::Touchpoint;

use notification::clients::iterable::IterableUserId;
use notification::clients::{iterable::IterableClient, iterable::IterableMode};
use notification::email::EmailPayload;
use tracing::event;
use tracing::instrument;
use tracing::Level;
use types::account::identifiers::AccountId;

use crate::ses::SESMode;
use crate::{error::WorkerError, ses::SESClient};

pub struct SendEmail {
    ses: SESClient,
    iterable: IterableClient,
}

impl SendEmail {
    pub async fn new(ses_mode: SESMode, iterable_mode: IterableMode) -> Self {
        Self {
            ses: SESClient::new(ses_mode).await,
            iterable: IterableClient::from(iterable_mode),
        }
    }

    #[instrument(skip(self))]
    pub async fn send(
        &self,
        account_id: &AccountId,
        touchpoint: &Touchpoint,
        payload: &EmailPayload,
    ) -> Result<(), WorkerError> {
        let Touchpoint::Email {
            email_address,
            active,
            ..
        } = touchpoint
        else {
            return Err(WorkerError::IncorrectTouchpointType);
        };

        match payload {
            EmailPayload::SES {
                template_name,
                tags,
            } => {
                self.ses
                    .send_email(
                        email_address.to_owned(),
                        template_name.to_owned(),
                        tags.to_owned(),
                    )
                    .await
            }
            EmailPayload::Iterable {
                campaign_type,
                data_fields,
            } => {
                let recipient_user_id = if *active {
                    IterableUserId::Account(account_id)
                } else {
                    IterableUserId::Touchpoint(account_id)
                };

                self.iterable
                    .send_targeted_email(
                        recipient_user_id,
                        campaign_type.to_owned(),
                        data_fields.to_owned(),
                    )
                    .await.map_err(|e| {
                        event!(
                            Level::ERROR,
                            "Notification lambda could not publish email notification with error: {:?}",
                            e
                        );
                        e
                    })?;
                Ok(())
            }
        }
    }
}
