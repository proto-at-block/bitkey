use std::{collections::HashMap, sync::Arc};

use launchdarkly_server_sdk::Client;

use crate::config::Mode;

#[derive(Clone)]
pub struct Service {
    pub(crate) client: Arc<Client>,
    pub(crate) mode: Mode,
    pub(crate) override_flags: HashMap<String, String>,
}

impl Service {
    pub(crate) fn new(client: Client, mode: Mode, override_flags: HashMap<String, String>) -> Self {
        Self {
            client: Arc::new(client),
            mode,
            override_flags,
        }
    }
}
