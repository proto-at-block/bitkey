use std::collections::HashMap;

use analytics::routes::definitions::PlatformInfo;

use crate::{claims::ExperimentationClaims, routes::CommonFeatureFlagsAttributes};

const DEVICE_ID_ATTRIBUTE_NAME: &str = "device_id";
const APPLICATION_VERSION_ATTRIBUTE_NAME: &str = "application_version";
const OS_TYPE_ATTRIBUTE_NAME: &str = "os_type";
const OS_VERSION_ATTRIBUTE_NAME: &str = "os_version";
const APP_ID_ATTRIBUTE_NAME: &str = "app_id";
const APP_INSTALLATION_ID_ATTRIBUTE_NAME: &str = "app_installation_id";
const DEVICE_REGION_ATTRIBUTE_NAME: &str = "device_region";
const DEVICE_LANGUAGE_ATTRIBUTE_NAME: &str = "device_language";

pub(crate) trait ToLaunchDarklyAttributes {
    fn to_attributes(&self) -> HashMap<&'static str, String>;
}

impl ToLaunchDarklyAttributes for ExperimentationClaims {
    fn to_attributes(&self) -> HashMap<&'static str, String> {
        let mut attributes = HashMap::new();
        if let Some(app_installation_id) = &self.app_installation_id {
            attributes.insert(
                APP_INSTALLATION_ID_ATTRIBUTE_NAME,
                app_installation_id.clone(),
            );
        }
        if let Some(app_version) = &self.app_version {
            attributes.insert(APPLICATION_VERSION_ATTRIBUTE_NAME, app_version.clone());
        }
        if let Some(os_type) = &self.os_type {
            attributes.insert(OS_TYPE_ATTRIBUTE_NAME, os_type.clone());
        }
        if let Some(os_version) = &self.os_version {
            attributes.insert(OS_VERSION_ATTRIBUTE_NAME, os_version.clone());
        }
        if let Some(device_region) = &self.device_region {
            attributes.insert(DEVICE_REGION_ATTRIBUTE_NAME, device_region.clone());
        }
        attributes
    }
}

impl ToLaunchDarklyAttributes for PlatformInfo {
    fn to_attributes(&self) -> HashMap<&'static str, String> {
        let mut attributes = HashMap::new();
        attributes.insert(DEVICE_ID_ATTRIBUTE_NAME, self.device_id.clone());
        attributes.insert(
            APPLICATION_VERSION_ATTRIBUTE_NAME,
            self.application_version.clone(),
        );
        attributes.insert(OS_TYPE_ATTRIBUTE_NAME, self.os_type.to_string());
        attributes.insert(OS_VERSION_ATTRIBUTE_NAME, self.os_version.clone());
        attributes.insert(APP_ID_ATTRIBUTE_NAME, self.app_id.clone());
        attributes
    }
}

impl ToLaunchDarklyAttributes for CommonFeatureFlagsAttributes {
    fn to_attributes(&self) -> HashMap<&'static str, String> {
        let mut attributes = HashMap::new();
        attributes.insert(
            APP_INSTALLATION_ID_ATTRIBUTE_NAME,
            self.app_installation_id.clone(),
        );
        attributes.insert(DEVICE_REGION_ATTRIBUTE_NAME, self.device_region.clone());
        attributes.insert(DEVICE_LANGUAGE_ATTRIBUTE_NAME, self.device_language.clone());
        attributes.extend(self.platform_info.to_attributes());
        attributes
    }
}
