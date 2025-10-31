use std::{error::Error, fmt::Display, str::FromStr};

use bdk::{
    bitcoin::{
        ecdsa,
        key::Secp256k1,
        psbt::{raw::ProprietaryKey, Input as PsbtInput, Psbt},
        secp256k1::{All, Message, PublicKey, Scalar, SecretKey},
        sighash::{EcdsaSighashType, SighashCache},
        Transaction,
    },
    miniscript::{psbt::PsbtExt, Descriptor},
};
use crypto::chaincode_delegation::common::{PROPRIETARY_KEY_PREFIX, PROPRIETARY_KEY_SUBTYPE};

use crate::psbt_verification::verify_inputs_only_have_one_signature;

#[derive(Debug)]
pub enum ChaincodeDelegateSignerError {
    SighashError(String),
    InvalidTweak(String),
    InvalidPsbt(String),
    InvalidWitness(String),
}

impl Display for ChaincodeDelegateSignerError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{:?}", self)
    }
}

impl Error for ChaincodeDelegateSignerError {}

#[derive(Debug)]
pub struct ChaincodeDelegateSigner {
    custodian_key: SecretKey,
    app_public_key: PublicKey,
    hw_public_key: PublicKey,
}

impl ChaincodeDelegateSigner {
    pub fn new(
        custodian_key: SecretKey,
        app_public_key: PublicKey,
        hw_public_key: PublicKey,
    ) -> Self {
        Self {
            custodian_key,
            app_public_key,
            hw_public_key,
        }
    }

    pub fn sign_psbt(
        &self,
        psbt: &mut Psbt,
        secp: &Secp256k1<All>,
    ) -> Result<(), ChaincodeDelegateSignerError> {
        let tx = &psbt.unsigned_tx;
        let mut sighash_cache = SighashCache::new(tx);
        let proprietary_keys = self.proprietary_keys(secp);

        verify_inputs_only_have_one_signature(&psbt.inputs)
            .map_err(|e| ChaincodeDelegateSignerError::InvalidPsbt(e.to_string()))?;

        for (input_index, psbt_input) in psbt.inputs.iter_mut().enumerate() {
            // Extract and parse tweaks from proprietary map
            let (app_tweak, hw_tweak, custodian_tweak) =
                self.extract_tweaks(psbt_input, &proprietary_keys)?;

            // Derive tweaked keys
            let tweaked_custodian_key =
                self.custodian_key
                    .add_tweak(&custodian_tweak)
                    .map_err(|e| {
                        ChaincodeDelegateSignerError::InvalidTweak(format!(
                            "Failed to tweak custodian key: {}",
                            e
                        ))
                    })?;
            let tweaked_custodian_public_key = tweaked_custodian_key.public_key(secp);
            let tweaked_hw_public_key =
                self.hw_public_key
                    .add_exp_tweak(secp, &hw_tweak)
                    .map_err(|e| {
                        ChaincodeDelegateSignerError::InvalidTweak(format!(
                            "Failed to tweak hw key: {}",
                            e
                        ))
                    })?;
            let tweaked_app_public_key = self
                .app_public_key
                .add_exp_tweak(secp, &app_tweak)
                .map_err(|e| {
                    ChaincodeDelegateSignerError::InvalidTweak(format!(
                        "Failed to tweak app key: {}",
                        e
                    ))
                })?;

            // Verify BIP32 derivation info is present
            if !psbt_input
                .bip32_derivation
                .contains_key(&tweaked_app_public_key)
            {
                return Err(ChaincodeDelegateSignerError::InvalidPsbt(
                    "Missing BIP32 derivation for app key".to_string(),
                ));
            }

            // Verify witness script matches expected multisig
            self.verify_witness_script(
                psbt_input,
                &tweaked_app_public_key,
                &tweaked_hw_public_key,
                &tweaked_custodian_public_key,
            )?;

            // Add custodian signature to PSBT
            let signature = secp.sign_ecdsa(
                &self.compute_sighash(&mut sighash_cache, input_index, psbt_input)?,
                &tweaked_custodian_key,
            );
            let sighash_type = psbt_input
                .sighash_type
                .and_then(|sht| sht.ecdsa_hash_ty().ok())
                .unwrap_or(EcdsaSighashType::All);

            psbt_input.partial_sigs.insert(
                bdk::bitcoin::PublicKey::new(tweaked_custodian_public_key),
                ecdsa::Signature {
                    sig: signature,
                    hash_ty: sighash_type,
                },
            );
        }

        psbt.finalize_mut(secp).map_err(|errors| {
            ChaincodeDelegateSignerError::InvalidPsbt(format!(
                "Failed to finalize PSBT. Errors: {}",
                errors
                    .iter()
                    .map(|e| e.to_string())
                    .collect::<Vec<String>>()
                    .join(", ")
            ))
        })?;

        Ok(())
    }

