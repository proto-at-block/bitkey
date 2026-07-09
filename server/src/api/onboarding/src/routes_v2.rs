use std::str::FromStr;

use account::attestation_verifier::{test_fixture, verify_hardware_attestation};
use account::hardware_verification::{
    hardware_verification_enforced, should_enroll_in_hardware_verification, ENROLLMENT_FLIPPED,
    TRIGGER_KEY, TRIGGER_ONBOARDING,
};
use account::service::{
    CreateAccountAndKeysetsInput, CreateInactiveSpendingKeysetInput, FetchAccountInput,
    Service as AccountService, UpgradeLiteAccountToFullAccountInput,
};
use axum::{
    extract::{Path, State},
    Json,
};
use bdk_utils::bdk::{bitcoin::secp256k1::PublicKey, keys::DescriptorPublicKey};
use errors::{ApiError, ErrorCode, RouteError};
use experimentation::claims::ExperimentationClaims;
use external_identifier::ExternalIdentifier;
use feature_flags::{
    flag::{evaluate_flag_value, Flag},
    service::Service as FeatureFlagsService,
};
use http_server::middlewares::identifier_generator::IdentifierGenerator;
use instrumentation::metrics::KeyValue;
use instrumentation::middleware::HardwareSerialHeader;
use notification::clients::iterable::IterableClient;
use recovery::{
    entities::{RecoveryStatus, RecoveryType},
    repository::RecoveryRepository,
};
use repository::public_key::{KeyType, PublicKeyRepository};
use serde::{Deserialize, Serialize};
use time::{Duration, OffsetDateTime};
use tracing::{error, instrument};
use types::account::{
    bitcoin::to_wsm_bitcoin_network,
    entities::{
        v2::{
            FullAccountAuthKeysInputV2, HardwareAttestation, SpendingKeysetInputV2,
            UpgradeLiteAccountAuthKeysInputV2,
        },
        Account, FullAccount, HardwareType, Keyset, LiteAccount,
    },
    identifiers::{AccountId, AuthKeysId, KeysetId},
    keys::FullAccountAuthKeys,
    spending::{AttestedHardwareSerial, SpendingKeyset},
};
use userpool::userpool::UserPoolService;
use utoipa::ToSchema;
use wsm_rust_client::{SigningService, WsmClient};

use crate::{
    account_validation::{
        error::AccountValidationError, AccountValidation, AccountValidationRequest,
    },
    emit_keyset_created,
    metrics::PRIVATE_VALUE,
    routes::Config,
    upsert_account_iterable_user,
};

/// Opportunistic attestation collection.
///
/// - If `required` and no attestation is supplied → 400
///   `HARDWARE_ATTESTATION_REQUIRED`.
/// - If `required` and the supplied attestation fails to verify → 400
///   `HARDWARE_ATTESTATION_INVALID`.
/// - If not `required` and no attestation is supplied → `None`.
/// - If not `required` and the supplied attestation fails to verify →
///   log a warning and persist `None`. We accept opportunistic
///   attestations from gate-not-yet-flipped clients so we can populate
///   the persistence path before enforcement, without breaking clients
///   that ship buggy attestation code in the meantime.
///
/// Test-account bypass: when `is_test_account` is true and the supplied
/// attestation matches the magic fixture in [`test_fixture`], skip the
/// real verifier and persist the canned serial. See the module docs for
/// the risk accepted by this bypass.
fn collect_attested_hardware_serial(
    required: bool,
    request_attestation: Option<&HardwareAttestation>,
    hardware_pub: &PublicKey,
    is_test_account: bool,
) -> Result<Option<AttestedHardwareSerial>, ApiError> {
    let Some(attestation) = request_attestation else {
        return if required {
            Err(ApiError::Specific {
                code: ErrorCode::HardwareAttestationRequired,
                detail: Some(
                    "hardware_attestation is required on this account's keyset creations"
                        .to_string(),
                ),
                field: Some("spend.hardware_attestation".to_string()),
            })
        } else {
            Ok(None)
        };
    };
    if is_test_account && test_fixture::matches(&attestation.signature, &attestation.cert_chain) {
        return Ok(Some(AttestedHardwareSerial::Pending(
            test_fixture::TEST_ACCOUNT_ATTESTED_SERIAL.to_string(),
        )));
    }
    match verify_hardware_attestation(
        &attestation.cert_chain,
        &attestation.signature,
        hardware_pub,
    ) {
        Ok(serial) => Ok(Some(AttestedHardwareSerial::Pending(serial))),
        Err(e) if required => Err(ApiError::Specific {
            code: ErrorCode::HardwareAttestationInvalid,
            detail: Some(e.to_string()),
            field: Some("spend.hardware_attestation".to_string()),
        }),
        Err(e) => {
            tracing::warn!("opportunistic attestation failed verification: {e}");
            Ok(None)
        }
    }
}

