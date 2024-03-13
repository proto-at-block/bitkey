extern crate boring_sys;
use crate::hkdf::Hkdf;
use crate::hmac::{generate_mac, verify_mac};
use boring_sys::*;
use std::ffi::CString;
use std::os::raw::c_uchar;
use std::sync::{Arc, Mutex};
use std::{ptr, slice};
use thiserror::Error;

pub struct Spake2Context {
    ctx: Arc<Mutex<*mut SPAKE2_CTX>>,
    role: Spake2Role,
}

#[derive(Debug, PartialEq)]
pub struct Spake2Keys {
    pub alice_encryption_key: Vec<u8>,
    pub bob_encryption_key: Vec<u8>,
    pub alice_conf_key: Vec<u8>,
    pub bob_conf_key: Vec<u8>,
}

#[derive(Debug, PartialEq, Clone, Copy)]
pub enum Spake2Role {
    Alice,
    Bob,
}

impl From<spake2_role_t> for Spake2Role {
    fn from(role: spake2_role_t) -> Self {
        match role {
            spake2_role_t::spake2_role_alice => Spake2Role::Alice,
            spake2_role_t::spake2_role_bob => Spake2Role::Bob,
            _ => panic!("Invalid role"),
        }
    }
}

impl From<Spake2Role> for spake2_role_t {
    fn from(val: Spake2Role) -> Self {
        match val {
            Spake2Role::Alice => spake2_role_t::spake2_role_alice,
            Spake2Role::Bob => spake2_role_t::spake2_role_bob,
        }
    }
}

const CONFIRMATION_KEYS_INFO: &str = "ConfirmationKeys";
const CONFIRMATION_LABEL: &str = "SocRecKeyConfirmationV1";
const ENCRYPTION_KEYS_INFO: &str = "EncryptionKeys";
const KE_KA_LENGTH: usize = 32;
const KCA_KCB_LENGTH: usize = 16;

// https://datatracker.ietf.org/doc/rfc9382/
// Section 4
fn derive_confirmation_keys(
    ka: &[u8],
    aad: Option<&[u8]>,
) -> Result<(Vec<u8>, Vec<u8>), Spake2Error> {
    if ka.len() != KE_KA_LENGTH {
        return Err(Spake2Error::LengthError);
    }

    let mut info = Vec::from(CONFIRMATION_KEYS_INFO.as_bytes());
    if let Some(additional_data) = aad {
        info.extend_from_slice(additional_data);
    }

    // Per RFC9382:
    // AAD -> KDF(Ka, nil, "ConfirmationKeys" || AAD) = KcA || KcB
    let hkdf = Hkdf::new(&[], ka);
    let okm = hkdf
        .expand(&info, (KCA_KCB_LENGTH * 2) as i32)
        .map_err(|_| Spake2Error::HkdfError)?;

    let kca = okm[..KCA_KCB_LENGTH].to_vec();
    let kcb = okm[KCA_KCB_LENGTH..].to_vec();

    Ok((kca, kcb))
}

fn derive_encryption_keys(ke: &[u8]) -> Result<(Vec<u8>, Vec<u8>), Spake2Error> {
    let hkdf = Hkdf::new(&[], ke);
    let info = Vec::from(ENCRYPTION_KEYS_INFO.as_bytes());
    let ke_okm = hkdf
        .expand(&info, (KE_KA_LENGTH * 2) as i32)
        .map_err(|_| Spake2Error::HkdfError)?;

    let kea = ke_okm[..KE_KA_LENGTH].to_vec();
    let keb = ke_okm[KE_KA_LENGTH..].to_vec();

    Ok((kea, keb))
}

fn derive_keys(key_material: &[u8], aad: Option<Vec<u8>>) -> Result<Spake2Keys, Spake2Error> {
    // ke is the first half of input key material
    let ke = key_material[..KE_KA_LENGTH].to_vec();
    // and ka is the second half
    let ka = key_material[KE_KA_LENGTH..].to_vec();

    // Derive confirmation keys (KcA, KcB) from Ka
    let aad_slice = aad.as_deref();
    let (kca, kcb) = derive_confirmation_keys(ka.as_slice(), aad_slice)?;

    // Derive encryption keys (KEa, KEb) from KE
    let (kea, keb) = derive_encryption_keys(ke.as_slice())?;

    Ok(Spake2Keys {
        alice_encryption_key: kea,
        bob_encryption_key: keb,
        alice_conf_key: kca,
        bob_conf_key: kcb,
    })
}

