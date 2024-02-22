use std::{fmt::Display, str::FromStr};

use crate::UniffiCustomTypeConverter;
use wca::commands::Signature;

trait Stringable: Display + FromStr {}
impl Stringable for wca::commands::PartiallySignedTransaction {}
impl Stringable for wca::commands::DescriptorPublicKey {}
impl Stringable for crypto::invoice::Sha256 {}
impl Stringable for crypto::keys::PublicKey {}
impl Stringable for Signature {}

impl<T> UniffiCustomTypeConverter for T
where
    T: Stringable,
    <T as FromStr>::Err: Send + Sync + std::error::Error + 'static,
{
    type Builtin = String;

    fn into_custom(val: Self::Builtin) -> uniffi::Result<Self> {
        Ok(Self::from_str(&val)?)
    }

    fn from_custom(obj: Self) -> Self::Builtin {
        obj.to_string()
    }
}
