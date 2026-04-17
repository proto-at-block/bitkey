use std::collections::HashMap;

use account::service::{FetchAccountInput, Service as AccountService};
use authn_authz::{Action, Authorization, AuthorizationRequirements};
use axum::{
    extract::{Path, State},
    routing::get,
    routing::post,
    routing::put,
    Form, Json, Router,
};
use axum_extra::TypedHeader;
use errors::ApiError;
use http_server::{
    router::RouterBuilder,
    swagger::{SwaggerEndpoint, Url},
};
use instrumentation::metrics::KeyValue;

use serde::{Deserialize, Serialize};
use tracing::{event, instrument, Level};
use types::{
    account::{
        entities::{HardwareType, Touchpoint},
        identifiers::AccountId,
    },
    notification::{NotificationChannel, NotificationsPreferences, NotificationsTriggerType},
};
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};

use crate::{
    address_repo::{AddressAndKeysetId, AddressWatchlistTrait},
    payloads::test_notification::TestNotificationPayload,
    service::{
        FetchNotificationsPreferencesInput, SendNotificationInput,
        UpdateNotificationsPreferencesInput, UpdateNotificationsTriggersInput,
    },
    NotificationPayloadType,
};
use crate::{clients::twilio::find_supported_sms_country_code, NotificationPayloadBuilder};
use crate::{
    clients::twilio::TwilioClient,
    metrics::{FACTORY, FACTORY_NAME},
};
use crate::{metrics, service::Service as NotificationService};

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub NotificationService,
    pub AccountService,
    pub Box<dyn AddressWatchlistTrait>,
    pub TwilioClient,
    pub UserPoolService,
    pub repository::anti_replay::AntiReplayRepository,
);

impl RouterBuilder for RouteState {
    fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/twilio/status-callback", post(twilio_status_callback))
            .route_layer(FACTORY.route_layer(FACTORY_NAME.to_owned()))
            .with_state(self.to_owned())
    }

    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/notifications/test",
                post(send_test_push),
            )
            .route(
                "/api/accounts/:account_id/notifications/addresses",
                post(add_address).delete(delete_addresses),
            )
            .route(
                "/api/accounts/:account_id/notifications-preferences",
                put(set_notifications_preferences),
            )
            .route(
                "/api/accounts/:account_id/notifications-preferences",
                get(get_notifications_preferences),
            )
            .route(
                "/api/accounts/:account_id/notifications/triggers",
                put(set_notifications_triggers),
            )
            .route_layer(FACTORY.route_layer(FACTORY_NAME.to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Notification", "/docs/notification/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        send_test_push,
        add_address,
        delete_addresses,
        set_notifications_preferences,
        get_notifications_preferences,
        set_notifications_triggers,
    ),
    components(
        schemas(SendTestPushData, SendTestPushResponse),
        schemas(RegisterWatchAddressRequest, RegisterWatchAddressResponse),
        schemas(NotificationsPreferences, NotificationChannel),
        schemas(NotificationsTriggerType, SetNotificationsTriggersRequest, SetNotificationsTriggersResponse),
    ),
    tags(
        (name = "Notification", description = "Touchpoints with Users")
    )
)]
struct ApiDoc;

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SendTestPushData {}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SendTestPushResponse {}

#[instrument(err, skip(account_service, notification_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/notifications/test",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = SendTestPushData,
    responses(
        (status = 200, description = "Test Notification was created", body=SendTestPushResponse),
        (status = 404, description = "Wallet not found")
    ),
)]
pub async fn send_test_push(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(notification_service): State<NotificationService>,
    Json(request): Json<SendTestPushData>,
) -> Result<Json<SendTestPushResponse>, ApiError> {
    account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let payload = match NotificationPayloadBuilder::default()
        .test_notification_payload(Some(TestNotificationPayload::default()))
        .build()
    {
        Ok(payload) => payload,
        Err(err) => {
            event!(
                Level::ERROR,
                "Couldn't create NotificationPayload due to error {}",
                err
            );
            return Err(ApiError::GenericInternalApplicationError(
                "Internal Server Error".to_owned(),
            ));
        }
    };

    notification_service
        .send_notification(SendNotificationInput {
            account_id: &account_id,
            payload_type: NotificationPayloadType::TestPushNotification,
            payload: &payload,
            only_touchpoints: None,
        })
        .await?;
    Ok(Json(SendTestPushResponse {}))
}

