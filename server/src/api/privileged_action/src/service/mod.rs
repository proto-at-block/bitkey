use std::sync::Arc;

use base64::{engine::general_purpose::URL_SAFE_NO_PAD as b64, Engine as _};
use notification::service::Service as NotificationService;
use repository::account::AccountRepository;
use repository::privileged_action::PrivilegedActionRepository;
use serde::Deserialize;
use types::time::Clock;

pub mod authorize_privileged_action;
pub mod cancel_pending_instance;
pub mod configure_delay_duration_for_test;
pub mod configure_privileged_action_delay_durations;
pub mod error;
pub mod get_by_web_auth_token;
pub mod get_pending_instance;
pub mod get_pending_instances;
pub mod get_privileged_action_definitions;

#[derive(Clone, Deserialize)]
pub struct Config {
    pub ext_secure_site_base_url: String,
}

#[derive(Clone)]
pub struct Service {
    pub config: Config,
    pub privileged_action_repository: PrivilegedActionRepository,
    pub account_repository: AccountRepository,
    pub clock: Arc<dyn Clock>,
    pub notification_service: NotificationService,
}

impl Service {
    #[must_use]
    pub fn new(
        config: Config,
        privileged_action_repository: PrivilegedActionRepository,
        account_repository: AccountRepository,
        clock: Arc<dyn Clock>,
        notification_service: NotificationService,
    ) -> Self {
        Self {
            config,
            privileged_action_repository,
            account_repository,
            clock,
            notification_service,
        }
    }
}

pub fn gen_token() -> String {
    b64.encode(rand::random::<[u8; 16]>())
}
