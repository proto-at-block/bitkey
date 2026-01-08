use crate::bitcoin::Network;
use crate::error::DescriptorKeyError;
use crate::keys::{DerivationPath, DescriptorPublicKey, DescriptorSecretKey, Mnemonic};
use std::sync::Arc;

fn get_inner() -> DescriptorSecretKey {
    let mnemonic = Mnemonic::from_string("chaos fabric time speed sponsor all flat solution wisdom trophy crack object robot pave observe combine where aware bench orient secret primary cable detect".to_string()).unwrap();
    DescriptorSecretKey::new(Network::Testnet, &mnemonic, None)
}

fn derive_dsk(
    key: &DescriptorSecretKey,
    path: &str,
) -> Result<Arc<DescriptorSecretKey>, DescriptorKeyError> {
    let path = DerivationPath::new(path.to_string()).unwrap();
    key.derive(&path)
}

fn extend_dsk(
    key: &DescriptorSecretKey,
    path: &str,
) -> Result<Arc<DescriptorSecretKey>, DescriptorKeyError> {
    let path = DerivationPath::new(path.to_string()).unwrap();
    key.extend(&path)
}

fn derive_dpk(
    key: &DescriptorPublicKey,
    path: &str,
) -> Result<Arc<DescriptorPublicKey>, DescriptorKeyError> {
    let path = DerivationPath::new(path.to_string()).unwrap();
    key.derive(&path)
}

fn extend_dpk(
    key: &DescriptorPublicKey,
    path: &str,
) -> Result<Arc<DescriptorPublicKey>, DescriptorKeyError> {
    let path = DerivationPath::new(path.to_string()).unwrap();
    key.extend(&path)
}

#[test]
fn test_generate_descriptor_secret_key() {
    let master_dsk = get_inner();
    assert_eq!(master_dsk.to_string(), "tprv8ZgxMBicQKsPdWuqM1t1CDRvQtQuBPyfL6GbhQwtxDKgUAVPbxmj71pRA8raTqLrec5LyTs5TqCxdABcZr77bt2KyWA5bizJHnC4g4ysm4h/*");
    assert_eq!(master_dsk.as_public().to_string(), "tpubD6NzVbkrYhZ4WywdEfYbbd62yuvqLjAZuPsNyvzCNV85JekAEMbKHWSHLF9h3j45SxewXDcLv328B1SEZrxg4iwGfmdt1pDFjZiTkGiFqGa/*");
}

#[test]
fn test_derive_self() {
    let master_dsk = get_inner();
    let derived_dsk: &DescriptorSecretKey = &derive_dsk(&master_dsk, "m").unwrap();
    assert_eq!(derived_dsk.to_string(), "[d1d04177]tprv8ZgxMBicQKsPdWuqM1t1CDRvQtQuBPyfL6GbhQwtxDKgUAVPbxmj71pRA8raTqLrec5LyTs5TqCxdABcZr77bt2KyWA5bizJHnC4g4ysm4h/*");
    let master_dpk: &DescriptorPublicKey = &master_dsk.as_public();
    let derived_dpk: &DescriptorPublicKey = &derive_dpk(master_dpk, "m").unwrap();
    assert_eq!(derived_dpk.to_string(), "[d1d04177]tpubD6NzVbkrYhZ4WywdEfYbbd62yuvqLjAZuPsNyvzCNV85JekAEMbKHWSHLF9h3j45SxewXDcLv328B1SEZrxg4iwGfmdt1pDFjZiTkGiFqGa/*");
}

#[test]
fn test_derive_descriptors_keys() {
    let master_dsk = get_inner();
    let derived_dsk: &DescriptorSecretKey = &derive_dsk(&master_dsk, "m/0").unwrap();
    assert_eq!(derived_dsk.to_string(), "[d1d04177/0]tprv8d7Y4JLmD25jkKbyDZXcdoPHu1YtMHuH21qeN7mFpjfumtSU7eZimFYUCSa3MYzkEYfSNRBV34GEr2QXwZCMYRZ7M1g6PUtiLhbJhBZEGYJ/*");
    let master_dpk: &DescriptorPublicKey = &master_dsk.as_public();
    let derived_dpk: &DescriptorPublicKey = &derive_dpk(master_dpk, "m/0").unwrap();
    assert_eq!(derived_dpk.to_string(), "[d1d04177/0]tpubD9oaCiP1MPmQdndm7DCD3D3QU34pWd6BbKSRedoZF1UJcNhEk3PJwkALNYkhxeTKL29oGNR7psqvT1KZydCGqUDEKXN6dVQJY2R8ooLPy8m/*");
}

