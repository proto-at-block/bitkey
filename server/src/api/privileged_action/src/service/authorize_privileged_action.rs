use std::{future::Future, pin::Pin};

use authn_authz::key_claims::KeyClaims;
use derive_builder::Builder;
use errors::ApiError;
use notification::{
    payloads::{
        privileged_action_completed_delay_period::PrivilegedActionCompletedDelayPeriodPayload,
        privileged_action_pending_delay_period::PrivilegedActionPendingDelayPeriodPayload,
    },
    schedule::ScheduleNotificationType,
    service::ScheduleNotificationsInput,
    NotificationPayloadBuilder,
};
use serde::{de::DeserializeOwned, Serialize};
use serde_json::Value;
use time::Duration;
use tracing::instrument;
use types::{
    account::{identifiers::AccountId, AccountType},
    privileged_action::{
        definition::{
            AuthorizationStrategyDefinition, DelayAndNotifyDefinition,
            HardwareProofOfPossessionDefinition, PrivilegedActionDefinition,
        },
        repository::{
            AuthorizationStrategyRecord, DelayAndNotifyRecord, DelayAndNotifyStatus,
            PrivilegedActionInstanceRecord,
        },
        router::generic::{
            AuthorizationStrategyInput, ContinuePrivilegedActionRequest, PrivilegedActionRequest,
            PrivilegedActionResponse,
        },
        shared::PrivilegedActionType,
    },
};

use super::{error::ServiceError, gen_token, Service};

/// Validator for privileged action requests that allows registering callbacks to validate
/// requests based on their authorization strategy.
///
/// The validator can have callbacks registered for:
/// - Delay and notify strategy: Validates requests that require a delay period and notifications
/// - Hardware proof of possession: Validates requests that require hardware authentication
///
/// The callbacks are executed when a privileged action request is initiated with the corresponding
/// authorization strategy. They can perform any necessary validation logic and return an error
/// if the request is invalid.
///
/// Type parameters:
/// - ReqT: The type of the request payload
/// - ErrT: The error type that can be converted into an ApiError
#[derive(Builder)]
#[builder(pattern = "owned", setter(strip_option), default)]
pub struct PrivilegedActionRequestValidator<ReqT, ErrT>
where
    ErrT: Into<ApiError>,
{
    pub on_initiate_delay_and_notify: Option<
        Box<dyn FnOnce(ReqT) -> Pin<Box<dyn Future<Output = Result<(), ErrT>> + Send>> + Send>,
    >,
    pub on_initiate_hardware_proof_of_possession: Option<
        Box<dyn FnOnce(ReqT) -> Pin<Box<dyn Future<Output = Result<(), ErrT>> + Send>> + Send>,
    >,
}

impl From<PrivilegedActionRequestValidatorBuilderError> for ApiError {
    fn from(e: PrivilegedActionRequestValidatorBuilderError) -> Self {
        ApiError::GenericInternalApplicationError(e.to_string())
    }
}

impl<ReqT, ErrT> Default for PrivilegedActionRequestValidator<ReqT, ErrT>
where
    ErrT: Into<ApiError>,
{
    fn default() -> Self {
        Self {
            on_initiate_delay_and_notify: None,
            on_initiate_hardware_proof_of_possession: None,
        }
    }
}

/// Input parameters for authorizing a privileged action in the system.
///
/// This struct encapsulates all the necessary information to determine whether a privileged
/// action should be authorized, delayed for additional verification, or rejected.
///
/// # Type Parameters
/// * `ReqT` - The type of the request payload contained within the privileged action request
/// * `ErrT` - An error type that can be converted into an ApiError
///
/// # Fields
/// * `account_id` - The account identifier for which the privileged action is being requested
/// * `privileged_action_definition` - Definition of the privileged action with its requirements
/// * `key_proof` - Proof of possession (typically signatures from app/hardware keys)
/// * `privileged_action_request` - The actual request containing action-specific parameters
/// * `request_validator` - Validator that contains handlers for different authorization paths, checked before the authorization is checked
pub struct AuthorizePrivilegedActionInput<'a, ReqT, ErrT>
where
    ErrT: Into<ApiError>,
{
    pub account_id: &'a AccountId,
    pub privileged_action_definition: &'a PrivilegedActionDefinition,
    pub key_proof: &'a KeyClaims,
    pub privileged_action_request: &'a PrivilegedActionRequest<ReqT>,
    pub request_validator: PrivilegedActionRequestValidator<ReqT, ErrT>,
}

