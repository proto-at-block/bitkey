use crate::{entities::Code, error::PromotionCodeError};

use super::Service;

impl Service {
    pub async fn mark_code_as_redeemed(&self, code: &str) -> Result<(), PromotionCodeError> {
        let Some(code) = self.repo.fetch_by_promotion_code(code).await? else {
            return Ok(());
        };
        self.repo
            .persist(&Code {
                is_redeemed: true,
                ..code
            })
            .await?;
        Ok(())
    }
}