    fn proprietary_keys(&self, secp: &Secp256k1<All>) -> WalletProprietaryKeys {
        WalletProprietaryKeys {
            custodian_key: ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: self.custodian_key.public_key(secp).serialize().to_vec(),
            },
            app_key: ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: self.app_public_key.serialize().to_vec(),
            },
            hw_key: ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: self.hw_public_key.serialize().to_vec(),
            },
        }
    }

    fn extract_tweaks(
        &self,
        psbt_input: &PsbtInput,
        proprietary_keys: &WalletProprietaryKeys,
    ) -> Result<(Scalar, Scalar, Scalar), ChaincodeDelegateSignerError> {
        let parse_tweak = |bytes: &[u8],
                           name: &str|
         -> Result<Scalar, ChaincodeDelegateSignerError> {
            let array: [u8; 32] = bytes.try_into().map_err(|_| {
                ChaincodeDelegateSignerError::InvalidTweak(format!("{} must be 32 bytes", name))
            })?;
            Scalar::from_be_bytes(array).map_err(|_| {
                ChaincodeDelegateSignerError::InvalidTweak(format!("Invalid {} scalar value", name))
            })
        };

        let app_tweak_bytes = psbt_input
            .proprietary
            .get(&proprietary_keys.app_key)
            .ok_or_else(|| {
                ChaincodeDelegateSignerError::InvalidPsbt("App tweak not found in PSBT".to_string())
            })?;
        let hw_tweak_bytes = psbt_input
            .proprietary
            .get(&proprietary_keys.hw_key)
            .ok_or_else(|| {
                ChaincodeDelegateSignerError::InvalidPsbt("HW tweak not found in PSBT".to_string())
            })?;
        let custodian_tweak_bytes = psbt_input
            .proprietary
            .get(&proprietary_keys.custodian_key)
            .ok_or_else(|| {
                ChaincodeDelegateSignerError::InvalidPsbt(
                    "Custodian tweak not found in PSBT".to_string(),
                )
            })?;

        let app_tweak = parse_tweak(app_tweak_bytes, "App tweak")?;
        let hw_tweak = parse_tweak(hw_tweak_bytes, "HW tweak")?;
        let custodian_tweak = parse_tweak(custodian_tweak_bytes, "Custodian tweak")?;

        Ok((app_tweak, hw_tweak, custodian_tweak))
    }

    fn verify_witness_script(
        &self,
        psbt_input: &PsbtInput,
        tweaked_app_public_key: &PublicKey,
        tweaked_hw_public_key: &PublicKey,
        tweaked_custodian_public_key: &PublicKey,
    ) -> Result<(), ChaincodeDelegateSignerError> {
        let psbt_input_witness_script = psbt_input.witness_script.as_ref().ok_or_else(|| {
            ChaincodeDelegateSignerError::SighashError("Witness script missing".to_string())
        })?;

        // Create expected descriptor from the tweaked keys
        let derived_output_descriptor = Descriptor::<PublicKey>::from_str(
            format!(
                "wsh(sortedmulti(2,{},{},{}))",
                tweaked_app_public_key, tweaked_custodian_public_key, tweaked_hw_public_key
            )
            .as_str(),
        )
        .map_err(|e| {
            ChaincodeDelegateSignerError::InvalidPsbt(format!(
                "Unable to create output descriptor: {}",
                e
            ))
        })?;

        // Verify witness script matches expected
        let output_witness = derived_output_descriptor.script_code().map_err(|e| {
            ChaincodeDelegateSignerError::InvalidPsbt(format!("Unrecognized witness script: {}", e))
        })?;
        if psbt_input_witness_script != &output_witness {
            return Err(ChaincodeDelegateSignerError::InvalidWitness(
                "Witness script mismatch".to_string(),
            ));
        }

        // Verify witness UTXO script pubkey matches the descriptor
        let witness_utxo = psbt_input.witness_utxo.as_ref().ok_or_else(|| {
            ChaincodeDelegateSignerError::InvalidPsbt("Witness UTXO missing".to_string())
        })?;
        let output_witness_hash = derived_output_descriptor.script_pubkey();
        if witness_utxo.script_pubkey != output_witness_hash {
            return Err(ChaincodeDelegateSignerError::InvalidWitness(
                "Witness UTXO script pubkey mismatch".to_string(),
            ));
        }

        Ok(())
    }

    /// Compute the sighash message for a PSBT input
    fn compute_sighash(
        &self,
        sighash_cache: &mut SighashCache<&Transaction>,
        input_index: usize,
        psbt_input: &PsbtInput,
    ) -> Result<Message, ChaincodeDelegateSignerError> {
        let sighash_type = psbt_input
            .sighash_type
            .and_then(|sht| sht.ecdsa_hash_ty().ok())
            .unwrap_or(EcdsaSighashType::All);

        let witness_script = psbt_input.witness_script.as_ref().ok_or_else(|| {
            ChaincodeDelegateSignerError::InvalidPsbt(
                "Witness script missing for sighash".to_string(),
            )
        })?;

        let witness_utxo = psbt_input.witness_utxo.as_ref().ok_or_else(|| {
            ChaincodeDelegateSignerError::InvalidPsbt(
                "Witness UTXO missing for sighash".to_string(),
            )
        })?;

        let sighash = sighash_cache
            .segwit_signature_hash(
                input_index,
                witness_script,
                witness_utxo.value,
                sighash_type,
            )
            .map_err(|e| ChaincodeDelegateSignerError::SighashError(e.to_string()))?;

        Message::from_slice(&sighash[..]).map_err(|e| {
            ChaincodeDelegateSignerError::SighashError(format!("Invalid sighash message: {}", e))
        })
    }
}

