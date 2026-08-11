use instrumentation::metrics::factory::{Counter, MetricsFactory};
use once_cell::sync::Lazy;

pub(crate) static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new("onboarding"));

// Counts the number of keyset creation events
pub(crate) static KEYSET_CREATED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("keyset_created", None));
pub(crate) static V1_HARDWARE_KEYSET_USE: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("v1_hardware_keyset_use", None));

#[derive(Clone, Copy)]
pub(crate) enum V1HardwareKeysetOperation {
    FullCreate,
    LiteUpgrade,
    CreateKeyset,
}

impl V1HardwareKeysetOperation {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::FullCreate => "full_create",
            Self::LiteUpgrade => "lite_upgrade",
            Self::CreateKeyset => "create_keyset",
        }
    }
}

#[derive(Clone, Copy)]
pub(crate) enum V1HardwareKeysetOutcome {
    Attempted,
    Created,
    Existing,
}

impl V1HardwareKeysetOutcome {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Attempted => "attempted",
            Self::Created => "created",
            Self::Existing => "existing",
        }
    }
}

pub(crate) static TEST_HARDWARE_ATTESTATION_FIXTURE_USE: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("test_hardware_attestation_fixture_use", None));

#[derive(Clone, Copy)]
pub(crate) enum V2HardwareAttestationOperation {
    FullAccountCreation,
    LiteToFullUpgrade,
    CreateKeyset,
}

impl V2HardwareAttestationOperation {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::FullAccountCreation => "full_account_creation",
            Self::LiteToFullUpgrade => "lite_to_full_upgrade",
            Self::CreateKeyset => "create_keyset",
        }
    }
}

#[derive(Clone, Copy)]
pub(crate) enum V2HardwareAttestationNetwork {
    Mainnet,
    NonMainnet,
}

impl V2HardwareAttestationNetwork {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Mainnet => "mainnet",
            Self::NonMainnet => "non_mainnet",
        }
    }
}

pub const KEYSET_TYPE_KEY: &str = "keyset_type";
pub const LEGACY_VALUE: &str = "legacy";
pub const PRIVATE_VALUE: &str = "private";
pub const APP_ID_KEY: &str = "app_id";
pub const V1_OPERATION_KEY: &str = "operation";
pub const V1_OUTCOME_KEY: &str = "outcome";
pub(crate) const V2_OPERATION_KEY: &str = "operation";
pub(crate) const V2_NETWORK_KEY: &str = "network";

#[cfg(test)]
mod tests {
    use super::{
        V1HardwareKeysetOperation, V1HardwareKeysetOutcome, V2HardwareAttestationNetwork,
        V2HardwareAttestationOperation,
    };

    #[test]
    fn v1_hardware_keyset_metric_values_are_fixed() {
        assert_eq!(
            [
                V1HardwareKeysetOperation::FullCreate,
                V1HardwareKeysetOperation::LiteUpgrade,
                V1HardwareKeysetOperation::CreateKeyset,
            ]
            .map(V1HardwareKeysetOperation::as_str),
            ["full_create", "lite_upgrade", "create_keyset"]
        );
        assert_eq!(
            [
                V1HardwareKeysetOutcome::Attempted,
                V1HardwareKeysetOutcome::Created,
                V1HardwareKeysetOutcome::Existing,
            ]
            .map(V1HardwareKeysetOutcome::as_str),
            ["attempted", "created", "existing"]
        );
    }

    #[test]
    fn test_hardware_attestation_fixture_metric_values_are_fixed() {
        assert_eq!(
            [
                V2HardwareAttestationOperation::FullAccountCreation,
                V2HardwareAttestationOperation::LiteToFullUpgrade,
                V2HardwareAttestationOperation::CreateKeyset,
            ]
            .map(V2HardwareAttestationOperation::as_str),
            [
                "full_account_creation",
                "lite_to_full_upgrade",
                "create_keyset",
            ]
        );
        assert_eq!(
            [
                V2HardwareAttestationNetwork::Mainnet,
                V2HardwareAttestationNetwork::NonMainnet,
            ]
            .map(V2HardwareAttestationNetwork::as_str),
            ["mainnet", "non_mainnet"]
        );
    }
}