/// Wapper around the SPAKE2_CTX struct from BoringSSL.
/// * Automatically handles BoringSSL initialization
/// * Thread safe
/// * Provides a key confirmation API
impl Spake2Context {
    /// BoringSSL documentation for this function is repeated below:
    ///
    /// SPAKE2_CTX_new creates a new |SPAKE2_CTX| (which can only be used for a
    /// single execution of the protocol). SPAKE2 requires the symmetry of the two
    /// parties to be broken which is indicated via |my_role| – each party must pass
    /// a different value for this argument.
    ///
    /// The |my_name| and |their_name| arguments allow optional, opaque names to be
    /// bound into the protocol. For example MAC addresses, hostnames, usernames
    /// etc. These values are not exposed and can avoid context-confusion attacks
    /// when a password is shared between several devices.
    pub fn new(
        my_role: Spake2Role,
        my_name: String,
        their_name: String,
    ) -> Result<Self, Spake2Error> {
        unsafe {
            // CRYPTO_library_init initializes the crypto library.
            // It must be called if the library is built with BORINGSSL_NO_STATIC_INITIALIZER.
            // Otherwise, it does nothing and a static initializer is used instead.
            // It is safe to call this function multiple times and concurrently from multiple threads.
            // On some ARM configurations, this function may require filesystem access and should be called before entering a sandbox.
            boring_sys::CRYPTO_library_init();
        }

        let my_name_cstr = CString::new(my_name).map_err(|_| Spake2Error::InvalidName)?;
        let their_name_cstr = CString::new(their_name).map_err(|_| Spake2Error::InvalidName)?;

        let ctx = unsafe {
            SPAKE2_CTX_new(
                my_role.into(),
                my_name_cstr.as_ptr() as *const c_uchar,
                my_name_cstr.to_bytes().len(),
                their_name_cstr.as_ptr() as *const c_uchar,
                their_name_cstr.to_bytes().len(),
            )
        };

        if ctx.is_null() {
            Err(Spake2Error::ContextCreationError)
        } else {
            Ok(Spake2Context {
                ctx: Arc::new(Mutex::new(ctx)),
                role: my_role,
            })
        }
    }

    /// SPAKE2_generate_msg generates a SPAKE2 message for the given password.
    /// This function can only be called once for a given SPAKE2_CTX, and will error if so.
    pub fn generate_msg(&self, password: Vec<u8>) -> Result<Vec<u8>, Spake2Error> {
        let ctx_guard = self.ctx.lock().unwrap();
        let ctx = *ctx_guard;

        let mut out = vec![0u8; boring_sys::SPAKE2_MAX_MSG_SIZE as usize];
        let mut out_len = 0;

        let result = unsafe {
            SPAKE2_generate_msg(
                ctx,
                out.as_mut_ptr(),
                &mut out_len,
                boring_sys::SPAKE2_MAX_MSG_SIZE as usize,
                password.as_ptr(),
                password.len(),
            )
        };

        if result == 0 {
            Err(Spake2Error::GenerateMessageError)
        } else {
            out.truncate(out_len);
            Ok(out)
        }
    }

    /// BoringSSL's documentation for this function is repeated below:
    ///
    /// SPAKE2_process_msg completes the SPAKE2 exchange given the peer's message in
    /// |their_msg|, writes at most |max_out_key_len| bytes to |out_key| and sets
    /// |*out_key_len| to the number of bytes written.
    ///
    /// The resulting keying material is suitable for:
    ///    - Using directly in a key-confirmation step: i.e. each side could
    ///      transmit a hash of their role, a channel-binding value and the key
    ///      material to prove to the other side that they know the shared key.
    ///   -  Using as input keying material to HKDF to generate a variety of subkeys
    ///      for encryption etc.
    ///
    /// If |max_out_key_key| is smaller than the amount of key material generated
    /// then the key is silently truncated. If you want to ensure that no truncation
    /// occurs then |max_out_key| should be at least |SPAKE2_MAX_KEY_SIZE|.
    ///
    /// You must call |SPAKE2_generate_msg| on a given |SPAKE2_CTX| before calling
    /// this function. On successful return, |ctx| is complete and calling
    /// |SPAKE2_CTX_free| is the only acceptable operation on it.
    ///
    /// Returns one on success or zero on error.
    pub fn process_msg(
        &self,
        their_msg: Vec<u8>,
        aad: Option<Vec<u8>>,
    ) -> Result<Spake2Keys, Spake2Error> {
        let ctx_guard = self.ctx.lock().unwrap();
        let ctx = *ctx_guard;

        let mut out_key_material = vec![0u8; boring_sys::SPAKE2_MAX_KEY_SIZE as usize];
        let mut out_key_material_len = 0;

        let result = unsafe {
            SPAKE2_process_msg(
                ctx,
                out_key_material.as_mut_ptr(),
                &mut out_key_material_len,
                boring_sys::SPAKE2_MAX_KEY_SIZE as usize,
                their_msg.as_ptr(),
                their_msg.len(),
            )
        };

        if result == 0 {
            Err(Spake2Error::ProcessMessageError)
        } else {
            out_key_material.truncate(out_key_material_len);
            derive_keys(out_key_material.as_slice(), aad)
        }
    }

