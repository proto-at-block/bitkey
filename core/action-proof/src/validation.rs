//! Value validation - UTF-8 with homoglyph protection.

use crate::payload::UNIT_SEPARATOR;
use thiserror::Error;

#[derive(Debug, Clone, Error, PartialEq, Eq)]
pub enum ValidationError {
    #[error("value contains unit separator (0x1F) at position {position}")]
    ContainsDelimiter { position: usize },

    #[error("value contains control character at position {position}: byte 0x{byte:02X}")]
    ControlCharacter { position: usize, byte: u8 },

    #[error("value contains dangerous Unicode character at position {position}")]
    DangerousCharacter { position: usize },

    #[error("value mixes Latin with confusable script (homoglyph risk)")]
    MixedScripts,

    #[error("value is empty")]
    Empty,

    #[error("value exceeds maximum length of {max} bytes")]
    TooLong { max: usize },
}

pub const MAX_VALUE_LENGTH: usize = 128;

fn check_unicode_safety(value: &str) -> Result<(), ValidationError> {
    let mut has_latin = false;
    let mut has_cyrillic = false;
    let mut has_greek = false;
    let mut has_armenian = false;

    for (position, c) in value.chars().enumerate() {
        if matches!(c, '\u{200B}'..='\u{200D}' | '\u{202A}'..='\u{202E}' | '\u{FEFF}') {
            return Err(ValidationError::DangerousCharacter { position });
        }

        match c {
            'a'..='z' | 'A'..='Z' => has_latin = true,
            '\u{0400}'..='\u{04FF}' => has_cyrillic = true,
            '\u{0370}'..='\u{03FF}' => has_greek = true,
            '\u{0530}'..='\u{058F}' => has_armenian = true,
            _ => {}
        }
    }

    if has_latin && (has_cyrillic || has_greek || has_armenian) {
        return Err(ValidationError::MixedScripts);
    }

    Ok(())
}

pub fn validate_value(value: &str) -> Result<(), ValidationError> {
    if value.is_empty() {
        return Err(ValidationError::Empty);
    }

    if value.len() > MAX_VALUE_LENGTH {
        return Err(ValidationError::TooLong {
            max: MAX_VALUE_LENGTH,
        });
    }

    for (position, byte) in value.bytes().enumerate() {
        if byte < 0x20 || byte == 0x7F {
            return if byte == UNIT_SEPARATOR {
                Err(ValidationError::ContainsDelimiter { position })
            } else {
                Err(ValidationError::ControlCharacter { position, byte })
            };
        }
    }

    check_unicode_safety(value)
}

/// Validates an optional value. `None` and `Some("")` are both considered valid
/// and represent "no value provided". Non-empty strings are validated with `validate_value`.
pub fn validate_if_present(value: Option<&str>) -> Result<(), ValidationError> {
    value
        .filter(|v| !v.is_empty())
        .map_or(Ok(()), validate_value)
}

pub fn is_valid_value(s: &str) -> bool {
    validate_value(s).is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn valid_inputs() {
        for valid in [
            "Alice",
            "500.00 USD",
            "Hello, World!",
            "test@example.com",
            "+1 (555) 123-4567",
            "田中",
            "Müller",
            "Пётр",
            "Ελληνικά",
            "👋🎉",                        // Unicode scripts
            &"a".repeat(MAX_VALUE_LENGTH), // Max length
        ] {
            assert!(validate_value(valid).is_ok(), "should accept: {valid:?}");
        }
        // All printable ASCII
        let all_printable: String = (0x20u8..=0x7E).map(|b| b as char).collect();
        assert!(validate_value(&all_printable).is_ok());
    }

    #[test]
    fn rejects_empty() {
        assert_eq!(validate_value(""), Err(ValidationError::Empty));
    }

    #[test]
    fn rejects_control_chars() {
        for (input, expected_byte) in [
            ("Hello\x1FWorld", 0x1F), // unit separator
            ("Hello\x00World", 0x00), // NUL
            ("Hello\nWorld", 0x0A),   // newline
            ("Hello\x7FWorld", 0x7F), // DEL
        ] {
            let err = validate_value(input).unwrap_err();
            let matches = match &err {
                ValidationError::ContainsDelimiter { .. } => expected_byte == 0x1F,
                ValidationError::ControlCharacter { byte, .. } => *byte == expected_byte,
                _ => false,
            };
            assert!(
                matches,
                "expected control char 0x{expected_byte:02X}, got {err:?}"
            );
        }
    }

    #[test]
    fn rejects_dangerous_unicode() {
        for input in ["pay\u{200B}pal", "hello\u{202E}txt", "\u{FEFF}hello"] {
            assert!(matches!(
                validate_value(input),
                Err(ValidationError::DangerousCharacter { .. })
            ));
        }
    }

    #[test]
    fn rejects_mixed_scripts() {
        assert!(matches!(
            validate_value("p\u{0430}ypal"),
            Err(ValidationError::MixedScripts)
        ));
    }

    #[test]
    fn rejects_too_long() {
        assert!(matches!(
            validate_value(&"a".repeat(MAX_VALUE_LENGTH + 1)),
            Err(ValidationError::TooLong { .. })
        ));
    }

    #[test]
    fn optional_value_handling() {
        assert!(validate_if_present(None).is_ok());
        assert!(validate_if_present(Some("")).is_ok());
        assert!(validate_if_present(Some("Alice")).is_ok());
        assert!(matches!(
            validate_if_present(Some("x\x1Fy")),
            Err(ValidationError::ContainsDelimiter { .. })
        ));
    }
}
