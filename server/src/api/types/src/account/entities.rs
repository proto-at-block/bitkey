use std::collections::HashMap;
use std::{env, env::VarError};

use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use bdk_utils::DescriptorKeyset;
use isocountry::CountryCode;
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as};
use strum_macros::{Display as StrumDisplay, EnumString};
use time::{serde::rfc3339, OffsetDateTime};
use utoipa::ToSchema;

use super::errors::AccountError;
use crate::notification::NotificationsTrigger;
use crate::{
    account::{
        identifiers::{AccountId, AuthKeysId, KeyDefinitionId, KeysetId, TouchpointId},
        keys::{FullAccountAuthKeys, LiteAccountAuthKeys, SoftwareAccountAuthKeys},
        money::Money,
        spend_limit::SpendingLimit,
        spending::{SpendingKeyDefinition, SpendingKeyset},
        AccountType, PubkeysToAccount,
    },
    notification::{NotificationChannel, NotificationsPreferencesState},
    privileged_action::shared::PrivilegedActionDelayDuration,
};

#[derive(Deserialize, Serialize, PartialEq, Debug, Clone, Copy, ToSchema, StrumDisplay)]
pub enum AuthFactor {
    App,
    Hw,
    Recovery,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, Clone, Copy, ToSchema, StrumDisplay)]
pub enum Factor {
    App,
    Hw,
}

#[derive(
    Deserialize, Serialize, Clone, Copy, Debug, PartialEq, Eq, ToSchema, StrumDisplay, EnumString,
)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[derive(Default)]
pub enum HardwareType {
    #[default]
    W1,
    W3,
}

/// W1 serial number prefixes at positions 4..8 (0-based) of the hardware serial.
const W1_SERIAL_CODES: [&str; 3] = ["S203", "S204", "S233"];
/// W3 serial number prefixes at positions 4..8 (0-based) of the hardware serial.
const W3_SERIAL_CODES: [&str; 2] = ["S271", "S272"];

impl HardwareType {
    /// Fake W1 serial for use in tests. Positions 4..8 = "S203".
    pub const TEST_SERIAL_W1: &'static str = "fakeS203serial";
    /// Fake W3 serial for use in tests. Positions 4..8 = "S271".
    pub const TEST_SERIAL_W3: &'static str = "fakeS271serial";

    /// Classify hardware type from a serial number by inspecting positions 4..8.
    /// Returns `None` if the serial is too short or the code is unrecognized.
    pub fn from_serial(serial: &str) -> Option<HardwareType> {
        let Some(code) = serial.get(4..8) else {
            tracing::warn!(
                hw_serial = serial,
                "Hardware serial too short to classify (need at least 8 chars)"
            );
            return None;
        };
        if W1_SERIAL_CODES.contains(&code) {
            Some(HardwareType::W1)
        } else if W3_SERIAL_CODES.contains(&code) {
            Some(HardwareType::W3)
        } else {
            tracing::warn!(
                hw_serial = serial,
                "Unrecognized hardware serial code at positions 4..8"
            );
            None
        }
    }

    /// Parse the hardware type from an optional serial header, returning an error
    /// if the serial is missing or unrecognized.
    fn require_known_serial(serial: Option<&str>) -> Result<HardwareType, &'static str> {
        let serial = serial.ok_or("Hardware serial header is required.")?;
        HardwareType::from_serial(serial).ok_or("Unrecognized hardware serial number.")
    }

    /// For v1 routes: require that the serial is present and maps to W1 hardware.
    /// Returns `Err` with a message suitable for a 403 response if the check fails.
    pub fn require_w1_serial(serial: Option<&str>) -> Result<(), &'static str> {
        match Self::require_known_serial(serial)? {
            HardwareType::W1 => Ok(()),
            _ => {
                Err("This endpoint requires W1 hardware. Upgrade your app to use the v2 endpoints.")
            }
        }
    }

    /// For v2 routes: block if the serial is missing, unrecognized, or if the serial
    /// indicates W3 but the request claims W1 hardware type.
    /// Returns `Err` with a message suitable for a 403 response if the check fails.
    pub fn reject_w3_claiming_w1(
        serial: Option<&str>,
        claimed_hardware_type: HardwareType,
    ) -> Result<(), &'static str> {
        let hw_type = Self::require_known_serial(serial)?;
        if hw_type == HardwareType::W3 && claimed_hardware_type == HardwareType::W1 {
            return Err("W3 hardware must not onboard as W1. Use the correct hardware type.");
        }
        Ok(())
    }
}

#[derive(Deserialize, Serialize, PartialEq, Debug, Clone)]
pub enum KeyDomain {
    Spend,
    Auth,
    Config,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, Clone)]
pub struct KeyInfo {
    pub xpub: String,
    pub derivation_path: String,
    pub factor: Factor,
    pub domain: KeyDomain,
}

#[derive(
    Deserialize,
    Serialize,
    Clone,
    Copy,
    Debug,
    PartialEq,
    Eq,
    ToSchema,
    StrumDisplay,
    EnumString,
    Hash,
)]
pub enum TouchpointPlatform {
    ApnsCustomer,
    ApnsTeam,
    ApnsTeamAlpha,
    FcmCustomer,
    FcmTeam,
}

