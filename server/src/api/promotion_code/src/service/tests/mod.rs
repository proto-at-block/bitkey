use database::ddb::{Config, Repository};
use http_server::config;

use crate::{repository::PromotionCodeRepository, service::Service, web_shop::WebShopClient};

pub async fn construct_test_promotion_code_service() -> Service {
    let profile = Some("test");
    let ddb_config = config::extract::<Config>(profile).expect("extract ddb config");
    let ddb_connection = ddb_config.to_connection().await;
    let promotion_code_repository = PromotionCodeRepository::new(ddb_connection.clone());
    Service::new(promotion_code_repository, WebShopClient::Test)
}

#[cfg(test)]
mod tests {
    use types::account::identifiers::AccountId;

    use crate::entities::CodeKey;

    use super::*;

    struct TestContext {
        service: Service,
    }

    impl TestContext {
        async fn new() -> Self {
            let service = construct_test_promotion_code_service().await;
            Self { service }
        }

        fn create_benefactor_test_code_key() -> (CodeKey, AccountId) {
            let account_id = AccountId::gen().expect("Account ID generation failed");
            let code_key = CodeKey::inheritance_benefactor(account_id.clone());
            (code_key, account_id)
        }

        fn create_beneficiary_test_code_key() -> (CodeKey, AccountId) {
            let account_id = AccountId::gen().expect("Account ID generation failed");
            let code_key = CodeKey::inheritance_beneficiary(account_id.clone());
            (code_key, account_id)
        }
    }

    #[tokio::test]
    async fn test_generate_new_promotion_code() {
        let ctx = TestContext::new().await;
        let (code_key, account_id) = TestContext::create_benefactor_test_code_key();

        let generated_code = ctx
            .service
            .generate_code(&code_key, &account_id)
            .await
            .expect("Failed to create new promotion code");

        let stored_code = ctx
            .service
            .get(&code_key)
            .await
            .expect("Failed to get code")
            .expect("Code not found");

        assert_eq!(generated_code, stored_code);
    }

    #[tokio::test]
    async fn test_generate_new_promotion_code_with_existing_key() {
        let ctx = TestContext::new().await;
        let (code_key, account_id) = TestContext::create_benefactor_test_code_key();

        let first_code = ctx
            .service
            .generate_code(&code_key, &account_id)
            .await
            .expect("Failed to create initial promotion code");

        let second_code = ctx
            .service
            .generate_code(&code_key, &account_id)
            .await
            .expect("Failed to retrieve existing promotion code");

        assert_eq!(first_code, second_code);
    }

    #[tokio::test]
    async fn test_get_promotion_code_for_beneficiary() {
        let ctx = TestContext::new().await;
        let (code_key, account_id) = TestContext::create_beneficiary_test_code_key();

        let code = ctx
            .service
            .generate_code(&code_key, &account_id)
            .await
            .expect("Failed to create promotion code");

        let retrieved_code = ctx
            .service
            .get(&code_key)
            .await
            .expect("Failed to retrieve promotion code")
            .expect("Code not found");

        assert_eq!(code, retrieved_code);
    }

    #[tokio::test]
    async fn test_mark_promotion_code_as_used() {
        let ctx = TestContext::new().await;
        let (code_key, account_id) = TestContext::create_beneficiary_test_code_key();

        let c = ctx
            .service
            .generate_code(&code_key, &account_id)
            .await
            .expect("Failed to create promotion code");

        ctx.service
            .mark_code_as_redeemed(c.code.as_str())
            .await
            .expect("Failed to mark code as used");

        let retrieved_code = ctx
            .service
            .get(&code_key)
            .await
            .expect("Failed to retrieve promotion code")
            .expect("Code not found");

        assert!(retrieved_code.is_redeemed);
    }
}
