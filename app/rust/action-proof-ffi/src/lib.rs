// Allow empty line warnings in UniFFI-generated scaffolding code
#![allow(clippy::empty_line_after_doc_comments)]

use action_proof::{BuildError, ValidationError};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Action {
    Add,
    Remove,
    Set,
    Disable,
    Accept,
}

impl From<Action> for action_proof::Action {
    fn from(a: Action) -> Self {
        match a {
            Action::Add => action_proof::Action::Add,
            Action::Remove => action_proof::Action::Remove,
            Action::Set => action_proof::Action::Set,
            Action::Disable => action_proof::Action::Disable,
            Action::Accept => action_proof::Action::Accept,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Field {
    SpendWithoutHardware,
    VerificationThreshold,
    RecoveryEmail,
    RecoveryPhone,
    RecoveryPushNotifications,
    RecoveryContacts,
    Beneficiaries,
    RecoveryContactsInvite,
    BeneficiariesInvite,
}

impl From<Field> for action_proof::Field {
    fn from(f: Field) -> Self {
        match f {
            Field::SpendWithoutHardware => action_proof::Field::SpendWithoutHardware,
            Field::VerificationThreshold => action_proof::Field::VerificationThreshold,
            Field::RecoveryEmail => action_proof::Field::RecoveryEmail,
            Field::RecoveryPhone => action_proof::Field::RecoveryPhone,
            Field::RecoveryPushNotifications => action_proof::Field::RecoveryPushNotifications,
            Field::RecoveryContacts => action_proof::Field::RecoveryContacts,
            Field::Beneficiaries => action_proof::Field::Beneficiaries,
            Field::RecoveryContactsInvite => action_proof::Field::RecoveryContactsInvite,
            Field::BeneficiariesInvite => action_proof::Field::BeneficiariesInvite,
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
    #[error("action is not valid for the specified field")]
    InvalidActionForField,
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
            BuildError::InvalidActionForField { .. } => ActionProofError::InvalidActionForField,
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
    field: Field,
    value: Option<String>,
    current: Option<String>,
    bindings: Vec<ContextBindingPair>,
) -> Result<Vec<u8>, ActionProofError> {
    let mut bindings = bindings;
    bindings.sort_by(|a, b| a.key.cmp(&b.key));

    let binding_tuples: Vec<(&str, &str)> = bindings
        .iter()
        .map(|b| (b.key.as_str(), b.value.as_str()))
        .collect();

    action_proof::build_payload(
        action.into(),
        field.into(),
        value.as_deref(),
        current.as_deref(),
        &binding_tuples,
    )
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
            Action::Add,
            Field::RecoveryContacts,
            Some("Alice".to_string()),
            None,
            bindings,
        )
        .unwrap();
        assert!(!payload.is_empty());
    }

    // Enum completeness tests: ensure FFI enums stay in sync with core
    #[test]
    fn all_actions_mapped() {
        for action in [
            Action::Add,
            Action::Remove,
            Action::Set,
            Action::Disable,
            Action::Accept,
        ] {
            let _: action_proof::Action = action.into();
        }
    }

    #[test]
    fn all_fields_mapped() {
        for field in [
            Field::SpendWithoutHardware,
            Field::VerificationThreshold,
            Field::RecoveryEmail,
            Field::RecoveryPhone,
            Field::RecoveryPushNotifications,
            Field::RecoveryContacts,
            Field::Beneficiaries,
            Field::RecoveryContactsInvite,
            Field::BeneficiariesInvite,
        ] {
            let _: action_proof::Field = field.into();
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
