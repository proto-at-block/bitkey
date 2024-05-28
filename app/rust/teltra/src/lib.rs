use std::{
    mem,
    sync::{Mutex, PoisonError},
};
use teltra_sys::bindings::*;
use thiserror::Error;

const DEVICE_INFO_PLACEHOLDER_SIZE: usize = 36;

#[derive(Error, Debug, PartialEq)]
pub enum TeltraError {
    #[error("failed to parse bitlog byte stream")]
    ParsingError,
    #[error("failed to translate bitlog events: {0}")]
    TranslationError(u32),
    #[error("failed to acquire lock")]
    LockError(String),
}

impl<T> From<PoisonError<T>> for TeltraError {
    fn from(err: PoisonError<T>) -> Self {
        Self::LockError(err.to_string())
    }
}

pub struct TelemetryIdentifiers {
    pub serial: String,
    pub version: String,
    pub sw_type: String,
    pub hw_revision: String,
}

pub struct Teltra {}

static LOCK: std::sync::Mutex<()> = Mutex::new(());

impl Default for Teltra {
    fn default() -> Self {
        Self::new()
    }
}

impl Teltra {
    pub fn new() -> Teltra {
        Self {}
    }

    pub fn translate_bitlogs(
        &self,
        bitlog_bytes: Vec<u8>,
        device_info: TelemetryIdentifiers,
    ) -> Result<Vec<Vec<u8>>, TeltraError> {
        let bitlogs = match parse_bitlogs(bitlog_bytes) {
            Ok(bitlogs) => bitlogs,
            Err(e) => return Err(e),
        };

        let mut memfault_events = Vec::new();

        for mut bitlog in bitlogs {
            unsafe {
                let mut serialized_memfault_event: [u8; 512] = [0; 512];
                let mut length: usize = serialized_memfault_event.len();

                let mut raw_device_info = teltra_device_info_t {
                    device_serial: convert_to_fixed_array(device_info.serial.as_bytes()),
                    software_type: convert_to_fixed_array(device_info.sw_type.as_bytes()),
                    software_version: convert_to_fixed_array(device_info.version.as_bytes()),
                    hardware_version: convert_to_fixed_array(device_info.hw_revision.as_bytes()),
                };

                let result = {
                    let _guard = LOCK.lock()?;
                    teltra_translate(
                        &mut raw_device_info as *mut teltra_device_info_t,
                        &mut bitlog as *mut bitlog_event_t,
                        serialized_memfault_event.as_mut_ptr(),
                        &mut length as *mut usize,
                    )
                };

                if result != teltra_err_t_TELTRA_OK {
                    return Err(TeltraError::TranslationError(result));
                }

                let mut event_vec: Vec<u8> = serialized_memfault_event.to_vec();
                event_vec.truncate(length);
                memfault_events.push(event_vec);
            }
        }

        Ok(memfault_events)
    }
}

fn parse_bitlogs(bitlogs: Vec<u8>) -> Result<Vec<bitlog_event_t>, TeltraError> {
    let mut result = Vec::new();

    let size = mem::size_of::<bitlog_event_t>();

    if bitlogs.len() % size != 0 {
        return Err(TeltraError::ParsingError);
    }

    for chunk in bitlogs.chunks_exact(size) {
        let foo: bitlog_event_t = unsafe { std::ptr::read_unaligned(chunk.as_ptr() as *const _) };
        result.push(foo);
    }

    Ok(result)
}

fn convert_to_fixed_array(slice: &[u8]) -> [std::os::raw::c_char; DEVICE_INFO_PLACEHOLDER_SIZE] {
    let mut array: [std::os::raw::c_char; DEVICE_INFO_PLACEHOLDER_SIZE] =
        [0; DEVICE_INFO_PLACEHOLDER_SIZE];
    let len = slice.len().min(DEVICE_INFO_PLACEHOLDER_SIZE);

    for i in 0..len {
        array[i] = slice[i] as std::os::raw::c_char;
    }

    array
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn translate_ok() {
        let bitlogs =
            hex::decode("2da70100c8d6680401460400000100c9d6680401460400000100cad66804014604")
                .expect("decoding failed");
        let device_info = TelemetryIdentifiers {
            serial: "312FS20402100009".to_string(),
            sw_type: "app-a-dev".to_string(),
            version: "1.0.12".to_string(),
            hw_revision: "evt".to_string(),
        };

        let t = Teltra {};
        let result = t.translate_bitlogs(bitlogs, device_info);
        assert!(result.is_ok());
        let events = result.unwrap();

        // Can't check the exact event bytes since teltra-sys inserts a timestamp. But,
        // we can check the expected length.
        for event in events {
            assert_eq!(event.len(), 58);
        }
    }

    #[test]
    fn translate_bad_length() {
        // Same input as above, but with the last byte removed.
        let bitlogs =
            hex::decode("2da70100c8d6680401460400000100c9d6680401460400000100cad668040146")
                .expect("decoding failed");
        let device_info = TelemetryIdentifiers {
            serial: "312FS20402100009".to_string(),
            sw_type: "app-a-dev".to_string(),
            version: "1.0.12".to_string(),
            hw_revision: "evt".to_string(),
        };

        let t = Teltra {};
        let result = t.translate_bitlogs(bitlogs, device_info);
        assert!(result.is_err());
        assert_eq!(result.err().unwrap(), TeltraError::ParsingError);
    }

    #[test]
    fn translate_bad_device_info() {
        let bitlogs =
            hex::decode("2da70100c8d6680401460400000100c9d6680401460400000100cad66804014604")
                .expect("decoding failed");
        let device_info = TelemetryIdentifiers {
            serial: "WAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONG".to_string(),
            sw_type: "WAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONG".to_string(),
            version: "WAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONG".to_string(),
            hw_revision: "WAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONGWAYTOOLONG".to_string(),
        };

        // Oversized TelemetryIdentifiers members should not cause a crash.
        let t = Teltra {};
        let result = t.translate_bitlogs(bitlogs, device_info);
        assert!(result.is_ok());
        let events = result.unwrap();
        for event in events {
            assert_eq!(event.len(), 259);
        }
    }
}