// A call to `authorize_privileged_action` can return one of the following:
// - `Authorized(ReqT)`: The request is authorized and the request payload is returned
// - `Pending(PrivilegedActionResponse<RespT>)`: The request is pending and the response is returned
#[derive(Debug)]
pub enum AuthorizePrivilegedActionOutput<ReqT, RespT> {
    Authorized(ReqT),
    Pending(PrivilegedActionResponse<RespT>),
}

impl Service {
    /// Authorizes a privileged action request based on the defined authorization strategy.
    ///
    /// This function evaluates whether a privileged action should be immediately authorized,
    /// delayed for additional verification, or rejected. It handles both initial requests
    /// and continuation of previously initiated privileged actions.
    ///
    /// # Authorization Flow
    /// - For initial requests:
    ///   - With HardwareProofOfPossession strategy: Verifies key signatures and may bypass checks during onboarding
    ///   - With DelayAndNotify strategy: May start a time-delay period with notifications
    ///
    /// - For continuation requests:
    ///   - Validates the instance ID, completion token, and ensures the delay period has elapsed
    ///
    /// # Type Parameters
    /// * `ReqT` - Request payload type (must be serializable/deserializable as it's persisted in the database)
    /// * `RespT` - Response type for pending actions
    /// * `ErrT` - Error type that can be converted to ApiError
    ///
    /// # Returns
    /// * `AuthorizePrivilegedActionOutput::Authorized(ReqT)` - If the action is authorized immediately
    /// * `AuthorizePrivilegedActionOutput::Pending(PrivilegedActionResponse<RespT>)` - If additional steps are needed
    ///
    /// # Errors
    /// Returns a ServiceError if authorization fails due to invalid inputs, missing permissions,
    /// or when trying to continue an action with an incompatible strategy.
    #[instrument(skip(self, input))]
    pub async fn authorize_privileged_action<ReqT, RespT, ErrT>(
        &self,
        input: AuthorizePrivilegedActionInput<ReqT, ErrT, '_>,
    ) -> Result<AuthorizePrivilegedActionOutput<ReqT, RespT>, ServiceError>
    where
        ReqT: Serialize + DeserializeOwned + Clone,
        ErrT: Into<ApiError>,
    {
        let account = &self.account_repository.fetch(input.account_id).await?;
        let account_type: AccountType = account.into();
        let privileged_action_type = input
            .privileged_action_definition
            .privileged_action_type
            .clone();

        let Some(privileged_action_definition) = input.privileged_action_definition.resolve(
            account_type.clone(),
            account
                .get_common_fields()
                .configured_privileged_action_delay_durations
                .clone(),
        ) else {
            return Err(ServiceError::NoAuthorizationStrategyDefinedForbidden(
                privileged_action_type,
                account_type,
            ));
        };

        match input.privileged_action_request.clone() {
            PrivilegedActionRequest::Initiate(initial_request) => {
                match privileged_action_definition.authorization_strategy {
                    AuthorizationStrategyDefinition::HardwareProofOfPossession(
                        hardware_proof_of_possession_definition,
                    ) => {
                        self.initiate_hardware_proof_of_possession(
                            &hardware_proof_of_possession_definition,
                            input.key_proof,
                            initial_request.clone(),
                            input.request_validator.on_initiate_delay_and_notify,
                            account.get_common_fields().onboarding_complete,
                        )
                        .await?;
                        return Ok(AuthorizePrivilegedActionOutput::Authorized(initial_request));
                    }
                    AuthorizationStrategyDefinition::DelayAndNotify(
                        delay_and_notify_definition,
                    ) => {
                        let output = self
                            .initiate_delay_and_notify(
                                input.account_id,
                                account_type,
                                account.get_common_fields().properties.is_test_account,
                                privileged_action_definition.privileged_action_type,
                                &delay_and_notify_definition,
                                initial_request.clone(),
                                input.request_validator.on_initiate_delay_and_notify,
                                account.get_common_fields().onboarding_complete,
                            )
                            .await?
                            .map_or(
                                AuthorizePrivilegedActionOutput::Authorized(initial_request),
                                |r| AuthorizePrivilegedActionOutput::Pending(r),
                            );

                        return Ok(output);
                    }
                }
            }
            PrivilegedActionRequest::Continue(continue_request) => {
                match privileged_action_definition.authorization_strategy {
                    AuthorizationStrategyDefinition::HardwareProofOfPossession(_) => {
                        return Err(ServiceError::CannotContinueDefinedAuthorizationStrategyType);
                    }
                    AuthorizationStrategyDefinition::DelayAndNotify(_) => {
                        let initial_request: ReqT = self
                            .continue_delay_and_notify(
                                input.account_id,
                                account_type,
                                privileged_action_definition.privileged_action_type,
                                continue_request.clone(),
                            )
                            .await?;
                        return Ok(AuthorizePrivilegedActionOutput::Authorized(initial_request));
                    }
                }
            }
        }
    }

