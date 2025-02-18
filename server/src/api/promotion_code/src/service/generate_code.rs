use database::ddb::DatabaseError;
use tracing::instrument;
use types::account::identifiers::AccountId;

use crate::{
    entities::{Code, CodeKey, GenerateCodeRequest},
    error::PromotionCodeError,
};

use super::Service;

impl Service {
    #[instrument(skip(self))]
    pub async fn generate_code(
        &self,
        code_key: &CodeKey,
        creator_account_id: &AccountId,
    ) -> Result<Code, PromotionCodeError> {
        if let Ok(Some(existing_code)) = self.get(code_key).await {
            return Ok(existing_code);
        }

        let request = GenerateCodeRequest::from(code_key);
        let response = self
            .shop_client
            .generate_new_promotion_code(&request)
            .await?;
        let code = Code::new(
            code_key.to_owned(),
            response.code,
            creator_account_id.to_owned(),
        );

        self.repo.persist(&code).await?;
        Ok(code)
    }

    #[instrument(skip(self))]
    pub async fn get(&self, key: &CodeKey) -> Result<Option<Code>, PromotionCodeError> {
        match self.repo.fetch(key).await {
            Ok(record) => Ok(Some(record)),
            Err(DatabaseError::ObjectNotFound(_)) => Ok(None),
            Err(err) => Err(err.into()),
        }
    }
}
