#[macro_export]
macro_rules! impl_from_core_type {
    ($core_type:ident, $ffi_type:ident) => {
        impl From<$core_type> for $ffi_type {
            fn from(core_type: $core_type) -> Self {
                $ffi_type(core_type)
            }
        }
    };
}

#[macro_export]
macro_rules! impl_into_core_type {
    ($ffi_type:ident, $core_type:ident) => {
        impl From<$ffi_type> for $core_type {
            fn from(ffi_type: $ffi_type) -> Self {
                ffi_type.0
            }
        }
    };
}

#[macro_export]
macro_rules! impl_hash_like {
    ($ffi_type:ident, $core_type:ident) => {
        #[uniffi::export]
        impl $ffi_type {
            /// Construct a hash-like type from 32 bytes.
            #[uniffi::constructor]
            pub fn from_bytes(bytes: Vec<u8>) -> Result<Self, HashParseError> {
                let hash_like: $core_type = deserialize(&bytes).map_err(|_| {
                    let len = bytes.len() as u32;
                    HashParseError::InvalidHash { len }
                })?;
                Ok(Self(hash_like))
            }

            /// Construct a hash-like type from a hex string.
            #[uniffi::constructor]
            pub fn from_string(hex: String) -> Result<Self, HashParseError> {
                hex.parse::<$core_type>()
                    .map(Self)
                    .map_err(|_| HashParseError::InvalidHexString { hex })
            }

            /// Serialize this type into a 32 byte array.
            pub fn serialize(&self) -> Vec<u8> {
                serialize(&self.0)
            }
        }

        impl std::fmt::Display for $ffi_type {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                self.0.fmt(f)
            }
        }
    };
}
