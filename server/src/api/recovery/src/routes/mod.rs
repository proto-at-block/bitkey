use serde::{Deserialize, Deserializer};
use types::recovery::social::PAKE_PUBLIC_KEY_STRING_LENGTH;

pub mod delay_notify;
pub mod inheritance;
pub mod relationship;
pub mod social_challenge;

fn deserialize_pake_pubkey<'de, D>(deserializer: D) -> Result<String, D::Error>
where
    D: Deserializer<'de>,
{
    let s: String = match Deserialize::deserialize(deserializer) {
        Ok(s) => s,
        Err(_) => {
            return Ok(String::default());
        }
    };
    hex::decode(&s).map_err(|_| serde::de::Error::custom("Invalid PAKE public key format"))?;
    if s.len() != PAKE_PUBLIC_KEY_STRING_LENGTH {
        return Err(serde::de::Error::custom("Invalid PAKE public key length"));
    }
    Ok(s)
}