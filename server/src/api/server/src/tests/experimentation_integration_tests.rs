use once_cell::sync::Lazy;
use std::collections::HashMap;

use experimentation::routes::{
    GetAccountFeatureFlagsRequest, GetAppInstallationFeatureFlagsRequest,
};
use feature_flags::flag::FeatureFlagValue;
use http::{HeaderMap, HeaderValue, StatusCode};

use crate::tests::gen_services_with_overrides;
use crate::tests::lib::{create_default_account_with_predefined_wallet, create_lite_account};
use crate::tests::requests::axum::TestClient;
use crate::{tests, GenServiceOverrides};

struct GetFeatureFlagsTestVector {
    flag_keys: Vec<String>,
    overriden_feature_flags: Vec<(String, String)>,
    expected_status: StatusCode,
    expected_feature_flags: Vec<(String, FeatureFlagValue)>,
}

static DEFAULT_EXPERIMENTATION_HEADERS: Lazy<HeaderMap> = Lazy::new(|| {
    let mut headers = HeaderMap::new();
    headers.insert(
        "Bitkey-App-Installation-ID",
        HeaderValue::from_static("test-app-installation-id"),
    );
    headers.insert("Bitkey-App-Version", HeaderValue::from_static("2024.51.0"));
    headers.insert(
        "Bitkey-OS-Type",
        HeaderValue::from_static("OS_TYPE_ANDROID"),
    );
    headers.insert("Bitkey-OS-Version", HeaderValue::from_static("1.0"));
    headers.insert("Bitkey-Device-Region", HeaderValue::from_static("AMERICA"));
    headers
});

