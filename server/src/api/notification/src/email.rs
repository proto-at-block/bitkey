use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::clients::iterable::IterableCampaignType;

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "type")]
pub enum EmailPayload {
    SES {
        template_name: String,
        tags: HashMap<String, String>,
    },
    Iterable {
        campaign_type: IterableCampaignType,
        data_fields: HashMap<String, String>,
    },
}
