use bdk_utils::{bdk::bitcoin::secp256k1::PublicKey, signature::check_signature};
use std::fmt;

pub(crate) enum SignatureType {
    DelayNotify,
    LockInheritance,
}

impl fmt::Display for SignatureType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SignatureType::DelayNotify => write!(f, "CompleteDelayNotify"),
            SignatureType::LockInheritance => write!(f, "LockInheritanceClaim"),
        }
    }
}

#[derive(Debug)]
pub struct SignatureValidationError;

pub(crate) fn validate_signatures(
    signature_type: &SignatureType,
    app: PublicKey,
    hw: PublicKey,
    recovery: Option<PublicKey>,
    challenge: &str,
    app_sig: &str,
    hw_sig: Option<&str>,
) -> Result<(), SignatureValidationError> {
    let expected_challenge = format!(
        "{}{}{}{}",
        signature_type,
        hw,
        app,
        recovery.map_or(String::new(), |f| f.to_string())
    );

    let has_valid_signatures = match signature_type {
        SignatureType::DelayNotify => {
            let hw_sig = hw_sig.ok_or(SignatureValidationError)?;
            check_signature(challenge, app_sig, app).is_ok()
                && check_signature(challenge, hw_sig, hw).is_ok()
        }
        SignatureType::LockInheritance => check_signature(challenge, app_sig, app).is_ok(),
    };

    if expected_challenge == challenge && has_valid_signatures {
        Ok(())
    } else {
        Err(SignatureValidationError)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bdk_utils::bdk::bitcoin::key::Secp256k1;
    use bdk_utils::signature::sign_message;
    use rstest::rstest;

    #[rstest]
    #[case(SignatureType::DelayNotify)]
    #[case(SignatureType::LockInheritance)]
    fn test_validate_signatures_success(#[case] signature_type: SignatureType) {
        // arrange
        let secp = Secp256k1::new();

        let (app_secret_key, app_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (hw_secret_key, hw_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (_, recovery_public_key) = secp.generate_keypair(&mut rand::thread_rng());

        let app = app_public_key;
        let hw = hw_public_key;
        let recovery = Some(recovery_public_key);

        let challenge = signature_type.to_string()
            + &hw.to_string()
            + &app.to_string()
            + &recovery.as_ref().unwrap().to_string();

        let app_sig = sign_message(&secp, &challenge, &app_secret_key);
        let hw_sig = match signature_type {
            SignatureType::DelayNotify => Some(sign_message(&secp, &challenge, &hw_secret_key)),
            SignatureType::LockInheritance => None,
        };

        // act
        let result = validate_signatures(
            &signature_type,
            app,
            hw,
            recovery,
            &challenge,
            &app_sig,
            hw_sig.as_deref(),
        );

        // assert
        assert!(result.is_ok());
    }

    #[test]
    fn test_validate_signatures_invalid_challenge() {
        // arrange
        let secp = Secp256k1::new();

        let (app_secret_key, app_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (hw_secret_key, hw_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (_, recovery_public_key) = secp.generate_keypair(&mut rand::thread_rng());

        let signature_type = &SignatureType::DelayNotify;
        let app = app_public_key;
        let hw = hw_public_key;
        let recovery = Some(recovery_public_key);

        let expected_challenge = signature_type.to_string()
            + &hw.to_string()
            + &app.to_string()
            + &recovery.as_ref().unwrap().to_string();

        let challenge = "invalid_challenge".to_string();

        let app_sig = sign_message(&secp, &expected_challenge, &app_secret_key);
        let hw_sig = sign_message(&secp, &expected_challenge, &hw_secret_key);

        // act
        let result = validate_signatures(
            signature_type,
            app,
            hw,
            recovery,
            &challenge,
            &app_sig,
            Some(&hw_sig),
        );

        // assert
        assert!(result.is_err());
    }

    #[test]
    fn test_validate_signatures_invalid_app_signature() {
        // arrange
        let secp = Secp256k1::new();
        let (_, app_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (hw_secret_key, hw_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (_, recovery_public_key) = secp.generate_keypair(&mut rand::thread_rng());

        let signature_type = &SignatureType::DelayNotify;
        let app = app_public_key;
        let hw = hw_public_key;
        let recovery = Some(recovery_public_key);

        let expected_challenge = signature_type.to_string()
            + &hw.to_string()
            + &app.to_string()
            + &recovery.as_ref().unwrap().to_string();

        let challenge = expected_challenge.clone();

        let app_sig = sign_message(&secp, &challenge, &hw_secret_key); // Wrong key
        let hw_sig = sign_message(&secp, &challenge, &hw_secret_key);

        // act
        let result = validate_signatures(
            signature_type,
            app,
            hw,
            recovery,
            &challenge,
            &app_sig,
            Some(&hw_sig),
        );

        // assert
        assert!(result.is_err());
    }

    #[test]
    fn test_validate_signatures_invalid_hw_signature() {
        // arrange
        let secp = Secp256k1::new();

        // Generate key pairs for app, hw, and recovery
        let (app_secret_key, app_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (_, hw_public_key) = secp.generate_keypair(&mut rand::thread_rng());
        let (_, recovery_public_key) = secp.generate_keypair(&mut rand::thread_rng());

        let signature_type = &SignatureType::DelayNotify;
        let app = app_public_key;
        let hw = hw_public_key;
        let recovery = Some(recovery_public_key);

        let expected_challenge = signature_type.to_string()
            + &hw.to_string()
            + &app.to_string()
            + &recovery.as_ref().unwrap().to_string();

        let challenge = expected_challenge.clone();

        let app_sig = sign_message(&secp, &challenge, &app_secret_key);
        let hw_sig = sign_message(&secp, &challenge, &app_secret_key); // Wrong key

        // act
        let result = validate_signatures(
            signature_type,
            app,
            hw,
            recovery,
            &challenge,
            &app_sig,
            Some(&hw_sig),
        );

        // assert
        assert!(result.is_err());
    }
}
