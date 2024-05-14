use serde::Deserialize;
use ulid::Ulid;

#[derive(Clone, Deserialize)]
pub struct IdentifierGenerator {
    pub use_local_wallet_id: bool,
}

impl IdentifierGenerator {
    pub fn gen_account_id(&self) -> Ulid {
        Ulid::new()
    }

    pub fn gen_spending_keyset_id(&self) -> Ulid {
        match self.use_local_wallet_id {
            true => Ulid::default(),
            false => Ulid::new(),
        }
    }
}