async fn get_account_feature_flags(vector: GetFeatureFlagsTestVector) {
    let feature_flag_override = vector
        .overriden_feature_flags
        .into_iter()
        .collect::<HashMap<String, String>>();
    let overrides = GenServiceOverrides::new().feature_flags(feature_flag_override);
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;
    let (full_account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let lite_account = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let full_account_response = client
        .get_full_account_feature_flags(
            &full_account.id.to_string(),
            DEFAULT_EXPERIMENTATION_HEADERS.clone(),
            &GetAccountFeatureFlagsRequest {
                flag_keys: vector.flag_keys.clone(),
            },
        )
        .await;
    let lite_account_response = client
        .get_lite_account_feature_flags(
            &lite_account.id.to_string(),
            DEFAULT_EXPERIMENTATION_HEADERS.clone(),
            &GetAccountFeatureFlagsRequest {
                flag_keys: vector.flag_keys,
            },
        )
        .await;

    let expected_flags = vector
        .expected_feature_flags
        .into_iter()
        .collect::<HashMap<String, FeatureFlagValue>>();
    for response in [full_account_response, lite_account_response] {
        assert_eq!(response.status_code, vector.expected_status);
        if let Some(body) = response.body {
            for flag in body.flags {
                assert_eq!(flag.value, *expected_flags.get(&flag.key).unwrap());
            }
        }
    }
}

tests! {
    runner = get_account_feature_flags,
    test_get_account_feature_flags_with_invalid_flag: GetFeatureFlagsTestVector {
        flag_keys: vec!["random_flag".to_string()],
        overriden_feature_flags: vec![],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![],
    },
    test_get_account_feature_flags_with_all_valid_flags: GetFeatureFlagsTestVector {
        flag_keys: vec!["flag_1".to_string(), "flag_2".to_string()],
        overriden_feature_flags: vec![(String::from("flag_1"), String::from("true")), (String::from("flag_2"), String::from("false")), (String::from("flag_3"), String::from("false"))],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![(String::from("flag_1"), FeatureFlagValue::Boolean{boolean: true}), (String::from("flag_2"), FeatureFlagValue::Boolean{boolean: false})],
    },
    test_get_account_feature_flags_with_missing_flags: GetFeatureFlagsTestVector {
        flag_keys: vec!["flag_not_found".to_string(), "flag_2".to_string()],
        overriden_feature_flags: vec![(String::from("flag_1"), String::from("true")), (String::from("flag_2"), String::from("false")), (String::from("flag_3"), String::from("false"))],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![(String::from("flag_2"), FeatureFlagValue::Boolean{boolean: false})],
    },
    test_get_account_feature_flags_with_all_types_of_flags: GetFeatureFlagsTestVector {
        flag_keys: vec!["flag_1".to_string(), "flag_2".to_string(), "flag_3".to_string()],
        overriden_feature_flags: vec![(String::from("flag_1"), String::from("true")), (String::from("flag_2"), String::from("test")), (String::from("flag_3"), String::from("1.001")), (String::from("flag_4"), String::from("1.002"))],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![(String::from("flag_1"), FeatureFlagValue::Boolean{boolean: true}), (String::from("flag_2"), FeatureFlagValue::String{string: "test".to_owned()}), (String::from("flag_3"), FeatureFlagValue::Double { double: 1.001 })],
    },
}

async fn get_app_installation_feature_flags(vector: GetFeatureFlagsTestVector) {
    let feature_flag_override = vector
        .overriden_feature_flags
        .into_iter()
        .collect::<HashMap<String, String>>();
    let overrides = GenServiceOverrides::new().feature_flags(feature_flag_override);
    let (_, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;
    let response = client
        .get_app_installation_feature_flags(
            DEFAULT_EXPERIMENTATION_HEADERS.clone(),
            &GetAppInstallationFeatureFlagsRequest {
                flag_keys: vector.flag_keys,
            },
        )
        .await;

    assert_eq!(response.status_code, vector.expected_status);
    if let Some(body) = response.body {
        let expected_flags = vector
            .expected_feature_flags
            .into_iter()
            .collect::<HashMap<String, FeatureFlagValue>>();
        for flag in body.flags {
            assert_eq!(flag.value, *expected_flags.get(&flag.key).unwrap());
        }
    }
}

tests! {
    runner = get_app_installation_feature_flags,
    test_get_app_installation_feature_flags_with_invalid_flag: GetFeatureFlagsTestVector {
        flag_keys: vec!["random_flag".to_string()],
        overriden_feature_flags: vec![],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![],
    },
    test_get_app_installation_feature_flags_with_all_valid_flags: GetFeatureFlagsTestVector {
        flag_keys: vec!["flag_1".to_string(), "flag_2".to_string()],
        overriden_feature_flags: vec![(String::from("flag_1"), String::from("true")), (String::from("flag_2"), String::from("false")), (String::from("flag_3"), String::from("false"))],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![(String::from("flag_1"), FeatureFlagValue::Boolean{boolean: true}), (String::from("flag_2"), FeatureFlagValue::Boolean{boolean: false})],
    },
    test_get_app_installation_feature_flags_with_missing_flags: GetFeatureFlagsTestVector {
        flag_keys: vec!["flag_not_found".to_string(), "flag_2".to_string()],
        overriden_feature_flags: vec![(String::from("flag_1"), String::from("true")), (String::from("flag_2"), String::from("false")), (String::from("flag_3"), String::from("false"))],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![(String::from("flag_2"), FeatureFlagValue::Boolean{boolean: false})],
    },
    test_get_app_installation_feature_flags_with_all_types_of_flags: GetFeatureFlagsTestVector {
        flag_keys: vec!["flag_1".to_string(), "flag_2".to_string(), "flag_3".to_string()],
        overriden_feature_flags: vec![(String::from("flag_1"), String::from("true")), (String::from("flag_2"), String::from("test")), (String::from("flag_3"), String::from("1.001")), (String::from("flag_4"), String::from("1.002"))],
        expected_status: StatusCode::OK,
        expected_feature_flags: vec![(String::from("flag_1"), FeatureFlagValue::Boolean{boolean: true}), (String::from("flag_2"), FeatureFlagValue::String{string: "test".to_owned()}), (String::from("flag_3"), FeatureFlagValue::Double { double: 1.001 })],
    },
}