/// Promote `Pending(s)` to `Verified(s)` when the active keyset is
/// already `Verified(s)` — same physical Bitkey, user has already OOBA'd
/// for this serial, so skip the re-prompt at the next sweep. Other
/// inputs pass through unchanged.
fn inherit_verified_status_from_active_keyset(
    new_attestation: Option<AttestedHardwareSerial>,
    account: &FullAccount,
) -> Option<AttestedHardwareSerial> {
    let Some(AttestedHardwareSerial::Pending(ref new_serial)) = new_attestation else {
        return new_attestation;
    };
    let active_attested = account
        .active_spending_keyset()
        .and_then(|k| k.optional_private_multi_sig())
        .and_then(|p| p.attested_hardware_serial.as_ref());
    if let Some(AttestedHardwareSerial::Verified(active_serial)) = active_attested {
        if active_serial == new_serial {
            return Some(AttestedHardwareSerial::Verified(new_serial.clone()));
        }
    }
    new_attestation
}

const PRIVATE_KEYSET_CREATION_BLOCKED: Flag<'_, bool> =
    Flag::new("f8e-private-keyset-creation-blocked");

const RECENT_RECOVERY_GRACE: Duration = Duration::minutes(30);

#[derive(Deserialize, Serialize, Debug, ToSchema)]
pub struct CreateAccountRequestV2 {
    pub auth: FullAccountAuthKeysInputV2,
    pub spend: SpendingKeysetInputV2,
    #[serde(default)]
    pub is_test_account: bool,
}

impl From<&CreateAccountRequestV2> for AccountValidationRequest {
    fn from(value: &CreateAccountRequestV2) -> Self {
        AccountValidationRequest::CreateFullAccountV2 {
            auth: value.auth.clone(),
            spend: value.spend.clone(),
            is_test_account: value.is_test_account,
        }
    }
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema)]
pub struct CreateAccountResponseV2 {
    pub account_id: AccountId,
    pub keyset_id: KeysetId,
    pub server_pub: PublicKey,
    pub server_pub_integrity_sig: String,
}

impl TryFrom<&Account> for CreateAccountResponseV2 {
    type Error = ApiError;

    fn try_from(value: &Account) -> Result<Self, Self::Error> {
        match value {
            Account::Full(full_account) => {
                let keyset = full_account
                    .active_spending_keyset()
                    .ok_or(RouteError::NoActiveSpendKeyset)?
                    .private_multi_sig_or(RouteError::ConflictingKeysetType)?;
                Ok(CreateAccountResponseV2 {
                    account_id: full_account.id.clone(),
                    keyset_id: full_account.active_keyset_id.clone(),
                    server_pub: keyset.server_pub,
                    server_pub_integrity_sig: keyset.server_pub_integrity_sig.clone(),
                })
            }
            _ => Err(ApiError::GenericInternalApplicationError(
                "Unexpected account type".to_string(),
            )),
        }
    }
}

