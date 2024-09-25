use account::entities::AccountProperties;
use rand::Rng;
use repository::recovery::social::SocialRecoveryRepository;
use time::{Duration, OffsetDateTime};

use notification::service::Service as NotificationService;

pub mod accept_recovery_relationship_invitation;
pub mod create_recovery_relationship_invitation;
pub mod delete_recovery_relationship;
pub mod endorse_recovery_relationships;
pub mod error;
pub mod get_recovery_relationship_invitation_for_code;
pub mod get_recovery_relationships;
pub mod reissue_recovery_relationship_invitation;

const TEST_EXPIRATION_SECS: i64 = 3000;
const INVITATION_CODE_BIT_LENGTH: usize = 20;
const INVITATION_CODE_BYTE_LENGTH: usize = (INVITATION_CODE_BIT_LENGTH + 7) / 8;
const INVITATION_CODE_LAST_BYTE_MASK: u8 = 0xFF << (8 - (INVITATION_CODE_BIT_LENGTH % 8));
const EXPIRATION_DAYS: i64 = 3;

#[derive(Clone)]
pub struct Service {
    pub repository: SocialRecoveryRepository,
    pub notification_service: NotificationService,
}

impl Service {
    #[must_use]
    pub fn new(
        repository: SocialRecoveryRepository,
        notification_service: NotificationService,
    ) -> Self {
        Self {
            repository,
            notification_service,
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

/// Generates an expiration date for a recovery relationship based on the account properties.
///
/// # Arguments
///
/// * `account_properties` - The properties for a given account, for now only the test account flag is used
fn gen_expiration(account_properties: &AccountProperties) -> OffsetDateTime {
    match account_properties.is_test_account {
        true => OffsetDateTime::now_utc() + Duration::seconds(TEST_EXPIRATION_SECS),
        false => OffsetDateTime::now_utc() + Duration::days(EXPIRATION_DAYS),
    }
}
