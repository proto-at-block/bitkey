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
    /// * Settings > Manage inheritance (Beneficiaries tab) *
    ManageInheritance = 3,
    /// * Settings > Manage inheritance (benefactor tab) *
    ManageInheritanceBenefactor = 4,
    /// * Decline claim modal flow - requires claim id in payload *
    InheritanceDeclineClaim = 5,
    /// * Complete claim modal flow - requires claim id in payload *
    InheritanceCompleteClaim = 6,
    /// * The success screen shown to a benefactor when an invite is accepted upon tapping the notification. *
    InheritanceBenefactorInviteAccepted = 7,
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
            NavigationScreenId::ManageInheritance => {
                "NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE"
            }
            NavigationScreenId::ManageInheritanceBenefactor => {
                "NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE_BENEFACTOR"
            }
            NavigationScreenId::InheritanceDeclineClaim => {
                "NAVIGATION_SCREEN_ID_INHERITANCE_DECLINE_CLAIM"
            }
            NavigationScreenId::InheritanceCompleteClaim => {
                "NAVIGATION_SCREEN_ID_INHERITANCE_COMPLETE_CLAIM"
            }
            NavigationScreenId::InheritanceBenefactorInviteAccepted => {
                "NAVIGATION_SCREEN_ID_INHERITANCE_BENEFACTOR_INVITE_ACCEPTED"
            }
        }
    }
    /// Creates an enum from field names used in the ProtoBuf definition.
    pub fn from_str_name(value: &str) -> ::core::option::Option<Self> {
        match value {
            "NAVIGATION_SCREEN_ID_UNSPECIFIED" => Some(Self::Unspecified),
            "NAVIGATION_SCREEN_ID_MONEY_HOME" => Some(Self::MoneyHome),
            "NAVIGATION_SCREEN_ID_SETTINGS" => Some(Self::Settings),
            "NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE" => Some(Self::ManageInheritance),
            "NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE_BENEFACTOR" => {
                Some(Self::ManageInheritanceBenefactor)
            }
            "NAVIGATION_SCREEN_ID_INHERITANCE_DECLINE_CLAIM" => {
                Some(Self::InheritanceDeclineClaim)
            }
            "NAVIGATION_SCREEN_ID_INHERITANCE_COMPLETE_CLAIM" => {
                Some(Self::InheritanceCompleteClaim)
            }
            "NAVIGATION_SCREEN_ID_INHERITANCE_BENEFACTOR_INVITE_ACCEPTED" => {
                Some(Self::InheritanceBenefactorInviteAccepted)
            }
            _ => None,
        }
    }
}
