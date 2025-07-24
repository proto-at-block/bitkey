use std::collections::HashMap;
use std::time::Duration;

use anyhow::Result;
use aws_sdk_dynamodb::types::AttributeValue;
use indicatif::{ProgressBar, ProgressStyle};
use serde_yaml;

pub fn create_spinner(message: &str) -> ProgressBar {
    let pb = ProgressBar::new_spinner();
    pb.set_style(
        ProgressStyle::default_spinner()
            .tick_strings(&["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"])
            .template("{spinner:.green} {msg}")
            .unwrap(),
    );
    pb.set_message(message.to_string());
    pb.enable_steady_tick(Duration::from_millis(120));
    pb
}

pub fn get_table_name(environment: &str, base_name: &str) -> String {
    if environment.starts_with("prod") || environment.starts_with("stag") {
        base_name.to_string()
    } else {
        format!("{}.{}", environment, base_name)
    }
}

pub async fn query_dynamo(
    ddb: &aws_sdk_dynamodb::Client,
    table_name: &str,
    partition_key: &str,
    index_name: Option<&str>,
    key_condition: &str,
    filter_expression: Option<&str>,
    expression_attribute_names: Option<(&str, &str)>,
    expression_attribute_values: Option<(&str, &str)>,
) -> Result<Vec<HashMap<String, AttributeValue>>> {
    let mut query = ddb
        .query()
        .table_name(table_name)
        .key_condition_expression(key_condition)
        .expression_attribute_values(
            ":partition_key",
            AttributeValue::S(partition_key.to_string()),
        );

    if let Some(index) = index_name {
        query = query.index_name(index);
    }

    if let Some(filter) = filter_expression {
        query = query.filter_expression(filter);
    }

    if let Some((name, value)) = expression_attribute_names {
        query = query.expression_attribute_names(name, value);
    }

    if let Some((name, value)) = expression_attribute_values {
        query = query.expression_attribute_values(name, AttributeValue::S(value.to_string()));
    }

    let items = query.send().await?.items.unwrap_or_default();

    Ok(items)
}

pub fn attribute_value_to_serde_value(attr: &AttributeValue) -> serde_yaml::Value {
    match attr {
        AttributeValue::S(s) => serde_yaml::Value::String(s.clone()),
        AttributeValue::N(n) => {
            // Try to parse as integer first, then float, otherwise keep as string
            if let Ok(int_val) = n.parse::<i64>() {
                serde_yaml::Value::Number(serde_yaml::Number::from(int_val))
            } else if let Ok(float_val) = n.parse::<f64>() {
                serde_yaml::Value::Number(serde_yaml::Number::from(float_val))
            } else {
                serde_yaml::Value::String(n.clone())
            }
        }
        AttributeValue::B(b) => {
            serde_yaml::Value::String(format!("<binary: {} bytes>", b.as_ref().len()))
        }
        AttributeValue::Ss(ss) => serde_yaml::Value::Sequence(
            ss.iter()
                .map(|s| serde_yaml::Value::String(s.clone()))
                .collect(),
        ),
        AttributeValue::Ns(ns) => serde_yaml::Value::Sequence(
            ns.iter()
                .map(|n| {
                    if let Ok(int_val) = n.parse::<i64>() {
                        serde_yaml::Value::Number(serde_yaml::Number::from(int_val))
                    } else if let Ok(float_val) = n.parse::<f64>() {
                        serde_yaml::Value::Number(serde_yaml::Number::from(float_val))
                    } else {
                        serde_yaml::Value::String(n.clone())
                    }
                })
                .collect(),
        ),
        AttributeValue::Bs(bs) => serde_yaml::Value::String(format!("[{} binary items]", bs.len())),
        AttributeValue::M(m) => {
            let mut map = serde_yaml::Mapping::new();
            for (k, v) in m {
                map.insert(
                    serde_yaml::Value::String(k.clone()),
                    attribute_value_to_serde_value(v),
                );
            }
            serde_yaml::Value::Mapping(map)
        }
        AttributeValue::L(l) => {
            serde_yaml::Value::Sequence(l.iter().map(attribute_value_to_serde_value).collect())
        }
        AttributeValue::Null(_) => serde_yaml::Value::Null,
        AttributeValue::Bool(b) => serde_yaml::Value::Bool(*b),
        _ => serde_yaml::Value::String("<unknown>".to_string()),
    }
}

pub fn format_items_yaml(title: Option<&str>, items: &[HashMap<String, AttributeValue>]) {
    if let Some(title) = title {
        println!("  {}:", title);
    }

    if items.is_empty() {
        println!("  📭 No items found");
        return;
    }

    println!("  📦 Found {} item(s):", items.len());
    println!();

    for (index, item) in items.iter().enumerate() {
        if index > 0 {
            println!("  {}", "─".repeat(60)); // Separator line between items
        }

        // Convert the DynamoDB item to serde_yaml::Value
        let mut yaml_map = serde_yaml::Mapping::new();
        for (key, value) in item {
            yaml_map.insert(
                serde_yaml::Value::String(key.clone()),
                attribute_value_to_serde_value(value),
            );
        }
        let yaml_value = serde_yaml::Value::Mapping(yaml_map);

        // Convert to YAML string and print with indentation
        match serde_yaml::to_string(&yaml_value) {
            Ok(yaml_string) => {
                // Add 2-space indentation to each line
                for line in yaml_string.lines() {
                    if !line.trim().is_empty() {
                        println!("  {}", line);
                    }
                }
            }
            Err(e) => {
                println!("  ⚠️  Error formatting item as YAML: {}", e);
            }
        }
        println!();
    }
}
