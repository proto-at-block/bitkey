#[derive(serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
#[serde(default)]
#[rustfmt::skip]
#[allow(clippy::derive_partial_eq_without_eq)]
#[derive(Clone, PartialEq, ::prost::Message)]
pub struct DeepLinkNotification {
    #[prost(enumeration = "NavigationScreenId", tag = "1")]
    pub id: i32,
}
/// * Screen Ids used to navigate to a specific screen in the app via server-sent push notifications. *
#[derive(serde::Serialize, serde::Deserialize)]
#[rustfmt::skip]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, ::prost::Enumeration)]
#[repr(i32)]
pub enum NavigationScreenId {
    /// * Default value *
    Unspecified = 0,
    /// * The root money home screen. *
    MoneyHome = 1,
    /// * The root settings screen. *
    Settings = 2,
}
impl NavigationScreenId {
    /// String value of the enum field names used in the ProtoBuf definition.
    ///
    /// The values are not transformed in any way and thus are considered stable
    /// (if the ProtoBuf definition does not change) and safe for programmatic use.
    pub fn as_str_name(&self) -> &'static str {
        match self {
            NavigationScreenId::Unspecified => "NAVIGATION_SCREEN_ID_UNSPECIFIED",
            NavigationScreenId::MoneyHome => "NAVIGATION_SCREEN_ID_MONEY_HOME",
            NavigationScreenId::Settings => "NAVIGATION_SCREEN_ID_SETTINGS",
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "NAVIGATION_SCREEN_ID_UNSPECIFIED" => Some(Self::Unspecified),
            "NAVIGATION_SCREEN_ID_MONEY_HOME" => Some(Self::MoneyHome),
            "NAVIGATION_SCREEN_ID_SETTINGS" => Some(Self::Settings),
            _ => None,
        }
    }
}
