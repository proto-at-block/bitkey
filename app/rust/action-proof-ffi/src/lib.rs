// Allow empty line warnings in UniFFI-generated scaffolding code
#![allow(clippy::empty_line_after_doc_comments)]

use action_proof::{BuildError, ValidationError};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Action {
    SetSpendWithoutHardware,
    DisableSpendWithoutHardware,
    SetVerificationThreshold,
    SetRecoveryEmail,
    DisableRecoveryEmail,
    SetRecoveryPhone,
    DisableRecoveryPhone,
    SetRecoveryPushNotifications,
    DisableRecoveryPushNotifications,
    AddRecoveryContact,
    RemoveRecoveryContact,
    RemoveRecoveryCustomer,
    AddBeneficiary,
    RemoveBeneficiary,
    RemoveBenefactor,
    CreateSpendingKeyset,
    RotateSpendingKeyset,
    DeleteAccount,
    UpdateDescriptorBackups,
    CreateLostAppRecovery,
    CreateLostHardwareRecovery,
    CancelLostAppRecovery,
    CancelLostHardwareRecovery,
    CancelConflictingRecovery,
    SendRecoveryVerificationCode,
    VerifyRecoveryVerificationCode,
    RotateAppAuthKeys,
}

impl From<Action> for action_proof::Action {
    fn from(a: Action) -> Self {
        match a {
            Action::SetSpendWithoutHardware => action_proof::Action::SetSpendWithoutHardware,
            Action::DisableSpendWithoutHardware => {
                action_proof::Action::DisableSpendWithoutHardware
            }
            Action::SetVerificationThreshold => action_proof::Action::SetVerificationThreshold,
            Action::SetRecoveryEmail => action_proof::Action::SetRecoveryEmail,
            Action::DisableRecoveryEmail => action_proof::Action::DisableRecoveryEmail,
            Action::SetRecoveryPhone => action_proof::Action::SetRecoveryPhone,
            Action::DisableRecoveryPhone => action_proof::Action::DisableRecoveryPhone,
            Action::SetRecoveryPushNotifications => {
                action_proof::Action::SetRecoveryPushNotifications
            }
            Action::DisableRecoveryPushNotifications => {
                action_proof::Action::DisableRecoveryPushNotifications
            }
            Action::AddRecoveryContact => action_proof::Action::AddRecoveryContact,
            Action::RemoveRecoveryContact => action_proof::Action::RemoveRecoveryContact,
            Action::RemoveRecoveryCustomer => action_proof::Action::RemoveRecoveryCustomer,
            Action::AddBeneficiary => action_proof::Action::AddBeneficiary,
            Action::RemoveBeneficiary => action_proof::Action::RemoveBeneficiary,
            Action::RemoveBenefactor => action_proof::Action::RemoveBenefactor,
            Action::CreateSpendingKeyset => action_proof::Action::CreateSpendingKeyset,
            Action::RotateSpendingKeyset => action_proof::Action::RotateSpendingKeyset,
            Action::DeleteAccount => action_proof::Action::DeleteAccount,
            Action::UpdateDescriptorBackups => action_proof::Action::UpdateDescriptorBackups,
            Action::CreateLostAppRecovery => action_proof::Action::CreateLostAppRecovery,
            Action::CreateLostHardwareRecovery => action_proof::Action::CreateLostHardwareRecovery,
            Action::CancelLostAppRecovery => action_proof::Action::CancelLostAppRecovery,
            Action::CancelLostHardwareRecovery => action_proof::Action::CancelLostHardwareRecovery,
            Action::CancelConflictingRecovery => action_proof::Action::CancelConflictingRecovery,
            Action::SendRecoveryVerificationCode => {
                action_proof::Action::SendRecoveryVerificationCode
            }
            Action::VerifyRecoveryVerificationCode => {
                action_proof::Action::VerifyRecoveryVerificationCode
            }
            Action::RotateAppAuthKeys => action_proof::Action::RotateAppAuthKeys,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ContextBinding {
    TokenBinding,
    EntityId,
    Nonce,
}

impl From<ContextBinding> for action_proof::ContextBinding {
    fn from(b: ContextBinding) -> Self {
        match b {
            ContextBinding::TokenBinding => action_proof::ContextBinding::TokenBinding,
            ContextBinding::EntityId => action_proof::ContextBinding::EntityId,
            ContextBinding::Nonce => action_proof::ContextBinding::Nonce,
        }
    }
}

/// Returns the string key for a context binding.
/// Delegates to core library - no string duplication.
pub fn context_binding_key(binding: ContextBinding) -> String {
    action_proof::ContextBinding::from(binding)
        .key()
        .to_string()
}

pub struct ContextBindingPair {
    pub key: String,
    pub value: String,
}

#[derive(Debug, thiserror::Error)]
pub enum ActionProofError {
    #[error("value exceeds maximum length")]
    TooLong,
    #[error("value is empty")]
    Empty,
    #[error("value contains control character")]
    ControlCharacter,
    #[error("value contains unit separator")]
    ContainsDelimiter,
    #[error("value contains dangerous Unicode character")]
    DangerousCharacter,
    #[error("value mixes Latin with confusable scripts")]
    MixedScripts,
    #[error("binding key is empty")]
    EmptyBindingKey,
    #[error("binding key contains reserved characters")]
    InvalidBindingKey,
    #[error("binding value contains reserved characters")]
    InvalidBindingValue,
    #[error("duplicate binding key")]
    DuplicateBindingKey,
    #[error("bindings not sorted")]
    BindingsNotSorted,
}

impl From<ValidationError> for ActionProofError {
    fn from(e: ValidationError) -> Self {
        match e {
            ValidationError::TooLong { .. } => ActionProofError::TooLong,
            ValidationError::Empty => ActionProofError::Empty,
            ValidationError::ControlCharacter { .. } => ActionProofError::ControlCharacter,
            ValidationError::ContainsDelimiter { .. } => ActionProofError::ContainsDelimiter,
            ValidationError::DangerousCharacter { .. } => ActionProofError::DangerousCharacter,
            ValidationError::MixedScripts => ActionProofError::MixedScripts,
        }
    }
}

impl From<BuildError> for ActionProofError {
    fn from(e: BuildError) -> Self {
        match e {
            BuildError::BindingsNotSorted(_) => ActionProofError::BindingsNotSorted,
            BuildError::InvalidBindingKey(_) => ActionProofError::InvalidBindingKey,
            BuildError::InvalidBindingValue(_) => ActionProofError::InvalidBindingValue,
            BuildError::EmptyBindingKey => ActionProofError::EmptyBindingKey,
            BuildError::DuplicateBindingKey(_) => ActionProofError::DuplicateBindingKey,
            BuildError::InvalidValue(v) => v.into(),
        }
    }
}

pub fn compute_token_binding(jwt: String) -> String {
    action_proof::compute_token_binding(&jwt)
}

pub fn validate_value(value: String) -> Result<(), ActionProofError> {
    action_proof::validate_value(&value).map_err(ActionProofError::from)
}

pub fn build_payload(
    action: Action,
    value: Option<String>,
    bindings: Vec<ContextBindingPair>,
) -> Result<Vec<u8>, ActionProofError> {
    let mut bindings = bindings;
    bindings.sort_by(|a, b| a.key.cmp(&b.key));

    let binding_tuples: Vec<(&str, &str)> = bindings
        .iter()
        .map(|b| (b.key.as_str(), b.value.as_str()))
        .collect();

    action_proof::build_payload(action.into(), value.as_deref(), &binding_tuples)
        .map_err(ActionProofError::from)
}

uniffi::include_scaffolding!("action-proof");

#[cfg(test)]
mod tests {
    use super::*;

    // FFI-specific test: verifies the auto-sorting behavior unique to this layer
    #[test]
    fn build_payload_auto_sorts_bindings() {
        // Bindings provided out of order should be auto-sorted
        let bindings = vec![
            ContextBindingPair {
                key: "tb".to_string(),
                value: "X".to_string(),
            },
            ContextBindingPair {
                key: "eid".to_string(),
                value: "Y".to_string(),
            },
        ];
        let payload = build_payload(
            Action::AddRecoveryContact,
            Some("Alice".to_string()),
            bindings,
        )
        .unwrap();
        assert!(!payload.is_empty());
    }

    // Enum completeness tests: ensure FFI enums stay in sync with core
    #[test]
    fn all_actions_mapped() {
        for action in [
            Action::SetSpendWithoutHardware,
            Action::DisableSpendWithoutHardware,
            Action::SetVerificationThreshold,
            Action::SetRecoveryEmail,
            Action::DisableRecoveryEmail,
            Action::SetRecoveryPhone,
            Action::DisableRecoveryPhone,
            Action::SetRecoveryPushNotifications,
            Action::DisableRecoveryPushNotifications,
            Action::AddRecoveryContact,
            Action::RemoveRecoveryContact,
            Action::RemoveRecoveryCustomer,
            Action::AddBeneficiary,
            Action::RemoveBeneficiary,
            Action::RemoveBenefactor,
            Action::CreateSpendingKeyset,
            Action::RotateSpendingKeyset,
            Action::DeleteAccount,
            Action::UpdateDescriptorBackups,
            Action::CreateLostAppRecovery,
            Action::CreateLostHardwareRecovery,
            Action::CancelLostAppRecovery,
            Action::CancelLostHardwareRecovery,
            Action::CancelConflictingRecovery,
            Action::SendRecoveryVerificationCode,
            Action::VerifyRecoveryVerificationCode,
            Action::RotateAppAuthKeys,
        ] {
            let _: action_proof::Action = action.into();
        }
    }

    #[test]
    fn all_context_bindings_mapped() {
        for binding in [
            ContextBinding::TokenBinding,
            ContextBinding::EntityId,
            ContextBinding::Nonce,
        ] {
            let _: action_proof::ContextBinding = binding.into();
        }
    }

    #[test]
    fn context_binding_key_delegates_to_core() {
        assert_eq!(context_binding_key(ContextBinding::TokenBinding), "tb");
        assert_eq!(context_binding_key(ContextBinding::EntityId), "eid");
        assert_eq!(context_binding_key(ContextBinding::Nonce), "n");
    }
}
