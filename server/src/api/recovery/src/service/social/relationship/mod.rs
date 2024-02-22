use account::entities::AccountProperties;
use base32::Alphabet;
use rand::Rng;
use repository::recovery::social::Repository;
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

const EXPIRATION_DAYS: i64 = 3;

#[derive(Clone)]
pub struct Service {
    pub repository: Repository,
    pub notification_service: NotificationService,
}

impl Service {
    #[must_use]
    pub fn new(repository: Repository, notification_service: NotificationService) -> Self {
        Self {
            repository,
            notification_service,
        }
    }
}

fn disambiguate_code_input(code: &str) -> String {
    // https://www.crockford.com/base32.html
    // Decode Os as 0s and Is and Ls as 1s for human readability errors
    code.to_uppercase()
        .replace('O', "0")
        .replace(['I', 'L'], "1")
}

fn gen_code() -> String {
    let mut code_bytes: [u8; 5] = [0; 5];
    rand::thread_rng().fill(&mut code_bytes);
    base32::encode(Alphabet::Crockford, &code_bytes)
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