    /// Validates hardware proof-of-possession for a privileged action.
    ///
    /// This method ensures that sensitive operations have proper authorization by verifying
    /// that the request has signatures from both hardware and app authentication factors.
    /// This requirement can be bypassed during the onboarding process if configured.
    ///
    /// # Parameters
    /// * `hardware_proof_of_possession_definition` - Configuration for hardware proof verification
    /// * `key_proof` - The key claims containing signature information from authentication factors
    /// * `initial_request` - The original request payload
    /// * `on_initiate_hardware_proof_of_possession` - Optional callback function to execute during validation
    /// * `onboarding_complete` - Whether the account has completed onboarding
    ///
    /// # Returns
    /// * `Result<(), ServiceError>` - Success if validation passes
    #[instrument(skip(self, initial_request, on_initiate_hardware_proof_of_possession))]
    async fn initiate_hardware_proof_of_possession<ReqT, ErrT>(
        &self,
        hardware_proof_of_possession_definition: &HardwareProofOfPossessionDefinition,
        key_proof: &KeyClaims,
        initial_request: ReqT,
        on_initiate_hardware_proof_of_possession: Option<
            Box<dyn FnOnce(ReqT) -> Pin<Box<dyn Future<Output = Result<(), ErrT>> + Send>> + Send>,
        >,
        onboarding_complete: bool,
    ) -> Result<(), ServiceError>
    where
        ReqT: Clone,
        ErrT: Into<ApiError>,
    {
        let is_signed_by_both_factors = key_proof.app_signed && key_proof.hw_signed;
        let skip_during_onboarding = hardware_proof_of_possession_definition.skip_during_onboarding;

        // Authorization is successful if:
        //  1. Signed by both factors, OR
        //  2. Account is in onboarding, and the definition allows skipping this requirement during onboarding
        // Otherwise, error
        if !is_signed_by_both_factors && (onboarding_complete || !skip_during_onboarding) {
            return Err(ServiceError::FailedHardwareProofOfPossessionCheck);
        }

        if let Some(on_initiate_hardware_proof_of_possession) =
            on_initiate_hardware_proof_of_possession
        {
            on_initiate_hardware_proof_of_possession(initial_request.clone())
                .await
                .map_err(Into::into)?;
        }

        return Ok(());
    }

