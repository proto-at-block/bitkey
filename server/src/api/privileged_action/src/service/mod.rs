use std::sync::Arc;

use account::repository::Repository as AccountRepository;
use base64::{engine::general_purpose::URL_SAFE_NO_PAD as b64, Engine as _};
use notification::service::Service as NotificationService;
use repository::privileged_action::Repository as PrivilegedActionRepository;
use types::time::Clock;

pub mod authorize_privileged_action;
pub mod cancel_pending_delay_and_notify_instance;
pub mod configure_privileged_action_delay_durations;
pub mod error;
pub mod get_pending_delay_and_notify_instances;
pub mod get_privileged_action_definitions;

#[derive(Clone)]
pub struct Service {
    pub privileged_action_repository: PrivilegedActionRepository,
    pub account_repository: AccountRepository,
    pub clock: Arc<dyn Clock>,
    pub notification_service: NotificationService,
}

impl Service {
    #[must_use]
    pub fn new(
        privileged_action_repository: PrivilegedActionRepository,
        account_repository: AccountRepository,
        clock: Arc<dyn Clock>,
        notification_service: NotificationService,
    ) -> Self {
        Self {
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