/// App <> F8e request
#[derive(Clone, Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RegisterWatchAddressRequest {
    addresses: Vec<AddressAndKeysetId>,
}

impl From<Vec<AddressAndKeysetId>> for RegisterWatchAddressRequest {
    fn from(addresses: Vec<AddressAndKeysetId>) -> Self {
        Self { addresses }
    }
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RegisterWatchAddressResponse {}

#[instrument(err, skip(account_service, notification_service, request))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/notifications/addresses",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = RegisterWatchAddressRequest,
    responses(
        (status = 200, description = "Addresses successfully registered", body=RegisterWatchAddressResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn add_address(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(address_repo_service): State<Box<dyn AddressWatchlistTrait>>,
    State(notification_service): State<NotificationService>,
    Json(request): Json<RegisterWatchAddressRequest>,
) -> Result<Json<RegisterWatchAddressResponse>, ApiError> {
    account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    // Check if money movement notifications are enabled before recording watch addresses
    let notifications_preferences = notification_service
        .fetch_notifications_preferences(FetchNotificationsPreferencesInput {
            account_id: &account_id,
        })
        .await?;

    // Only insert addresses if money movement notifications are enabled for any channel
    if !notifications_preferences.money_movement.is_empty() {
        let addresses = request
            .addresses
            .into_iter()
            .collect::<Vec<AddressAndKeysetId>>();

        address_repo_service.insert(&addresses, &account_id).await?;
    }

    Ok(Json(RegisterWatchAddressResponse {}))
}

mod x_twilio_signature {
    static NAME: axum::http::HeaderName = axum::http::HeaderName::from_static("x-twilio-signature");

    #[derive(Debug)]
    pub struct Header(pub axum::http::HeaderValue);

    impl axum_extra::headers::Header for Header {
        fn name() -> &'static axum::http::HeaderName {
            &NAME
        }

        fn decode<'i, I>(values: &mut I) -> Result<Self, axum_extra::headers::Error>
        where
            Self: Sized,
            I: Iterator<Item = &'i axum::http::HeaderValue>,
        {
            values
                .next()
                .cloned()
                .ok_or_else(axum_extra::headers::Error::invalid)
                .map(Header)
        }

        fn encode<E: Extend<axum::http::HeaderValue>>(&self, values: &mut E) {
            values.extend(::std::iter::once((&self.0).into()));
        }
    }
}

#[instrument(err, skip(twilio_client, signature, request))]
#[utoipa::path(
    post,
    path = "/api/twilio/status-callback",
    request_body = HashMap<String, String>,
    responses(
        (status = 204, description = "Callback successful"),
        (status = 401, description = "Request failed signature validation")
    ),
)]
pub async fn twilio_status_callback(
    State(twilio_client): State<TwilioClient>,
    TypedHeader(signature): TypedHeader<x_twilio_signature::Header>,
    Form(request): Form<HashMap<String, String>>,
) -> Result<impl axum::response::IntoResponse, ApiError> {
    let signature = signature
        .0
        .to_str()
        .map_err(|_| ApiError::GenericBadRequest("Invalid signature header".to_string()))?;
    twilio_client.validate_callback_signature(&request, signature.to_string())?;

    let Some(status) = request.get("MessageStatus") else {
        return Err(ApiError::GenericBadRequest(
            "Expected MessageStatus field".to_string(),
        ));
    };

    let mut attributes = vec![];

    if let Some(country_code) =
        find_supported_sms_country_code(request.get("To").cloned().unwrap_or_default())
    {
        attributes.push(KeyValue::new(
            metrics::COUNTRY_CODE_KEY,
            country_code.alpha2(),
        ));
    }

    match status.as_str() {
        "sent" => metrics::TWILIO_MESSAGE_STATUS_SENT.add(1, &attributes),
        "failed" => metrics::TWILIO_MESSAGE_STATUS_FAILED.add(1, &attributes),
        "delivered" => metrics::TWILIO_MESSAGE_STATUS_DELIVERED.add(1, &attributes),
        "undelivered" => metrics::TWILIO_MESSAGE_STATUS_UNDELIVERED.add(1, &attributes),
        _ => {}
    }

    Ok(axum::http::StatusCode::NO_CONTENT)
}