    /// Initiates a delay-and-notify privileged action flow.
    ///
    /// This method implements a time-delay security mechanism for sensitive operations. It creates
    /// a pending privileged action instance with a delay period, during which the user can cancel
    /// the action if it was initiated fraudulently. Notifications are sent to the user at the start
    /// and end of the delay period.
    ///
    /// The delay is bypassed if:
    /// - The delay duration is set to 0 seconds
    /// - The account is in onboarding and the action is configured to skip delays during onboarding
    ///
    /// # Parameters
    /// * `account_id` - The ID of the account initiating the privileged action
    /// * `account_type` - The type of the account (Full, Lite, etc.)
    /// * `privileged_action_type` - The type of privileged action being initiated
    /// * `delay_and_notify_definition` - Configuration for the delay period and notification behavior
    /// * `initial_request` - The original request payload that will be stored and executed after the delay
    /// * `on_initiate_delay_and_notify` - Optional callback function to execute when initiating the delay
    /// * `onboarding_complete` - Whether the account has completed onboarding
    ///
    /// # Returns
    /// * `Result<Option<PrivilegedActionResponse<RespT>>, ServiceError>` - None if the action can proceed
    ///   immediately, or a PrivilegedActionResponse if a delay period was initiated
    #[instrument(skip(self, initial_request, on_initiate_delay_and_notify))]
    async fn initiate_delay_and_notify<ReqT, RespT, ErrT>(
        &self,
        account_id: &AccountId,
        account_type: AccountType,
        is_test_account: bool,
        privileged_action_type: PrivilegedActionType,
        delay_and_notify_definition: &DelayAndNotifyDefinition,
        initial_request: ReqT,
        on_initiate_delay_and_notify: Option<
            Box<dyn FnOnce(ReqT) -> Pin<Box<dyn Future<Output = Result<(), ErrT>> + Send>> + Send>,
        >,
        onboarding_complete: bool,
    ) -> Result<Option<PrivilegedActionResponse<RespT>>, ServiceError>
    where
        ReqT: Serialize + Clone,
        ErrT: Into<ApiError>,
    {
        // Authorization is successful if:
        //  1. Delay duration is 0, OR
        //  2. Account is in onboarding, and the definition allows skipping this requirement during onboarding
        // Otherwise, start delay and notify process
        if delay_and_notify_definition.delay_duration_secs == 0 {
            return Ok(None);
        }

        if !onboarding_complete && delay_and_notify_definition.skip_during_onboarding {
            return Ok(None);
        }

        if !delay_and_notify_definition.concurrency
            && !self
                .privileged_action_repository
                .fetch_delay_notify_for_account_id::<Value>(
                    account_id,
                    Some(privileged_action_type.clone()),
                    Some(DelayAndNotifyStatus::Pending),
                )
                .await?
                .is_empty()
        {
            return Err(ServiceError::MultipleConcurrentInstancesConflict(
                privileged_action_type,
            ));
        }

        if let Some(on_initiate_delay_and_notify) = on_initiate_delay_and_notify {
            on_initiate_delay_and_notify(initial_request.clone())
                .await
                .map_err(Into::into)?;
        }

        let delay_end_time = self.clock.now_utc()
            + Duration::seconds(
                delay_and_notify_definition
                    .effective_delay_duration_secs(is_test_account)
                    .try_into()?,
            );

        let instance_record = PrivilegedActionInstanceRecord::new(
            account_id.clone(),
            privileged_action_type.clone(),
            AuthorizationStrategyRecord::DelayAndNotify(DelayAndNotifyRecord {
                status: DelayAndNotifyStatus::Pending,
                cancellation_token: gen_token(),
                completion_token: gen_token(),
                delay_end_time,
            }),
            initial_request,
        )?;

        self.privileged_action_repository
            .persist(&instance_record)
            .await?;

        self.notification_service
            .schedule_notifications(ScheduleNotificationsInput {
                account_id: account_id.clone(),
                notification_type: ScheduleNotificationType::PrivilegedActionPendingDelayNotify(
                    delay_end_time,
                ),
                payload: NotificationPayloadBuilder::default()
                    .privileged_action_pending_delay_period_payload(Some(
                        PrivilegedActionPendingDelayPeriodPayload {
                            privileged_action_instance_id: instance_record.id.clone(),
                            account_type: account_type.clone(),
                            privileged_action_type: instance_record.privileged_action_type.clone(),
                            delay_end_time,
                        },
                    ))
                    .privileged_action_completed_delay_period_payload(Some(
                        PrivilegedActionCompletedDelayPeriodPayload {
                            privileged_action_instance_id: instance_record.id.clone(),
                            account_type,
                            privileged_action_type: instance_record.privileged_action_type.clone(),
                        },
                    ))
                    .build()?,
            })
            .await?;

        Ok(Some(instance_record.into()))
    }

