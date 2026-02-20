//! Canonical payload: `ACTIONPROOF␟1␟Action␟Field␟Value␟Current␟key1=val1,key2=val2`

use crate::actions::Action;
use crate::fields::Field;
use crate::validation::{validate_if_present, ValidationError};
use thiserror::Error;

pub const UNIT_SEPARATOR: u8 = 0x1F;
pub const BINDING_KEY_VALUE_SEPARATOR: u8 = b'=';
pub const BINDING_PAIR_SEPARATOR: u8 = b',';
pub const CANONICAL_VERSION: u8 = 1;
pub const CANONICAL_VERSION_STR: &str = "1";
pub const CANONICAL_MAGIC: &str = "ACTIONPROOF";

#[derive(Debug, Clone, PartialEq, Eq, Error)]
#[error("bindings not sorted alphabetically: '{second}' must come before '{first}'")]
pub struct BindingsNotSortedError {
    pub first: String,
    pub second: String,
}

fn contains_reserved_byte(s: &str) -> bool {
    s.bytes().any(|b| {
        b == UNIT_SEPARATOR || b == BINDING_KEY_VALUE_SEPARATOR || b == BINDING_PAIR_SEPARATOR
    })
}

fn validate_bindings(bindings: &[(&str, &str)]) -> Result<(), BuildError> {
    for window in bindings.windows(2) {
        let (first, second) = (window[0].0, window[1].0);
        if first > second {
            return Err(BindingsNotSortedError {
                first: first.to_string(),
                second: second.to_string(),
            }
            .into());
        }
        if first == second {
            return Err(BuildError::DuplicateBindingKey(first.to_string()));
        }
    }

    for (key, val) in bindings {
        if key.is_empty() {
            return Err(BuildError::EmptyBindingKey);
        }
        if contains_reserved_byte(key) {
            return Err(BuildError::InvalidBindingKey(key.to_string()));
        }
        if contains_reserved_byte(val) {
            return Err(BuildError::InvalidBindingValue(val.to_string()));
        }
    }

    Ok(())
}

