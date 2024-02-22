#[cfg(all(not(feature = "std"), feature = "alloc"))]
use alloc::borrow::{Cow, ToOwned};
#[cfg(feature = "std")]
use std::borrow::Cow;

use crate::{cow::TriCow, parse_urn, UrnSlice};

#[cfg(feature = "alloc")]
use crate::Urn;

#[cfg_attr(docsrs, doc(cfg(feature = "serde")))]
impl<'de> serde::Deserialize<'de> for UrnSlice<'de> {
    fn deserialize<D>(de: D) -> Result<Self, <D as serde::Deserializer<'de>>::Error>
    where
        D: serde::Deserializer<'de>,
    {
        #[cfg(feature = "alloc")]
        let s = match Cow::<str>::deserialize(de)? {
            Cow::Owned(s) => TriCow::Owned(s),
            Cow::Borrowed(s) => TriCow::Borrowed(s),
        };
        #[cfg(not(feature = "alloc"))]
        let s = TriCow::Borrowed(<&str>::deserialize(de)?);
        parse_urn(s).map_err(serde::de::Error::custom)
    }
}

#[cfg_attr(docsrs, doc(cfg(feature = "serde")))]
impl serde::Serialize for UrnSlice<'_> {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        serializer.serialize_str(self.as_str())
    }
}

#[cfg(feature = "alloc")]
#[cfg_attr(docsrs, doc(cfg(feature = "serde")))]
impl<'de> serde::Deserialize<'de> for Urn {
    fn deserialize<D>(de: D) -> Result<Self, <D as serde::Deserializer<'de>>::Error>
    where
        D: serde::Deserializer<'de>,
    {
        #[allow(clippy::redundant_clone)]
        Ok(UrnSlice::deserialize(de)?.to_owned())
    }
}

#[cfg(feature = "alloc")]
#[cfg_attr(docsrs, doc(cfg(feature = "serde")))]
impl serde::Serialize for Urn {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        self.0.serialize(serializer)
    }
}
