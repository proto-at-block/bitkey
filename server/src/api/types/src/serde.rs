use serde::{Deserialize, Deserializer, Serializer};

use crate::currencies::{Currency, CurrencyCode};
use time::OffsetDateTime;

/// Serialize an OffsetDataTime into an i64 unix epoch time. Used in structs for DDB TTLs
pub fn serialize_ts<S>(x: &OffsetDateTime, s: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    s.serialize_i64(x.unix_timestamp())
}

/// Deserialize an i64 representing a unix epoch time into an OffsetDateTime. Used in structs for DDB TTLs
pub fn deserialize_ts<'de, D>(d: D) -> Result<OffsetDateTime, D::Error>
where
    D: Deserializer<'de>,
{
    let ts = i64::deserialize(d)?;
    OffsetDateTime::from_unix_timestamp(ts).map_err(serde::de::Error::custom)
}

pub fn deserialize_ts_vec<'de, D>(deserializer: D) -> Result<Vec<OffsetDateTime>, D::Error>
where
    D: Deserializer<'de>,
{
    #[derive(Deserialize)]
    struct Wrapper(#[serde(deserialize_with = "deserialize_ts")] OffsetDateTime);

    let v = Vec::deserialize(deserializer)?;
    Ok(v.into_iter().map(|Wrapper(a)| a).collect())
}

/// Deserialize a string representing ISO-4217 currency code into a CurrencyCode
pub fn deserialize_iso_4217<'de, D>(d: D) -> Result<CurrencyCode, D::Error>
where
    D: Deserializer<'de>,
{
    let s = String::deserialize(d)?;

    Currency::supported_currency_codes()
        .into_iter()
        .find(|c| c.to_string() == s.to_uppercase())
        .ok_or(serde::de::Error::custom("Unsupported currency"))
}

pub fn f64_from_str<'de, D>(deserializer: D) -> Result<f64, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    s.parse::<f64>().map_err(serde::de::Error::custom)
}