    /// Continues a delay-and-notify privileged action flow that was previously initiated.
    ///
    /// This method is called when the delay period for a privileged action is complete and
    /// the user wants to finalize the action. It validates:
    /// - The instance belongs to the specified account
    /// - The action type matches what was initiated
    /// - The authorization strategy is DelayAndNotify
    /// - The action is still in Pending status
    /// - The delay period has elapsed
    /// - The completion token provided matches the stored token
    ///
    /// If all validations pass, it updates the status to Completed and returns the original request.
    ///
    /// # Parameters
    /// * `account_id` - The ID of the account performing the privileged action
    /// * `account_type` - The type of the account (Full, Lite, etc.)
    /// * `privileged_action_type` - The type of privileged action being continued
    /// * `continue_request` - The request containing the privileged action instance and completion token
    ///
    /// # Returns
    /// * `Result<ReqT, ServiceError>` - The original request payload on success, or an error if validation fails
    #[instrument(skip(self, continue_request))]
    async fn continue_delay_and_notify<ReqT>(
        &self,
        account_id: &AccountId,
        account_type: AccountType,
        privileged_action_type: PrivilegedActionType,
        continue_request: ContinuePrivilegedActionRequest,
    ) -> Result<ReqT, ServiceError>
    where
        ReqT: Serialize + DeserializeOwned + Clone,
    {
        let instance_record: PrivilegedActionInstanceRecord<ReqT> = self
            .privileged_action_repository
            .fetch_by_id(&continue_request.privileged_action_instance.id)
            .await?;

        if instance_record.account_id != *account_id {
            return Err(ServiceError::RecordAccountIdForbidden);
        }

        if instance_record.privileged_action_type != privileged_action_type {
            return Err(ServiceError::RecordPrivilegedActionTypeConflict);
        }

        let AuthorizationStrategyRecord::DelayAndNotify(delay_and_notify_record) =
            instance_record.authorization_strategy
        else {
            return Err(ServiceError::RecordAuthorizationStrategyTypeUnexpected);
        };

        if delay_and_notify_record.status != DelayAndNotifyStatus::Pending {
            return Err(ServiceError::RecordDelayAndNotifyStatusConflict);
        }

        if self.clock.now_utc() < delay_and_notify_record.delay_end_time {
            return Err(ServiceError::DelayAndNotifyEndTimeInFuture);
        }

        let AuthorizationStrategyInput::DelayAndNotify(delay_and_notify_input) = continue_request
            .privileged_action_instance
            .authorization_strategy
            .clone()
        else {
            return Err(ServiceError::BadInputAuthorizationStrategyType);
        };

        if delay_and_notify_input.completion_token != delay_and_notify_record.completion_token {
            return Err(ServiceError::BadInputCompletionToken);
        }

        let updated_instance = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(
                DelayAndNotifyRecord {
                    status: DelayAndNotifyStatus::Completed,
                    ..delay_and_notify_record
                },
            ),
            request: instance_record.request.clone(),
            ..instance_record
        };

        self.privileged_action_repository
            .persist(&updated_instance)
            .await?;

        // TODO: send notification

        Ok(instance_record.request)
    }
}
