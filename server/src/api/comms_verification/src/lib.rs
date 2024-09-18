pub mod error;

use std::collections::HashSet;

use account::entities::{AccountProperties, CommsVerificationClaim};
use account::service::{PutCommsVerificationClaimInput, Service as AccountService};
use account::{
    entities::{CommsVerificationScope, CommsVerificationStatus},
    service::FetchOrCreateCommsVerificationClaimInput,
};
use argon2::password_hash::SaltString;
use argon2::{Argon2, PasswordHash, PasswordHasher, PasswordVerifier};
use error::CommsVerificationError;

use notification::entities::NotificationTouchpoint;
use notification::payloads::comms_verification::TemplateType;
use notification::service::SendNotificationInput;
use notification::{
    payloads::comms_verification::CommsVerificationPayload,
    service::Service as NotificationService, NotificationPayloadBuilder, NotificationPayloadType,
};
use rand::rngs::OsRng;
use rand::Rng;
use serde::Deserialize;
use strum_macros::EnumString;
use time::{Duration, OffsetDateTime};
use types::account::identifiers::AccountId;

pub const TEST_CODE: &str = "123456";
const TEST_EXPIRATION_SECS: i64 = 30;

const CODE_MAX_VALUE: i64 = 999999;
const EXPIRATION_SECS: i64 = 300; // 5 mins
const RESEND_SECS: i64 = 10;

#[derive(Deserialize, EnumString, Clone)]
#[serde(rename_all = "lowercase")]
pub enum CommsVerificationMode {
    Test,
    Environment,
}

#[derive(Clone)]
pub struct Service {
    pub account_service: AccountService,
    pub notification_service: NotificationService,
}

pub struct ConsumeVerificationForScopeInput<'a> {
    pub account_id: &'a AccountId,
    pub scope: CommsVerificationScope,
}

pub struct InitiateVerificationForScopeInput<'a> {
    pub account_id: &'a AccountId,
    pub account_properties: &'a AccountProperties,
    pub scope: CommsVerificationScope,
    pub only_touchpoints: Option<HashSet<NotificationTouchpoint>>,
}

pub struct VerifyForScopeInput<'a> {
    pub account_id: &'a AccountId,
    pub scope: CommsVerificationScope,
    pub code: String,
    pub duration: Duration,
}

impl Service {
    pub async fn new(
        account_service: AccountService,
        notification_service: NotificationService,
    ) -> Self {
        Self {
            account_service,
            notification_service,
        }
    }

    pub async fn consume_verification_for_scope(
        &self,
        input: ConsumeVerificationForScopeInput<'_>,
    ) -> Result<(), CommsVerificationError> {
        let claim = self
            .account_service
            .fetch_or_create_comms_verification_claim(FetchOrCreateCommsVerificationClaimInput {
                account_id: input.account_id,
                scope: input.scope.to_owned(),
            })
            .await?;

        if !matches!(claim.status, CommsVerificationStatus::Verified { expires_at } if OffsetDateTime::now_utc() < expires_at)
        {
            return Err(CommsVerificationError::StatusMismatch);
        }

        self.account_service
            .put_comms_verification_claim(PutCommsVerificationClaimInput {
                account_id: input.account_id,
                claim: CommsVerificationClaim::new(input.scope, None),
            })
            .await?;

        Ok(())
    }