#[derive(Debug)]
pub struct WalletProprietaryKeys {
    custodian_key: ProprietaryKey,
    app_key: ProprietaryKey,
    hw_key: ProprietaryKey,
}

#[cfg(test)]
mod tests {
    use super::*;
    use bdk::bitcoin::{
        psbt::{Input as PsbtInput, Psbt},
        secp256k1::{rand, All, Message, PublicKey, Scalar, Secp256k1, SecretKey},
        sighash::{EcdsaSighashType, SighashCache},
        OutPoint, ScriptBuf, Transaction, TxIn, TxOut, Witness,
    };
    use bdk::miniscript::Descriptor;
    use std::{collections::BTreeMap, str::FromStr};

    struct TestSetup {
        secp: Secp256k1<All>,
        custodian_key: SecretKey,
        app_secret_key: SecretKey,
        app_public_key: PublicKey,
        hw_public_key: PublicKey,
        signer: ChaincodeDelegateSigner,
    }

    impl TestSetup {
        fn new() -> Self {
            let secp = Secp256k1::new();
            let mut rng = rand::thread_rng();

            let custodian_key = SecretKey::new(&mut rng);
            let (app_secret_key, app_public_key) = secp.generate_keypair(&mut rng);
            let (_hw_secret_key, hw_public_key) = secp.generate_keypair(&mut rng);

            let signer = ChaincodeDelegateSigner::new(custodian_key, app_public_key, hw_public_key);

            Self {
                secp,
                custodian_key,
                app_secret_key,
                app_public_key,
                hw_public_key,
                signer,
            }
        }

