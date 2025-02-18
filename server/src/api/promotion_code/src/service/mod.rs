use crate::repository::PromotionCodeRepository;
use crate::web_shop::WebShopClient;

mod generate_code;
mod mark_code_as_redeemed;
pub mod tests;

#[derive(Clone)]
pub struct Service {
    repo: PromotionCodeRepository,
    shop_client: WebShopClient,
}

impl Service {
    pub fn new(repo: PromotionCodeRepository, shop_client: WebShopClient) -> Self {
        Self { repo, shop_client }
    }
}
