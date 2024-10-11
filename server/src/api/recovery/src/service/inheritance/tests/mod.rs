mod lock_inheritance_claim_tests;

use crate::service::inheritance;
use crate::service::inheritance::cancel_inheritance_claim::CancelInheritanceClaimInput;
use crate::service::inheritance::create_inheritance_claim::CreateInheritanceClaimInput;
use crate::service::social::relationship::accept_recovery_relationship_invitation::AcceptRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::create_recovery_relationship_invitation::CreateRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::endorse_recovery_relationships::EndorseRecoveryRelationshipsInput;
use crate::service::social::relationship::tests::construct_test_recovery_relationship_service;
use account::service::tests::construct_test_account_service;
use database::ddb;
use database::ddb::Repository;
use feature_flags::config::Config;
use http_server::config;
use notification::service::tests::construct_test_notification_service;
use repository::recovery::inheritance::InheritanceRepository;
use std::collections::HashMap;
use time::OffsetDateTime;
use types::account::entities::{Account, FullAccount};
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimCanceledBy, InheritanceClaimPending,
};
use types::recovery::inheritance::package::Package;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipEndorsement, RecoveryRelationshipId,
};
use types::recovery::trusted_contacts::{TrustedContactInfo, TrustedContactRole};

pub async fn create_accepted_inheritance_relationship(
    benefactor_account: &FullAccount,
    beneficiary_account: &Account,
) -> RecoveryRelationshipId {
    let trusted_contact = TrustedContactInfo::new(
        "test_trusted_contact_alias".to_string(),
        vec![TrustedContactRole::Beneficiary],
    )
    .unwrap();

    let recovery_invite_input = CreateRecoveryRelationshipInvitationInput {
        customer_account: benefactor_account,
        trusted_contact: &trusted_contact,
        protected_customer_enrollment_pake_pubkey: "",
    };
    let recovery_relationship_service = construct_test_recovery_relationship_service().await;
    let recovery_relationship = recovery_relationship_service
        .create_recovery_relationship_invitation(recovery_invite_input)
        .await
        .unwrap();
    let endorsements = vec![RecoveryRelationshipEndorsement {
        recovery_relationship_id: recovery_relationship.common_fields().id.clone(),
        delegated_decryption_pubkey_certificate: "TEST_CERT".to_string(),
    }];

    let invitation =
        if let RecoveryRelationship::Invitation(invitation) = recovery_relationship.clone() {
            invitation
        } else {
            panic!("Expected RecoveryRelationship to be an Invitation")
        };

    let recovery_relationship_id = recovery_relationship.common_fields().clone().id;

    let accept_invite_input = AcceptRecoveryRelationshipInvitationInput {
        trusted_contact_account_id: beneficiary_account.get_id(),
        recovery_relationship_id: &recovery_relationship_id,
        code: &invitation.code,
        customer_alias: "customer_alias",
        trusted_contact_enrollment_pake_pubkey: "TEST_PUBKEY",
        enrollment_pake_confirmation: "TEST_CONFIRMATION",
        sealed_delegated_decryption_pubkey: "TEST_SEALED_PUBKEY",
    };
    recovery_relationship_service
        .accept_recovery_relationship_invitation(accept_invite_input)
        .await
        .unwrap();

    recovery_relationship_service
        .endorse_recovery_relationships(EndorseRecoveryRelationshipsInput {
            customer_account_id: &benefactor_account.id,
            endorsements,
        })
        .await
        .unwrap();

    recovery_relationship_id
}

pub async fn create_pending_inheritance_claim(
    benefactor_account: &FullAccount,
    beneficiary_account: &Account,
    auth_keys: &InheritanceClaimAuthKeys,
    delay_end_time_override: Option<OffsetDateTime>,
) -> InheritanceClaimPending {
    let inheritance_service = construct_test_inheritance_service().await;
    let recovery_relationship_id =
        create_accepted_inheritance_relationship(benefactor_account, beneficiary_account).await;

    let create_inheritance_input = CreateInheritanceClaimInput {
        beneficiary_account,
        recovery_relationship_id,
        auth_keys: auth_keys.to_owned(),
    };

    let claim = inheritance_service
        .create_claim(create_inheritance_input)
        .await
        .unwrap();

    let InheritanceClaim::Pending(pending_claim) = claim else {
        panic!("Expected pending claim");
    };

    if let Some(delay_end_time) = delay_end_time_override {
        update_claim_delay_end_time(&pending_claim, delay_end_time).await
    } else {
        pending_claim
    }
}

async fn update_claim_delay_end_time(
    pending_claim: &InheritanceClaimPending,
    delay_end_time: OffsetDateTime,
) -> InheritanceClaimPending {
    let inheritance_repository = construct_inheritance_repository().await;
    let pending_claim_past_delay_end_time = InheritanceClaimPending {
        common_fields: pending_claim.common_fields.clone(),
        delay_end_time,
    };

    let claim = inheritance_repository
        .persist_inheritance_claim(&InheritanceClaim::Pending(
            pending_claim_past_delay_end_time,
        ))
        .await
        .expect("persist claim");

    if let InheritanceClaim::Pending(pending_claim) = claim {
        pending_claim
    } else {
        panic!("Expected pending claim");
    }
}

pub async fn cancel_claim(
    claim: &InheritanceClaim,
    account: &Account,
) -> (InheritanceClaimCanceledBy, InheritanceClaim) {
    let inheritance_service = construct_test_inheritance_service().await;
    let input = CancelInheritanceClaimInput {
        account,
        inheritance_claim_id: claim.common_fields().id.clone(),
    };
    inheritance_service.cancel_claim(input).await.unwrap()
}

pub async fn create_inheritance_package(
    recovery_relationship_id: &RecoveryRelationshipId,
    sealed_dek: &str,
    sealed_mobile_key: &str,
) {
    let package = Package {
        recovery_relationship_id: recovery_relationship_id.to_owned(),
        sealed_dek: sealed_dek.to_string(),
        sealed_mobile_key: sealed_mobile_key.to_string(),

        updated_at: OffsetDateTime::now_utc(),
        created_at: OffsetDateTime::now_utc(),
    };
    let inheritance_service = construct_test_inheritance_service().await;
    inheritance_service
        .upload_packages(vec![package])
        .await
        .expect("upload packages");
}

pub async fn construct_test_inheritance_service() -> inheritance::Service {
    let inheritance_repository = construct_inheritance_repository().await;

    let feature_flags_service = Config::new_with_overrides(HashMap::new())
        .to_service()
        .await
        .unwrap();

    inheritance::Service::new(
        inheritance_repository,
        construct_test_recovery_relationship_service().await,
        construct_test_notification_service().await,
        construct_test_account_service().await,
        feature_flags_service,
    )
}

pub async fn construct_inheritance_repository() -> InheritanceRepository {
    let profile = Some("test");
    let ddb_config = config::extract::<ddb::Config>(profile).expect("extract ddb config");
    let ddb_connection = ddb_config.to_connection().await;
    InheritanceRepository::new(ddb_connection.clone())
}
