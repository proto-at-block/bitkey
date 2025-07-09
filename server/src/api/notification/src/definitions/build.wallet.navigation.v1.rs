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
    /// * The success screen shown to a protected customer when an invite is accepted upon tapping the notification. *
    SocialRecoveryProtectedCustomerInviteAccepted = 8,
    /// * The screen shown to enable or disable the biometric app lock setting. *
    ManageBiometric = 9,
    /// * The screen shown to enable or disable critical alerts and the information associated with the alert *
    ManageCriticalAlerts = 10,
    /// * The screen shown to view the status of the EEK backup. *
    EakBackupHealth = 11,
    /// * The screen shown to manage the HW fingerprints. *
    ManageFingerprints = 12,
    /// * The screen shown to view the status of the App Key backup. *
    MobileKeyBackup = 13,
    /// * The screen shown to manage recovery contacts. *
    ManageRecoveryContacts = 14,
    /// * The screen shown to update firmware. *
    UpdateFirmware = 15,
    /// * The screen shown to pair a bitkey device. *
    PairDevice = 16,
    /// * The screen shown to manage Bitkey device. *
    ManageBitkeyDevice = 17,
    /// * The security hub screen. *
    SecurityHub = 18,
    /// * screen for repairing cloud back ups if there is an issue *
    CloudRepair = 19,
    /// * Transaction Verification Policy management screen. *
    TxVerificationPolicy = 20,
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
            NavigationScreenId::SocialRecoveryProtectedCustomerInviteAccepted => {
                "NAVIGATION_SCREEN_ID_SOCIAL_RECOVERY_PROTECTED_CUSTOMER_INVITE_ACCEPTED"
            }
            NavigationScreenId::ManageBiometric => {
                "NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC"
            }
            NavigationScreenId::ManageCriticalAlerts => {
                "NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS"
            }
            NavigationScreenId::EakBackupHealth => {
                "NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH"
            }
            NavigationScreenId::ManageFingerprints => {
                "NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS"
            }
            NavigationScreenId::MobileKeyBackup => {
                "NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP"
            }
            NavigationScreenId::ManageRecoveryContacts => {
                "NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS"
            }
            NavigationScreenId::UpdateFirmware => "NAVIGATION_SCREEN_ID_UPDATE_FIRMWARE",
            NavigationScreenId::PairDevice => "NAVIGATION_SCREEN_ID_PAIR_DEVICE",
            NavigationScreenId::ManageBitkeyDevice => {
                "NAVIGATION_SCREEN_ID_MANAGE_BITKEY_DEVICE"
            }
            NavigationScreenId::SecurityHub => "NAVIGATION_SCREEN_ID_SECURITY_HUB",
            NavigationScreenId::CloudRepair => "NAVIGATION_SCREEN_ID_CLOUD_REPAIR",
            NavigationScreenId::TxVerificationPolicy => {
                "NAVIGATION_SCREEN_ID_TX_VERIFICATION_POLICY"
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
            "NAVIGATION_SCREEN_ID_SOCIAL_RECOVERY_PROTECTED_CUSTOMER_INVITE_ACCEPTED" => {
                Some(Self::SocialRecoveryProtectedCustomerInviteAccepted)
            }
            "NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC" => Some(Self::ManageBiometric),
            "NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS" => {
                Some(Self::ManageCriticalAlerts)
            }
            "NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH" => Some(Self::EakBackupHealth),
            "NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS" => Some(Self::ManageFingerprints),
            "NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP" => Some(Self::MobileKeyBackup),
            "NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS" => {
                Some(Self::ManageRecoveryContacts)
            }
            "NAVIGATION_SCREEN_ID_UPDATE_FIRMWARE" => Some(Self::UpdateFirmware),
            "NAVIGATION_SCREEN_ID_PAIR_DEVICE" => Some(Self::PairDevice),
            "NAVIGATION_SCREEN_ID_MANAGE_BITKEY_DEVICE" => Some(Self::ManageBitkeyDevice),
            "NAVIGATION_SCREEN_ID_SECURITY_HUB" => Some(Self::SecurityHub),
            "NAVIGATION_SCREEN_ID_CLOUD_REPAIR" => Some(Self::CloudRepair),
            "NAVIGATION_SCREEN_ID_TX_VERIFICATION_POLICY" => {
                Some(Self::TxVerificationPolicy)
            }
            _ => None,
        }
    }
}