#[test]
fn test_extend_descriptor_keys() {
    let master_dsk = get_inner();
    let extended_dsk: &DescriptorSecretKey = &extend_dsk(&master_dsk, "m/0").unwrap();
    assert_eq!(extended_dsk.to_string(), "tprv8ZgxMBicQKsPdWuqM1t1CDRvQtQuBPyfL6GbhQwtxDKgUAVPbxmj71pRA8raTqLrec5LyTs5TqCxdABcZr77bt2KyWA5bizJHnC4g4ysm4h/0/*");
    let master_dpk: &DescriptorPublicKey = &master_dsk.as_public();
    let extended_dpk: &DescriptorPublicKey = &extend_dpk(master_dpk, "m/0").unwrap();
    assert_eq!(extended_dpk.to_string(), "tpubD6NzVbkrYhZ4WywdEfYbbd62yuvqLjAZuPsNyvzCNV85JekAEMbKHWSHLF9h3j45SxewXDcLv328B1SEZrxg4iwGfmdt1pDFjZiTkGiFqGa/0/*");
    let wif = "L2wTu6hQrnDMiFNWA5na6jB12ErGQqtXwqpSL7aWquJaZG8Ai3ch";
    let extended_key = DescriptorSecretKey::from_string(wif.to_string()).unwrap();
    let result = extended_key.derive(&DerivationPath::new("m/0".to_string()).unwrap());
    dbg!(&result);
    assert!(result.is_err());
}

#[test]
fn test_from_str_inner() {
    let key1 = "L2wTu6hQrnDMiFNWA5na6jB12ErGQqtXwqpSL7aWquJaZG8Ai3ch";
    let key2 = "tprv8ZgxMBicQKsPcwcD4gSnMti126ZiETsuX7qwrtMypr6FBwAP65puFn4v6c3jrN9VwtMRMph6nyT63NrfUL4C3nBzPcduzVSuHD7zbX2JKVc/1/1/1/*";
    let private_descriptor_key1 = DescriptorSecretKey::from_string(key1.to_string()).unwrap();
    let private_descriptor_key2 = DescriptorSecretKey::from_string(key2.to_string()).unwrap();
    dbg!(private_descriptor_key1);
    dbg!(private_descriptor_key2);
    // Should error out because you can't produce a DescriptorSecretKey from an xpub
    let key0 = "tpubDBrgjcxBxnXyL575sHdkpKohWu5qHKoQ7TJXKNrYznh5fVEGBv89hA8ENW7A8MFVpFUSvgLqc4Nj1WZcpePX6rrxviVtPowvMuGF5rdT2Vi";
    assert!(DescriptorSecretKey::from_string(key0.to_string()).is_err());
}

#[test]
fn test_derive_and_extend_inner() {
    let master_dsk = get_inner();
    // derive DescriptorSecretKey with path "m/0" from master
    let derived_dsk: &DescriptorSecretKey = &derive_dsk(&master_dsk, "m/0").unwrap();
    assert_eq!(derived_dsk.to_string(), "[d1d04177/0]tprv8d7Y4JLmD25jkKbyDZXcdoPHu1YtMHuH21qeN7mFpjfumtSU7eZimFYUCSa3MYzkEYfSNRBV34GEr2QXwZCMYRZ7M1g6PUtiLhbJhBZEGYJ/*");
    // extend derived_dsk with path "m/0"
    let extended_dsk: &DescriptorSecretKey = &extend_dsk(derived_dsk, "m/0").unwrap();
    assert_eq!(extended_dsk.to_string(), "[d1d04177/0]tprv8d7Y4JLmD25jkKbyDZXcdoPHu1YtMHuH21qeN7mFpjfumtSU7eZimFYUCSa3MYzkEYfSNRBV34GEr2QXwZCMYRZ7M1g6PUtiLhbJhBZEGYJ/0/*");
}

#[test]
fn test_derive_hardened_path_using_public() {
    let master_dpk = get_inner().as_public();
    let derived_dpk = &derive_dpk(&master_dpk, "m/84h/1h/0h");
    assert!(derived_dpk.is_err());
}

#[test]
fn test_derivation_path_parsing() {
    let path_str = "m/44h/0h/0h/0/0";
    let expected_path = "44'/0'/0'/0/0";
    let path_str_2 = "m";

    let derivation_path = DerivationPath::new(path_str.to_string()).unwrap();
    assert_eq!(derivation_path.to_string(), expected_path);

    let derivation_path2 = DerivationPath::new(path_str_2.to_string()).unwrap();
    assert!(derivation_path2.is_master());

    let derivation_path3 = DerivationPath::master();
    assert!(derivation_path3.is_master());

    assert!(!derivation_path.is_master());
}

#[test]
fn test_invalid_derivation_path() {
    let invalid_path_str = "m/44x/0h/0h/0/0";
    let result = DerivationPath::new(invalid_path_str.to_string());
    assert!(result.is_err());
}