#[instrument(
    err,
    skip(account_service, notification_service, anti_replay_repository)
)]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/notifications-preferences",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = NotificationsPreferences,
    responses(
        (status = 200, description = "Notifications preferences set", body=NotificationsPreferences),
    ),
)]
pub async fn set_notifications_preferences(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(notification_service): State<NotificationService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    auth: Authorization,
    Json(request): Json<NotificationsPreferences>,
) -> Result<Json<NotificationsPreferences>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let hardware_type = full_account
        .active_hardware_type()
        .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;

    // Determine the action from the account_security preference change.
    // The mobile app changes one channel at a time, so we map the diff to
    // the corresponding Set*/Disable* action. Non-security-only changes
    // use jwt_only() — no proof needed for either W1 or W3.
    let current_prefs: NotificationsPreferences = full_account
        .common_fields
        .notifications_preferences_state
        .clone()
        .into();
    let action = account_security_action(
        &current_prefs,
        &request,
        &full_account.common_fields.touchpoints,
        hardware_type,
    )?;

    // No route-level signature requirement — the service conditionally enforces
    // both-factor auth only when reducing account_security preferences.
    let requirements = match action {
        Some(AccountSecurityAction { action, entity_id }) => {
            AuthorizationRequirements::new(action, hardware_type)
                .entity_id_opt(entity_id)
                .proof(authn_authz::ProofRequirement::Conditional)
        }
        None => AuthorizationRequirements::jwt_only(hardware_type),
    };
    let result = requirements
        .execute(&auth, &anti_replay_repository, |ctx| async move {
            let new_notifications_preferences = request;
            notification_service
                .update_notifications_preferences(UpdateNotificationsPreferencesInput {
                    account_id: &account_id,
                    notifications_preferences: &new_notifications_preferences,
                    signed_by_both_factors: ctx.app_signed() && ctx.hw_signed(),
                })
                .await?;

            Ok::<_, ApiError>(new_notifications_preferences)
        })
        .await?;

    Ok(Json(result))
}

/// Result of diffing account_security preferences: the Action to verify
/// and an optional touchpoint entity ID for the proof binding.
struct AccountSecurityAction {
    action: Action,
    /// Touchpoint entity ID for email/SMS disable actions (the client signs
    /// with `eid=<touchpoint_id>`). None for push disable actions.
    entity_id: Option<String>,
}

/// Maps an account_security preference change to the corresponding Action.
///
/// For W3 accounts, returns an error if more than one channel changes in a
/// single request, since each channel maps to a distinct action proof and
/// only one can be verified per request. W1 accounts use KeyClaims and are
/// not subject to this restriction.
fn account_security_action(
    current: &NotificationsPreferences,
    new: &NotificationsPreferences,
    touchpoints: &[Touchpoint],
    hardware_type: HardwareType,
) -> Result<Option<AccountSecurityAction>, ApiError> {
    let removed: Vec<_> = current
        .account_security
        .difference(&new.account_security)
        .collect();

    // W3 accounts require a per-channel ActionProof for disabling, so only
    // one disable is allowed per request. Enables don't need proof so they
    // are unrestricted. W1 accounts use KeyClaims and can change multiple
    // channels at once.
    if hardware_type == HardwareType::W3 && removed.len() > 1 {
        return Err(ApiError::GenericBadRequest(
            "Only one account_security channel can be disabled per request".to_string(),
        ));
    }

    // Check for channel removed from account_security (disable)
    if let Some(channel) = removed.first() {
        let (action, entity_id) = match channel {
            NotificationChannel::Email => {
                let eid = touchpoints.iter().find_map(|t| match t {
                    Touchpoint::Email { id, .. } => Some(id.to_string()),
                    _ => None,
                });
                (Action::DisableRecoveryEmail, eid)
            }
            NotificationChannel::Sms => {
                let eid = touchpoints.iter().find_map(|t| match t {
                    Touchpoint::Phone { id, .. } => Some(id.to_string()),
                    _ => None,
                });
                (Action::DisableRecoveryPhone, eid)
            }
            NotificationChannel::Push => (Action::DisableRecoveryPushNotifications, None),
        };
        return Ok(Some(AccountSecurityAction { action, entity_id }));
    }

    // No disable detected — enables and no-ops don't require proof.
    Ok(None)
}

