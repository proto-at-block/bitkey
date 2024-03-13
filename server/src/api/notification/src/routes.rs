use std::collections::HashMap;

use ::metrics::KeyValue;
use account::service::{FetchAccountInput, Service as AccountService};
use authn_authz::key_claims::KeyClaims;
use axum::{
    extract::{Path, State},
    routing::get,
    routing::post,
    routing::put,
    Form, Json, Router,
};
use axum_extra::TypedHeader;
use errors::ApiError;
use http_server::swagger::{SwaggerEndpoint, Url};

use serde::{Deserialize, Serialize};
use tracing::{event, instrument, Level};
use types::{
    account::identifiers::AccountId,
    notification::{NotificationChannel, NotificationsPreferences},
};
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};

use crate::{
    address_repo::{AddressAndKeysetId, AddressWatchlistTrait},
    payloads::test_notification::TestNotificationPayload,
    service::{
        FetchNotificationsPreferencesInput, SendNotificationInput,
        UpdateNotificationsPreferencesInput,
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
);

impl RouteState {
    pub fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/twilio/status-callback", post(twilio_status_callback))
            .route_layer(FACTORY.route_layer(FACTORY_NAME.to_owned()))
            .with_state(self.to_owned())
    }
    pub fn authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/notifications/test",
                post(send_test_push),
            )
            .route(
                "/api/accounts/:account_id/notifications/addresses",
                post(add_address),
            )
            .route(
                "/api/accounts/:account_id/notifications-preferences",
                put(set_notifications_preferences),
            )
            .route(
                "/api/accounts/:account_id/notifications-preferences",
                get(get_notifications_preferences),
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
        set_notifications_preferences,
        get_notifications_preferences,
    ),
    components(
        schemas(SendTestPushData, SendTestPushResponse),
        schemas(RegisterWatchAddressRequest, RegisterWatchAddressResponse),
        schemas(NotificationsPreferences, NotificationChannel),
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

#[instrument(err, skip(account_service))]
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
    State(mut address_repo_service): State<Box<dyn AddressWatchlistTrait>>,
    Json(request): Json<RegisterWatchAddressRequest>,
) -> Result<Json<RegisterWatchAddressResponse>, ApiError> {
    account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let addresses = request
        .addresses
        .into_iter()
        .map(AddressAndKeysetId::from)
        .collect::<Vec<AddressAndKeysetId>>();

    address_repo_service.insert(&addresses, &account_id).await?;

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

#[instrument(err, skip(twilio_client))]
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

#[instrument(err, skip(notification_service))]
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
    State(notification_service): State<NotificationService>,
    key_proof: KeyClaims,
    Json(request): Json<NotificationsPreferences>,
) -> Result<Json<NotificationsPreferences>, ApiError> {
    let new_notifications_preferences = request;
    notification_service
        .update_notifications_preferences(UpdateNotificationsPreferencesInput {
            account_id: &account_id,
            notifications_preferences: &new_notifications_preferences,
            key_proof: Some(key_proof),
        })
        .await?;

    Ok(Json(new_notifications_preferences))
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