    /// MAC a fixed message with our confirmation key derived from the SPAKE2 key material.
    pub fn generate_key_conf_msg(&self, keys: &Spake2Keys) -> Result<Vec<u8>, Spake2Error> {
        let key = match &self.role {
            Spake2Role::Alice => &keys.alice_conf_key,
            Spake2Role::Bob => &keys.bob_conf_key,
        };

        generate_mac(key, CONFIRMATION_LABEL.as_bytes()).map_err(|_| Spake2Error::MacError)
    }

    /// Verify a MAC for a fixed message with our peer's confirmation key derived from the SPAKE2 key material.
    pub fn process_key_conf_msg(
        &self,
        received_mac: Vec<u8>,
        keys: &Spake2Keys,
    ) -> Result<(), Spake2Error> {
        let key = match &self.role {
            Spake2Role::Alice => &keys.bob_conf_key, // Alice verifies Bob's MAC
            Spake2Role::Bob => &keys.alice_conf_key, // Bob verifies Alice's MAC
        };

        verify_mac(key, CONFIRMATION_LABEL.as_bytes(), received_mac.as_slice())
            .map_err(|_| Spake2Error::MacError)
    }

    pub fn read_private_key(&self) -> Vec<u8> {
        // This is not good. We are bypassing BoringSSL's struct hiding here so that we can read the private key
        // to persist it, to support async communication.
        // This **BADLY** breaks if BoringSSL changes the struct layout.
        //
        // struct spake2_ctx_st {
        //     uint8_t private_key[32];
        //     uint8_t my_msg[32];
        //     uint8_t password_scalar[32];
        //     uint8_t password_hash[64];
        //     uint8_t *my_name;
        //     size_t my_name_len;
        //     uint8_t *their_name;
        //     size_t their_name_len;
        //     enum spake2_role_t my_role;
        //     enum spake2_state_t state;
        //     char disable_password_scalar_hack;
        //   };
        let ctx_guard = self.ctx.lock().unwrap();
        let ctx = *ctx_guard;

        unsafe {
            let private_key_ptr = ctx as *mut u8;
            let private_key_slice = slice::from_raw_parts(private_key_ptr, 32);
            private_key_slice.to_vec()
        }
    }

    pub fn read_public_key(&self) -> Vec<u8> {
        // See note in `read_private_key`.

        let ctx_guard = self.ctx.lock().unwrap();
        let ctx = *ctx_guard as *mut u8;

        unsafe {
            let my_msg_offset = ctx.add(32); // Offset by the size of private_key
            let public_key_slice = slice::from_raw_parts(my_msg_offset, 32);
            public_key_slice.to_vec()
        }
    }

    pub fn write_key_pair(
        &self,
        private_key: Vec<u8>,
        public_key: Vec<u8>,
    ) -> Result<(), Spake2Error> {
        if private_key.len() != 32 || public_key.len() != 32 {
            return Err(Spake2Error::LengthError);
        }

        // See note in `read_private_key`. EVIL! BAD! NO! Anyway.

        let ctx_guard = self.ctx.lock().unwrap();
        let ctx = *ctx_guard as *mut u8;

        unsafe {
            // Copy private_key to the start of the context
            ptr::copy_nonoverlapping(private_key.as_ptr(), ctx, 32);
            // Offset by the size of private_key
            let my_msg_offset = ctx.add(32);
            ptr::copy_nonoverlapping(public_key.as_ptr(), my_msg_offset, 32);
        }
        Ok(())
    }
}

