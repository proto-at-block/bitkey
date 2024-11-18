use boring::bn::{BigNum, BigNumContext};
use boring::ec::{EcGroup, EcKey, EcPoint, PointConversionForm};
use boring::nid::Nid;
use p256::elliptic_curve::sec1::{FromEncodedPoint, ToEncodedPoint};
use p256::{EncodedPoint, PublicKey};
use snow::{
    params::{CipherChoice, DHChoice, HashChoice, NoiseParams},
    resolvers::{CryptoResolver, DefaultResolver},
    types::{Cipher, Dh, Hash, Random},
    Builder, HandshakeState, TransportState,
};
use std::{
    ops::Deref,
    sync::{Arc, Mutex},
};
use thiserror::Error;

#[derive(Debug, thiserror::Error)]
pub enum DhError {
    #[error("Invalid public key")]
    InvalidPublicKey,
    #[error("Key exchange failed")]
    ExchangeFailed,
}

pub struct HardwareBackedKeyPair {
    pub privkey_name: String,
    pub pubkey: Vec<u8>,
}

pub enum PrivateKey {
    InMemory { secret_bytes: Vec<u8> },
    HardwareBacked { name: String },
}

// This struct is used to resolve the DH implementation to use for `snow`.
struct HardwareP256Resolver {
    hardware_backed_dh: Arc<Box<dyn HardwareBackedDh>>,
}

impl HardwareP256Resolver {
    fn new(hardware_backed_dh: Box<dyn HardwareBackedDh>) -> Self {
        HardwareP256Resolver {
            hardware_backed_dh: Arc::new(hardware_backed_dh),
        }
    }
}

// This struct provides a bridge between the `Dh` trait from the `snow` crate and the `HardwareBackedDh` trait
// which implements foreign bindings to use a hardware keystore. It carries the state the `snow` needs, along
// with an FFI interface.
struct HardwareBackedDhAdapter {
    foreign_impl: Arc<Box<dyn HardwareBackedDh>>, // FFI

    privkey: String, // Private key *name*; the identifier used by the hardware keystore
    pubkey: Vec<u8>, // This will be DER encoded coming from the hardware keystore, but we store it here as SEC1
}

pub trait HardwareBackedDh: Send + Sync {
    fn dh(&self, our_privkey_name: String, peer_pubkey: Vec<u8>) -> Result<Vec<u8>, DhError>;
    fn generate(&self) -> Result<HardwareBackedKeyPair, DhError>;
    fn pubkey(&self, privkey_name: String) -> Result<Vec<u8>, DhError>;
}

// This is the hardware-backed implementation of the DH trait. It relies on callbacks provided
// by Kotlin / Swift code to grant access to the hardware keystore.
// Calls to `foreign_impl` are FFI calls back up to the Swift / Kotlin code.
impl Dh for HardwareBackedDhAdapter {
    fn name(&self) -> &'static str {
        "p256"
    }

    fn pub_len(&self) -> usize {
        33
    }

    fn priv_len(&self) -> usize {
        unreachable!("priv_len() should not be called");
    }

    fn set(&mut self, privkey: &[u8]) {
        self.privkey = String::from_utf8(privkey.to_vec()).expect("Invalid UTF-8");
        let pubkey_der = self
            .foreign_impl
            .pubkey(self.privkey.clone())
            .expect("Failed to get pubkey");
        self.pubkey = sec1_uncompressed_to_compressed(&pubkey_der).expect("Invalid public key");
    }

    fn generate(&mut self, _: &mut dyn Random) {
        let keypair = self
            .foreign_impl
            .generate()
            .expect("Failed to generate keypair");
        self.privkey = keypair.privkey_name;
        self.pubkey = sec1_uncompressed_to_compressed(&keypair.pubkey).expect("Invalid public key");
    }

    fn pubkey(&self) -> &[u8] {
        &self.pubkey
    }

    fn privkey(&self) -> &[u8] {
        &self.privkey.as_bytes()
    }

    fn dh(&self, pubkey: &[u8], out: &mut [u8]) -> Result<(), snow::Error> {
        // snow doesn't trim the pubkey, so we'll get leading zeros up to MAXDHLEN if we
        // don't do this.
        let trimmed_pubkey = &pubkey[..self.pub_len()];
        // We also need to encode to DER, since that's what the hardware keystore expects
        let der_pubkey =
            sec1_compressed_to_uncompressed(trimmed_pubkey).map_err(|_| snow::Error::Dh)?;

        let shared_secret = self
            .foreign_impl
            .deref()
            .dh(self.privkey.clone(), der_pubkey)
            .map_err(|_| snow::Error::Dh)?;

        // The shared secret should be the x coordinate of the shared point
        assert!(shared_secret.len() == 32);
        out[..shared_secret.len()].copy_from_slice(&shared_secret);

        Ok(())
    }
}