impl TouchpointPlatform {
    pub fn get_platform_arn(self) -> Result<String, VarError> {
        match self {
            TouchpointPlatform::ApnsCustomer => env::var("APNS_CUSTOMER_PLATFORM_ARN"),
            TouchpointPlatform::ApnsTeam => env::var("APNS_TEAM_PLATFORM_ARN"),
            TouchpointPlatform::ApnsTeamAlpha => env::var("APNS_TEAM_ALPHA_PLATFORM_ARN"),
            TouchpointPlatform::FcmCustomer => env::var("FCM_CUSTOMER_PLATFORM_ARN"),
            TouchpointPlatform::FcmTeam => env::var("FCM_TEAM_PLATFORM_ARN"),
        }
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(tag = "type")]
pub enum Touchpoint {
    Email {
        id: TouchpointId,
        email_address: String,
        #[serde(default)]
        active: bool,
    },
    Phone {
        id: TouchpointId,
        phone_number: String,
        country_code: CountryCode,
        #[serde(default)]
        active: bool,
    },
    Push {
        platform: TouchpointPlatform,
        arn: String,
        device_token: String,
    },
}

impl Touchpoint {
    pub fn new_phone(
        id: TouchpointId,
        phone_number: String,
        country_code: CountryCode,
        active: bool,
    ) -> Self {
        Touchpoint::Phone {
            id,
            phone_number,
            country_code,
            active,
        }
    }

    pub fn new_email(id: TouchpointId, email_address: String, active: bool) -> Self {
        Touchpoint::Email {
            id,
            email_address,
            active,
        }
    }

    pub fn is_active(&self) -> bool {
        match self {
            Touchpoint::Email { active, .. } | Touchpoint::Phone { active, .. } => *active,
            Touchpoint::Push { .. } => true,
        }
    }

    pub fn email_address(&self) -> Option<String> {
        match self {
            Touchpoint::Email { email_address, .. } => Some(email_address.clone()),
            _ => None,
        }
    }

    pub fn phone_number(&self) -> Option<String> {
        match self {
            Touchpoint::Phone { phone_number, .. } => Some(phone_number.clone()),
            _ => None,
        }
    }

