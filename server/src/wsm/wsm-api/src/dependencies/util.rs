use anyhow::anyhow;
use aws_sdk_dynamodb::types::AttributeValue;
use std::collections::HashMap;

pub fn safe_get_str(item: &HashMap<String, AttributeValue>, k: &str) -> anyhow::Result<String> {
    item.get(k)
        .ok_or(anyhow!(format!("could not get key {k} from ddb item")))?
        .as_s()
        .map(String::from)
        .map_err(|value| anyhow!("could not return {:?} as string", value))
}