// This struct is used to resolve the DH implementation to use for `snow`, but with a software-based implementation.
// This is used for Android phones that lack a TEE or SE which supports hardware-backed ECDH.
#[derive(Default)]
pub struct SoftwareP256DhAdapter {
    private_key: Vec<u8>,
    public_key: Vec<u8>,
}

fn compute_pubkey(privkey: &[u8]) -> Result<Vec<u8>, DhError> {
    let group = EcGroup::from_curve_name(Nid::X9_62_PRIME256V1).expect("Failed to create group");
    let priv_bn = BigNum::from_slice(privkey).map_err(|_| DhError::ExchangeFailed)?;

    let mut ctx = BigNumContext::new().map_err(|_| DhError::ExchangeFailed)?;
    let mut pub_key = EcPoint::new(&group).map_err(|_| DhError::ExchangeFailed)?;

    pub_key
        .mul_generator(&group, &priv_bn, &mut ctx)
        .map_err(|_| DhError::ExchangeFailed)?;

    let pub_key_bytes = pub_key
        .to_bytes(&group, PointConversionForm::COMPRESSED, &mut ctx)
        .map_err(|_| DhError::ExchangeFailed)?;

    Ok(pub_key_bytes)
}

impl Dh for SoftwareP256DhAdapter {
    fn name(&self) -> &'static str {
        "p256"
    }

    fn pub_len(&self) -> usize {
        33
    }

    fn priv_len(&self) -> usize {
        32
    }

    fn set(&mut self, privkey: &[u8]) {
        let group =
            EcGroup::from_curve_name(Nid::X9_62_PRIME256V1).expect("Failed to create group");
        let priv_bn = BigNum::from_slice(privkey).expect("Failed to create private key");

        // Recreate the EcKey from the private key bytes
        let mut ctx = BigNumContext::new().expect("Failed to create context");
        let mut pub_key = EcPoint::new(&group).expect("Failed to create public key");

        // Derive the public key from the private key
        pub_key
            .mul_generator(&group, &priv_bn, &mut ctx)
            .expect("Failed to multiply generator");

        let private_key = EcKey::from_private_components(&group, &priv_bn, &pub_key)
            .expect("Failed to create private key");

        self.private_key = privkey.to_vec();
        self.public_key = private_key
            .public_key()
            .to_bytes(&group, PointConversionForm::COMPRESSED, &mut ctx)
            .expect("Failed to create public key");
    }

    fn generate(&mut self, _: &mut dyn Random) {
        let group =
            EcGroup::from_curve_name(Nid::X9_62_PRIME256V1).expect("Failed to create group");

        let private_key = EcKey::generate(&group).expect("Failed to generate private key");

        self.private_key = private_key.private_key().to_vec();

        // Compute and store the public key in compressed form
        let mut ctx = BigNumContext::new().expect("Failed to create context");
        self.public_key = private_key
            .public_key()
            .to_bytes(&group, PointConversionForm::COMPRESSED, &mut ctx)
            .expect("Failed to create public key");
    }

    fn pubkey(&self) -> &[u8] {
        &self.public_key
    }

    fn privkey(&self) -> &[u8] {
        &self.private_key
    }

    fn dh(&self, pubkey: &[u8], out: &mut [u8]) -> Result<(), snow::Error> {
        // Create the elliptic curve group for P-256 (prime256v1)
        let group =
            EcGroup::from_curve_name(Nid::X9_62_PRIME256V1).expect("Failed to create group");
        let mut ctx = BigNumContext::new().expect("Failed to create context");

        // snow doesn't trim the pubkey, so we'll get leading zeros up to MAXDHLEN if we
        // don't do this.
        let pubkey = &pubkey[..self.pub_len()];

        // Recreate the peer's public key from the compressed SEC1 bytes
        let peer_pub_key =
            EcPoint::from_bytes(&group, pubkey, &mut ctx).map_err(|_| snow::Error::Dh)?;

        // Recreate the private key from the stored private key bytes
        let priv_bn = BigNum::from_slice(&self.private_key).map_err(|_| snow::Error::Dh)?;

        let public_key = compute_pubkey(&self.private_key).map_err(|_| snow::Error::Dh)?;
        let pubkey_ecpoint =
            EcPoint::from_bytes(&group, &public_key, &mut ctx).map_err(|_| snow::Error::Dh)?;
        let private_key = EcKey::from_private_components(&group, &priv_bn, &pubkey_ecpoint)
            .map_err(|_| snow::Error::Dh)?;

        // Create a new point to store the result of the ECDH operation
        let mut shared_secret_point = EcPoint::new(&group).map_err(|_| snow::Error::Dh)?;

        // Perform the scalar multiplication (ECDH) to compute the shared secret
        shared_secret_point
            .mul(&group, &peer_pub_key, private_key.private_key(), &mut ctx)
            .map_err(|_| snow::Error::Dh)?;

        let shared_secret_bytes = shared_secret_point
            .to_bytes(&group, PointConversionForm::COMPRESSED, &mut ctx)
            .map_err(|_| snow::Error::Dh)?;

        // The shared secret should be the (x, y) coordinates of the shared point
        assert!(shared_secret_bytes[0] == 2 || shared_secret_bytes[0] == 3);
        let shared_secret_bytes = &shared_secret_bytes[1..]; // Lop off the leading byte
        assert!(shared_secret_bytes.len() == 32);

        out[..shared_secret_bytes.len()].copy_from_slice(&shared_secret_bytes);

        Ok(())
    }
}