#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum BuildError {
    #[error(transparent)]
    BindingsNotSorted(#[from] BindingsNotSortedError),
    #[error("binding key contains reserved character")]
    InvalidBindingKey(String),
    #[error("binding value contains reserved character")]
    InvalidBindingValue(String),
    #[error("binding key cannot be empty")]
    EmptyBindingKey,
    #[error("duplicate binding key: '{0}'")]
    DuplicateBindingKey(String),
    #[error("action {action} is not valid for field {field}")]
    InvalidActionForField { action: Action, field: Field },
    #[error("invalid value: {0}")]
    InvalidValue(#[from] ValidationError),
}

#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum ParseError {
    #[error("expected 7 parts separated by 0x1F, got {0}")]
    InvalidPartCount(usize),
    #[error("invalid magic: expected '{CANONICAL_MAGIC}', got '{0}'")]
    InvalidMagic(String),
    #[error("unsupported version: expected {CANONICAL_VERSION}, got {0}")]
    UnsupportedVersion(u8),
    #[error("invalid action: '{0}'")]
    InvalidAction(String),
    #[error("invalid field: '{0}'")]
    InvalidField(String),
    #[error("action '{action}' is not valid for field '{field}'")]
    InvalidActionForField { action: String, field: String },
    #[error("invalid UTF-8 in {0}")]
    InvalidUtf8(&'static str),
    #[error("invalid version format")]
    InvalidVersionFormat,
    #[error("invalid binding format: '{0}'")]
    InvalidBindingFormat(String),
    #[error("duplicate binding key: '{0}'")]
    DuplicateBindingKey(String),
    #[error(transparent)]
    BindingsNotSorted(#[from] BindingsNotSortedError),
    #[error("non-canonical version format: '{0}'")]
    NonCanonicalVersion(String),
    #[error("invalid value: {0}")]
    InvalidValue(String),
    #[error("invalid current: {0}")]
    InvalidCurrent(String),
}

/// Bindings must be sorted alphabetically by key.
pub fn build_payload(
    action: Action,
    field: Field,
    value: Option<&str>,
    current: Option<&str>,
    bindings: &[(&str, &str)],
) -> Result<Vec<u8>, BuildError> {
    if !field.is_valid_action(action) {
        return Err(BuildError::InvalidActionForField { action, field });
    }

    validate_if_present(value)?;
    validate_if_present(current)?;

    validate_bindings(bindings)?;

    let mut payload = Vec::with_capacity(256);

    payload.extend_from_slice(CANONICAL_MAGIC.as_bytes());
    payload.push(UNIT_SEPARATOR);
    payload.extend_from_slice(CANONICAL_VERSION_STR.as_bytes());
    payload.push(UNIT_SEPARATOR);

    payload.extend_from_slice(action.as_str().as_bytes());
    payload.push(UNIT_SEPARATOR);
    payload.extend_from_slice(field.as_str().as_bytes());
    payload.push(UNIT_SEPARATOR);

    if let Some(v) = value {
        payload.extend_from_slice(v.as_bytes());
    }
    payload.push(UNIT_SEPARATOR);

    if let Some(c) = current {
        payload.extend_from_slice(c.as_bytes());
    }
    payload.push(UNIT_SEPARATOR);

    for (i, (key, val)) in bindings.iter().enumerate() {
        if i > 0 {
            payload.push(BINDING_PAIR_SEPARATOR);
        }
        payload.extend_from_slice(key.as_bytes());
        payload.push(BINDING_KEY_VALUE_SEPARATOR);
        payload.extend_from_slice(val.as_bytes());
    }

    Ok(payload)
}

pub fn parse_payload(payload: &[u8]) -> Result<ParsedPayload<'_>, ParseError> {
    let parts: Vec<&[u8]> = payload.split(|&b| b == UNIT_SEPARATOR).collect();

    if parts.len() != 7 {
        return Err(ParseError::InvalidPartCount(parts.len()));
    }

    let magic = std::str::from_utf8(parts[0]).map_err(|_| ParseError::InvalidUtf8("magic"))?;
    if magic != CANONICAL_MAGIC {
        return Err(ParseError::InvalidMagic(magic.to_string()));
    }

    let version_str =
        std::str::from_utf8(parts[1]).map_err(|_| ParseError::InvalidUtf8("version"))?;
    // Require exact canonical format: single ASCII digit, no leading zeros or whitespace
    if version_str != CANONICAL_VERSION_STR {
        return match version_str.parse::<u8>() {
            Ok(v) if v == CANONICAL_VERSION => {
                Err(ParseError::NonCanonicalVersion(version_str.to_string()))
            }
            Ok(v) => Err(ParseError::UnsupportedVersion(v)),
            Err(_) => Err(ParseError::InvalidVersionFormat),
        };
    }

    let action_str =
        std::str::from_utf8(parts[2]).map_err(|_| ParseError::InvalidUtf8("action"))?;
    let action = action_str
        .parse()
        .map_err(|_| ParseError::InvalidAction(action_str.to_string()))?;

    let field_str = std::str::from_utf8(parts[3]).map_err(|_| ParseError::InvalidUtf8("field"))?;
    let field: Field = field_str
        .parse()
        .map_err(|_| ParseError::InvalidField(field_str.to_string()))?;

    if !field.is_valid_action(action) {
        return Err(ParseError::InvalidActionForField {
            action: action_str.to_string(),
            field: field_str.to_string(),
        });
    }

    let value = std::str::from_utf8(parts[4]).map_err(|_| ParseError::InvalidUtf8("value"))?;
    let value = (!value.is_empty()).then_some(value);

    let current = std::str::from_utf8(parts[5]).map_err(|_| ParseError::InvalidUtf8("current"))?;
    let current = (!current.is_empty()).then_some(current);

    if let Some(v) = value {
        crate::validation::validate_value(v)
            .map_err(|e| ParseError::InvalidValue(e.to_string()))?;
    }
    if let Some(c) = current {
        crate::validation::validate_value(c)
            .map_err(|e| ParseError::InvalidCurrent(e.to_string()))?;
    }

    let bindings_str =
        std::str::from_utf8(parts[6]).map_err(|_| ParseError::InvalidUtf8("bindings"))?;
    let bindings = parse_bindings(bindings_str)?;

    Ok(ParsedPayload {
        action,
        field,
        value,
        current,
        bindings,
    })
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParsedPayload<'a> {
    pub action: Action,
    pub field: Field,
    pub value: Option<&'a str>,
    pub current: Option<&'a str>,
    pub bindings: Vec<(&'a str, &'a str)>,
}

fn parse_bindings(s: &str) -> Result<Vec<(&str, &str)>, ParseError> {
    if s.is_empty() {
        return Ok(Vec::new());
    }

    let mut bindings = Vec::new();
    for pair in s.split(BINDING_PAIR_SEPARATOR as char) {
        let (key, value) = pair
            .split_once(BINDING_KEY_VALUE_SEPARATOR as char)
            .ok_or_else(|| ParseError::InvalidBindingFormat(pair.to_string()))?;
        bindings.push((key, value));
    }

    validate_parsed_bindings(&bindings)?;

    Ok(bindings)
}

fn validate_parsed_bindings(bindings: &[(&str, &str)]) -> Result<(), ParseError> {
    for window in bindings.windows(2) {
        let (first, second) = (window[0].0, window[1].0);
        if first > second {
            return Err(BindingsNotSortedError {
                first: first.to_string(),
                second: second.to_string(),
            }
            .into());
        }
        if first == second {
            return Err(ParseError::DuplicateBindingKey(first.to_string()));
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn build_payload_formats() {
        // With value only
        let p = build_payload(
            Action::Add,
            Field::RecoveryContacts,
            Some("Alice"),
            None,
            &[("tb", "XYZ")],
        )
        .unwrap();
        assert_eq!(
            p,
            b"ACTIONPROOF\x1f1\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=XYZ"
        );

        // With value and current
        let p = build_payload(
            Action::Set,
            Field::SpendWithoutHardware,
            Some("500 USD"),
            Some("250 USD"),
            &[("tb", "ABC")],
        )
        .unwrap();
        assert_eq!(
            p,
            b"ACTIONPROOF\x1f1\x1fSet\x1fSpendWithoutHardware\x1f500 USD\x1f250 USD\x1ftb=ABC"
        );

        // No value (disable)
        let p = build_payload(
            Action::Disable,
            Field::SpendWithoutHardware,
            None,
            None,
            &[("tb", "ABC")],
        )
        .unwrap();
        assert_eq!(
            p,
            b"ACTIONPROOF\x1f1\x1fDisable\x1fSpendWithoutHardware\x1f\x1f\x1ftb=ABC"
        );

        // Multiple bindings (alphabetically sorted)
        let p = build_payload(
            Action::Add,
            Field::RecoveryContacts,
            Some("Alice"),
            None,
            &[("eid", "ABC"), ("tb", "XYZ")],
        )
        .unwrap();
        assert_eq!(
            p,
            b"ACTIONPROOF\x1f1\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1feid=ABC,tb=XYZ"
        );
    }

    #[test]
    fn build_rejects_unsorted_bindings() {
        let result = build_payload(
            Action::Add,
            Field::RecoveryContacts,
            Some("Alice"),
            None,
            &[("tb", "XYZ"), ("eid", "ABC")],
        );
        assert!(
            matches!(result, Err(BuildError::BindingsNotSorted(ref e)) if e.first == "tb" && e.second == "eid")
        );
    }

    #[test]
    fn build_validates_values() {
        // Validation is tested thoroughly in validation.rs; just verify it's wired up
        for invalid in ["Hello\x00World", "p\u{0430}ypal", &"a".repeat(129)] {
            assert!(matches!(
                build_payload(
                    Action::Add,
                    Field::RecoveryContacts,
                    Some(invalid),
                    None,
                    &[("tb", "X")]
                ),
                Err(BuildError::InvalidValue(_))
            ));
        }
    }

    #[test]
    fn parse_roundtrip() {
        let original = build_payload(
            Action::Set,
            Field::SpendWithoutHardware,
            Some("500 USD"),
            Some("250 USD"),
            &[("eid", "ABC"), ("tb", "XYZ")],
        )
        .unwrap();
        let parsed = parse_payload(&original).expect("should parse");
        assert_eq!(
            (parsed.action, parsed.field),
            (Action::Set, Field::SpendWithoutHardware)
        );
        assert_eq!(
            (parsed.value, parsed.current),
            (Some("500 USD"), Some("250 USD"))
        );
        assert_eq!(parsed.bindings, vec![("eid", "ABC"), ("tb", "XYZ")]);
    }

    #[test]
    fn parse_errors() {
        let cases: &[(&[u8], &str)] = &[
            (
                b"INVALID\x1f1\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X",
                "InvalidMagic",
            ),
            (
                b"ACTIONPROOF\x1f99\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X",
                "UnsupportedVersion",
            ),
            (b"ACTIONPROOF\x1f1\x1fAdd", "InvalidPartCount"),
            (
                b"ACTIONPROOF\x1f1\x1fBadAction\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X",
                "InvalidAction",
            ),
            (
                b"ACTIONPROOF\x1f1\x1fAdd\x1fBadField\x1fAlice\x1f\x1ftb=X",
                "InvalidField",
            ),
            (
                b"ACTIONPROOF\x1f1\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X,invalid",
                "InvalidBindingFormat",
            ),
            (
                b"ACTIONPROOF\x1f1\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X,eid=Y",
                "BindingsNotSorted",
            ),
        ];
        for (payload, expected) in cases {
            let err = parse_payload(payload).unwrap_err();
            assert!(
                format!("{err:?}").contains(expected),
                "expected {expected} for {err:?}"
            );
        }
    }

    #[test]
    fn parse_version_strictness() {
        // Non-canonical forms that parse to v1 are rejected
        for (payload, err_type) in [
            (
                &b"ACTIONPROOF\x1f01\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X"[..],
                "NonCanonicalVersion",
            ),
            (
                b"ACTIONPROOF\x1f+1\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X",
                "NonCanonicalVersion",
            ),
            (
                b"ACTIONPROOF\x1f 1\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X",
                "InvalidVersionFormat",
            ),
            (
                b"ACTIONPROOF\x1fone\x1fAdd\x1fRecoveryContacts\x1fAlice\x1f\x1ftb=X",
                "InvalidVersionFormat",
            ),
        ] {
            let err = parse_payload(payload).unwrap_err();
            assert!(
                format!("{err:?}").contains(err_type),
                "expected {err_type} for {err:?}"
            );
        }
    }

    #[test]
    fn all_action_field_combinations_roundtrip() {
        for action in Action::all() {
            for field in Field::all() {
                if !field.is_valid_action(*action) {
                    continue;
                }
                let payload =
                    build_payload(*action, *field, Some("test"), None, &[("tb", "TEST")]).unwrap();
                let parsed = parse_payload(&payload)
                    .unwrap_or_else(|e| panic!("{action:?}/{field:?}: {e:?}"));
                assert_eq!((parsed.action, parsed.field), (*action, *field));
            }
        }
    }
}
