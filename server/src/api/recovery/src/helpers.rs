use bdk_utils::{bdk::bitcoin::secp256k1::PublicKey, signature::check_signature};

use crate::error::RecoveryError;

pub(crate) fn validate_signatures(
    app: PublicKey,
    hw: PublicKey,
    recovery: Option<PublicKey>,
    challenge: &str,
    app_sig: &str,
    hw_sig: &str,
) -> Result<(), RecoveryError> {
    let expected_challenge = "CompleteDelayNotify".to_string()
        + &hw.to_string()
        + &app.to_string()
        + &recovery.map_or("".to_string(), |f| f.to_string());
    let valid_app_sig = check_signature(challenge, app_sig, app).is_ok();
    let valid_hardware_sig = check_signature(challenge, hw_sig, hw).is_ok();
    let is_valid = expected_challenge == challenge && valid_app_sig && valid_hardware_sig;
    match is_valid {
        true => Ok(()),
        false => Err(RecoveryError::InvalidInputForCompletion),
    }
}
