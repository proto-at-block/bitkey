mod complete_inheritance_claim_tests;
mod create_inheritance_claim_tests;
mod has_incomplete_claim_tests;
mod lock_inheritance_claim_tests;
mod recreate_pending_claims_for_beneficiary_tests;
mod shorten_delay_tests;

use crate::service::inheritance;
use crate::service::inheritance::cancel_inheritance_claim::CancelInheritanceClaimInput;
use crate::service::inheritance::create_inheritance_claim::CreateInheritanceClaimInput;
use crate::service::inheritance::lock_inheritance_claim::LockInheritanceClaimInput;

use crate::service::social::relationship::accept_recovery_relationship_invitation::AcceptRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::create_recovery_relationship_invitation::CreateRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::endorse_recovery_relationships::EndorseRecoveryRelationshipsInput;
use crate::service::social::relationship::tests::construct_test_recovery_relationship_service;
use account::service::tests::{
    construct_test_account_service, create_full_account_for_test, generate_test_authkeys,
};
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::secp256k1;
use bdk_utils::signature::sign_message;
use database::ddb;
use database::ddb::Repository;
use feature_flags::config::Config;
use http_server::config;
use notification::service::tests::construct_test_notification_service;
use promotion_code::service::tests::construct_test_promotion_code_service;
use rand::thread_rng;
use repository::recovery::inheritance::InheritanceRepository;
use repository::screener::ScreenerRepository;
use screener::service::Service as ScreenerService;
use screener::ScreenerMode;
use std::collections::HashMap;
use std::str::FromStr;
use std::sync::Arc;
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::account::entities::FullAccount;
use types::account::keys::FullAccountAuthKeys;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimCanceled,
    InheritanceClaimCompleted, InheritanceClaimLocked, InheritanceClaimPending,
    InheritanceCompletionMethod, InheritanceRole,
};
use types::recovery::inheritance::package::Package;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipEndorsement, RecoveryRelationshipId,
};
use types::recovery::trusted_contacts::{TrustedContactInfo, TrustedContactRole};

use super::packages::UploadPackagesInput;

fn get_auth_keys(account: &FullAccount) -> InheritanceClaimAuthKeys {
    InheritanceClaimAuthKeys::FullAccount(
        account
            .active_auth_keys()
            .expect("Account has active auth keys")
            .to_owned(),
    )
}

pub async fn create_accepted_inheritance_relationship(
    benefactor_account: &FullAccount,
    beneficiary_account: &FullAccount,
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
        trusted_contact_account_id: &beneficiary_account.id,
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
    beneficiary_account: &FullAccount,
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

    if let InheritanceClaim::Pending(updated_pending_claim) = claim {
        updated_pending_claim
    } else {
        panic!("Expected pending claim");
    }
}

pub async fn cancel_claim(
    claim: &InheritanceClaim,
    account: &FullAccount,
) -> (InheritanceRole, InheritanceClaim) {
    let inheritance_service = construct_test_inheritance_service().await;
    let input = CancelInheritanceClaimInput {
        account,
        inheritance_claim_id: claim.common_fields().id.clone(),
    };
    inheritance_service.cancel_claim(input).await.unwrap()
}

pub async fn create_locked_claim(
    benefactor_account: &FullAccount,
    beneficiary_account: &FullAccount,
) -> InheritanceClaimLocked {
    let secp = Secp256k1::new();
    let (auth_keys, challenge, app_signature, _) = setup_keys_and_signatures(&secp);

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        benefactor_account,
        beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;
    let inheritance_service = construct_test_inheritance_service().await;

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = update_claim_delay_end_time(&pending_claim, delay_end_time).await;

    let recovery_relationship_id = pending_claim.common_fields.recovery_relationship_id.clone();

    let sealed_dek = "TEST_SEALED_DEK".to_string();
    let sealed_mobile_key = "TEST_SEALED_MOBILE_KEY".to_string();
    create_inheritance_package(
        benefactor_account,
        &recovery_relationship_id,
        &sealed_dek,
        &sealed_mobile_key,
        None,
        None,
    )
    .await;

    let input = LockInheritanceClaimInput {
        inheritance_claim_id: pending_claim.common_fields.id.clone(),
        beneficiary_account: beneficiary_account.to_owned(),
        challenge,
        app_signature,
    };
    inheritance_service.lock(input).await.expect("lock claim")
}

pub async fn create_completed_claim(
    locked_claim: &InheritanceClaimLocked,
) -> InheritanceClaimCompleted {
    let inheritance_repository = construct_inheritance_repository().await;
    let signed_psbt = Psbt::from_str("cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA").expect("Failed to parse PSBT");

    let completed_claim = InheritanceClaim::Completed(InheritanceClaimCompleted {
        common_fields: locked_claim.common_fields.clone(),
        completion_method: InheritanceCompletionMethod::WithPsbt {
            txid: signed_psbt.unsigned_tx.txid(),
        },
        completed_at: OffsetDateTime::now_utc(),
    });

    let claim = inheritance_repository
        .persist_inheritance_claim(&completed_claim)
        .await
        .expect("persist claim");

    if let InheritanceClaim::Completed(completed_claim) = claim {
        completed_claim
    } else {
        panic!("Expected completed claim");
    }
}

