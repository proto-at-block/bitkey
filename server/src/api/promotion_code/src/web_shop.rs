use std::env;

use rand::Rng;
use reqwest::Client;
use serde::Deserialize;
use strum_macros::EnumString;

use crate::{
    entities::{GenerateCodeRequest, GenerateCodeResponse},
    error::PromotionCodeError,
};

const AUTHORIZATION: &str = "Authorization";

#[derive(Clone, Debug, Deserialize, EnumString)]
#[serde(rename_all = "lowercase", tag = "mode")]
pub enum WebShopMode {
    Test,
    Environment { base_url: String },
}

impl WebShopMode {
    pub fn to_client(&self) -> WebShopClient {
        WebShopClient::new(self.to_owned())
    }
}

#[derive(Clone)]
pub enum WebShopClient {
    Real {
        client: Client,
        api_key: String,
        base_url: String,
    },
    Test,
}

impl WebShopClient {
    pub fn new(mode: WebShopMode) -> Self {
        match mode {
            WebShopMode::Environment { base_url } => Self::Real {
                client: Client::new(),
                api_key: env::var("WEB_SHOP_API_KEY")
                    .expect("WEB_SHOP_API_KEY environment variable not set"),
                base_url,
            },
            WebShopMode::Test => Self::Test,
        }
    }

    pub(crate) async fn generate_new_promotion_code(
        &self,
        request: &GenerateCodeRequest,
    ) -> Result<GenerateCodeResponse, PromotionCodeError> {
        match self {
            WebShopClient::Real {
                client,
                base_url,
                api_key,
            } => client
                .post(&format!("{}/v1/internal/fromagerie/promotion", base_url))
                .header(AUTHORIZATION, format!("Bearer {}", api_key))
                .json(request)
                .send()
                .await?
                .json()
                .await
                .map_err(PromotionCodeError::from),
            WebShopClient::Test => Ok(GenerateCodeResponse {
                code: rand::thread_rng().gen_range(100000..999999).to_string(),
            }),
        }
    }
}
