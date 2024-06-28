use std::collections::HashMap;

use once_cell::sync::Lazy;
use regex::Regex;

use crate::claims::ExperimentationClaims;

const APPLICATION_VERSION_ATTRIBUTE_NAME: &str = "application_version";
const OS_TYPE_ATTRIBUTE_NAME: &str = "os_type";
const OS_VERSION_ATTRIBUTE_NAME: &str = "os_version";
const APP_INSTALLATION_ID_ATTRIBUTE_NAME: &str = "app_installation_id";
const DEVICE_REGION_ATTRIBUTE_NAME: &str = "device_region";

static SEMANTIC_VERSION: Lazy<Regex> = Lazy::new(|| Regex::new(r"(\d+)\.(\d+)\.(\d+)").unwrap());

pub(crate) trait ToLaunchDarklyAttributes {
    fn to_attributes(&self) -> HashMap<&'static str, String>;
}

impl ToLaunchDarklyAttributes for ExperimentationClaims {
    fn to_attributes(&self) -> HashMap<&'static str, String> {
        let mut attributes = HashMap::new();
        if let Some(app_installation_id) = &self.app_installation_id {
            attributes.insert(
                APP_INSTALLATION_ID_ATTRIBUTE_NAME,
                app_installation_id.to_owned(),
            );
        }
        // Clean this up once client passes up appropriate semver application version
        if let Some(app_version) = &self.app_version {
            if let Some(valid_semver_app_version) = SEMANTIC_VERSION
                .captures(app_version)
                .map(|caps| format!("{}.{}.{}", &caps[1], &caps[2], &caps[3]))
            {
                attributes.insert(
                    APPLICATION_VERSION_ATTRIBUTE_NAME,
                    valid_semver_app_version.to_owned(),
                );
            }
        }
        if let Some(os_type) = &self.os_type {
            attributes.insert(OS_TYPE_ATTRIBUTE_NAME, os_type.to_owned());
        }
        if let Some(os_version) = &self.os_version {
            attributes.insert(OS_VERSION_ATTRIBUTE_NAME, os_version.to_owned());
        }
        if let Some(device_region) = &self.device_region {
            attributes.insert(DEVICE_REGION_ATTRIBUTE_NAME, device_region.to_owned());
        }
        attributes
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_update_semver() {
        struct TestCase<'a> {
            app_version: &'a str,
            expected_app_version: Option<&'a str>,
        }

        fn validate_app_version(v: TestCase) {
            let claims = ExperimentationClaims {
                account_id: None,
                app_installation_id: None,
                app_version: Some(v.app_version.to_string()),
                os_type: None,
                os_version: None,
                device_region: None,
            };

            let attributes = claims.to_attributes();
            assert_eq!(
                attributes.get("application_version").map(|s| s.as_str()),
                v.expected_app_version
            );
        }

        let test_cases = vec![
            TestCase {
                app_version: "2024.59.1",
                expected_app_version: Some("2024.59.1"),
            },
            TestCase {
                app_version: "2024.59.1 (1)",
                expected_app_version: Some("2024.59.1"),
            },
            TestCase {
                app_version: "2024.59.1.1",
                expected_app_version: Some("2024.59.1"),
            },
            TestCase {
                app_version: " ",
                expected_app_version: None,
            },
        ];

        for test_case in test_cases {
            validate_app_version(test_case);
        }
    }
}
