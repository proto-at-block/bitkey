use std::collections::HashMap;
use std::{env, env::VarError};

use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use bdk_utils::DescriptorKeyset;
use isocountry::CountryCode;
use serde::{Deserialize, Serialize};
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
pub struct FullAccountAuthKeysPayload {
    // TODO: [W-774] Update visibility of struct after migration
    pub app: PublicKey,
    pub hardware: PublicKey,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub recovery: Option<PublicKey>,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct LiteAccountAuthKeysPayload {
    // TODO: [W-774] Update visibility of struct after migration
    pub recovery: PublicKey,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct SoftwareAccountAuthKeysPayload {
    // TODO: [W-774] Update visibility of struct after migration
    pub app: PublicKey,
    pub recovery: PublicKey,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct UpgradeLiteAccountAuthKeysPayload {
    // TODO: [W-774] Update visibility of struct after migration
    pub app: PublicKey,
    pub hardware: PublicKey,
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema, Clone)]
pub struct SpendingKeysetRequest {
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
pub enum TransactionVerificationPolicy {
    Never,
    Threshold(Money),
    Always,
}

impl Default for TransactionVerificationPolicy {
    fn default() -> Self {
        Self::Never
    }
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
    pub descriptor_backups: HashMap<KeysetId, String>,
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
            descriptor_backups: HashMap::new(),
            spending_limit: None,
            transaction_verification_policy: None,
            application_auth_pubkey,
            hardware_auth_pubkey,
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
            .map_or(false, |limit| limit.active)
    }

    pub fn active_descriptor_keyset(&self) -> Option<DescriptorKeyset> {
        self.active_spending_keyset()
            .map(|keyset| keyset.clone().into())
    }

    pub fn inactive_descriptor_keysets(&self) -> Vec<DescriptorKeyset> {
        self.spending_keysets
            .iter()
            .filter_map(|keyset| {
                if *keyset.0 == self.active_keyset_id {
                    None
                } else {
                    Some(keyset.1.clone().into())
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
    ) -> FullAccount {
        FullAccount {
            id: self.id.clone(),
            active_keyset_id: keyset_id.clone(),
            spending_keysets: HashMap::from([(keyset_id, spending_keyset)]),
            descriptor_backups: HashMap::new(),
            spending_limit: None,
            transaction_verification_policy: None,
            application_auth_pubkey: Some(auth_keys.app_pubkey),
            hardware_auth_pubkey: auth_keys.hardware_pubkey,
            auth_keys: HashMap::from([(auth_keys_id.clone(), auth_keys)]),
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
        self.get_common_fields().touchpoints.iter().find(|t| {
            matches!(t, Touchpoint::Phone { id, .. } if *id == touchpoint_id)
                || matches!(t, Touchpoint::Email { id, .. } if *id == touchpoint_id)
        })
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

#[derive(Debug, Clone, Serialize, Deserialize, ToSchema)]
pub struct DescriptorBackup {
    pub keyset_id: KeysetId,
    pub sealed_descriptor: String,
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
        entities::{CommonAccountFields, FullAccount},
        identifiers::{AccountId, AuthKeysId, KeysetId},
        spend_limit::SpendingLimit,
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
            },
            SpendingKeyset {
                network: Default::default(),
                app_dpub: descriptor_public_key.clone(),
                hardware_dpub: descriptor_public_key.clone(),
                server_dpub: descriptor_public_key,
            },
            properties.clone(),
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
                    SpendingKeyset {
                        network: Network::BitcoinMain,
                        app_dpub: descriptor_public_key.clone(),
                        hardware_dpub: descriptor_public_key.clone(),
                        server_dpub: descriptor_public_key.clone(),
                    },
                ),
                (
                    KeysetId::gen().unwrap(),
                    SpendingKeyset {
                        network: Network::BitcoinMain,
                        app_dpub: descriptor_public_key.clone(),
                        hardware_dpub: descriptor_public_key.clone(),
                        server_dpub: descriptor_public_key.clone(),
                    },
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
        let mut pubkey = [2; 33];
        pubkey[1] = 0xff;

        let descriptor_public_key = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap();

        FullAccount {
            id: AccountId::gen().unwrap(),
            active_keyset_id: keyset_id.clone(),
            auth_keys: Default::default(),
            spending_keysets: HashMap::from([(
                keyset_id,
                SpendingKeyset::new(
                    Network::BitcoinMain,
                    descriptor_public_key.clone(),
                    descriptor_public_key.clone(),
                    descriptor_public_key,
                ),
            )]),
            descriptor_backups: HashMap::new(),
            spending_limit: None,
            transaction_verification_policy: None,
            application_auth_pubkey: None,
            hardware_auth_pubkey: PublicKey::from_slice(&pubkey).unwrap(),
            common_fields: CommonAccountFields {
                active_auth_keys_id: AuthKeysId::gen().unwrap(),
                touchpoints: vec![],
                created_at: OffsetDateTime::now_utc(),
                updated_at: OffsetDateTime::now_utc(),
                properties: Default::default(),
                onboarding_complete: false,
                recovery_auth_pubkey: None,
                notifications_preferences_state: Default::default(),
                configured_privileged_action_delay_durations: Default::default(),
                comms_verification_claims: Default::default(),
                notifications_triggers: Default::default(),
            },
        }
    }
}
