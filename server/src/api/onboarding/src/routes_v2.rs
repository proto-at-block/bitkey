use std::str::FromStr;

use account::service::{
    CreateAccountAndKeysetsInput, CreateInactiveSpendingKeysetInput, FetchAccountInput,
    Service as AccountService, UpgradeLiteAccountToFullAccountInput,
};
use authn_authz::key_claims::KeyClaims;
use axum::{
    extract::{Path, State},
    Json,
};
use bdk_utils::bdk::{bitcoin::secp256k1::PublicKey, keys::DescriptorPublicKey};
use errors::{ApiError, RouteError};
use external_identifier::ExternalIdentifier;
use http_server::middlewares::identifier_generator::IdentifierGenerator;
use notification::clients::iterable::IterableClient;
use recovery::repository::RecoveryRepository;
use serde::{Deserialize, Serialize};
use tracing::{error, event, instrument, Level};
use types::account::{
    entities::{
        v2::{
            FullAccountAuthKeysInputV2, SpendingKeysetInputV2, UpgradeLiteAccountAuthKeysInputV2,
        },
        Account, Keyset, LiteAccount,
    },
    identifiers::{AccountId, AuthKeysId, KeysetId},
    keys::FullAccountAuthKeys,
    spending::SpendingKeyset,
};
use userpool::userpool::UserPoolService;
use utoipa::ToSchema;
use wsm_rust_client::{SigningService, WsmClient};

use crate::{
    account_validation::{AccountValidation, AccountValidationRequest},
    routes::Config,
    upsert_account_iterable_user,
};

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
    State(wsm_client): State<WsmClient>,
    State(id_generator): State<IdentifierGenerator>,
    State(user_pool_service): State<UserPoolService>,
    State(config): State<Config>,
    State(iterable_client): State<IterableClient>,
    Json(request): Json<CreateAccountRequestV2>,
) -> Result<Json<CreateAccountResponseV2>, ApiError> {
    if let Some(v) = AccountValidation::default()
        .validate(
            AccountValidationRequest::from(&request),
            &config,
            &account_service,
            &recovery_repository,
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
        .create_root_key(&keyset_id.to_string(), request.spend.network)
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
            auth: FullAccountAuthKeys {
                app_pubkey: request.auth.app_pub,
                hardware_pubkey: request.auth.hardware_pub,
                recovery_pubkey: Some(request.auth.recovery_pub),
            },
            spending: SpendingKeyset::new_private_multi_sig(
                request.spend.network.into(),
                request.spend.app_pub,
                request.spend.hardware_pub,
                server_pub,
                key.pub_sig.clone(),
            ),
        },
        is_test_account: request.is_test_account,
    };
    let account = account_service.create_account_and_keysets(input).await?;

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
        config
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
    State(wsm_client): State<WsmClient>,
    State(id_generator): State<IdentifierGenerator>,
    State(user_pool_service): State<UserPoolService>,
    State(config): State<Config>,
    Path(account_id): Path<AccountId>,
    Json(request): Json<UpgradeAccountRequestV2>,
) -> Result<Json<CreateKeysetResponseV2>, ApiError> {
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
        )
        .await?;

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
        .create_root_key(&keyset_id.to_string(), request.spend.network)
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
        ),
        auth_key_id: auth_key_id.clone(),
        auth_keys: FullAccountAuthKeys {
            app_pubkey: request.auth.app_pub,
            hardware_pubkey: request.auth.hardware_pub,
            recovery_pubkey: Some(
                lite_account
                    .active_auth_keys()
                    .ok_or(RouteError::NoActiveAuthKeys)?
                    .recovery_pubkey,
            ),
        },
    };
    let full_account = account_service
        .upgrade_lite_account_to_full_account(input)
        .await?;

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

#[instrument(skip(account_service))]
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
    key_proof: KeyClaims,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<SpendingKeysetInputV2>,
) -> Result<Json<CreateKeysetResponseV2>, ApiError> {
    if !(key_proof.hw_signed && key_proof.app_signed) {
        event!(
            Level::WARN,
            "valid signature over access token required by both app and hw auth keys"
        );
        return Err(ApiError::GenericForbidden(
            "valid signature over access token required by both app and hw auth keys".to_string(),
        ));
    }

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
    account.active_spending_keyset().map_or(
        Err(RouteError::NoActiveSpendKeyset),
        |active_keyset| {
            if active_keyset.network() != request.network.into() {
                return Err(RouteError::InvalidNetworkForNewKeyset);
            }
            Ok(())
        },
    )?;

    let spending_keyset_id = KeysetId::gen().map_err(RouteError::InvalidIdentifier)?;
    let key = wsm_client
        .create_root_key(&spending_keyset_id.to_string(), request.network)
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
            ),
        })
        .await?;

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
