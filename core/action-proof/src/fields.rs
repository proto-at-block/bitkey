use crate::actions::Action;

define_enum!(Field, ParseFieldError, "invalid field: {0}" {
    SpendWithoutHardware,
    VerificationThreshold,
    RecoveryEmail,
    RecoveryPhone,
    RecoveryPushNotifications,
    RecoveryContacts,
    Beneficiaries,
    RecoveryContactsInvite,
    BeneficiariesInvite
});

impl Field {
    pub const fn display_name(&self) -> &'static str {
        match self {
            Self::SpendWithoutHardware => "Spend Without Hardware",
            Self::VerificationThreshold => "Verification Threshold",
            Self::RecoveryEmail => "Recovery Email",
            Self::RecoveryPhone => "Recovery Phone",
            Self::RecoveryPushNotifications => "Recovery Push Notifications",
            Self::RecoveryContacts => "Recovery Contacts",
            Self::Beneficiaries => "Beneficiaries",
            Self::RecoveryContactsInvite => "Recovery Contacts Invite",
            Self::BeneficiariesInvite => "Beneficiaries Invite",
        }
    }

    pub const fn valid_actions(&self) -> &'static [Action] {
        match self {
            Self::SpendWithoutHardware => &[Action::Set, Action::Disable],
            Self::VerificationThreshold => &[Action::Set],
            Self::RecoveryEmail => &[Action::Set, Action::Add, Action::Disable],
            Self::RecoveryPhone => &[Action::Set, Action::Add, Action::Disable],
            Self::RecoveryPushNotifications => &[Action::Add, Action::Disable],
            Self::RecoveryContacts => &[Action::Add, Action::Remove],
            Self::Beneficiaries => &[Action::Add, Action::Remove],
            Self::RecoveryContactsInvite => &[Action::Accept],
            Self::BeneficiariesInvite => &[Action::Accept],
        }
    }

    pub const fn value_format(&self) -> Option<ValueFormat> {
        match self {
            Self::SpendWithoutHardware => Some(ValueFormat::Money),
            Self::VerificationThreshold => Some(ValueFormat::VerificationPolicy),
            Self::RecoveryEmail => Some(ValueFormat::Email),
            Self::RecoveryPhone => Some(ValueFormat::Phone),
            Self::RecoveryPushNotifications => None,
            Self::RecoveryContacts
            | Self::Beneficiaries
            | Self::RecoveryContactsInvite
            | Self::BeneficiariesInvite => Some(ValueFormat::ContactName),
        }
    }

    pub const fn relationship_field(&self) -> Option<Self> {
        match self {
            Self::RecoveryContactsInvite => Some(Self::RecoveryContacts),
            Self::BeneficiariesInvite => Some(Self::Beneficiaries),
            _ => None,
        }
    }

    pub fn is_valid_action(&self, action: Action) -> bool {
        self.valid_actions().contains(&action)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ContextBinding {
    TokenBinding,
    EntityId,
    Nonce,
}

impl ContextBinding {
    pub const fn key(&self) -> &'static str {
        match self {
            Self::TokenBinding => "tb",
            Self::EntityId => "eid",
            Self::Nonce => "n",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ValueFormat {
    Money,
    VerificationPolicy,
    Email,
    Phone,
    ContactName,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn field_parsing() {
        // Canonical case succeeds
        assert_eq!(
            "SpendWithoutHardware".parse::<Field>().unwrap(),
            Field::SpendWithoutHardware
        );
        assert_eq!(
            "RecoveryContacts".parse::<Field>().unwrap(),
            Field::RecoveryContacts
        );
        assert_eq!(
            "RecoveryPushNotifications".parse::<Field>().unwrap(),
            Field::RecoveryPushNotifications
        );

        // Wrong case fails
        assert!("spendwithouthardware".parse::<Field>().is_err());
        assert!("SPENDWITHOUTHARDWARE".parse::<Field>().is_err());

        // Invalid
        assert!("invalid".parse::<Field>().is_err());
    }

    #[test]
    fn valid_actions() {
        // SpendWithoutHardware: Set/Disable only
        assert!(Field::SpendWithoutHardware.is_valid_action(Action::Set));
        assert!(Field::SpendWithoutHardware.is_valid_action(Action::Disable));
        assert!(!Field::SpendWithoutHardware.is_valid_action(Action::Add));
        assert!(!Field::SpendWithoutHardware.is_valid_action(Action::Remove));

        // RecoveryEmail/RecoveryPhone: Set/Add/Disable
        assert!(Field::RecoveryEmail.is_valid_action(Action::Set));
        assert!(Field::RecoveryEmail.is_valid_action(Action::Add));
        assert!(Field::RecoveryEmail.is_valid_action(Action::Disable));
        assert!(!Field::RecoveryEmail.is_valid_action(Action::Remove));
        assert!(Field::RecoveryPhone.is_valid_action(Action::Set));
        assert!(Field::RecoveryPhone.is_valid_action(Action::Add));
        assert!(Field::RecoveryPhone.is_valid_action(Action::Disable));
        assert!(!Field::RecoveryPhone.is_valid_action(Action::Remove));

        // RecoveryPushNotifications: Add/Disable only
        assert!(Field::RecoveryPushNotifications.is_valid_action(Action::Add));
        assert!(Field::RecoveryPushNotifications.is_valid_action(Action::Disable));
        assert!(!Field::RecoveryPushNotifications.is_valid_action(Action::Set));

        // RecoveryContacts: Add/Remove only
        assert!(Field::RecoveryContacts.is_valid_action(Action::Add));
        assert!(Field::RecoveryContacts.is_valid_action(Action::Remove));
        assert!(!Field::RecoveryContacts.is_valid_action(Action::Set));

        // Invite fields: Accept only
        assert!(Field::RecoveryContactsInvite.is_valid_action(Action::Accept));
        assert!(!Field::RecoveryContactsInvite.is_valid_action(Action::Add));
    }
}