#[instrument(err, skip(notification_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/notifications-preferences",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Notifications preferences set", body=NotificationsPreferences),
    ),
)]
pub async fn get_notifications_preferences(
    Path(account_id): Path<AccountId>,
    State(notification_service): State<NotificationService>,
) -> Result<Json<NotificationsPreferences>, ApiError> {
    let notifications_preferences = notification_service
        .fetch_notifications_preferences(FetchNotificationsPreferencesInput {
            account_id: &account_id,
        })
        .await?;

    Ok(Json(notifications_preferences))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SetNotificationsTriggersRequest {
    pub notifications_triggers: Vec<NotificationsTriggerType>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SetNotificationsTriggersResponse {}

#[instrument(err, skip(notification_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/notifications/triggers",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = SetNotificationsTriggersRequest,
    responses(
        (status = 200, description = "Notifications triggers set", body=SetNotificationsTriggersResponse),
    ),
)]
pub async fn set_notifications_triggers(
    Path(account_id): Path<AccountId>,
    State(notification_service): State<NotificationService>,
    Json(request): Json<SetNotificationsTriggersRequest>,
) -> Result<Json<SetNotificationsTriggersResponse>, ApiError> {
    notification_service
        .update_notifications_triggers(UpdateNotificationsTriggersInput {
            account_id: &account_id,
            trigger_types: request.notifications_triggers,
        })
        .await?;

    Ok(Json(SetNotificationsTriggersResponse {}))
}