        fn create_valid_psbt(&self) -> (Psbt, Scalar, Scalar, Scalar, SecretKey) {
            // Generate valid tweaks
            let app_tweak = Scalar::random();
            let hw_tweak = Scalar::random();
            let custodian_tweak = Scalar::random();

            // Create tweaked keys using the signer's keys
            let tweaked_app_key = self
                .app_public_key
                .add_exp_tweak(&self.secp, &app_tweak)
                .unwrap();
            let tweaked_hw_key = self
                .hw_public_key
                .add_exp_tweak(&self.secp, &hw_tweak)
                .unwrap();
            let tweaked_custodian_key = self
                .custodian_key
                .add_tweak(&custodian_tweak)
                .unwrap()
                .public_key(&self.secp);

            // Use the actual app secret key from the test setup
            let tweaked_app_secret_key = self.app_secret_key.add_tweak(&app_tweak).unwrap();

            // Create descriptor and witness script using the actual signer's app key
            let descriptor = Descriptor::<PublicKey>::from_str(&format!(
                "wsh(sortedmulti(2,{},{},{}))",
                tweaked_app_key, tweaked_custodian_key, tweaked_hw_key
            ))
            .unwrap();

            let witness_script = descriptor.script_code().unwrap();
            let script_pubkey = descriptor.script_pubkey();

            // Create a dummy transaction
            let tx = Transaction {
                version: 2,
                lock_time: bdk::bitcoin::locktime::absolute::LockTime::ZERO,
                input: vec![TxIn {
                    previous_output: OutPoint::null(),
                    script_sig: ScriptBuf::new(),
                    sequence: bdk::bitcoin::Sequence::ENABLE_RBF_NO_LOCKTIME,
                    witness: Witness::new(),
                }],
                output: vec![TxOut {
                    value: 100000,
                    script_pubkey: ScriptBuf::new(),
                }],
            };

            let witness_utxo = TxOut {
                value: 200000,
                script_pubkey,
            };

            // Create PSBT input with all required fields
            let mut psbt_input = PsbtInput {
                witness_utxo: Some(witness_utxo),
                witness_script: Some(witness_script),
                bip32_derivation: {
                    let mut derivation = BTreeMap::new();
                    derivation.insert(
                        tweaked_app_key,
                        (
                            bdk::bitcoin::bip32::Fingerprint::default(),
                            bdk::bitcoin::bip32::DerivationPath::master(),
                        ),
                    );
                    derivation
                },
                ..Default::default()
            };

            // Add proprietary tweaks
            let proprietary_keys = self.signer.proprietary_keys(&self.secp);
            psbt_input
                .proprietary
                .insert(proprietary_keys.app_key, app_tweak.to_be_bytes().to_vec());
            psbt_input
                .proprietary
                .insert(proprietary_keys.hw_key, hw_tweak.to_be_bytes().to_vec());
            psbt_input.proprietary.insert(
                proprietary_keys.custodian_key,
                custodian_tweak.to_be_bytes().to_vec(),
            );

            // Add app signature
            let mut sighash_cache = SighashCache::new(&tx);
            let sighash = sighash_cache
                .segwit_signature_hash(
                    0,
                    psbt_input.witness_script.as_ref().unwrap(),
                    psbt_input.witness_utxo.as_ref().unwrap().value,
                    EcdsaSighashType::All,
                )
                .unwrap();

            let sighash_msg = Message::from_slice(&sighash[..]).unwrap();
            let app_sig = self.secp.sign_ecdsa(&sighash_msg, &tweaked_app_secret_key);

            psbt_input.partial_sigs.insert(
                bdk::bitcoin::PublicKey::new(tweaked_app_key),
                ecdsa::Signature {
                    sig: app_sig,
                    hash_ty: EcdsaSighashType::All,
                },
            );

            let psbt = Psbt {
                unsigned_tx: tx,
                version: 0,
                xpub: BTreeMap::new(),
                proprietary: BTreeMap::new(),
                unknown: BTreeMap::new(),
                inputs: vec![psbt_input],
                outputs: vec![Default::default()],
            };

            (
                psbt,
                app_tweak,
                hw_tweak,
                custodian_tweak,
                tweaked_app_secret_key,
            )
        }
    }

    mod tweak_validation_tests {
        use super::*;

