use notification::service::Service as NotificationService;
use promotion_code::service::Service as PromotionCodeService;
use rand::Rng;
use repository::recovery::social::SocialRecoveryRepository;
use time::{Duration, OffsetDateTime};
use types::{account::entities::AccountProperties, recovery::trusted_contacts::TrustedContactRole};

pub mod accept_recovery_relationship_invitation;
pub mod backups;
pub mod create_recovery_relationship_invitation;
pub mod delete_recovery_relationship;
pub mod endorse_recovery_relationships;
pub mod error;
pub mod get_recovery_relationship;
pub mod get_recovery_relationship_invitation_for_code;
pub mod get_recovery_relationships;
pub mod reissue_recovery_relationship_invitation;
pub mod tests;

const TEST_EXPIRATION_SECS: i64 = 3000;
const INVITATION_CODE_BIT_LENGTH: usize = 20;
const INVITATION_CODE_BYTE_LENGTH: usize = (INVITATION_CODE_BIT_LENGTH + 7) / 8;
const INVITATION_CODE_LAST_BYTE_MASK: u8 = 0xFF << (8 - (INVITATION_CODE_BIT_LENGTH % 8));
const SOCIAL_RECOVERY_EXPIRATION_DAYS: i64 = 3;
const BENEFICIARY_EXPIRATION_DAYS: i64 = 30;

#[derive(Clone)]
pub struct Service {
    pub repository: SocialRecoveryRepository,
    pub notification_service: NotificationService,
    pub promotion_code_service: PromotionCodeService,
}

impl Service {
    #[must_use]
    pub fn new(
        repository: SocialRecoveryRepository,
        notification_service: NotificationService,
        promotion_code_service: PromotionCodeService,
    ) -> Self {
        Self {
            repository,
            notification_service,
            promotion_code_service,
        }
    }
}

// Generates a code of bit length INVITATION_CODE_BIT_LENGTH and returns it left-justified
// (right-padded) within hex-encoded byte sequence of length ceil(INVITATION_CODE_BIT_LENGTH / 8)
fn gen_code() -> (String, usize) {
    let mut code_bytes: [u8; INVITATION_CODE_BYTE_LENGTH] = [0; INVITATION_CODE_BYTE_LENGTH];
    rand::thread_rng().fill(&mut code_bytes);
    code_bytes[INVITATION_CODE_BYTE_LENGTH - 1] &= INVITATION_CODE_LAST_BYTE_MASK;
    (hex::encode(code_bytes), INVITATION_CODE_BIT_LENGTH)
}

/// Generates an expiration date for a recovery relationship based on the role and account properties.
///
/// # Arguments
///
/// * `role` - The role of the trusted contact
/// * `account_properties` - The properties for a given account, for now only the test account flag is used
fn gen_expiration(
    role: &TrustedContactRole,
    account_properties: &AccountProperties,
) -> OffsetDateTime {
    match (role, account_properties.is_test_account) {
        // Test accounts have a short expiration time
        (_, true) => OffsetDateTime::now_utc() + Duration::seconds(TEST_EXPIRATION_SECS),
        // Non-test accounts have a longer expiration time
        (TrustedContactRole::SocialRecoveryContact, false) => {
            OffsetDateTime::now_utc() + Duration::days(SOCIAL_RECOVERY_EXPIRATION_DAYS)
        }
        (TrustedContactRole::Beneficiary, false) => {
            OffsetDateTime::now_utc() + Duration::days(BENEFICIARY_EXPIRATION_DAYS)
        }
    }
}