#[instrument(
    fields(account_id),
    skip(
        account_service,
        recovery_repository,
        id_generator,
        user_pool_service,
        config,
        iterable_client,
        public_key_repository,
        feature_flags_service,
        request,
    )
)]
#[utoipa::path(
    post,
    path = "/api/v2/accounts",
    request_body = CreateAccountRequestV2,
    responses(
        (status = 200, description = "Account was created", body=CreateAccountResponseV2),
        (status = 400, description = "Input validation failed")
    ),
)]
pub async fn create_account_v2(
    State(account_service): State<AccountService>,
    State(recovery_repository): State<RecoveryRepository>,
    State(public_key_repository): State<PublicKeyRepository>,
    State(wsm_client): State<WsmClient>,
    State(id_generator): State<IdentifierGenerator>,
    State(user_pool_service): State<UserPoolService>,
    State(config): State<Config>,
    State(iterable_client): State<IterableClient>,
    State(feature_flags_service): State<FeatureFlagsService>,
    hw_serial: HardwareSerialHeader,
    mut experimentation_claims: ExperimentationClaims,
    Json(request): Json<CreateAccountRequestV2>,
) -> Result<Json<CreateAccountResponseV2>, ApiError> {
    // Block W3 hardware claiming to be W1
    HardwareType::reject_w3_claiming_w1(hw_serial.as_deref(), request.auth.hardware_type)
        .map_err(|e| ApiError::GenericForbidden(e.to_string()))?;
    if let Some(v) = AccountValidation::default()
        .validate(
            AccountValidationRequest::from(&request),
            &config,
            &account_service,
            &recovery_repository,
            &public_key_repository,
        )
        .await?
    {
        return Ok(Json(CreateAccountResponseV2::try_from(
            &v.existing_account,
        )?));
    }

    let account_id = AccountId::new(id_generator.gen_account_id()).map_err(|e| {
        let msg = "Failed to generate account id";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;

    // provide the generated account ID once we have it
    tracing::Span::current().record("account_id", account_id.to_string());

    // Resolve enrollment + verify attestation before any durable side
    // effects below (public_keys row, Cognito users, WSM root key).
    // Override LD context with the just-generated `account_id` since the
    // request is unauthenticated and the extractor has no JWT to read.
    experimentation_claims.hardware_type = Some(request.auth.hardware_type.to_string());
    let hardware_verification_required = should_enroll_in_hardware_verification(
        &feature_flags_service,
        &experimentation_claims.overridden_account_context_key(account_id.clone()),
    );
    let attested_hardware_serial = collect_attested_hardware_serial(
        hardware_verification_required,
        request.spend.hardware_attestation.as_ref(),
        &request.spend.hardware_pub,
        request.is_test_account,
    )?;

    // Record hw auth pubkey in public_keys table before any external side
    // effects (Cognito, WSM).
    if !public_key_repository
        .persist_public_key(
            &request.auth.hardware_pub.to_string(),
            &account_id,
            KeyType::HardwareAuth,
        )
        .await?
    {
        return Err(AccountValidationError::HwAuthPubkeyReuseAccount.into());
    }

    // Create Cognito users
    user_pool_service
        .create_or_update_account_users_if_necessary(
            &account_id,
            Some(request.auth.app_pub),
            Some(request.auth.hardware_pub),
            Some(request.auth.recovery_pub),
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create new accounts in Cognito";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    // Generate a server key in WSM
    let keyset_id = KeysetId::new(id_generator.gen_spending_keyset_id())
        .map_err(RouteError::InvalidIdentifier)?;

    let key = wsm_client
        .create_root_key(
            &keyset_id.to_string(),
            to_wsm_bitcoin_network(request.spend.network),
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create new key in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    // Attempt to parse the DescriptorPublicKey from the xpub string
    let server_dpub = DescriptorPublicKey::from_str(&key.xpub).map_err(|e| {
        let msg = "Failed to parse server dpub from WSM";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;
    let server_pub = parse_public_key(server_dpub)?;

    let auth_key_id = AuthKeysId::new(id_generator.gen_spending_keyset_id())
        .map_err(RouteError::InvalidIdentifier)?;

    let input = CreateAccountAndKeysetsInput {
        account_id,
        network: request.spend.network.into(),
        keyset_id,
        auth_key_id: auth_key_id.clone(),
        keyset: Keyset {
            auth: FullAccountAuthKeys::new(
                request.auth.app_pub,
                request.auth.hardware_pub,
                Some(request.auth.recovery_pub),
                request.auth.hardware_type,
            ),
            spending: SpendingKeyset::new_private_multi_sig(
                request.spend.network.into(),
                request.spend.app_pub,
                request.spend.hardware_pub,
                server_pub,
                key.pub_sig.clone(),
                attested_hardware_serial,
            ),
        },
        is_test_account: request.is_test_account,
        hardware_verification_required,
    };
    let account = account_service.create_account_and_keysets(input).await?;

    if hardware_verification_required {
        ENROLLMENT_FLIPPED.add(1, &[KeyValue::new(TRIGGER_KEY, TRIGGER_ONBOARDING)]);
    }

    emit_keyset_created(PRIVATE_VALUE);

    // Attempt to create account Iterable user early, but don't fail the account creation if
    // this fails. We upsert the users later when they're needed anyway; this is an optimization
    // to avoid added latency or errors waiting for Iterable user database consistency on first use.
    upsert_account_iterable_user(&iterable_client, &account.id, None, None)
        .await
        .map_or_else(
            |e| {
                error!("Failed to create account Iterable user: {e}");
            },
            |_| (),
        );

    Ok(Json(CreateAccountResponseV2 {
        account_id: account.id,
        keyset_id: account.active_keyset_id,
        server_pub,
        server_pub_integrity_sig: key.pub_sig,
    }))
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema)]
pub struct UpgradeAccountRequestV2 {
    pub auth: UpgradeLiteAccountAuthKeysInputV2,
    pub spend: SpendingKeysetInputV2,
}

impl From<(&LiteAccount, &UpgradeAccountRequestV2)> for AccountValidationRequest {
    fn from(value: (&LiteAccount, &UpgradeAccountRequestV2)) -> Self {
        AccountValidationRequest::UpgradeAccountV2 {
            auth: value.1.auth.to_owned(),
            is_test_account: value.0.common_fields.properties.is_test_account,
            spend_network: value.1.spend.network.into(),
        }
    }
}

#[instrument(
    fields(account_id),
    skip(
        account_service,
        recovery_repository,
        id_generator,
        user_pool_service,
        config,
        public_key_repository,
        feature_flags_service,
        experimentation_claims,
    )
)]
#[utoipa::path(
    post,
    path = "/api/v2/accounts/{account_id}/upgrade",
    request_body = UpgradeAccountRequestV2,
    responses(
        (status = 200, description = "Account was upgraded to a full account", body=CreateKeysetResponseV2),
        (status = 400, description = "Input validation failed")
    ),
)]
pub async fn upgrade_account_v2(
    State(account_service): State<AccountService>,
    State(recovery_repository): State<RecoveryRepository>,
    State(public_key_repository): State<PublicKeyRepository>,
    State(wsm_client): State<WsmClient>,
    State(id_generator): State<IdentifierGenerator>,
    State(user_pool_service): State<UserPoolService>,
    State(config): State<Config>,
    State(feature_flags_service): State<FeatureFlagsService>,
    Path(account_id): Path<AccountId>,
    hw_serial: HardwareSerialHeader,
    mut experimentation_claims: ExperimentationClaims,
    Json(request): Json<UpgradeAccountRequestV2>,
) -> Result<Json<CreateKeysetResponseV2>, ApiError> {
    // Block W3 hardware claiming to be W1
    HardwareType::reject_w3_claiming_w1(hw_serial.as_deref(), request.auth.hardware_type)
        .map_err(|e| ApiError::GenericForbidden(e.to_string()))?;
    let existing_account = &account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let lite_account = match existing_account {
        Account::Lite(lite_account) => lite_account,
        Account::Full(full_account) => {
            let Some(active_auth_keys) = full_account.active_auth_keys() else {
                return Err(RouteError::NoActiveAuthKeys)?;
            };

            let Some(active_spending_keyset) = full_account.active_spending_keyset() else {
                return Err(RouteError::NoActiveSpendKeyset)?;
            };

            let active_spending_keyset =
                active_spending_keyset.private_multi_sig_or(RouteError::ConflictingKeysetType)?;

            if active_auth_keys.app_pubkey == request.auth.app_pub
                && active_auth_keys.hardware_pubkey == request.auth.hardware_pub
                && active_spending_keyset.app_pub == request.spend.app_pub
                && active_spending_keyset.hardware_pub == request.spend.hardware_pub
            {
                let response = CreateKeysetResponseV2::try_from(existing_account)?;
                return Ok(Json(response));
            } else {
                return Err(ApiError::GenericConflict(
                    "Account is already a full account".to_string(),
                ));
            }
        }
        Account::Software(_) => {
            return Err(ApiError::GenericInternalApplicationError(
                "Unimplemented".to_string(),
            ));
        }
    };

    AccountValidation::default()
        .validate(
            AccountValidationRequest::from((lite_account, &request)),
            &config,
            &account_service,
            &recovery_repository,
            &public_key_repository,
        )
        .await?;

    // Mirrors `create_account_v2`: resolve enrollment + verify
    // attestation before durable side effects. No pre-existing
    // `hardware_verification_required` on a lite account to read.
    experimentation_claims.hardware_type = Some(request.auth.hardware_type.to_string());
    let hardware_verification_required = should_enroll_in_hardware_verification(
        &feature_flags_service,
        &experimentation_claims.overridden_account_context_key(account_id.clone()),
    );
    let attested_hardware_serial = collect_attested_hardware_serial(
        hardware_verification_required,
        request.spend.hardware_attestation.as_ref(),
        &request.spend.hardware_pub,
        lite_account.common_fields.properties.is_test_account,
    )?;

    // Record hw auth pubkey in public_keys table before any external side
    // effects (Cognito, WSM). This ensures a rejected upgrade never leaves
    // orphaned Cognito users or WSM keys behind.
    if !public_key_repository
        .persist_public_key(
            &request.auth.hardware_pub.to_string(),
            &account_id,
            KeyType::HardwareAuth,
        )
        .await?
    {
        return Err(AccountValidationError::HwAuthPubkeyReuseAccount.into());
    }

    // Create Cognito users
    user_pool_service
        .create_or_update_account_users_if_necessary(
            &account_id,
            Some(request.auth.app_pub),
            Some(request.auth.hardware_pub),
            None,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create new accounts in Cognito";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    // Generate a server key in WSM
    let keyset_id = KeysetId::new(id_generator.gen_spending_keyset_id())
        .map_err(RouteError::InvalidIdentifier)?;
    let key = wsm_client
        .create_root_key(
            &keyset_id.to_string(),
            to_wsm_bitcoin_network(request.spend.network),
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create new key in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    // Attempt to parse the DescriptorPublicKey from the xpub string
    let server_dpub = DescriptorPublicKey::from_str(&key.xpub).map_err(|e| {
        let msg = "Failed to parse server dpub from WSM";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;
    let server_pub = parse_public_key(server_dpub)?;

    let auth_key_id = AuthKeysId::new(id_generator.gen_spending_keyset_id())
        .map_err(RouteError::InvalidIdentifier)?;

    let input = UpgradeLiteAccountToFullAccountInput {
        lite_account,
        keyset_id,
        spending_keyset: SpendingKeyset::new_private_multi_sig(
            request.spend.network.into(),
            request.spend.app_pub,
            request.spend.hardware_pub,
            server_pub,
            key.pub_sig.clone(),
            attested_hardware_serial,
        ),
        auth_key_id: auth_key_id.clone(),
        auth_keys: FullAccountAuthKeys::new(
            request.auth.app_pub,
            request.auth.hardware_pub,
            Some(
                lite_account
                    .active_auth_keys()
                    .ok_or(RouteError::NoActiveAuthKeys)?
                    .recovery_pubkey,
            ),
            request.auth.hardware_type,
        ),
        hardware_verification_required,
    };
    let full_account = account_service
        .upgrade_lite_account_to_full_account(input)
        .await?;

    if hardware_verification_required {
        ENROLLMENT_FLIPPED.add(1, &[KeyValue::new(TRIGGER_KEY, TRIGGER_ONBOARDING)]);
    }

    emit_keyset_created(PRIVATE_VALUE);

    Ok(Json(CreateKeysetResponseV2 {
        keyset_id: full_account.active_keyset_id,
        server_pub,
        server_pub_integrity_sig: key.pub_sig,
    }))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateKeysetResponseV2 {
    pub keyset_id: KeysetId,
    pub server_pub: PublicKey,
    pub server_pub_integrity_sig: String,
}

impl TryFrom<&Account> for CreateKeysetResponseV2 {
    type Error = ApiError;

    fn try_from(value: &Account) -> Result<Self, Self::Error> {
        match value {
            Account::Full(full_account) => {
                let keyset = full_account
                    .active_spending_keyset()
                    .ok_or(RouteError::NoActiveSpendKeyset)?
                    .private_multi_sig_or(RouteError::ConflictingKeysetType)?;
                Ok(CreateKeysetResponseV2 {
                    keyset_id: full_account.active_keyset_id.clone(),
                    server_pub: keyset.server_pub,
                    server_pub_integrity_sig: keyset.server_pub_integrity_sig.clone(),
                })
            }
            _ => Err(ApiError::GenericInternalApplicationError(
                "Unexpected account type".to_string(),
            )),
        }
    }
}

#[instrument(skip(
    account_service,
    wsm_client,
    recovery_repository,
    feature_flags_service,
    experimentation_claims,
))]
#[utoipa::path(
    post,
    path = "/api/v2/accounts/{account_id}/keysets",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = SpendingKeysetInputV2,
    responses(
        (status = 200, description = "New keyset was created for account", body=CreateKeysetResponseV2),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn create_keyset_v2(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    State(recovery_repository): State<RecoveryRepository>,
    State(feature_flags_service): State<FeatureFlagsService>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<SpendingKeysetInputV2>,
) -> Result<Json<CreateKeysetResponseV2>, ApiError> {
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    if let Some((keyset_id, keyset)) = account
        .spending_keysets
        .iter()
        .filter_map(|(keyset_id, spending_keyset)| {
            if let Some(k) = spending_keyset.optional_private_multi_sig() {
                if k.app_pub == request.app_pub && k.hardware_pub == request.hardware_pub {
                    Some((keyset_id, k))
                } else {
                    None
                }
            } else {
                None
            }
        })
        .next()
    {
        return Ok(Json(CreateKeysetResponseV2 {
            keyset_id: keyset_id.to_owned(),
            server_pub: keyset.server_pub,
            server_pub_integrity_sig: keyset.server_pub_integrity_sig.clone(),
        }));
    }

    // Don't allow account to hop networks
    let active_keyset = account
        .active_spending_keyset()
        .ok_or(RouteError::NoActiveSpendKeyset)?;
    if active_keyset.network() != request.network.into() {
        return Err(RouteError::InvalidNetworkForNewKeyset.into());
    }

    // If the active keyset is legacy, the client is attempting the legacy -> private
    // migration. Consult LaunchDarkly to decide whether this app version is allowed
    // to perform the migration. Skip the check if the account just completed a D&N
    // recovery (legitimate re-setup path).
    if active_keyset.is_legacy() {
        let since = OffsetDateTime::now_utc() - RECENT_RECOVERY_GRACE;
        let recent_completed_recovery = recovery_repository
            .fetch_by_status_since(
                &account_id,
                RecoveryType::DelayAndNotify,
                RecoveryStatus::Complete,
                since,
            )
            .await
            .map_err(|e| {
                error!("Failed to fetch recent D&N recovery: {e}");
                ApiError::GenericInternalApplicationError(
                    "Failed to check recovery state".to_string(),
                )
            })?;

        if recent_completed_recovery.is_none() {
            let context_key = experimentation_claims.account_context_key().map_err(|e| {
                error!("Failed to build LaunchDarkly context: {e}");
                ApiError::GenericInternalApplicationError(
                    "Failed to evaluate feature flag".to_string(),
                )
            })?;
            // Fail open on any LD error (flag missing, client not ready, wrong type, etc.)
            // so a flag-config gap can't 500 the legacy -> private migration path.
            let blocked = match evaluate_flag_value::<bool>(
                &feature_flags_service,
                PRIVATE_KEYSET_CREATION_BLOCKED.key,
                &context_key,
            ) {
                Ok(v) => v,
                Err(e) => {
                    error!(
                        "Failed to evaluate {}: {e}; defaulting to false",
                        PRIVATE_KEYSET_CREATION_BLOCKED.key
                    );
                    false
                }
            };

            if blocked {
                return Err(ApiError::Specific {
                    code: ErrorCode::AppUpgradeRequired,
                    detail: Some("Update the Bitkey app to create a private keyset.".to_string()),
                    field: None,
                });
            }
        }
    }

    // Verify attestation before WSM key allocation so a 400 doesn't
    // orphan a WSM key. Account-keyed LD context so a missing
    // `Bitkey-App-Installation-Id` header can't dodge the kill switch.
    // Fails closed for an enrolled account: an unresolvable kill switch keeps
    // attestation required, so an LD outage can't mint an unattested keyset.
    let attestation_required = account.hardware_verification_required
        && hardware_verification_enforced(
            &feature_flags_service,
            &experimentation_claims.overridden_account_context_key(account_id.clone()),
        );
    let attested_hardware_serial = collect_attested_hardware_serial(
        attestation_required,
        request.hardware_attestation.as_ref(),
        &request.hardware_pub,
        account.common_fields.properties.is_test_account,
    )?;
    let attested_hardware_serial =
        inherit_verified_status_from_active_keyset(attested_hardware_serial, &account);

    let spending_keyset_id = KeysetId::gen().map_err(RouteError::InvalidIdentifier)?;
    let key = wsm_client
        .create_root_key(
            &spending_keyset_id.to_string(),
            to_wsm_bitcoin_network(request.network),
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create new key in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    let server_dpub = DescriptorPublicKey::from_str(&key.xpub).map_err(|e| {
        let msg = "Failed to parse server dpub from WSM";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;
    let server_pub = parse_public_key(server_dpub)?;

    let (inactive_spend_keyset_id, _) = account_service
        .create_inactive_spending_keyset(CreateInactiveSpendingKeysetInput {
            account_id,
            spending_keyset_id,
            spending: SpendingKeyset::new_private_multi_sig(
                request.network.into(),
                request.app_pub,
                request.hardware_pub,
                server_pub,
                key.pub_sig.clone(),
                attested_hardware_serial,
            ),
        })
        .await?;

    emit_keyset_created(PRIVATE_VALUE);

    Ok(Json(CreateKeysetResponseV2 {
        keyset_id: inactive_spend_keyset_id,
        server_pub,
        server_pub_integrity_sig: key.pub_sig,
    }))
}

fn parse_public_key(dpub: DescriptorPublicKey) -> Result<PublicKey, ApiError> {
    let DescriptorPublicKey::XPub(xpub) = dpub else {
        return Err(ApiError::GenericInternalApplicationError(
            "Expected an xpub".to_string(),
        ));
    };

    Ok(xpub.xkey.public_key)
}