pub async fn create_canceled_claim(
    pending_claim: &InheritanceClaimPending,
    canceled_by: InheritanceRole,
) -> InheritanceClaimCanceled {
    let inheritance_repository = construct_inheritance_repository().await;
    let canceled_claim = InheritanceClaim::Canceled(InheritanceClaimCanceled {
        common_fields: pending_claim.common_fields.clone(),
        canceled_by,
    });
    let claim = inheritance_repository
        .persist_inheritance_claim(&canceled_claim)
        .await
        .expect("persist claim");

    if let InheritanceClaim::Canceled(canceled_claim) = claim {
        canceled_claim
    } else {
        panic!("Expected canceled claim");
    }
}

pub async fn create_inheritance_package(
    benefactor_full_account: &FullAccount,
    recovery_relationship_id: &RecoveryRelationshipId,
    sealed_dek: &str,
    sealed_mobile_key: &str,
    sealed_descriptor: Option<&str>,
    sealed_server_root_xpub: Option<&str>,
) {
    let package = Package {
        recovery_relationship_id: recovery_relationship_id.to_owned(),
        sealed_dek: sealed_dek.to_string(),
        sealed_mobile_key: sealed_mobile_key.to_string(),
        sealed_descriptor: sealed_descriptor.map(|s| s.to_string()),
        sealed_server_root_xpub: sealed_server_root_xpub.map(|s| s.to_string()),

        updated_at: OffsetDateTime::now_utc(),
        created_at: OffsetDateTime::now_utc(),
    };
    let inheritance_service = construct_test_inheritance_service().await;
    inheritance_service
        .upload_packages(UploadPackagesInput {
            benefactor_full_account,
            packages: vec![package],
        })
        .await
        .expect("upload packages");
}

pub async fn construct_test_inheritance_service() -> inheritance::Service {
    let inheritance_repository = construct_inheritance_repository().await;
    let promotion_code_service = construct_test_promotion_code_service().await;
    let feature_flags_service = Config::new_with_overrides(HashMap::new())
        .to_service()
        .await
        .unwrap();
    let conn = config::extract::<ddb::Config>(Some("test"))
        .unwrap()
        .to_connection()
        .await;
    let repo = ScreenerRepository::new(conn);
    let screener_service = ScreenerService::new_and_load_data(
        None,
        repo,
        screener::Config {
            screener: ScreenerMode::Test,
        },
    )
    .await;

    inheritance::Service::new(
        inheritance_repository,
        construct_test_recovery_relationship_service().await,
        construct_test_notification_service().await,
        construct_test_account_service().await,
        feature_flags_service,
        Arc::new(screener_service),
        promotion_code_service,
    )
}

pub async fn construct_inheritance_repository() -> InheritanceRepository {
    let profile = Some("test");
    let ddb_config = config::extract::<ddb::Config>(profile).expect("extract ddb config");
    let ddb_connection = ddb_config.to_connection().await;
    InheritanceRepository::new(ddb_connection.clone())
}

pub async fn setup_accounts() -> (FullAccount, FullAccount) {
    setup_accounts_with_network(Network::BitcoinSignet).await
}

pub async fn setup_accounts_with_network(network: Network) -> (FullAccount, FullAccount) {
    let account_service = construct_test_account_service().await;
    let benefactor_account =
        create_full_account_for_test(&account_service, network, &generate_test_authkeys().into())
            .await;
    let beneficiary_account =
        create_full_account_for_test(&account_service, network, &generate_test_authkeys().into())
            .await;
    (benefactor_account, beneficiary_account)
}

pub fn setup_keys_and_signatures(
    secp: &Secp256k1<secp256k1::All>,
) -> (InheritanceClaimAuthKeys, String, String, String) {
    let (app_auth_seckey, app_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let (hardware_auth_seckey, hardware_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let (_recovery_auth_seckey, recovery_auth_pubkey) = secp.generate_keypair(&mut thread_rng());
    let auth_keys = InheritanceClaimAuthKeys::FullAccount(FullAccountAuthKeys::new(
        app_auth_pubkey,
        hardware_auth_pubkey,
        Some(recovery_auth_pubkey),
    ));

    let challenge = "LockInheritanceClaim".to_string()
        + &hardware_auth_pubkey.to_string()
        + &app_auth_pubkey.to_string()
        + &recovery_auth_pubkey.to_string();
    let app_signature = sign_message(secp, &challenge, &app_auth_seckey);
    let hardware_signature = sign_message(secp, &challenge, &hardware_auth_seckey);

    (auth_keys, challenge, app_signature, hardware_signature)
}
