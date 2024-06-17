use std::collections::HashMap;

use crate::claims::ExperimentationClaims;

const APPLICATION_VERSION_ATTRIBUTE_NAME: &str = "application_version";
const OS_TYPE_ATTRIBUTE_NAME: &str = "os_type";
const OS_VERSION_ATTRIBUTE_NAME: &str = "os_version";
const APP_INSTALLATION_ID_ATTRIBUTE_NAME: &str = "app_installation_id";
const DEVICE_REGION_ATTRIBUTE_NAME: &str = "device_region";

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
        if let Some(app_version) = &self.app_version {
            attributes.insert(APPLICATION_VERSION_ATTRIBUTE_NAME, app_version.to_owned());
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
