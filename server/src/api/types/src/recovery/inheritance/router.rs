use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use utoipa::ToSchema;

use crate::recovery::social::relationship::RecoveryRelationshipId;

use super::claim::{InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimId};

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
pub struct BeneficiaryInheritanceClaimViewPending {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
    #[serde(flatten)]
    pub pending_common_fields: InheritanceClaimViewPendingCommonFields,
    pub destination: Option<()>, // TODO: fix after W-9368
    pub auth_keys: InheritanceClaimAuthKeys,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct BeneficiaryInheritanceClaimViewCanceled {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimViewCommonFields,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BenefactorInheritanceClaimView {
    Pending(BenefactorInheritanceClaimViewPending),
    Canceled(BenefactorInheritanceClaimViewCanceled),
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BeneficiaryInheritanceClaimView {
    Pending(BeneficiaryInheritanceClaimViewPending),
    Canceled(BeneficiaryInheritanceClaimViewCanceled),
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
                    destination: None, // TODO: fix after W-9368
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
        }
    }
}
