#[cfg(feature = "account")]
pub mod account;
#[cfg(feature = "consent")]
pub mod consent;
#[cfg(feature = "encrypted_attachment")]
pub mod encrypted_attachment;
#[cfg(feature = "privileged_action")]
pub mod privileged_action;
#[cfg(feature = "recovery")]
pub mod recovery;
#[cfg(feature = "screener")]
pub mod screener;

pub use database::ddb::DatabaseError;