impl Drop for Spake2Context {
    fn drop(&mut self) {
        let ctx_guard = self.ctx.lock().unwrap();
        if !(*ctx_guard).is_null() {
            unsafe {
                SPAKE2_CTX_free(*ctx_guard);
            }
        }
    }
}

unsafe impl Send for Spake2Context {}
unsafe impl Sync for Spake2Context {}

#[derive(Debug, Error)]
pub enum Spake2Error {
    #[error("Failed to create SPAKE2_CTX")]
    ContextCreationError,
    #[error("Invalid argument length")]
    LengthError,
    #[error("Failed to generate SPAKE2 message")]
    GenerateMessageError,
    #[error("Failed to process SPAKE2 message")]
    ProcessMessageError,
    #[error("Invalid name")]
    InvalidName,
    #[error("Failed to expand HKDF")]
    HkdfError,
    #[error("MAC error")]
    MacError,
    #[error("Invalid role")]
    InvalidRole,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn setup_contexts() -> (Spake2Context, Spake2Context) {
        (
            Spake2Context::new(Spake2Role::Alice, "alice".to_string(), "bob".to_string()).unwrap(),
            Spake2Context::new(Spake2Role::Bob, "bob".to_string(), "alice".to_string()).unwrap(),
        )
    }

    fn generate_and_process_msgs(
        alice_ctx: &Spake2Context,
        bob_ctx: &Spake2Context,
        alice_password: &str,
        bob_password: &str,
    ) -> (Spake2Keys, Spake2Keys) {
        let alice_msg = alice_ctx
            .generate_msg(alice_password.as_bytes().to_vec())
            .unwrap();
        let bob_msg = bob_ctx
            .generate_msg(bob_password.as_bytes().to_vec())
            .unwrap();
        (
            alice_ctx.process_msg(bob_msg, None).unwrap(),
            bob_ctx.process_msg(alice_msg, None).unwrap(),
        )
    }

    #[test]
    fn test_good() {
        let (alice_ctx, bob_ctx) = setup_contexts();
        let (alice_keys, bob_keys) =
            generate_and_process_msgs(&alice_ctx, &bob_ctx, "password", "password");

        assert_eq!(alice_keys, bob_keys);

        let alice_key_conf_msg = alice_ctx.generate_key_conf_msg(&alice_keys).unwrap();
        let bob_key_conf_msg = bob_ctx.generate_key_conf_msg(&bob_keys).unwrap();

        assert!(alice_ctx
            .process_key_conf_msg(bob_key_conf_msg, &alice_keys)
            .is_ok());
        assert!(bob_ctx
            .process_key_conf_msg(alice_key_conf_msg, &bob_keys)
            .is_ok());
    }

    #[test]
    fn test_alice_wrong_password() {
        let (alice_ctx, bob_ctx) = setup_contexts();
        let (alice_keys, bob_keys) =
            generate_and_process_msgs(&alice_ctx, &bob_ctx, "passworf", "password");

        assert_ne!(alice_keys, bob_keys);

        let alice_key_conf_msg = alice_ctx.generate_key_conf_msg(&alice_keys).unwrap();
        let bob_key_conf_msg = bob_ctx.generate_key_conf_msg(&bob_keys).unwrap();

        assert!(alice_ctx
            .process_key_conf_msg(bob_key_conf_msg, &alice_keys)
            .is_err());
        assert!(bob_ctx
            .process_key_conf_msg(alice_key_conf_msg, &bob_keys)
            .is_err());
    }

    #[test]
    fn test_bob_wrong_password() {
        let (alice_ctx, bob_ctx) = setup_contexts();
        let (alice_keys, bob_keys) =
            generate_and_process_msgs(&alice_ctx, &bob_ctx, "password", "passworf");

        assert_ne!(alice_keys, bob_keys);

        let alice_key_conf_msg = alice_ctx.generate_key_conf_msg(&alice_keys).unwrap();
        let bob_key_conf_msg = bob_ctx.generate_key_conf_msg(&bob_keys).unwrap();

        assert!(alice_ctx
            .process_key_conf_msg(bob_key_conf_msg, &alice_keys)
            .is_err());
        assert!(bob_ctx
            .process_key_conf_msg(alice_key_conf_msg, &bob_keys)
            .is_err());
    }