impl CryptoResolver for HardwareP256Resolver {
    fn resolve_rng(&self) -> Option<Box<dyn Random>> {
        DefaultResolver.resolve_rng()
    }

    fn resolve_dh(&self, _: &DHChoice) -> Option<Box<dyn Dh>> {
        Some(Box::new(HardwareBackedDhAdapter {
            foreign_impl: self.hardware_backed_dh.clone(),
            privkey: String::new(),
            pubkey: Vec::new(),
        }))
    }

    fn resolve_hash(&self, choice: &HashChoice) -> Option<Box<dyn Hash>> {
        DefaultResolver.resolve_hash(choice)
    }

    fn resolve_cipher(&self, choice: &CipherChoice) -> Option<Box<dyn Cipher>> {
        DefaultResolver.resolve_cipher(choice)
    }
}

#[derive(Default)]
struct SoftwareP256Resolver {}

impl CryptoResolver for SoftwareP256Resolver {
    fn resolve_rng(&self) -> Option<Box<dyn Random>> {
        DefaultResolver.resolve_rng()
    }

    fn resolve_dh(&self, _: &DHChoice) -> Option<Box<dyn Dh>> {
        Some(Box::new(SoftwareP256DhAdapter::default()))
    }

    fn resolve_hash(&self, choice: &HashChoice) -> Option<Box<dyn Hash>> {
        DefaultResolver.resolve_hash(choice)
    }

    fn resolve_cipher(&self, choice: &CipherChoice) -> Option<Box<dyn Cipher>> {
        DefaultResolver.resolve_cipher(choice)
    }
}

const NOISE_PARAMS: &str = "Noise_IK_p256_ChaChaPoly_SHA256";
const NOISE_PROLOGUE: &[u8] = b"bitkey";

fn create_params() -> NoiseParams {
    NoiseParams {
        name: NOISE_PARAMS.to_string(),
        base: snow::params::BaseChoice::Noise,
        handshake: "IK".parse().expect("Invalid handshake argument"),
        dh: snow::params::DHChoice::Curve25519, // This value is ignored!! But we must satisfy DHChoice.
        cipher: snow::params::CipherChoice::ChaChaPoly,
        hash: snow::params::HashChoice::SHA256,
    }
}

