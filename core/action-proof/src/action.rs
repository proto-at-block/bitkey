/// Actions for privileged Bitkey operations.
///
/// Each variant represents a specific privileged action that can be authorized.
/// Serialized as a single PascalCase string, e.g. `SetRecoveryPhone`.
define_enum!(Action, ParseActionError, "invalid action: {0}" {
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
    SetDelayNotifyPeriod
});

impl Action {
    pub const fn display_name(&self) -> &'static str {
        match self {
            Self::SetSpendWithoutHardware => "Set Spend Without Hardware",
            Self::DisableSpendWithoutHardware => "Disable Spend Without Hardware",
            Self::SetVerificationThreshold => "Set Verification Threshold",
            Self::SetRecoveryEmail => "Set Recovery Email",
            Self::DisableRecoveryEmail => "Disable Recovery Email",
            Self::SetRecoveryPhone => "Set Recovery Phone",
            Self::DisableRecoveryPhone => "Disable Recovery Phone",
            Self::SetRecoveryPushNotifications => "Set Recovery Push Notifications",
            Self::DisableRecoveryPushNotifications => "Disable Recovery Push Notifications",
            Self::AddRecoveryContact => "Add Recovery Contact",
            Self::RemoveRecoveryContact => "Remove Recovery Contact",
            Self::RemoveRecoveryCustomer => "Remove Recovery Customer",
            Self::AddBeneficiary => "Add Beneficiary",
            Self::RemoveBeneficiary => "Remove Beneficiary",
            Self::RemoveBenefactor => "Remove Benefactor",
            Self::CreateSpendingKeyset => "Create Spending Keyset",
            Self::RotateSpendingKeyset => "Rotate Spending Keyset",
            Self::DeleteAccount => "Delete Account",
            Self::UpdateDescriptorBackups => "Update Descriptor Backups",
            Self::CreateLostAppRecovery => "Create Lost App Recovery",
            Self::CreateLostHardwareRecovery => "Create Lost Hardware Recovery",
            Self::CancelLostAppRecovery => "Cancel Lost App Recovery",
            Self::CancelLostHardwareRecovery => "Cancel Lost Hardware Recovery",
            Self::CancelConflictingRecovery => "Cancel Conflicting Recovery",
            Self::SendRecoveryVerificationCode => "Send Recovery Verification Code",
            Self::VerifyRecoveryVerificationCode => "Verify Recovery Verification Code",
            Self::RotateAppAuthKeys => "Rotate App Auth Keys",
            Self::SetDelayNotifyPeriod => "Set Delay Notify Period",
        }
    }

    pub const fn value_format(&self) -> Option<ValueFormat> {
        match self {
            Self::SetSpendWithoutHardware | Self::DisableSpendWithoutHardware => {
                Some(ValueFormat::Money)
            }
            Self::SetVerificationThreshold => Some(ValueFormat::VerificationPolicy),
            Self::SetRecoveryEmail | Self::DisableRecoveryEmail => Some(ValueFormat::Email),
            Self::SetRecoveryPhone | Self::DisableRecoveryPhone => Some(ValueFormat::Phone),
            Self::SetRecoveryPushNotifications | Self::DisableRecoveryPushNotifications => None,
            Self::AddRecoveryContact
            | Self::RemoveRecoveryContact
            | Self::RemoveRecoveryCustomer
            | Self::AddBeneficiary
            | Self::RemoveBeneficiary
            | Self::RemoveBenefactor => Some(ValueFormat::ContactName),
            Self::CreateSpendingKeyset
            | Self::RotateSpendingKeyset
            | Self::DeleteAccount
            | Self::UpdateDescriptorBackups
            | Self::CreateLostAppRecovery
            | Self::CreateLostHardwareRecovery
            | Self::CancelLostAppRecovery
            | Self::CancelLostHardwareRecovery
            | Self::CancelConflictingRecovery
            | Self::SendRecoveryVerificationCode
            | Self::VerifyRecoveryVerificationCode
            | Self::RotateAppAuthKeys
            | Self::SetDelayNotifyPeriod => None,
        }
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
    fn action_parsing() {
        // Canonical case succeeds
        assert_eq!(
            "SetRecoveryPhone".parse::<Action>().unwrap(),
            Action::SetRecoveryPhone
        );
        assert_eq!(
            "SetSpendWithoutHardware".parse::<Action>().unwrap(),
            Action::SetSpendWithoutHardware
        );
        // Wrong case fails
        assert!("setrecoveryphone".parse::<Action>().is_err());
        assert!("SETRECOVERYPHONE".parse::<Action>().is_err());

        // Invalid
        assert!("invalid".parse::<Action>().is_err());

        // Old-style separate action/field is invalid
        assert!("Set".parse::<Action>().is_err());
        assert!("RecoveryPhone".parse::<Action>().is_err());
    }

    #[test]
    fn all_variants_roundtrip() {
        for action in Action::all() {
            let s = action.as_str();
            let parsed: Action = s.parse().unwrap();
            assert_eq!(*action, parsed);
        }
    }

    #[test]
    fn display_names_non_empty() {
        for action in Action::all() {
            assert!(
                !action.display_name().is_empty(),
                "display_name for {:?} should not be empty",
                action
            );
        }
    }
}
