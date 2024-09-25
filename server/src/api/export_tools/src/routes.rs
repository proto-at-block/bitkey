use account::service::{FetchAccountInput, Service as AccountService};
use axum::{
    extract::{Path, State},
    routing::get,
    Json, Router,
};
use bdk_utils::bdk::descriptor::ExtendedDescriptor;
use errors::ApiError;
use serde::{Deserialize, Serialize};
use tracing::{error, instrument};
use types::account::identifiers::AccountId;
use utoipa::ToSchema;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub AccountService);

impl RouteState {
    pub fn account_or_recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/descriptors",
                get(get_account_descriptors),
            )
            .with_state(self.to_owned())
    }
}

#[instrument(skip(account_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/descriptors",
    params(
        ("account_id" = AccountId, Path, description = "AccountId")
    ),
    responses(
        (status = 200, description = "Returns the account's descriptors", body = GetAccountDescriptorResponse)
    )
)]
async fn get_account_descriptors(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
) -> Result<Json<GetAccountDescriptorResponse>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let active_descriptor = full_account
        .active_descriptor_keyset()
        .ok_or_else(|| {
            let msg = "Unable to find active descriptor keyset when we expected one.";
            error!("{msg}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?
        .into_multisig_descriptor()?;

    let inactive_descriptors: Vec<ExtendedDescriptor> = full_account
        .inactive_descriptor_keysets()
        .into_iter()
        .map(|keyset| keyset.into_multisig_descriptor())
        .collect::<Result<Vec<_>, _>>()?;

    Ok(Json(GetAccountDescriptorResponse {
        active_descriptor,
        inactive_descriptors,
    }))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct GetAccountDescriptorResponse {
    pub active_descriptor: ExtendedDescriptor,
    pub inactive_descriptors: Vec<ExtendedDescriptor>,
}