#[derive(Debug)]
pub enum NoiseRole {
    Initiator,
    Responder,
}

#[derive(Debug)]
struct NoiseContextState {
    handshake: Option<HandshakeState>,
    transport: Option<TransportState>,
}

#[derive(Debug)]
pub struct NoiseContext {
    pub role: NoiseRole,
    state: Arc<Mutex<NoiseContextState>>,
    scratch: Arc<Mutex<Vec<u8>>>,
}

#[derive(Debug, Error)]
pub enum NoiseWrapperError {
    #[error("Internal error: {0}")]
    InternalError(#[from] snow::Error),
    #[error("Not my turn to write message")]
    HandshakeNotMyTurn,
    #[error("Handshake not finished")]
    HandshakeNotFinished,
    #[error("Failed to access transport state")]
    Transport,
    #[error("Somebody wrote some bad code")]
    IllegalState,
}

const NOISE_MAX_MESSAGE_SIZE: usize = 65535;

impl NoiseContext {
    pub fn new(
        role: NoiseRole,
        privkey: PrivateKey,
        their_public_key: Option<Vec<u8>>, // DER encoded at this point. This is None for the server, should be set for the client.
        hardware_backed_dh: Option<Box<dyn HardwareBackedDh>>, // If None, uses software DH; not the hardware callbacks.
    ) -> Result<Self, NoiseWrapperError> {
        let builder = match hardware_backed_dh {
            Some(hardware_backed_dh) => {
                let resolver = Box::new(HardwareP256Resolver::new(hardware_backed_dh));
                Builder::with_resolver(create_params(), resolver)
            }
            None => {
                let resolver = Box::new(SoftwareP256Resolver::default());
                Builder::with_resolver(create_params(), resolver)
            }
        };

        let sec1_public_key = their_public_key
            .as_ref()
            .map(|der| sec1_uncompressed_to_compressed(der))
            .transpose()
            .map_err(|_| NoiseWrapperError::InternalError(snow::Error::Dh))?;

        let privkey = match privkey {
            PrivateKey::InMemory { secret_bytes } => secret_bytes,
            PrivateKey::HardwareBacked { name } => name.as_bytes().to_vec(),
        };

        let handshake = match role {
            NoiseRole::Initiator => builder
                .prologue(NOISE_PROLOGUE)
                .local_private_key(&privkey)
                .remote_public_key(&sec1_public_key.expect("No public key provided"))
                .build_initiator(),
            NoiseRole::Responder => builder
                .prologue(NOISE_PROLOGUE)
                .local_private_key(&privkey)
                .build_responder(),
        }?;

        Ok(Self {
            role,
            state: Arc::new(Mutex::new(NoiseContextState {
                handshake: Some(handshake),
                transport: None,
            })),
            scratch: Arc::new(Mutex::new(vec![0u8; NOISE_MAX_MESSAGE_SIZE])),
        })
    }

    pub fn initiate_handshake(&self) -> Result<Vec<u8>, NoiseWrapperError> {
        let mut result = self.do_advance_handshake(None)?;
        result.take().ok_or(NoiseWrapperError::HandshakeNotMyTurn)
    }

    pub fn advance_handshake(
        &self,
        peer_handshake_message: Vec<u8>,
    ) -> Result<Option<Vec<u8>>, NoiseWrapperError> {
        self.do_advance_handshake(Some(peer_handshake_message))
    }

    fn do_advance_handshake(
        &self,
        peer_handshake_message: Option<Vec<u8>>, // Should only be None if it's the first message.
    ) -> Result<Option<Vec<u8>>, NoiseWrapperError> {
        let mut state = self.state.lock().expect("Failed to lock state");
        let mut scratch = self.scratch.lock().expect("Failed to lock scratch");
        let mut handshake = state
            .handshake
            .take()
            .ok_or(NoiseWrapperError::IllegalState)
            .map_err(|_| NoiseWrapperError::IllegalState)?;

        if let Some(message) = peer_handshake_message {
            let _ = handshake.read_message(&message, &mut scratch)?;
            if handshake.is_handshake_finished() {
                state.handshake = Some(handshake);
                return Ok(None);
            }
        }

        let len = handshake.write_message(&[], &mut scratch)?;
        let result = scratch[..len].to_vec();

        state.handshake = Some(handshake);
        Ok(Some(result))
    }

