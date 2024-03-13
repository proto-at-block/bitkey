use std::fmt::{self, Display, Formatter};
use std::str::FromStr;

use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use ulid::Ulid;
use urn::Urn;

use crate::account::identifiers::AccountId;
use crate::notification::{NotificationCategory, NotificationChannel};

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct ConsentId(urn::Urn);

impl FromStr for ConsentId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for ConsentId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for ConsentId {
    fn namespace() -> &'static str {
        "consent"
    }
}

impl ConsentId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for ConsentId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
pub struct ConsentCommonFields {
    #[serde(rename = "partition_key")]
    pub account_id: AccountId,
    #[serde(rename = "sort_key")]
    pub consent_id: ConsentId,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
pub enum NotificationConsentAction {
    OptIn,
    OptOut,
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
pub struct NotificationConsent {
    #[serde(flatten)]
    pub common_fields: ConsentCommonFields,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub email_address: Option<String>,
    pub notification_category: NotificationCategory,
    pub notification_channel: NotificationChannel,
    pub action: NotificationConsentAction,
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
pub struct OnboardingTosAcceptanceConsent {
    #[serde(flatten)]
    pub common_fields: ConsentCommonFields,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub email_address: Option<String>,
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
#[serde(tag = "_Consent_type")]
pub enum Consent {
    Notification(NotificationConsent),
    OnboardingTosAcceptance(OnboardingTosAcceptanceConsent),
}

impl Consent {
    pub fn new_notification_opt_in(
        account_id: &AccountId,
        email_address: Option<String>,
        notification_category: NotificationCategory,
        notification_channel: NotificationChannel,
    ) -> Self {
        let now = OffsetDateTime::now_utc();
        Consent::Notification(NotificationConsent {
            common_fields: ConsentCommonFields {
                account_id: account_id.to_owned(),
                consent_id: ConsentId::gen().unwrap(),
                created_at: now,
                updated_at: now,
            },
            email_address,
            notification_category,
            notification_channel,
            action: NotificationConsentAction::OptIn,
        })
    }

    pub fn new_notification_opt_out(
        account_id: &AccountId,
        email_address: Option<String>,
        notification_category: NotificationCategory,
        notification_channel: NotificationChannel,
    ) -> Self {
        let now = OffsetDateTime::now_utc();
        Consent::Notification(NotificationConsent {
            common_fields: ConsentCommonFields {
                account_id: account_id.to_owned(),
                consent_id: ConsentId::gen().unwrap(),
                created_at: now,
                updated_at: now,
            },
            email_address,
            notification_category,
            notification_channel,
            action: NotificationConsentAction::OptOut,
        })
    }

    pub fn new_onboarding_tos_acceptance(
        account_id: &AccountId,
        email_address: Option<String>,
    ) -> Self {
        let now = OffsetDateTime::now_utc();
        Consent::OnboardingTosAcceptance(OnboardingTosAcceptanceConsent {
            common_fields: ConsentCommonFields {
                account_id: account_id.to_owned(),
                consent_id: ConsentId::gen().unwrap(),
                created_at: now,
                updated_at: now,
            },
            email_address,
        })
    }
}

impl Consent {
    pub fn get_common_fields(&self) -> &ConsentCommonFields {
        match self {
            Consent::Notification(notification_consent) => &notification_consent.common_fields,
            Consent::OnboardingTosAcceptance(onboarding_tos_acceptance_consent) => {
                &onboarding_tos_acceptance_consent.common_fields
            }
        }
    }

    pub fn with_common_fields(&self, common_fields: ConsentCommonFields) -> Self {
        match self {
            Consent::Notification(notification_consent) => {
                Consent::Notification(NotificationConsent {
                    common_fields,
                    ..notification_consent.to_owned()
                })
            }
            Consent::OnboardingTosAcceptance(onboarding_tos_acceptance_consent) => {
                Consent::OnboardingTosAcceptance(OnboardingTosAcceptanceConsent {
                    common_fields,
                    ..onboarding_tos_acceptance_consent.to_owned()
                })
            }
        }
    }
}