#[instrument(err, skip(account_service, address_repo_service))]
#[utoipa::path(
    delete,
    path = "/api/accounts/{account_id}/notifications/addresses",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "All addresses deleted successfully"),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn delete_addresses(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(address_repo_service): State<Box<dyn AddressWatchlistTrait>>,
) -> Result<(), ApiError> {
    account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    address_repo_service
        .delete_all_addresses(&account_id)
        .await?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use isocountry::CountryCode;
    use std::collections::HashSet;
    use types::account::identifiers::TouchpointId;

    fn prefs(security: &[NotificationChannel]) -> NotificationsPreferences {
        NotificationsPreferences {
            account_security: security.iter().copied().collect(),
            money_movement: HashSet::new(),
            product_marketing: HashSet::new(),
        }
    }

    fn email_touchpoint() -> Touchpoint {
        Touchpoint::Email {
            id: TouchpointId::gen().unwrap(),
            email_address: "test@example.com".to_string(),
            active: true,
        }
    }

    fn phone_touchpoint() -> Touchpoint {
        Touchpoint::Phone {
            id: TouchpointId::gen().unwrap(),
            phone_number: "+15551234567".to_string(),
            country_code: CountryCode::USA,
            active: true,
        }
    }

    // ── Disable (channel removed from account_security) ──

    #[test]
    fn disable_email() {
        let current = prefs(&[NotificationChannel::Email]);
        let new = prefs(&[]);
        let touchpoints = vec![email_touchpoint()];
        let result = account_security_action(&current, &new, &touchpoints, HardwareType::W3)
            .unwrap()
            .unwrap();
        assert_eq!(result.action, Action::DisableRecoveryEmail);
        assert!(
            result.entity_id.is_some(),
            "email disable should include touchpoint eid"
        );
    }

    #[test]
    fn disable_sms() {
        let current = prefs(&[NotificationChannel::Sms]);
        let new = prefs(&[]);
        let touchpoints = vec![phone_touchpoint()];
        let result = account_security_action(&current, &new, &touchpoints, HardwareType::W3)
            .unwrap()
            .unwrap();
        assert_eq!(result.action, Action::DisableRecoveryPhone);
        assert!(
            result.entity_id.is_some(),
            "sms disable should include touchpoint eid"
        );
    }

    #[test]
    fn disable_push() {
        let current = prefs(&[NotificationChannel::Push]);
        let new = prefs(&[]);
        let result = account_security_action(&current, &new, &[], HardwareType::W3)
            .unwrap()
            .unwrap();
        assert_eq!(result.action, Action::DisableRecoveryPushNotifications);
        assert!(
            result.entity_id.is_none(),
            "push disable has no touchpoint eid"
        );
    }

    // ── Enable (channel added to account_security) → no proof needed ──

    #[test]
    fn enable_email_returns_none() {
        let current = prefs(&[]);
        let new = prefs(&[NotificationChannel::Email]);
        assert!(
            account_security_action(&current, &new, &[], HardwareType::W3)
                .unwrap()
                .is_none()
        );
    }

    #[test]
    fn enable_sms_returns_none() {
        let current = prefs(&[]);
        let new = prefs(&[NotificationChannel::Sms]);
        assert!(
            account_security_action(&current, &new, &[], HardwareType::W3)
                .unwrap()
                .is_none()
        );
    }

    #[test]
    fn enable_push_returns_none() {
        let current = prefs(&[]);
        let new = prefs(&[NotificationChannel::Push]);
        assert!(
            account_security_action(&current, &new, &[], HardwareType::W3)
                .unwrap()
                .is_none()
        );
    }

    // ── No change → None ──

    #[test]
    fn no_security_change_returns_none() {
        let current = prefs(&[NotificationChannel::Push]);
        let new = prefs(&[NotificationChannel::Push]);
        assert!(
            account_security_action(&current, &new, &[], HardwareType::W3)
                .unwrap()
                .is_none()
        );
    }

    #[test]
    fn empty_to_empty_returns_none() {
        let current = prefs(&[]);
        let new = prefs(&[]);
        assert!(
            account_security_action(&current, &new, &[], HardwareType::W3)
                .unwrap()
                .is_none()
        );
    }

    // ── Non-security-only changes → None ──

    #[test]
    fn only_money_movement_change_returns_none() {
        let mut current = prefs(&[NotificationChannel::Push]);
        current.money_movement = HashSet::from([NotificationChannel::Email]);
        let mut new = prefs(&[NotificationChannel::Push]);
        new.money_movement = HashSet::new();
        assert!(
            account_security_action(&current, &new, &[], HardwareType::W3)
                .unwrap()
                .is_none()
        );
    }

    // ── Multiple channel changes → Error ──

    #[test]
    fn swap_channel_returns_disable_action() {
        // Swap: remove Email, add Push → only the disable matters
        let current = prefs(&[NotificationChannel::Email]);
        let new = prefs(&[NotificationChannel::Push]);
        let touchpoints = vec![email_touchpoint()];
        let result = account_security_action(&current, &new, &touchpoints, HardwareType::W3)
            .unwrap()
            .unwrap();
        assert_eq!(result.action, Action::DisableRecoveryEmail);
    }

    #[test]
    fn multiple_removals_rejected() {
        let current = prefs(&[NotificationChannel::Email, NotificationChannel::Sms]);
        let new = prefs(&[]);
        assert!(account_security_action(&current, &new, &[], HardwareType::W3).is_err());
    }

    // ── W1 allows multiple channel changes ──

    #[test]
    fn w1_allows_multiple_disables() {
        // W1 uses KeyClaims (not per-channel ActionProofs), so multiple
        // disables in one request are allowed — unlike W3.
        let current = prefs(&[NotificationChannel::Email, NotificationChannel::Sms]);
        let new = prefs(&[]);
        let result = account_security_action(&current, &new, &[], HardwareType::W1).unwrap();
        assert!(result.is_some());
    }
}
