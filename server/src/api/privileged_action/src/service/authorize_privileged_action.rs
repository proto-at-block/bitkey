use std::future::Future;

use authn_authz::key_claims::KeyClaims;
use errors::ApiError;
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

#[derive(Clone, Debug)]
pub struct AuthorizePrivilegedActionInput<'a, P, R, S, T>
where
    R: FnOnce(P) -> S,
    S: Future<Output = Result<(), T>>,
    T: Into<ApiError>,
{
    pub account_id: &'a AccountId,
    pub privileged_action_definition: &'a PrivilegedActionDefinition,
    pub key_proof: &'a KeyClaims,
    pub privileged_action_request: &'a PrivilegedActionRequest<P>,
    pub initial_request_validator: R,
}

#[derive(Debug)]
pub enum AuthorizePrivilegedActionOutput<P, Q> {
    Authorized(P),
    Pending(PrivilegedActionResponse<Q>),
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn authorize_privileged_action<P, Q, R, S, T>(
        &self,
        input: AuthorizePrivilegedActionInput<P, R, S, T, '_>,
    ) -> Result<AuthorizePrivilegedActionOutput<P, Q>, ServiceError>
    where
        P: Serialize + DeserializeOwned + Clone,
        R: FnOnce(P) -> S,
        S: Future<Output = Result<(), T>>,
        T: Into<ApiError>,
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
                                privileged_action_definition.privileged_action_type,
                                &delay_and_notify_definition,
                                initial_request.clone(),
                                input.initial_request_validator,
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
                        let initial_request: P = self
                            .continue_delay_and_notify(
                                input.account_id,
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

    #[instrument(skip(self))]
    async fn initiate_hardware_proof_of_possession(
        &self,
        hardware_proof_of_possession_definition: &HardwareProofOfPossessionDefinition,
        key_proof: &KeyClaims,
        onboarding_complete: bool,
    ) -> Result<(), ServiceError> {
        let is_signed_by_both_factors = key_proof.app_signed && key_proof.hw_signed;
        let skip_during_onboarding = hardware_proof_of_possession_definition.skip_during_onboarding;

        // Authorization is successful if:
        //  1. Signed by both factors, OR
        //  2. Account is in onboarding, and the definition allows skipping this requirement during onboarding
        if is_signed_by_both_factors || (!onboarding_complete && skip_during_onboarding) {
            return Ok(());
        }

        Err(ServiceError::FailedHardwareProofOfPossessionCheck)
    }

    #[instrument(skip(self, initial_request, initial_request_validator))]
    async fn initiate_delay_and_notify<P, Q, R, S, T>(
        &self,
        account_id: &AccountId,
        privileged_action_type: PrivilegedActionType,
        delay_and_notify_definition: &DelayAndNotifyDefinition,
        initial_request: P,
        initial_request_validator: R,
    ) -> Result<Option<PrivilegedActionResponse<Q>>, ServiceError>
    where
        P: Serialize + Clone,
        R: FnOnce(P) -> S,
        S: Future<Output = Result<(), T>>,
        T: Into<ApiError>,
    {
        if delay_and_notify_definition.delay_duration_secs == 0 {
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

        initial_request_validator(initial_request.clone())
            .await
            .map_err(Into::into)?;

        let instance_record = PrivilegedActionInstanceRecord::new(
            account_id.clone(),
            privileged_action_type.clone(),
            AuthorizationStrategyRecord::DelayAndNotify(DelayAndNotifyRecord {
                status: DelayAndNotifyStatus::Pending,
                cancellation_token: gen_token(),
                completion_token: gen_token(),
                delay_end_time: self.clock.now_utc()
                    + Duration::seconds(
                        delay_and_notify_definition.delay_duration_secs.try_into()?,
                    ),
            }),
            initial_request,
        )?;

        self.privileged_action_repository
            .persist(&instance_record)
            .await?;

        // TODO: schedule notifications [W-8970]

        Ok(Some(instance_record.into()))
    }

    #[instrument(skip(self, continue_request))]
    async fn continue_delay_and_notify<T>(
        &self,
        account_id: &AccountId,
        privileged_action_type: PrivilegedActionType,
        continue_request: ContinuePrivilegedActionRequest,
    ) -> Result<T, ServiceError>
    where
        T: Serialize + DeserializeOwned + Clone,
    {
        let instance_record: PrivilegedActionInstanceRecord<T> = self
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

        // TODO: send notification [W-8970]

        Ok(instance_record.request)
    }
}
