mod types;

use crate::types::FfiNetwork;
use bitcoin::{psbt::Psbt, secp256k1::ecdsa::Signature, Network};
use crypto::chacha20poly1305::{ChaCha20Poly1305Error, XChaCha20Poly1305};
use crypto::crypto_box::{CryptoBox, CryptoBoxError, CryptoBoxKeyPair, CryptoBoxKeyPairError};
use crypto::ecdh::Secp256k1SharedSecret;
use crypto::frost::{signing::SigningError, FrostShare};
use crypto::hkdf::{Hkdf, HkdfError};
use crypto::keys::{PublicKey, SecretKey, SecretKeyError};
use crypto::noise::{
    DhError, HardwareBackedDh, HardwareBackedKeyPair, NoiseContext, NoiseRole, NoiseWrapperError,
    PrivateKey,
};
use crypto::p256_box::{P256Box, P256BoxError, P256BoxKeyPair, P256BoxKeyPairError};
use crypto::signature_utils::{
    compact_signature_from_der, compact_signature_to_der, CompactSignature, DERSignature,
    SignatureUtilsError,
};
use crypto::signature_verifier::{SignatureVerifier, SignatureVerifierError};
use crypto::spake2::{Spake2Context, Spake2Error, Spake2Keys, Spake2Role};
use frost::{
    compute_frost_wallet_descriptor, FrostSigner, KeyCommitments, KeygenError, ShareDetails,
    ShareGenerator, SharePackage, WalletDescriptor,
};
use lightning_support::invoice::{Invoice, InvoiceError, Sha256};
use miniscript::DescriptorPublicKey;
use wsm_integrity::{WsmContext, WsmIntegrityVerifier, WsmIntegrityVerifierError};

uniffi::include_scaffolding!("core");