    pub fn id(&self) -> Option<&TouchpointId> {
        match self {
            Touchpoint::Email { id, .. } | Touchpoint::Phone { id, .. } => Some(id),
            Touchpoint::Push { .. } => None,
        }
    }
}

impl From<&Touchpoint> for NotificationChannel {
    fn from(v: &Touchpoint) -> Self {
        match v {
            Touchpoint::Email { .. } => NotificationChannel::Email,
            Touchpoint::Push { .. } => NotificationChannel::Push,
            Touchpoint::Phone { .. } => NotificationChannel::Sms,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub enum CommsVerificationScope {
    AddTouchpointId(TouchpointId),
    DelayNotifyActor(Factor),
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Default)]
pub enum CommsVerificationStatus {
    #[default]
    Unverified,
    Pending {
        #[serde(default)]
        code_hash: String,
        #[serde(with = "rfc3339")]
        sent_at: OffsetDateTime,
        #[serde(with = "rfc3339")]
        expires_at: OffsetDateTime,
    },
    Verified {
        #[serde(with = "rfc3339")]
        expires_at: OffsetDateTime,
    },
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct CommsVerificationClaim {
    pub scope: CommsVerificationScope,
    pub status: CommsVerificationStatus,
}

impl CommsVerificationClaim {
    pub fn new(scope: CommsVerificationScope, status: Option<CommsVerificationStatus>) -> Self {
        Self {
            scope,
            status: status.unwrap_or_default(),
        }
    }
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct FullAccountAuthKeysInput {
    // TODO: [W-774] Update visibility of struct after migration
    pub app: PublicKey,
    pub hardware: PublicKey,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub recovery: Option<PublicKey>,
    #[serde(default)]
    pub hardware_type: HardwareType,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct LiteAccountAuthKeysInput {
    // TODO: [W-774] Update visibility of struct after migration
    pub recovery: PublicKey,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct SoftwareAccountAuthKeysInput {
    // TODO: [W-774] Update visibility of struct after migration
    pub app: PublicKey,
    pub recovery: PublicKey,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct UpgradeLiteAccountAuthKeysInput {
    // TODO: [W-774] Update visibility of struct after migration
    pub app: PublicKey,
    pub hardware: PublicKey,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct SpendingKeysetInput {
    // TODO: [W-774] Update visibility of struct after migration
    pub network: bdk_utils::bdk::bitcoin::Network,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub app: DescriptorPublicKey,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub hardware: DescriptorPublicKey,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Default)]
pub struct AccountProperties {
    pub is_test_account: bool,
}

impl AccountProperties {
    fn validate(&self, to: &Self) -> Result<(), AccountError> {
        if self.is_test_account != to.is_test_account {
            return Err(AccountError::InvalidUpdateAccountProperties);
        }
        Ok(())
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct CommonAccountFields {
    // Authentication Keys
    pub active_auth_keys_id: AuthKeysId,
    // Touchpoints
    pub touchpoints: Vec<Touchpoint>,
    // Creation and Update Times
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
    #[serde(default)]
    pub properties: AccountProperties,
    //TODO: Move `onboarding_complete` into FullAccount
    #[serde(default)]
    pub onboarding_complete: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub recovery_auth_pubkey: Option<PublicKey>,
    #[serde(default)]
    #[serde(alias = "notifications_preferences")]
    pub notifications_preferences_state: NotificationsPreferencesState,
    #[serde(default)]
    pub configured_privileged_action_delay_durations: Vec<PrivilegedActionDelayDuration>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub delay_notify_period_secs: Option<usize>,
    #[serde(default)]
    pub comms_verification_claims: Vec<CommsVerificationClaim>,
    #[serde(default)]
    pub notifications_triggers: Vec<NotificationsTrigger>,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, ToSchema)]
#[serde(
    tag = "state",
    content = "threshold",
    rename_all = "SCREAMING_SNAKE_CASE"
)]
#[derive(Default)]
pub enum TransactionVerificationPolicy {
    #[default]
    Never,
    Threshold(Money),
    Always,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct FullAccount {
    #[serde(rename = "partition_key")]
    pub id: AccountId,
    // Spending Keysets
    pub active_keyset_id: KeysetId,
    #[serde(default)]
    pub spending_keysets: HashMap<KeysetId, SpendingKeyset>,
    #[serde(default)]
    pub descriptor_backups_set: Option<DescriptorBackupsSet>,
    #[serde(default)]
    pub wallet_metadata_backup: Option<WalletMetadataBackup>,
    // Spending limit
    pub spending_limit: Option<SpendingLimit>,
    #[serde(default)]
    pub transaction_verification_policy: Option<TransactionVerificationPolicy>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub application_auth_pubkey: Option<PublicKey>,
    // Hardware Authentication Key
    pub hardware_auth_pubkey: PublicKey,
    #[serde(default)]
    pub auth_keys: HashMap<AuthKeysId, FullAccountAuthKeys>,
    /// One-way enrollment flag for hardware-verification OOBA. Once `true`,
    /// must never be unset — see `account::hardware_verification` for the
    /// gate model.
    #[serde(default)]
    pub hardware_verification_required: bool,
    #[serde(flatten)]
    pub common_fields: CommonAccountFields,
}

impl FullAccount {
    #[must_use]
    pub fn new(
        account_id: AccountId,
        active_keyset_id: KeysetId,
        active_auth_keys_id: AuthKeysId,
        auth: FullAccountAuthKeys,
        spending: SpendingKeyset,
        properties: AccountProperties,
        hardware_verification_required: bool,
    ) -> Self {
        let now = OffsetDateTime::now_utc();
        let hardware_auth_pubkey = auth.hardware_pubkey;
        let application_auth_pubkey = Some(auth.app_pubkey);
        let recovery_auth_pubkey = auth.recovery_pubkey;
        Self {
            id: account_id,
            active_keyset_id: active_keyset_id.clone(),
            auth_keys: HashMap::from([(active_auth_keys_id.clone(), auth)]),
            spending_keysets: HashMap::from([(active_keyset_id, spending)]),
            descriptor_backups_set: None,
            wallet_metadata_backup: None,
            spending_limit: None,
            transaction_verification_policy: None,
            application_auth_pubkey,
            hardware_auth_pubkey,
            hardware_verification_required,
            common_fields: CommonAccountFields {
                active_auth_keys_id,
                touchpoints: vec![],
                created_at: now,
                updated_at: now,
                properties,
                onboarding_complete: false,
                recovery_auth_pubkey,
                notifications_preferences_state: Default::default(),
                configured_privileged_action_delay_durations: Default::default(),
                delay_notify_period_secs: None,
                comms_verification_claims: Default::default(),
                notifications_triggers: Default::default(),
            },
        }
    }

    pub fn active_auth_keys(&self) -> Option<&FullAccountAuthKeys> {
        self.auth_keys.get(&self.common_fields.active_auth_keys_id)
    }

    pub fn active_spending_keyset(&self) -> Option<&SpendingKeyset> {
        self.spending_keysets.get(&self.active_keyset_id)
    }

    pub fn is_spending_limit_active(&self) -> bool {
        self.spending_limit
            .as_ref()
            .is_some_and(|limit| limit.active)
    }

    /// Returns the hardware type of the active auth keys.
    ///
    /// Used by route handlers to determine whether to use ActionProof (W3) or
    /// KeyClaims (W1) authorization. Returns an error message suitable for
    /// wrapping in `ApiError::GenericInternalApplicationError`.
    pub fn active_hardware_type(&self) -> Result<HardwareType, &'static str> {
        self.active_auth_keys()
            .map(|k| k.hardware_type)
            .ok_or("Full account missing active auth keys")
    }

    pub fn active_descriptor_keyset(&self) -> Option<DescriptorKeyset> {
        self.active_spending_keyset()
            .and_then(|keyset| keyset.optional_legacy_multi_sig().map(|k| k.clone().into()))
    }

    pub fn inactive_descriptor_keysets(&self) -> Vec<DescriptorKeyset> {
        self.spending_keysets
            .iter()
            .filter_map(|keyset| {
                if *keyset.0 == self.active_keyset_id {
                    None
                } else {
                    keyset
                        .1
                        .optional_legacy_multi_sig()
                        .map(|k| k.clone().into())
                }
            })
            .collect()
    }
}

impl From<FullAccount> for Account {
    fn from(v: FullAccount) -> Self {
        Self::Full(v)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct LiteAccount {
    #[serde(rename = "partition_key")]
    pub id: AccountId,
    pub auth_keys: HashMap<AuthKeysId, LiteAccountAuthKeys>,
    #[serde(flatten)]
    pub common_fields: CommonAccountFields,
}

impl LiteAccount {
    #[must_use]
    pub fn new(
        account_id: AccountId,
        active_auth_keys_id: AuthKeysId,
        auth: LiteAccountAuthKeys,
        properties: AccountProperties,
    ) -> Self {
        let now = OffsetDateTime::now_utc();
        let recovery_auth_pubkey = Some(auth.recovery_pubkey);
        Self {
            id: account_id,
            auth_keys: HashMap::from([(active_auth_keys_id.clone(), auth)]),
            common_fields: CommonAccountFields {
                active_auth_keys_id,
                touchpoints: vec![],
                created_at: now,
                updated_at: now,
                properties,
                onboarding_complete: true,
                recovery_auth_pubkey,
                notifications_preferences_state: Default::default(),
                configured_privileged_action_delay_durations: Default::default(),
                delay_notify_period_secs: None,
                comms_verification_claims: Default::default(),
                notifications_triggers: Default::default(),
            },
        }
    }

    pub fn active_auth_keys(&self) -> Option<&LiteAccountAuthKeys> {
        self.auth_keys.get(&self.common_fields.active_auth_keys_id)
    }

    pub fn upgrade_to_full_account(
        &self,
        keyset_id: KeysetId,
        spending_keyset: SpendingKeyset,
        auth_keys_id: AuthKeysId,
        auth_keys: FullAccountAuthKeys,
        hardware_verification_required: bool,
    ) -> FullAccount {
        FullAccount {
            id: self.id.clone(),
            active_keyset_id: keyset_id.clone(),
            spending_keysets: HashMap::from([(keyset_id, spending_keyset)]),
            descriptor_backups_set: None,
            wallet_metadata_backup: None,
            spending_limit: None,
            transaction_verification_policy: None,
            application_auth_pubkey: Some(auth_keys.app_pubkey),
            hardware_auth_pubkey: auth_keys.hardware_pubkey,
            auth_keys: HashMap::from([(auth_keys_id.clone(), auth_keys)]),
            hardware_verification_required,
            common_fields: CommonAccountFields {
                active_auth_keys_id: auth_keys_id,
                onboarding_complete: false,
                ..self.common_fields.clone()
            },
        }
    }
}

impl From<LiteAccount> for Account {
    fn from(v: LiteAccount) -> Self {
        Self::Lite(v)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SoftwareAccount {
    #[serde(rename = "partition_key")]
    pub id: AccountId,
    // Spending Key Definitions
    pub active_key_definition_id: Option<KeyDefinitionId>,
    #[serde(default)]
    pub spending_key_definitions: HashMap<KeyDefinitionId, SpendingKeyDefinition>,
    pub application_auth_pubkey: PublicKey,
    #[serde(default)]
    pub auth_keys: HashMap<AuthKeysId, SoftwareAccountAuthKeys>,
    #[serde(flatten)]
    pub common_fields: CommonAccountFields,
}

impl SoftwareAccount {
    #[must_use]
    pub fn new(
        account_id: AccountId,
        active_auth_keys_id: AuthKeysId,
        auth: SoftwareAccountAuthKeys,
        properties: AccountProperties,
    ) -> Self {
        let now = OffsetDateTime::now_utc();
        let application_auth_pubkey = auth.app_pubkey;
        let recovery_auth_pubkey = Some(auth.recovery_pubkey);
        Self {
            id: account_id,
            active_key_definition_id: None,
            auth_keys: HashMap::from([(active_auth_keys_id.clone(), auth)]),
            spending_key_definitions: HashMap::new(),
            application_auth_pubkey,
            common_fields: CommonAccountFields {
                active_auth_keys_id,
                touchpoints: vec![],
                created_at: now,
                updated_at: now,
                properties,
                onboarding_complete: false,
                recovery_auth_pubkey,
                notifications_preferences_state: Default::default(),
                configured_privileged_action_delay_durations: Default::default(),
                delay_notify_period_secs: None,
                comms_verification_claims: Default::default(),
                notifications_triggers: Default::default(),
            },
        }
    }

    pub fn active_auth_keys(&self) -> Option<&SoftwareAccountAuthKeys> {
        self.auth_keys.get(&self.common_fields.active_auth_keys_id)
    }
}

impl From<SoftwareAccount> for Account {
    fn from(v: SoftwareAccount) -> Self {
        Self::Software(v)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
#[serde(untagged)]
pub enum Account {
    Full(FullAccount),
    Software(SoftwareAccount),
    Lite(LiteAccount),
}

impl Account {
    pub fn get_id(&self) -> &AccountId {
        match self {
            Account::Full(account) => &account.id,
            Account::Lite(account) => &account.id,
            Account::Software(account) => &account.id,
        }
    }

    pub fn get_common_fields(&self) -> &CommonAccountFields {
        match self {
            Self::Full(full_account) => &full_account.common_fields,
            Self::Lite(lite_account) => &lite_account.common_fields,
            Self::Software(software_account) => &software_account.common_fields,
        }
    }

    pub fn update(&self, common_fields: CommonAccountFields) -> Result<Account, AccountError> {
        self.get_common_fields()
            .properties
            .validate(&common_fields.properties)?;
        match self {
            Account::Full(full_account) => Ok(FullAccount {
                common_fields,
                ..full_account.to_owned()
            }
            .into()),
            Account::Lite(lite_account) => Ok(LiteAccount {
                common_fields,
                ..lite_account.to_owned()
            }
            .into()),
            Account::Software(software_account) => Ok(SoftwareAccount {
                common_fields,
                ..software_account.to_owned()
            }
            .into()),
        }
    }

    pub fn get_touchpoint_by_email_address(&self, email_address: String) -> Option<&Touchpoint> {
        self.get_common_fields().touchpoints.iter().find(
            |t| matches!(t, Touchpoint::Email { email_address: e, .. } if *e == email_address),
        )
    }

    pub fn get_touchpoint_by_phone_number(&self, phone_number: String) -> Option<&Touchpoint> {
        self.get_common_fields()
            .touchpoints
            .iter()
            .find(|t| matches!(t, Touchpoint::Phone { phone_number: p, .. } if *p == phone_number))
    }

    pub fn get_touchpoint_by_id(&self, touchpoint_id: TouchpointId) -> Option<&Touchpoint> {
        self.get_common_fields()
            .touchpoints
            .iter()
            .find(|t| t.id() == Some(&touchpoint_id))
    }

    pub fn get_push_touchpoint(&self) -> Option<&Touchpoint> {
        self.get_common_fields()
            .touchpoints
            .iter()
            .find(|t| matches!(t, Touchpoint::Push { .. }))
    }

    pub fn get_push_touchpoint_by_platform_and_device_token(
        &self,
        input_platform: TouchpointPlatform,
        input_device_token: String,
    ) -> Option<&Touchpoint> {
        self.get_common_fields()
            .touchpoints
            .iter()
            .find(|t| match t {
                Touchpoint::Push {
                    platform,
                    device_token,
                    ..
                } => *platform == input_platform && *device_token == input_device_token,
                _ => false,
            })
    }
}

impl From<&Account> for AccountType {
    fn from(account: &Account) -> Self {
        match account {
            Account::Full(_) => AccountType::Full,
            Account::Lite(_) => AccountType::Lite,
            Account::Software(_) => AccountType::Software,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Keyset {
    pub auth: FullAccountAuthKeys,
    pub spending: SpendingKeyset,
}

impl From<Account> for PubkeysToAccount {
    fn from(account: Account) -> Self {
        match account {
            Account::Full(full_account) => PubkeysToAccount {
                application_auth_pubkey: full_account.application_auth_pubkey,
                hardware_auth_pubkey: Some(full_account.hardware_auth_pubkey),
                recovery_auth_pubkey: full_account.common_fields.recovery_auth_pubkey,
                id: full_account.id,
            },
            Account::Lite(lite_account) => PubkeysToAccount {
                application_auth_pubkey: None,
                hardware_auth_pubkey: None,
                recovery_auth_pubkey: lite_account.common_fields.recovery_auth_pubkey,
                id: lite_account.id,
            },
            Account::Software(software_account) => PubkeysToAccount {
                application_auth_pubkey: Some(software_account.application_auth_pubkey),
                hardware_auth_pubkey: None,
                recovery_auth_pubkey: software_account.common_fields.recovery_auth_pubkey,
                id: software_account.id,
            },
        }
    }
}

#[serde_as]
#[derive(Serialize, Deserialize, Default, Debug, Clone, PartialEq, ToSchema)]
pub struct DescriptorBackupsSet {
    /// Wallet Key Encryption Key-wrapped Server Storage Encryption Key (SSEK)
    #[serde_as(as = "Base64")]
    pub wrapped_ssek: Vec<u8>,
    pub descriptor_backups: Vec<DescriptorBackup>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, ToSchema)]
pub struct WalletMetadataBackup {
    /// Wallet Key Encryption Key-wrapped Server Storage Encryption Key (SSEK)
    #[serde_as(as = "Base64")]
    pub wrapped_ssek: Vec<u8>,
    /// Server Storage Encryption Key-sealed wallet metadata snapshot.
    ///
    /// Writes are last-write-wins: the snapshot is client-encrypted and carries its own schema
    /// version internally, and clients are responsible for merging concurrent edits when they
    /// pull the latest backup. Concurrent PUTs within a single handler race are still serialized
    /// by the account repository's `updated_at` compare-and-swap.
    pub sealed_wallet_metadata_snapshot: String,
}

impl DescriptorBackupsSet {
    pub fn get_sealed_descriptor(&self, keyset_id: &KeysetId) -> Option<String> {
        self.descriptor_backups
            .iter()
            .find(|backup| backup.keyset_id() == keyset_id)
            .map(|backup| backup.sealed_descriptor().clone())
    }

    pub fn is_superset(&self, other: &DescriptorBackupsSet) -> bool {
        other
            .descriptor_backups
            .iter()
            .all(|backup| self.get_sealed_descriptor(backup.keyset_id()).is_some())
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, ToSchema, PartialEq)]
#[serde(untagged)]
pub enum DescriptorBackup {
    Private {
        keyset_id: KeysetId,
        /// Server storage encryption key-sealed descriptor
        sealed_descriptor: String,
        sealed_server_root_xpub: String,
    },
    Legacy {
        keyset_id: KeysetId,
        /// Server storage encryption key-sealed descriptor
        sealed_descriptor: String,
    },
}

impl DescriptorBackup {
    pub fn keyset_id(&self) -> &KeysetId {
        match self {
            DescriptorBackup::Legacy { keyset_id, .. } => keyset_id,
            DescriptorBackup::Private { keyset_id, .. } => keyset_id,
        }
    }

    pub fn sealed_descriptor(&self) -> &String {
        match self {
            DescriptorBackup::Legacy {
                sealed_descriptor, ..
            } => sealed_descriptor,
            DescriptorBackup::Private {
                sealed_descriptor, ..
            } => sealed_descriptor,
        }
    }

    pub fn is_legacy(&self) -> bool {
        matches!(self, DescriptorBackup::Legacy { .. })
    }

    pub fn is_private(&self) -> bool {
        matches!(self, DescriptorBackup::Private { .. })
    }
}

pub mod v2 {
    use bdk_utils::bdk::bitcoin::{secp256k1::PublicKey, Network};
    use serde::{Deserialize, Serialize};
    use utoipa::ToSchema;

    use crate::account::entities::HardwareType;

    #[derive(Deserialize, Serialize, Debug, ToSchema, Clone)]
    pub struct FullAccountAuthKeysInputV2 {
        pub app_pub: PublicKey,
        pub hardware_pub: PublicKey,
        pub recovery_pub: PublicKey,
        #[serde(default)]
        pub hardware_type: HardwareType,
    }

    #[derive(Deserialize, Serialize, Debug, PartialEq, ToSchema, Clone)]
    pub struct SpendingKeysetInputV2 {
        pub network: Network,
        pub app_pub: PublicKey,
        pub hardware_pub: PublicKey,
        /// `Option` because pre-enrollment clients don't send it; the
        /// server only requires it when the account is enrolled.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        pub hardware_attestation: Option<HardwareAttestation>,
    }

    /// Device-identity-key signature over `b"HWV1" || hardware_pub`, with
    /// the cert chain back to Silicon Labs needed to validate the signing
    /// key. `signature` and each `cert_chain` entry are base64 on the wire.
    #[serde_with::serde_as]
    #[derive(Deserialize, Serialize, Debug, PartialEq, ToSchema, Clone)]
    pub struct HardwareAttestation {
        #[serde_as(as = "serde_with::base64::Base64")]
        #[schema(value_type = String, format = Byte)]
        pub signature: Vec<u8>,
        #[serde_as(as = "Vec<serde_with::base64::Base64>")]
        #[schema(value_type = Vec<String>)]
        pub cert_chain: Vec<Vec<u8>>,
    }

    #[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
    pub struct UpgradeLiteAccountAuthKeysInputV2 {
        pub app_pub: PublicKey,
        pub hardware_pub: PublicKey,
        #[serde(default)]
        pub hardware_type: HardwareType,
    }
}

#[cfg(test)]
mod tests {

    use std::collections::HashMap;
    use std::str::FromStr;

    use bdk_utils::bdk::bitcoin::key::Secp256k1;
    use bdk_utils::bdk::bitcoin::secp256k1::rand::thread_rng;
    use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
    use bdk_utils::bdk::keys::DescriptorPublicKey;
    use time::OffsetDateTime;

    use super::{
        Account, AccountProperties, FullAccountAuthKeys, LiteAccount, LiteAccountAuthKeys,
        SoftwareAccount, SoftwareAccountAuthKeys, SpendingKeyset,
    };
    use crate::account::{
        bitcoin::Network,
        entities::{CommonAccountFields, DescriptorBackup, FullAccount, HardwareType},
        identifiers::{AccountId, AuthKeysId, KeysetId},
        spend_limit::SpendingLimit,
        spending::LegacyMultiSigSpendingKeyset,
    };

    #[test]
    fn test_is_spending_limit_active() {
        let unset_spending_limit_account = generate_default_test_account();
        let set_and_enabled_spending_limit_account = FullAccount {
            spending_limit: Some(SpendingLimit {
                active: true,
                ..Default::default()
            }),
            ..unset_spending_limit_account.clone()
        };
        let set_but_disabled_spending_limit_account = FullAccount {
            spending_limit: Some(SpendingLimit {
                active: false,
                ..Default::default()
            }),
            ..unset_spending_limit_account.clone()
        };

        assert!(!unset_spending_limit_account.is_spending_limit_active());
        assert!(set_and_enabled_spending_limit_account.is_spending_limit_active());
        assert!(!set_but_disabled_spending_limit_account.is_spending_limit_active())
    }

    #[test]
    // This may seem trivial, but because the Account enum is untagged, and because the account types
    // are essentially telescoping (FullAccount contains all the fields and more of SoftwareAccount, which
    // in turn contains all the fields and more of LiteAccount), and because we don't deny unknown fields
    // during serde deserialization, this test failed when the variants are defined in their original order
    // (Full, Lite, Software). This means that you could create a SoftwareAccount, save it to the db, and it
    // would deserialize as a LiteAccount.
    fn test_untagged_account_type_deserialization() {
        let account_id = AccountId::gen().unwrap();
        let active_auth_keys_id = AuthKeysId::gen().unwrap();

        let secp = Secp256k1::new();
        let public_key = secp.generate_keypair(&mut thread_rng()).1;

        let descriptor_public_key = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap();
        let properties = AccountProperties {
            is_test_account: false,
        };

        let full_account = FullAccount::new(
            account_id.clone(),
            KeysetId::gen().unwrap(),
            active_auth_keys_id.clone(),
            FullAccountAuthKeys {
                app_pubkey: public_key,
                hardware_pubkey: public_key,
                recovery_pubkey: Some(public_key),
                hardware_type: HardwareType::W1,
            },
            SpendingKeyset::LegacyMultiSig(LegacyMultiSigSpendingKeyset {
                network: Default::default(),
                app_dpub: descriptor_public_key.clone(),
                hardware_dpub: descriptor_public_key.clone(),
                server_dpub: descriptor_public_key,
            }),
            properties.clone(),
            false,
        );

        let lite_account = LiteAccount::new(
            account_id.clone(),
            active_auth_keys_id.clone(),
            LiteAccountAuthKeys {
                recovery_pubkey: public_key,
            },
            properties.clone(),
        );

        let software_account = SoftwareAccount::new(
            account_id,
            active_auth_keys_id,
            SoftwareAccountAuthKeys {
                app_pubkey: public_key,
                recovery_pubkey: public_key,
            },
            properties,
        );

        let accounts = [
            Account::Full(full_account),
            Account::Lite(lite_account),
            Account::Software(software_account),
        ];

        for account in accounts.iter() {
            let serialized = serde_json::to_string(account).unwrap();
            let deserialized: Account = serde_json::from_str(&serialized).unwrap();
            assert_eq!(account, &deserialized);
        }
    }

    #[test]
    fn test_active_descriptor_keyset() {
        // Ensure that the active descriptor keyset is correctly returned
        assert!(generate_default_test_account()
            .active_spending_keyset()
            .is_some());
    }

    #[test]
    fn test_inactive_descriptor_keysets() {
        let descriptor_public_key = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap();
        let active_keyset_id = KeysetId::gen().unwrap();

        let test_account = FullAccount {
            spending_keysets: HashMap::from([
                (
                    active_keyset_id.clone(),
                    SpendingKeyset::LegacyMultiSig(LegacyMultiSigSpendingKeyset {
                        network: Network::BitcoinMain,
                        app_dpub: descriptor_public_key.clone(),
                        hardware_dpub: descriptor_public_key.clone(),
                        server_dpub: descriptor_public_key.clone(),
                    }),
                ),
                (
                    KeysetId::gen().unwrap(),
                    SpendingKeyset::LegacyMultiSig(LegacyMultiSigSpendingKeyset {
                        network: Network::BitcoinMain,
                        app_dpub: descriptor_public_key.clone(),
                        hardware_dpub: descriptor_public_key.clone(),
                        server_dpub: descriptor_public_key.clone(),
                    }),
                ),
            ]),
            active_keyset_id,
            ..generate_default_test_account()
        };

        assert!(test_account.active_spending_keyset().is_some());
        assert_eq!(test_account.inactive_descriptor_keysets().len(), 1);
    }

    fn generate_default_test_account() -> FullAccount {
        let keyset_id = KeysetId::gen().unwrap();
        let auth_keys_id = AuthKeysId::gen().unwrap();
        let mut pubkey = [2; 33];
        pubkey[1] = 0xff;
        let public_key = PublicKey::from_slice(&pubkey).unwrap();

        let descriptor_public_key = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap();

        FullAccount {
            id: AccountId::gen().unwrap(),
            active_keyset_id: keyset_id.clone(),
            auth_keys: HashMap::from([(
                auth_keys_id.clone(),
                FullAccountAuthKeys {
                    app_pubkey: public_key,
                    hardware_pubkey: public_key,
                    recovery_pubkey: None,
                    hardware_type: HardwareType::W1,
                },
            )]),
            spending_keysets: HashMap::from([(
                keyset_id,
                SpendingKeyset::new_legacy_multi_sig(
                    Network::BitcoinMain,
                    descriptor_public_key.clone(),
                    descriptor_public_key.clone(),
                    descriptor_public_key,
                ),
            )]),
            descriptor_backups_set: None,
            wallet_metadata_backup: None,
            spending_limit: None,
            transaction_verification_policy: None,
            application_auth_pubkey: None,
            hardware_auth_pubkey: public_key,
            hardware_verification_required: false,
            common_fields: CommonAccountFields {
                active_auth_keys_id: auth_keys_id,
                touchpoints: vec![],
                created_at: OffsetDateTime::now_utc(),
                updated_at: OffsetDateTime::now_utc(),
                properties: Default::default(),
                onboarding_complete: false,
                recovery_auth_pubkey: None,
                notifications_preferences_state: Default::default(),
                configured_privileged_action_delay_durations: Default::default(),
                delay_notify_period_secs: None,
                comms_verification_claims: Default::default(),
                notifications_triggers: Default::default(),
            },
        }
    }

    #[test]
    fn test_active_hardware_type() {
        let account = generate_default_test_account();
        // Default test account has default auth keys → W1
        assert_eq!(account.active_hardware_type(), Ok(HardwareType::W1));

        // Swap auth keys to W3
        let auth_keys_id = account.common_fields.active_auth_keys_id.clone();
        let mut w3_auth_keys = account.auth_keys.clone();
        if let Some(keys) = w3_auth_keys.get_mut(&auth_keys_id) {
            keys.hardware_type = HardwareType::W3;
        }
        let w3_account = FullAccount {
            auth_keys: w3_auth_keys,
            ..account.clone()
        };
        assert_eq!(w3_account.active_hardware_type(), Ok(HardwareType::W3));

        // No auth keys → Err
        let empty_account = FullAccount {
            auth_keys: HashMap::new(),
            ..account
        };
        assert!(empty_account.active_hardware_type().is_err());
    }

    /// Records persisted before `hardware_verification_required` must still
    /// deserialize, with the field defaulting to `false`.
    #[test]
    fn test_full_account_deserializes_legacy_shape_without_hardware_verification_required() {
        let account = generate_default_test_account();
        let mut value = serde_json::to_value(&account).expect("serialize");
        value
            .as_object_mut()
            .expect("object")
            .remove("hardware_verification_required");

        let parsed: FullAccount = serde_json::from_value(value).expect("legacy shape deserializes");
        assert!(
            !parsed.hardware_verification_required,
            "absent field must default to false (account not yet enrolled)"
        );
    }

    #[test]
    fn test_untagged_descriptor_backup_deserialization() {
        let keyset_id = KeysetId::gen().unwrap();

        let descriptor_backups = [
            DescriptorBackup::Legacy {
                keyset_id: keyset_id.clone(),
                sealed_descriptor: "".to_string(),
            },
            DescriptorBackup::Private {
                keyset_id,
                sealed_descriptor: "".to_string(),
                sealed_server_root_xpub: "".to_string(),
            },
        ];

        for descriptor_backup in descriptor_backups.iter() {
            let serialized = serde_json::to_string(descriptor_backup).unwrap();
            let deserialized: DescriptorBackup = serde_json::from_str(&serialized).unwrap();
            assert_eq!(descriptor_backup, &deserialized);
        }
    }

    #[test]
    fn test_from_serial_w1_codes() {
        assert_eq!(
            HardwareType::from_serial("0000S203rest"),
            Some(HardwareType::W1)
        );
        assert_eq!(
            HardwareType::from_serial("0000S204rest"),
            Some(HardwareType::W1)
        );
        assert_eq!(
            HardwareType::from_serial("0000S233rest"),
            Some(HardwareType::W1)
        );
    }

    #[test]
    fn test_from_serial_w3_codes() {
        assert_eq!(
            HardwareType::from_serial("0000S271rest"),
            Some(HardwareType::W3)
        );
        assert_eq!(
            HardwareType::from_serial("0000S272rest"),
            Some(HardwareType::W3)
        );
    }

    #[test]
    fn test_from_serial_unrecognized() {
        assert_eq!(HardwareType::from_serial("0000S999rest"), None);
        assert_eq!(HardwareType::from_serial("0000XXXXrest"), None);
    }

    #[test]
    fn test_from_serial_too_short() {
        assert_eq!(HardwareType::from_serial("abc"), None);
        assert_eq!(HardwareType::from_serial(""), None);
        assert_eq!(HardwareType::from_serial("0000S20"), None); // only 7 chars
    }

    #[test]
    fn test_from_serial_test_constants() {
        assert_eq!(
            HardwareType::from_serial(HardwareType::TEST_SERIAL_W1),
            Some(HardwareType::W1)
        );
        assert_eq!(
            HardwareType::from_serial(HardwareType::TEST_SERIAL_W3),
            Some(HardwareType::W3)
        );
    }

    #[test]
    fn test_require_w1_serial() {
        assert!(HardwareType::require_w1_serial(Some(HardwareType::TEST_SERIAL_W1)).is_ok());
        assert!(HardwareType::require_w1_serial(Some(HardwareType::TEST_SERIAL_W3)).is_err());
        assert!(HardwareType::require_w1_serial(Some("0000XXXX")).is_err());
        assert!(HardwareType::require_w1_serial(None).is_err());
    }

    #[test]
    fn test_reject_w3_claiming_w1() {
        // Missing serial → error
        assert!(HardwareType::reject_w3_claiming_w1(None, HardwareType::W1).is_err());
        assert!(HardwareType::reject_w3_claiming_w1(None, HardwareType::W3).is_err());

        // W3 serial claiming W1 → error
        assert!(HardwareType::reject_w3_claiming_w1(
            Some(HardwareType::TEST_SERIAL_W3),
            HardwareType::W1
        )
        .is_err());

        // W3 serial claiming W3 → ok
        assert!(HardwareType::reject_w3_claiming_w1(
            Some(HardwareType::TEST_SERIAL_W3),
            HardwareType::W3
        )
        .is_ok());

        // W1 serial → ok regardless of claimed type
        assert!(HardwareType::reject_w3_claiming_w1(
            Some(HardwareType::TEST_SERIAL_W1),
            HardwareType::W1
        )
        .is_ok());
        assert!(HardwareType::reject_w3_claiming_w1(
            Some(HardwareType::TEST_SERIAL_W1),
            HardwareType::W3
        )
        .is_ok());

        // Unrecognized serial → error
        assert!(HardwareType::reject_w3_claiming_w1(Some("0000XXXX"), HardwareType::W1).is_err());
    }
}
