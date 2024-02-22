pub mod descriptor_key {
    use std::str::FromStr;

    use bdk::miniscript::DescriptorPublicKey;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S>(dpk: &DescriptorPublicKey, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&dpk.to_string())
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<DescriptorPublicKey, D::Error>
    where
        D: Deserializer<'de>,
    {
        let s = String::deserialize(deserializer)?;
        DescriptorPublicKey::from_str(&s).map_err(serde::de::Error::custom)
    }
}
