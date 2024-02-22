use std::collections::HashMap;

use aws_config::BehaviorVersion;
use aws_sdk_sesv2::types::Destination;
use aws_sdk_sesv2::types::EmailContent;
use aws_sdk_sesv2::types::Template;
use aws_sdk_sesv2::Client;
use aws_sdk_sns::error::ProvideErrorMetadata;
use serde::Deserialize;
use strum_macros::EnumString;
use tracing::event;
use tracing::Level;

use crate::error::WorkerError;

#[derive(Deserialize, EnumString, Clone)]
#[serde(rename_all = "lowercase")]
pub enum SESMode {
    Test,
    Environment,
}

pub enum SESClient {
    Real { client: Client },
    Test,
}

impl SESClient {
    pub async fn new(mode: SESMode) -> Self {
        match mode {
            SESMode::Environment => {
                let sdk_config = aws_config::load_defaults(BehaviorVersion::latest()).await;

                Self::Real {
                    client: Client::new(&sdk_config),
                }
            }
            SESMode::Test => Self::Test,
        }
    }

    pub async fn send_email(
        &self,
        to_address: String,
        template_name: String,
        tags: HashMap<String, String>,
    ) -> Result<(), WorkerError> {
        match self {
            Self::Real { client } => {
                let dest = Destination::builder().to_addresses(to_address).build();

                let template_data_content = tags
                    .iter()
                    .map(|(tag_name, tag_value)| format!("\"{}\": \"{}\"", tag_name, tag_value))
                    .collect::<Vec<String>>()
                    .join(", ");
                let email_content = EmailContent::builder()
                    .template(
                        Template::builder()
                            .template_name(template_name.to_owned())
                            .template_data(format!("{{ {} }}", template_data_content))
                            .build(),
                    )
                    .build();

                client
                    .send_email()
                    .from_email_address("noreply@dev.wallet.build")
                    .destination(dest)
                    .content(email_content)
                    .send()
                    .await
                    .map_or_else(|e| {
                        let service_err = e.into_service_error();
                        event!(
                            Level::ERROR,
                            "Email Notification Lambda could not publish to email to SES due to error kind: {service_err:?} with message: {:?}",
                            service_err.message()
                        );
                        Err(WorkerError::SESPublishError(service_err))
                    },|_| Ok(()))
            }
            Self::Test => Ok(()),
        }
    }
}