        #[test]
        fn test_missing_app_tweak() {
            let setup = TestSetup::new();
            let (mut psbt, _, _hw_tweak, _custodian_tweak, _) = setup.create_valid_psbt();

            // Remove app tweak
            let proprietary_keys = setup.signer.proprietary_keys(&setup.secp);
            psbt.inputs[0].proprietary.remove(&proprietary_keys.app_key);

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidPsbt(msg)) if msg.contains("App tweak not found"))
            );
        }

        #[test]
        fn test_missing_hw_tweak() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            let proprietary_keys = setup.signer.proprietary_keys(&setup.secp);
            psbt.inputs[0].proprietary.remove(&proprietary_keys.hw_key);

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidPsbt(msg)) if msg.contains("HW tweak not found"))
            );
        }

        #[test]
        fn test_missing_custodian_tweak() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            let proprietary_keys = setup.signer.proprietary_keys(&setup.secp);
            psbt.inputs[0]
                .proprietary
                .remove(&proprietary_keys.custodian_key);

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidPsbt(msg)) if msg.contains("Custodian tweak not found"))
            );
        }

        #[test]
        fn test_wrong_tweak_size() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            let proprietary_keys = setup.signer.proprietary_keys(&setup.secp);
            psbt.inputs[0]
                .proprietary
                .insert(proprietary_keys.app_key, vec![0u8; 22]);

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidTweak(msg)) if msg.contains("App tweak must be 32 bytes"))
            );
        }

        #[test]
        fn test_invalid_scalar_tweak() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            let proprietary_keys = setup.signer.proprietary_keys(&setup.secp);
            psbt.inputs[0]
                .proprietary
                .insert(proprietary_keys.app_key, vec![0xFFu8; 32]);

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidTweak(msg)) if msg.contains("Invalid App tweak scalar value"))
            );
        }

        #[test]
        fn test_swapped_tweaks() {
            let setup = TestSetup::new();
            let (mut psbt, app_tweak, hw_tweak, _, _) = setup.create_valid_psbt();

            let proprietary_keys = setup.signer.proprietary_keys(&setup.secp);
            psbt.inputs[0]
                .proprietary
                .insert(proprietary_keys.app_key, hw_tweak.to_be_bytes().to_vec());
            psbt.inputs[0]
                .proprietary
                .insert(proprietary_keys.hw_key, app_tweak.to_be_bytes().to_vec());

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(result.is_err());
        }
    }

    mod witness_script_validation_tests {
        use super::*;
        use rand::seq::SliceRandom;

        #[test]
        fn test_missing_witness_script() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            psbt.inputs[0].witness_script = None;

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::SighashError(msg)) if msg.contains("Witness script missing"))
            );
        }

        #[test]
        fn test_wrong_multisig_threshold() {
            let setup = TestSetup::new();
            let (mut psbt, app_tweak, hw_tweak, custodian_tweak, _) = setup.create_valid_psbt();

            let tweaked_app_key = setup
                .app_public_key
                .add_exp_tweak(&setup.secp, &app_tweak)
                .unwrap();
            let tweaked_hw_key = setup
                .hw_public_key
                .add_exp_tweak(&setup.secp, &hw_tweak)
                .unwrap();
            let tweaked_custodian_key = setup
                .custodian_key
                .add_tweak(&custodian_tweak)
                .unwrap()
                .public_key(&setup.secp);

            let wrong_descriptor = Descriptor::<PublicKey>::from_str(&format!(
                "wsh(sortedmulti(1,{},{},{}))",
                tweaked_app_key, tweaked_custodian_key, tweaked_hw_key
            ))
            .unwrap();

            psbt.inputs[0].witness_script = Some(wrong_descriptor.script_code().unwrap());

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(matches!(
                result,
                Err(ChaincodeDelegateSignerError::InvalidWitness(_))
            ));
        }

        #[test]
        fn test_extra_key_in_multisig() {
            let setup = TestSetup::new();
            let (mut psbt, app_tweak, hw_tweak, custodian_tweak, _) = setup.create_valid_psbt();

            let mut rng = rand::thread_rng();
            let (_, attacker_key) = setup.secp.generate_keypair(&mut rng);

            let tweaked_app_key = setup
                .app_public_key
                .add_exp_tweak(&setup.secp, &app_tweak)
                .unwrap();
            let tweaked_hw_key = setup
                .hw_public_key
                .add_exp_tweak(&setup.secp, &hw_tweak)
                .unwrap();
            let tweaked_custodian_key = setup
                .custodian_key
                .add_tweak(&custodian_tweak)
                .unwrap()
                .public_key(&setup.secp);

            let wrong_descriptor = Descriptor::<PublicKey>::from_str(&format!(
                "wsh(sortedmulti(2,{},{},{},{}))",
                tweaked_app_key, tweaked_custodian_key, tweaked_hw_key, attacker_key
            ))
            .unwrap();

            psbt.inputs[0].witness_script = Some(wrong_descriptor.script_code().unwrap());

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(matches!(
                result,
                Err(ChaincodeDelegateSignerError::InvalidWitness(_))
            ));
        }

        #[test]
        fn test_unsorted_multisig() {
            let setup = TestSetup::new();
            let (mut psbt, app_tweak, hw_tweak, custodian_tweak, _) = setup.create_valid_psbt();

            let tweaked_app_key = setup
                .app_public_key
                .add_exp_tweak(&setup.secp, &app_tweak)
                .unwrap();
            let tweaked_hw_key = setup
                .hw_public_key
                .add_exp_tweak(&setup.secp, &hw_tweak)
                .unwrap();
            let tweaked_custodian_key = setup
                .custodian_key
                .add_tweak(&custodian_tweak)
                .unwrap()
                .public_key(&setup.secp);

            let mut keys = [tweaked_app_key, tweaked_custodian_key, tweaked_hw_key];
            let mut rng = rand::thread_rng();
            // Keep shuffling until the keys are not sorted
            loop {
                keys.shuffle(&mut rng);
                if !(keys[0] < keys[1] && keys[1] < keys[2]) {
                    break;
                }
            }

            // Create regular multisig instead of sortedmulti with unsorted keys
            let wrong_descriptor = Descriptor::<PublicKey>::from_str(&format!(
                "wsh(multi(2,{},{},{}))",
                keys[0], keys[1], keys[2]
            ))
            .unwrap();

            psbt.inputs[0].witness_script = Some(wrong_descriptor.script_code().unwrap());

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(matches!(
                result,
                Err(ChaincodeDelegateSignerError::InvalidWitness(_))
            ));
        }

        #[test]
        fn test_witness_utxo_script_mismatch() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            // Change witness UTXO script pubkey to something else
            let mut rng = rand::thread_rng();
            let (_, random_key) = setup.secp.generate_keypair(&mut rng);
            let wrong_descriptor = Descriptor::<PublicKey>::from_str(&format!(
                "wsh(sortedmulti(2,{},{},{}))",
                random_key, random_key, random_key
            ))
            .unwrap();

            psbt.inputs[0].witness_utxo.as_mut().unwrap().script_pubkey =
                wrong_descriptor.script_pubkey();

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidWitness(msg)) if msg.contains("script pubkey mismatch"))
            );
        }

        #[test]
        fn test_missing_witness_utxo() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            psbt.inputs[0].witness_utxo = None;

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidPsbt(msg)) if msg.contains("Witness UTXO missing"))
            );
        }
    }

    mod signature_validation_tests {
        use bdk::bitcoin::PublicKey;
        use rand::SeedableRng;

        use super::*;

        #[test]
        fn test_attacker_signature() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            // Insert random signature
            psbt.inputs[0].partial_sigs.clear();
            // Generate random keypair with fixed seed
            let (seckey, pubkey) = setup
                .secp
                .generate_keypair(&mut rand::rngs::StdRng::from_seed([0; 32]));
            psbt.inputs[0].partial_sigs.insert(
                PublicKey::new(pubkey),
                ecdsa::Signature {
                    sig: setup
                        .secp
                        .sign_ecdsa(&Message::from_slice(&[0; 32]).unwrap(), &seckey),
                    hash_ty: EcdsaSighashType::All,
                },
            );

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidPsbt(msg)) if msg.contains("Failed to finalize PSBT."))
            );
        }

        #[test]
        fn test_invalid_signature() {
            let setup = TestSetup::new();
            let (mut psbt, app_tweak, _, _, _) = setup.create_valid_psbt();

            let tweaked_app_key = setup
                .app_public_key
                .add_exp_tweak(&setup.secp, &app_tweak)
                .unwrap();

            // Create invalid signature
            let mut rng = rand::thread_rng();
            let fake_msg = Message::from_slice(&[1u8; 32]).unwrap();
            let (fake_key, _) = setup.secp.generate_keypair(&mut rng);
            let fake_sig = setup.secp.sign_ecdsa(&fake_msg, &fake_key);

            psbt.inputs[0].partial_sigs.insert(
                bdk::bitcoin::PublicKey::new(tweaked_app_key),
                ecdsa::Signature {
                    sig: fake_sig,
                    hash_ty: EcdsaSighashType::All,
                },
            );

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidPsbt(msg)) if msg.contains("Failed to finalize PSBT."))
            );
        }

        #[test]
        fn test_missing_bip32_derivation() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, _, _) = setup.create_valid_psbt();

            psbt.inputs[0].bip32_derivation.clear();

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                matches!(result, Err(ChaincodeDelegateSignerError::InvalidPsbt(msg)) if msg.contains("Missing BIP32 derivation for app key"))
            );
        }
    }

    mod signing_tests {
        use super::*;

        #[test]
        fn test_valid_psbt_signing() {
            let setup = TestSetup::new();
            let (mut psbt, _, _, custodian_tweak, _) = setup.create_valid_psbt();

            let result = setup.signer.sign_psbt(&mut psbt, &setup.secp);
            assert!(
                result.is_ok(),
                "PSBT signing should succeed but got: {:?}",
                result
            );
        }
    }
}