    pub fn finalize_handshake(&self) -> Result<(), NoiseWrapperError> {
        let mut state = self.state.lock().expect("Failed to lock state");

        // Take the handshake state out of the Option
        let handshake = state
            .handshake
            .take()
            .ok_or(NoiseWrapperError::HandshakeNotFinished)?;

        let new_transport = handshake.into_transport_mode()?;

        state.transport = Some(new_transport);

        Ok(())
    }

    pub fn is_handshake_finished(&self) -> bool {
        let state = self.state.lock().expect("Failed to lock state");
        if let Some(hs) = &state.handshake {
            hs.is_handshake_finished()
        } else {
            false
        }
    }

    pub fn encrypt_message(&self, message: &[u8]) -> Result<Vec<u8>, NoiseWrapperError> {
        let mut state = self.state.lock().expect("Failed to lock state");
        let transport_state = state
            .transport
            .as_mut()
            .ok_or(NoiseWrapperError::Transport)?;
        let mut scratch = self.scratch.lock().expect("Failed to lock scratch");

        // Write the message using the transport state
        let len = transport_state.write_message(message, &mut scratch)?;
        let result = scratch[..len].to_vec();

        Ok(result)
    }

    pub fn decrypt_message(&self, message: &[u8]) -> Result<Vec<u8>, NoiseWrapperError> {
        let mut state = self.state.lock().expect("Failed to lock state");
        let transport_state = state
            .transport
            .as_mut()
            .ok_or(NoiseWrapperError::Transport)?;
        let mut scratch = self.scratch.lock().expect("Failed to lock scratch");

        // Read the message using the transport state
        let len = transport_state.read_message(message, &mut scratch)?;
        let result = scratch[..len].to_vec();

        Ok(result)
    }
}

fn sec1_uncompressed_to_compressed(sec1_uncompressed: &[u8]) -> Result<Vec<u8>, DhError> {
    let encoded_point =
        EncodedPoint::from_bytes(sec1_uncompressed).map_err(|_| DhError::InvalidPublicKey)?;

    let public_key = PublicKey::from_encoded_point(&encoded_point);

    if public_key.is_some().into() {
        Ok(public_key
            .unwrap()
            .to_encoded_point(true)
            .as_bytes()
            .to_vec())
    } else {
        Err(DhError::InvalidPublicKey)
    }
}

fn sec1_compressed_to_uncompressed(sec1_compressed: &[u8]) -> Result<Vec<u8>, DhError> {
    // Parse the compressed public key (it should start with 0x02 or 0x03)
    let encoded_point =
        EncodedPoint::from_bytes(sec1_compressed).map_err(|_| DhError::InvalidPublicKey)?;

    // Create a `PublicKey` from the encoded point
    let public_key = PublicKey::from_encoded_point(&encoded_point);

    if public_key.is_some().into() {
        Ok(public_key
            .unwrap()
            .to_encoded_point(false)
            .as_bytes()
            .to_vec())
    } else {
        Err(DhError::InvalidPublicKey)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn generate_keypair() -> (Vec<u8>, Vec<u8>) {
        let builder: Builder =
            Builder::with_resolver(create_params(), Box::new(SoftwareP256Resolver::default()));
        let keypair = builder.generate_keypair().unwrap();
        (keypair.private, keypair.public)
    }

    fn server_session(static_keypair: (Vec<u8>, Vec<u8>)) -> HandshakeState {
        let (private, _) = static_keypair;
        let builder =
            Builder::with_resolver(create_params(), Box::new(SoftwareP256Resolver::default()));
        builder
            .prologue(NOISE_PROLOGUE)
            .local_private_key(&private)
            .build_responder()
            .unwrap()
    }

    fn client_session(server_pub_key: &[u8]) -> HandshakeState {
        let (sk, _) = generate_keypair();
        let builder: Builder =
            Builder::with_resolver(create_params(), Box::new(SoftwareP256Resolver::default()));
        builder
            .prologue(NOISE_PROLOGUE)
            .local_private_key(&sk)
            .remote_public_key(server_pub_key)
            .build_initiator()
            .unwrap()
    }

    fn proceed_handshake(
        client: &mut HandshakeState,
        server: &mut HandshakeState,
        client_buffer: &mut [u8],
        server_buffer: &mut [u8],
    ) -> bool {
        println!("client -> server");
        let len = client.write_message(&[], client_buffer).unwrap();
        server
            .read_message(&client_buffer[..len], server_buffer)
            .unwrap();

        println!("server -> client");
        let len = server.write_message(&[], server_buffer).unwrap();
        client
            .read_message(&server_buffer[..len], client_buffer)
            .unwrap();

        client.is_handshake_finished() && server.is_handshake_finished()
    }

    #[test]
    fn test_low_level_api() {
        // Generate static keypair for the server
        let server_keypair = generate_keypair();
        let server_pub_key = server_keypair.1.clone();

        let mut server = server_session(server_keypair);
        let mut client = client_session(&server_pub_key);

        let mut client_buffer = vec![0u8; 65535];
        let mut server_buffer = vec![0u8; 65535];

        // Message back-and-forth until handshake is finished
        while !proceed_handshake(
            &mut client,
            &mut server,
            &mut client_buffer,
            &mut server_buffer,
        ) {}

        assert!(server.is_handshake_finished());
        assert!(client.is_handshake_finished());

        let mut server_transport = server.into_transport_mode().unwrap();
        let mut client_transport = client.into_transport_mode().unwrap();

        // Client -> server

        let len = client_transport
            .write_message(b"Hello, server!", &mut client_buffer)
            .unwrap();

        // Ensure the buffer has encrypted contents
        assert_ne!(&client_buffer[..len], b"Hello, server!");

        let len = server_transport
            .read_message(&client_buffer[..len], &mut server_buffer)
            .unwrap();

        let received_message = &server_buffer[..len];
        assert_eq!(received_message, b"Hello, server!");

        // Server -> client

        let len = server_transport
            .write_message(b"Hello, client!", &mut server_buffer)
            .unwrap();

        // Ensure the buffer has encrypted contents
        assert_ne!(&server_buffer[..len], b"Hello, client!");

        let len = client_transport
            .read_message(&server_buffer[..len], &mut client_buffer)
            .unwrap();

        let received_message = &client_buffer[..len];
        assert_eq!(received_message, b"Hello, client!");
    }

    #[test]
    fn test_high_level_api() {
        let server_keypair = generate_keypair();
        let client_keypair = generate_keypair();

        let server = NoiseContext::new(
            NoiseRole::Responder,
            PrivateKey::InMemory {
                secret_bytes: server_keypair.0,
            },
            Some(client_keypair.1),
            None,
        )
        .unwrap();
        let client = NoiseContext::new(
            NoiseRole::Initiator,
            PrivateKey::InMemory {
                secret_bytes: client_keypair.0,
            },
            Some(server_keypair.1),
            None,
        )
        .unwrap();

        // Handshake
        let client_message = client.initiate_handshake().unwrap();
        let server_message = server.advance_handshake(client_message).unwrap();

        assert!(!client.is_handshake_finished());
        assert!(server.is_handshake_finished());

        let _ = client.advance_handshake(server_message.unwrap()).unwrap();

        assert!(client.is_handshake_finished());

        // Into transport mode
        assert!(client.finalize_handshake().is_ok());
        assert!(server.finalize_handshake().is_ok());

        // Session encryption
        let c2s_ct = client.encrypt_message(b"Hello, server!").unwrap();
        let c2s_pt = server.decrypt_message(&c2s_ct).unwrap();
        assert_eq!(c2s_pt, b"Hello, server!");

        let s2c_ct = server.encrypt_message(b"Hello, client!").unwrap();
        let s2c_pt = client.decrypt_message(&s2c_ct).unwrap();
        assert_eq!(s2c_pt, b"Hello, client!");
    }
}
