use std::{fmt, str::FromStr};

use authn_authz::key_claims::KeyClaims;
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, Duration, OffsetDateTime};
use types::account::entities::{Factor, FullAccount};
use types::account::identifiers::{AccountId, AuthKeysId};
use utoipa::ToSchema;

use crate::error::RecoveryError;

#[derive(Deserialize, Serialize, Clone, Copy, Debug, PartialEq, Eq, ToSchema)]
pub enum RecoveryType {
    DelayAndNotify,
}

impl fmt::Display for RecoveryType {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            RecoveryType::DelayAndNotify => write!(f, "DelayAndNotify"),
        }
    }
}

impl FromStr for RecoveryType {
    type Err = ();

    fn from_str(input: &str) -> Result<RecoveryType, Self::Err> {
        match input {
            "DelayAndNotify" => Ok(RecoveryType::DelayAndNotify),
            _ => Err(()),
        }
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, ToSchema)]
pub struct RecoveryRequirements {
    pub delay_notify_requirements: Option<DelayNotifyRequirements>,
}

#[derive(Deserialize, Serialize, Clone, Debug, ToSchema)]
pub struct DelayNotifyRequirements {
    pub lost_factor: Factor,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

#[derive(Deserialize, Serialize, Clone, Debug, ToSchema)]
pub struct RecoveryAction {
    pub delay_notify_action: Option<DelayNotifyRecoveryAction>,
}

#[derive(Deserialize, Serialize, Clone, Debug, ToSchema)]
pub struct DelayNotifyRecoveryAction {
    pub destination: RecoveryDestination,
}

#[derive(Debug, Clone, Serialize, Deserialize, ToSchema, PartialEq)]
#[serde(tag = "type")]
pub struct RecoveryDestination {
    // Source
    // These ids represent the state of the account upon creating the Recovery
    pub source_auth_keys_id: AuthKeysId,
    // Destination
    // These keys will be a part of the state of the account after completing the Recovery
    pub app_auth_pubkey: PublicKey,
    pub hardware_auth_pubkey: PublicKey,
    #[serde(default)]
    pub recovery_auth_pubkey: Option<PublicKey>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum RecoveryStatus {
    Pending,
    Complete,
    Canceled,
    CanceledInContest,
}

pub type WalletRecoveryCompositeKey = (AccountId, OffsetDateTime);

#[derive(Serialize, Deserialize, Clone, Debug, ToSchema)]
pub struct WalletRecovery {
    pub account_id: AccountId,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    pub recovery_status: RecoveryStatus,
    pub recovery_type: RecoveryType,
    pub recovery_type_time: String,
    pub requirements: RecoveryRequirements,
    pub recovery_action: RecoveryAction,
    #[serde(default)]
    pub destination_app_auth_pubkey: Option<PublicKey>,
    #[serde(default)]
    pub destination_hardware_auth_pubkey: Option<PublicKey>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub destination_recovery_auth_pubkey: Option<PublicKey>,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl WalletRecovery {
    pub fn get_lost_factor(&self) -> Option<Factor> {
        let requirements = self.requirements.delay_notify_requirements.as_ref();
        if let Some(requirements) = requirements {
            return Some(requirements.lost_factor);
        }

        None
    }
}

pub(crate) enum ToActorStrategy {
    // Prefer the non-lost factor, but fallback to the lost factor.
    // This is for scenarios in which both key proofs can be provided,
    //   (only possible for cancellation at the moment), in which we
    //   prefer the non-lost factor because that's the "happy path,"
    //   whereas acting as the lost factor represents a contest.
    PreferNonLostFactor(Factor),
    // Whichever, but enforce that there's only one.
    ExclusiveOr,
}

pub(crate) trait ToActor {
    // Maps a key proof to a single acting Factor based on strategy
    fn to_actor(&self, strategy: ToActorStrategy) -> Result<Factor, RecoveryError>;
}

impl ToActor for KeyClaims {
    fn to_actor(&self, strategy: ToActorStrategy) -> Result<Factor, RecoveryError> {
        match strategy {
            ToActorStrategy::PreferNonLostFactor(lost_factor) => {
                match lost_factor {
                    Factor::App => {
                        if self.hw_signed {
                            return Ok(Factor::Hw);
                        } else if self.app_signed {
                            return Ok(Factor::App);
                        }
                    }
                    Factor::Hw => {
                        if self.app_signed {
                            return Ok(Factor::App);
                        } else if self.hw_signed {
                            return Ok(Factor::Hw);
                        }
                    }
                }
                Err(RecoveryError::KeyProofRequired)
            }
            ToActorStrategy::ExclusiveOr => {
                if self.app_signed && self.hw_signed {
                    Err(RecoveryError::UnexpectedKeyProof)
                } else if self.app_signed {
                    Ok(Factor::App)
                } else if self.hw_signed {
                    Ok(Factor::Hw)
                } else {
                    Err(RecoveryError::KeyProofRequired)
                }
            }
        }
    }
}

pub trait RecoveryValuesPerAccountType {
    fn recovery_delay_period(&self) -> Duration;
}

impl RecoveryValuesPerAccountType for FullAccount {
    fn recovery_delay_period(&self) -> Duration {
        match self.common_fields.properties.is_test_account {
            true => Duration::seconds(20),
            false => Duration::days(7),
        }
    }
}