    pub async fn initiate_verification_for_scope(
        &self,
        input: InitiateVerificationForScopeInput<'_>,
    ) -> Result<(), CommsVerificationError> {
        let claim = self
            .account_service
            .fetch_or_create_comms_verification_claim(FetchOrCreateCommsVerificationClaimInput {
                account_id: input.account_id,
                scope: input.scope.to_owned(),
            })
            .await?;

        let now = OffsetDateTime::now_utc();

        // Skip sending code if sent too recently
        if let CommsVerificationStatus::Pending { sent_at, .. } = claim.status {
            if now < sent_at + Duration::seconds(RESEND_SECS) {
                return Ok(());
            }
        }

        let account_properties = &input.account_properties;
        let code = self.gen_code(account_properties);

        self.send_code(
            input.account_id.clone(),
            input.scope.clone(),
            code.to_owned(),
            input.only_touchpoints,
        )
        .await?;

        let code_hash = Argon2::default()
            .hash_password(code.as_bytes(), &SaltString::generate(&mut OsRng))?
            .to_string();

        self.account_service
            .put_comms_verification_claim(PutCommsVerificationClaimInput {
                account_id: input.account_id,
                claim: CommsVerificationClaim::new(
                    input.scope,
                    Some(CommsVerificationStatus::Pending {
                        code_hash,
                        sent_at: now,
                        expires_at: self.gen_expiration(account_properties, now),
                    }),
                ),
            })
            .await?;

        Ok(())
    }

    pub async fn verify_for_scope(
        &self,
        input: VerifyForScopeInput<'_>,
    ) -> Result<(), CommsVerificationError> {
        let claim = self
            .account_service
            .fetch_or_create_comms_verification_claim(FetchOrCreateCommsVerificationClaimInput {
                account_id: input.account_id,
                scope: input.scope.to_owned(),
            })
            .await?;

        let now = OffsetDateTime::now_utc();

        match claim.status {
            CommsVerificationStatus::Unverified => Err(CommsVerificationError::StatusTransition),
            CommsVerificationStatus::Verified { .. } => {
                Err(CommsVerificationError::StatusTransition)
            }
            CommsVerificationStatus::Pending {
                code_hash,
                sent_at: _,
                expires_at,
            } => {
                if now >= expires_at {
                    return Err(CommsVerificationError::CodeExpired);
                }

                Argon2::default()
                    .verify_password(input.code.as_bytes(), &PasswordHash::new(&code_hash)?)
                    .map_err(|e| match e {
                        argon2::password_hash::Error::Password => {
                            CommsVerificationError::CodeMismatch
                        }
                        _ => e.into(),
                    })?;

                let claim_expires_at = now + input.duration;
                self.account_service
                    .put_comms_verification_claim(PutCommsVerificationClaimInput {
                        account_id: input.account_id,
                        claim: CommsVerificationClaim::new(
                            input.scope,
                            Some(CommsVerificationStatus::Verified {
                                expires_at: claim_expires_at,
                            }),
                        ),
                    })
                    .await?;

                Ok(())
            }
        }
    }

    fn gen_code(&self, account_properties: &AccountProperties) -> String {
        match account_properties.is_test_account {
            true => TEST_CODE.to_owned(),
            false => {
                format!("{:0>6}", rand::thread_rng().gen_range(0..=CODE_MAX_VALUE))
            }
        }
    }

    fn gen_expiration(
        &self,
        account_properties: &AccountProperties,
        start: OffsetDateTime,
    ) -> OffsetDateTime {
        match account_properties.is_test_account {
            true => start + Duration::seconds(TEST_EXPIRATION_SECS),
            false => start + Duration::seconds(EXPIRATION_SECS),
        }
    }

    async fn send_code(
        &self,
        account_id: AccountId,
        scope: CommsVerificationScope,
        code: String,
        only_touchpoints: Option<HashSet<NotificationTouchpoint>>,
    ) -> Result<(), CommsVerificationError> {
        let payload = &NotificationPayloadBuilder::default()
            .comms_verification_payload(Some(CommsVerificationPayload {
                account_id: account_id.to_owned(),
                code,
                template_type: match scope {
                    CommsVerificationScope::AddTouchpointId(_) => TemplateType::Onboarding,
                    CommsVerificationScope::DelayNotifyActor(_) => TemplateType::Recovery,
                },
            }))
            .build()?;

        self.notification_service
            .send_notification(SendNotificationInput {
                account_id: &account_id,
                payload_type: NotificationPayloadType::CommsVerification,
                payload,
                only_touchpoints,
            })
            .await?;

        Ok(())
    }
}
