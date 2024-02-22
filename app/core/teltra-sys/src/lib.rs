#![allow(
    dead_code,
    non_camel_case_types,
    non_upper_case_globals,
    non_snake_case,
    unused_imports
)]

pub mod bindings {
    include!(concat!(env!("OUT_DIR"), "/bindings.rs"));
}

#[cfg(test)]
mod tests {
    use super::*;
    use bindings::{bitlog_event_t, teltra_device_info_t, teltra_translate, uint24_t};

    #[test]
    fn translate() {
        let mut device_info = teltra_device_info_t {
            device_serial: [0; 36usize],
            software_type: [0; 36usize],
            software_version: [0; 36usize],
            hardware_version: [0; 36usize],
        };

        let mut bitlog_event = bitlog_event_t {
            timestamp_delta: 123,
            event: 45,
            status: 2,
            pc: uint24_t {
                _bitfield_align_1: [0; 0],
                _bitfield_1: uint24_t::new_bitfield_1(1234),
            },
            lr: uint24_t {
                _bitfield_align_1: [0; 0],
                _bitfield_1: uint24_t::new_bitfield_1(9999),
            },
        };

        let mut serialized_memfault_event: [u8; 256] = [0; 256];
        let mut length: usize = 256;

        unsafe {
            let result = teltra_translate(
                &mut device_info as *mut teltra_device_info_t,
                &mut bitlog_event as *mut bitlog_event_t,
                serialized_memfault_event.as_mut_ptr(),
                &mut length as *mut usize,
            );

            assert_eq!(result, bindings::teltra_err_t_TELTRA_OK);
        }
    }
}
