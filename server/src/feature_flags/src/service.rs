use std::{collections::HashMap, sync::Arc};

use launchdarkly_server_sdk::Client;

#[derive(Clone)]
pub struct Service {
    pub(crate) client: Arc<Client>,
    pub(crate) override_flags: HashMap<String, String>,
}

impl Service {
    pub(crate) fn new(client: Client, override_flags: HashMap<String, String>) -> Self {
        Self {
            client: Arc::new(client),
            override_flags,
        }
    }
}