    #[test]
    fn test_call_generate_multiple_times() {
        let (alice_ctx, _) = setup_contexts();

        let alice_password = "password";
        alice_ctx
            .generate_msg(alice_password.as_bytes().to_vec())
            .unwrap();
        assert!(alice_ctx
            .generate_msg(alice_password.as_bytes().to_vec())
            .is_err());
    }

    #[test]
    fn same_password_multiple_times() {
        // First run
        let (alice_ctx, bob_ctx) = setup_contexts();
        let (alice_keys, bob_keys) =
            generate_and_process_msgs(&alice_ctx, &bob_ctx, "password", "password");

        assert_eq!(alice_keys, bob_keys);

        let alice_key_conf_msg = alice_ctx.generate_key_conf_msg(&alice_keys).unwrap();
        let bob_key_conf_msg = bob_ctx.generate_key_conf_msg(&bob_keys).unwrap();

        assert!(alice_ctx
            .process_key_conf_msg(bob_key_conf_msg, &alice_keys)
            .is_ok());
        assert!(bob_ctx
            .process_key_conf_msg(alice_key_conf_msg, &bob_keys)
            .is_ok());

        // Second run
        let (alice_ctx2, bob_ctx2) = setup_contexts();
        let (alice_keys2, bob_keys2) =
            generate_and_process_msgs(&alice_ctx2, &bob_ctx2, "password", "password");

        assert_eq!(alice_keys, bob_keys);

        let alice_key_conf_msg2 = alice_ctx2.generate_key_conf_msg(&alice_keys2).unwrap();
        let bob_key_conf_msg2 = bob_ctx2.generate_key_conf_msg(&bob_keys2).unwrap();

        assert!(alice_ctx2
            .process_key_conf_msg(bob_key_conf_msg2, &alice_keys2)
            .is_ok());
        assert!(bob_ctx2
            .process_key_conf_msg(alice_key_conf_msg2, &bob_keys2)
            .is_ok());

        // Keys should be different
        assert!(alice_keys != alice_keys2);
        assert!(bob_keys != bob_keys2);
    }

    #[test]
    fn read_write_key_pair() {
        let (alice_ctx, bob_ctx) = setup_contexts();

        let bob_pubkey = bob_ctx
            .generate_msg("password".as_bytes().to_vec())
            .unwrap();
        let bob_private_key = bob_ctx.read_private_key();

        alice_ctx
            .write_key_pair(bob_private_key.clone(), bob_pubkey.clone())
            .unwrap();

        let alice_new_private_key = alice_ctx.read_private_key();
        let alice_new_public_key = alice_ctx.read_public_key();

        assert_eq!(alice_new_private_key, bob_private_key);
        assert_eq!(alice_new_public_key, bob_pubkey);
    }

    #[test]
    fn async_ctx() {
        // Alice generates PAKE key
        let password = "password";
        let initial_alice_ctx =
            Spake2Context::new(Spake2Role::Alice, "alice".to_string(), "bob".to_string()).unwrap();
        let alice_pubkey = initial_alice_ctx
            .generate_msg(password.as_bytes().to_vec())
            .unwrap();
        let alice_privkey = initial_alice_ctx.read_private_key();

        // Bob generates PAKE key
        let bob_ctx =
            Spake2Context::new(Spake2Role::Bob, "bob".to_string(), "alice".to_string()).unwrap();
        let bob_pubkey = bob_ctx.generate_msg(password.as_bytes().to_vec()).unwrap();
        let bob_shared_secrets = bob_ctx.process_msg(alice_pubkey.clone(), None).unwrap();
        let bob_key_conf_msg = bob_ctx.generate_key_conf_msg(&bob_shared_secrets).unwrap();

        // Alice writes secrets into new context
        let new_alice_ctx =
            Spake2Context::new(Spake2Role::Alice, "alice".to_string(), "bob".to_string()).unwrap();
        new_alice_ctx
            .generate_msg(password.as_bytes().to_vec())
            .unwrap();
        new_alice_ctx
            .write_key_pair(alice_privkey.clone(), alice_pubkey.clone())
            .unwrap();

        // Alice verifies key confirmation from new context
        let alice_shared_secrets_from_new_ctx =
            new_alice_ctx.process_msg(bob_pubkey.clone(), None).unwrap();
        assert!(new_alice_ctx
            .process_key_conf_msg(bob_key_conf_msg, &alice_shared_secrets_from_new_ctx)
            .is_ok());
    }
}
