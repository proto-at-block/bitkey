use tracing::{event, Level};
use types::account::entities::{CommonAccountFields, Touchpoint};
use types::account::identifiers::TouchpointId;

use super::{
    FetchOrCreateEmailTouchpointInput, FetchOrCreatePhoneTouchpointInput, FetchTouchpointByIdInput,
    Service,
};
use crate::error::AccountError;

/// Top-level domains blocked for sanctions compliance.
/// Includes both Unicode and punycode (xn--) forms of IDN TLDs, since the
/// email regex only accepts ASCII domains and IDNs arrive in punycode.
const SANCTIONED_TLDS: &[&str] = &[
    ".cu",              // Cuba
    ".ir",              // Iran
    ".ایران",           // Iran (IDN)
    ".xn--mgba3a4f16a", // Iran (punycode)
    ".kp",              // North Korea
    ".sy",              // Syria
    ".ru",              // Russia
    ".su",              // Russia (Soviet Union legacy)
    ".рф",              // Russia (IDN)
    ".xn--p1ai",        // Russia (punycode)
    ".ua",              // Ukraine
    ".укр",             // Ukraine (IDN)
    ".xn--j1amh",       // Ukraine (punycode)
    ".bg",              // Bulgaria
    ".by",              // Belarus
    ".бел",             // Belarus (IDN)
    ".xn--90ais",       // Belarus (punycode)
    ".af",              // Afghanistan
    ".ve",              // Venezuela
];

fn has_sanctioned_tld(email: &str) -> bool {
    let email_lower = email.to_lowercase();
    SANCTIONED_TLDS.iter().any(|tld| email_lower.ends_with(tld))
}

impl Service {
    pub async fn fetch_touchpoint_by_id(
        &self,
        input: FetchTouchpointByIdInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;

        if let Some(touchpoint) = account.get_touchpoint_by_id(input.touchpoint_id) {
            Ok(touchpoint.to_owned())
        } else {
            event!(Level::ERROR, "Touchpoint not found",);
            Err(AccountError::TouchpointNotFound)
        }
    }

    pub async fn fetch_or_create_email_touchpoint(
        &self,
        input: FetchOrCreateEmailTouchpointInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        if has_sanctioned_tld(&input.email_address) {
            return Err(AccountError::SanctionedEmailDomain);
        }

        let account = self.account_repo.fetch(input.account_id).await?;

        if let Some(existing_touchpoint) =
            account.get_touchpoint_by_email_address(input.email_address.to_owned())
        {
            return Ok(existing_touchpoint.to_owned());
        }

        let common_fields = account.get_common_fields().to_owned();
        let mut touchpoints = account.get_common_fields().touchpoints.clone();

        // Purge inactive email touchpoints
        touchpoints.retain(|t| !matches!(t, Touchpoint::Email { active: false, .. }));

        let new_touchpoint =
            Touchpoint::new_email(TouchpointId::gen()?, input.email_address, false);

        // Add new touchpoint
        touchpoints.push(new_touchpoint.to_owned());

        let updated_account = account.update(CommonAccountFields {
            touchpoints,
            ..common_fields
        })?;
        self.account_repo.persist(&updated_account).await?;

        Ok(new_touchpoint)
    }

    pub async fn fetch_or_create_phone_touchpoint(
        &self,
        input: FetchOrCreatePhoneTouchpointInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;

        if let Some(existing_touchpoint) =
            account.get_touchpoint_by_phone_number(input.phone_number.to_owned())
        {
            if matches!(existing_touchpoint, Touchpoint::Phone{ country_code, .. }
                if *country_code == input.country_code)
            {
                return Ok(existing_touchpoint.to_owned());
            }
        }

        // Purge inactive phone touchpoints
        let common = account.get_common_fields().to_owned();
        let mut touchpoints = common.touchpoints;
        touchpoints.retain(|t| !matches!(t, Touchpoint::Phone { active: false, .. }));

        let new_touchpoint = Touchpoint::new_phone(
            TouchpointId::gen()?,
            input.phone_number,
            input.country_code,
            false,
        );

        // Add new touchpoint
        touchpoints.push(new_touchpoint.to_owned());
        let updated_account = account.update(CommonAccountFields {
            touchpoints,
            ..common
        })?;
        self.account_repo.persist(&updated_account).await?;

        Ok(new_touchpoint)
    }
}

#[cfg(test)]
mod tests {
    use super::has_sanctioned_tld;

    #[test]
    fn test_sanctioned_tlds_blocked() {
        assert!(has_sanctioned_tld("user@example.ru"));
        assert!(has_sanctioned_tld("user@example.cu"));
        assert!(has_sanctioned_tld("user@example.ir"));
        assert!(has_sanctioned_tld("user@example.kp"));
        assert!(has_sanctioned_tld("user@example.sy"));
        assert!(has_sanctioned_tld("user@example.su"));
        assert!(has_sanctioned_tld("user@example.ua"));
        assert!(has_sanctioned_tld("user@example.by"));
        assert!(has_sanctioned_tld("user@example.bg"));
        assert!(has_sanctioned_tld("user@example.af"));
        assert!(has_sanctioned_tld("user@example.ve"));
    }

    #[test]
    fn test_sanctioned_tlds_punycode_blocked() {
        assert!(has_sanctioned_tld("user@example.xn--mgba3a4f16a")); // .ایران
        assert!(has_sanctioned_tld("user@example.xn--p1ai")); // .рф
        assert!(has_sanctioned_tld("user@example.xn--j1amh")); // .укр
        assert!(has_sanctioned_tld("user@example.xn--90ais")); // .бел
    }

    #[test]
    fn test_sanctioned_tlds_case_insensitive() {
        assert!(has_sanctioned_tld("user@example.RU"));
        assert!(has_sanctioned_tld("user@example.Ru"));
        assert!(has_sanctioned_tld("user@EXAMPLE.CU"));
    }

    #[test]
    fn test_non_sanctioned_tlds_allowed() {
        assert!(!has_sanctioned_tld("user@example.com"));
        assert!(!has_sanctioned_tld("user@example.org"));
        assert!(!has_sanctioned_tld("user@example.co.uk"));
        assert!(!has_sanctioned_tld("user@example.de"));
        assert!(!has_sanctioned_tld("user@example.jp"));
    }

    #[test]
    fn test_sanctioned_tld_with_subdomains() {
        // Sanctioned TLD in subdomain only — should be allowed
        assert!(!has_sanctioned_tld("user@ru.example.com"));
        // Sanctioned TLD at end with subdomain — should be blocked
        assert!(has_sanctioned_tld("user@mail.example.ru"));
    }
}
