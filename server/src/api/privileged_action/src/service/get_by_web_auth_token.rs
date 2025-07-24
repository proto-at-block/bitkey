use serde::de::DeserializeOwned;
use tracing::instrument;
use types::privileged_action::repository::PrivilegedActionInstanceRecord;

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct GetPendingByWebAuthTokenInput<'a> {
    pub web_auth_token: &'a str,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn get_by_web_auth_token<T>(
        &self,
        web_auth_token: &str,
    ) -> Result<PrivilegedActionInstanceRecord<T>, ServiceError>
    where
        T: DeserializeOwned,
    {
        let instance = self
            .privileged_action_repository
            .fetch_by_web_auth_token(web_auth_token)
            .await?;

        Ok(instance)
    }
}
