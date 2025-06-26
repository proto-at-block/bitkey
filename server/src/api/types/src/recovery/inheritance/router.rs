use crate::recovery::social::relationship::RecoveryRelationshipId;
use bdk_utils::bdk::descriptor::ExtendedDescriptor;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use utoipa::ToSchema;

use super::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimId, InheritanceCompletionMethod,
};

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct InheritanceClaimViewCommonFields {
    pub id: InheritanceClaimId,
    pub recovery_relationship_id: RecoveryRelationshipId,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct InheritanceClaimViewPendingCommonFields {
    #[serde(with = "rfc3339")]
    pub delay_start_time: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BenefactorInheritanceClaimViewPending {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
    #[serde(flatten)]
    pub pending_common_fields: InheritanceClaimViewPendingCommonFields,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BenefactorInheritanceClaimViewCanceled {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BenefactorInheritanceClaimViewLocked {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BenefactorInheritanceClaimViewCompleted {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BeneficiaryInheritanceClaimViewPending {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
    #[serde(flatten)]
    pub pending_common_fields: InheritanceClaimViewPendingCommonFields,
    pub auth_keys: InheritanceClaimAuthKeys,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BeneficiaryInheritanceClaimViewCanceled {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BeneficiaryInheritanceClaimViewLocked {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
    // The dek is sealed using DH between an ephemeral key pair and the beneficiary's
    // delegated decryption key. The app(mobile) key and descriptor are encrypted using the dek.
    pub sealed_dek: String,
    pub sealed_mobile_key: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sealed_descriptor: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub benefactor_descriptor_keyset: Option<ExtendedDescriptor>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BeneficiaryInheritanceClaimViewCompleted {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
    pub psbt_txid: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BenefactorInheritanceClaimView {
    Pending(BenefactorInheritanceClaimViewPending),
    Canceled(BenefactorInheritanceClaimViewCanceled),
    Locked(BenefactorInheritanceClaimViewLocked),
    Completed(BenefactorInheritanceClaimViewCompleted),
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BeneficiaryInheritanceClaimView {
    Pending(BeneficiaryInheritanceClaimViewPending),
    Canceled(BeneficiaryInheritanceClaimViewCanceled),
    Locked(BeneficiaryInheritanceClaimViewLocked),
    Completed(BeneficiaryInheritanceClaimViewCompleted),
}

impl From<InheritanceClaim> for BenefactorInheritanceClaimView {
    fn from(claim: InheritanceClaim) -> Self {
        match claim {
            InheritanceClaim::Pending(pending) => {
                BenefactorInheritanceClaimView::Pending(BenefactorInheritanceClaimViewPending {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: pending.common_fields.id,
                        recovery_relationship_id: pending.common_fields.recovery_relationship_id,
                    },
                    pending_common_fields: InheritanceClaimViewPendingCommonFields {
                        delay_start_time: pending.common_fields.created_at,
                        delay_end_time: pending.delay_end_time,
                    },
                })
            }
            InheritanceClaim::Canceled(canceled) => {
                BenefactorInheritanceClaimView::Canceled(BenefactorInheritanceClaimViewCanceled {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: canceled.common_fields.id,
                        recovery_relationship_id: canceled.common_fields.recovery_relationship_id,
                    },
                })
            }
            InheritanceClaim::Locked(locked) => {
                BenefactorInheritanceClaimView::Locked(BenefactorInheritanceClaimViewLocked {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: locked.common_fields.id,
                        recovery_relationship_id: locked.common_fields.recovery_relationship_id,
                    },
                })
            }
            InheritanceClaim::Completed(completed) => {
                BenefactorInheritanceClaimView::Completed(BenefactorInheritanceClaimViewCompleted {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: completed.common_fields.id,
                        recovery_relationship_id: completed.common_fields.recovery_relationship_id,
                    },
                })
            }
        }
    }
}

impl From<InheritanceClaim> for BeneficiaryInheritanceClaimView {
    fn from(claim: InheritanceClaim) -> Self {
        match claim {
            InheritanceClaim::Pending(pending) => {
                BeneficiaryInheritanceClaimView::Pending(BeneficiaryInheritanceClaimViewPending {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: pending.common_fields.id,
                        recovery_relationship_id: pending.common_fields.recovery_relationship_id,
                    },
                    auth_keys: pending.common_fields.auth_keys,
                    pending_common_fields: InheritanceClaimViewPendingCommonFields {
                        delay_start_time: pending.common_fields.created_at,
                        delay_end_time: pending.delay_end_time,
                    },
                })
            }
            InheritanceClaim::Canceled(canceled) => {
                BeneficiaryInheritanceClaimView::Canceled(BeneficiaryInheritanceClaimViewCanceled {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: canceled.common_fields.id,
                        recovery_relationship_id: canceled.common_fields.recovery_relationship_id,
                    },
                })
            }
            InheritanceClaim::Locked(locked) => {
                BeneficiaryInheritanceClaimView::Locked(BeneficiaryInheritanceClaimViewLocked {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: locked.common_fields.id,
                        recovery_relationship_id: locked.common_fields.recovery_relationship_id,
                    },
                    sealed_dek: locked.sealed_dek,
                    sealed_mobile_key: locked.sealed_mobile_key,
                    sealed_descriptor: locked.sealed_descriptor,
                    benefactor_descriptor_keyset: locked.benefactor_descriptor_keyset,
                })
            }
            InheritanceClaim::Completed(completed) => BeneficiaryInheritanceClaimView::Completed(
                BeneficiaryInheritanceClaimViewCompleted {
                    common_fields: InheritanceClaimViewCommonFields {
                        id: completed.common_fields.id,
                        recovery_relationship_id: completed.common_fields.recovery_relationship_id,
                    },
                    psbt_txid: match completed.completion_method {
                        InheritanceCompletionMethod::WithPsbt { txid } => Some(txid.to_string()),
                        InheritanceCompletionMethod::EmptyBalance => None,
                    },
                },
            ),
        }
    }
}
