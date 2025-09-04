use serde::{Deserialize, Deserializer, Serializer};

use crate::currencies::CurrencyCode::XXX;
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

/// Serialize an optional OffsetDateTime into an optional i64 unix epoch time. Used in structs for DDB TTLs
pub fn serialize_optional_ts<S>(x: &Option<OffsetDateTime>, s: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    match x {
        Some(dt) => s.serialize_some(&dt.unix_timestamp()),
        None => s.serialize_none(),
    }
}

/// Deserialize an optional i64 representing a unix epoch time into an optional OffsetDateTime. Used in structs for DDB TTLs
pub fn deserialize_optional_ts<'de, D>(d: D) -> Result<Option<OffsetDateTime>, D::Error>
where
    D: Deserializer<'de>,
{
    let ts: Option<i64> = Option::deserialize(d)?;
    match ts {
        Some(ts) => OffsetDateTime::from_unix_timestamp(ts)
            .map(Some)
            .map_err(serde::de::Error::custom),
        None => Ok(None),
    }
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

    let currency_code = Currency::supported_currency_codes()
        .into_iter()
        .find(|c| c.to_string() == s.to_uppercase())
        .unwrap_or(XXX); // return no currency when it is unsupported

    Ok(currency_code)
}

pub fn f64_from_str<'de, D>(deserializer: D) -> Result<f64, D::Error>
where
    D: Deserializer<'de>,
{
    let s: &str = Deserialize::deserialize(deserializer)?;
    s.parse::<f64>().map_err(serde::de::Error::custom)
}

pub fn optional_f64_from_str<'de, D>(deserializer: D) -> Result<Option<f64>, D::Error>
where
    D: Deserializer<'de>,
{
    let s: Option<&str> = Deserialize::deserialize(deserializer)?;

    s.map(|s| s.parse::<f64>().map_err(serde::de::Error::custom))
        .transpose()
}

pub fn deserialize_iso_4217_vec<'de, D>(d: D) -> Result<Vec<CurrencyCode>, D::Error>
where
    D: Deserializer<'de>,
{
    let strings: Vec<&str> = Deserialize::deserialize(d)?;

    Ok(strings
        .into_iter()
        .map(|s| {
            Currency::supported_currency_codes()
                .into_iter()
                .find(|c| c.to_string() == s.to_uppercase())
                .unwrap_or(XXX)
        })
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Deserialize)]
    struct OptionalF64Wrapper {
        #[serde(default, deserialize_with = "optional_f64_from_str")]
        value: Option<f64>,
    }

    #[test]
    fn test_optional_64_from_str() {
        let json = r#"{"value": "1.0"}"#;
        let wrapper: OptionalF64Wrapper = serde_json::from_str(json).unwrap();
        assert_eq!(wrapper.value, Some(1.0));

        let json = r#"{"value": null}"#;
        let wrapper: OptionalF64Wrapper = serde_json::from_str(json).unwrap();
        assert_eq!(wrapper.value, None);

        let json = r#"{}"#;
        let wrapper: OptionalF64Wrapper = serde_json::from_str(json).unwrap();
        assert_eq!(wrapper.value, None);
    }

    #[derive(Deserialize)]
    struct Iso4217VecWrapper {
        #[serde(deserialize_with = "deserialize_iso_4217_vec")]
        value: Vec<CurrencyCode>,
    }

    #[test]
    fn test_deserialize_iso_4217_vec() {
        let json = r#"{"value": ["USD", "EUR"]}"#;
        let wrapper: Iso4217VecWrapper = serde_json::from_str(json).unwrap();
        assert_eq!(wrapper.value, vec![CurrencyCode::USD, CurrencyCode::EUR]);

        let json = r#"{"value": []}"#;
        let wrapper: Iso4217VecWrapper = serde_json::from_str(json).unwrap();
        assert_eq!(wrapper.value, vec![]);
    }
}
