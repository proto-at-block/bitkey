package build.wallet.analytics.events.screen.context

import build.wallet.analytics.events.EventTrackerContext

/**
 * Context for NFC related screens in the app.
 */
enum class NfcEventTrackerScreenIdContext : EventTrackerContext {
  /** NFC flow to start fingerprint enrollment */
  PAIR_NEW_HW_FINGERPRINT,

  /** NFC flow to confirm fingerprint enrollment and complete HW pairing */
  PAIR_NEW_HW_ACTIVATION,

  /** NFC flow to sign and rotate keys during app recovery */
  APP_DELAY_NOTIFY_SIGN_ROTATE_KEYS,

  /** NFC flow to provision app auth key to hardware after auth key rotation */
  APP_DELAY_NOTIFY_PROVISION_APP_AUTH_KEY,

  /** NFC flow to seal ddk during lost hardware recovery */
  APP_DELAY_NOTIFY_SEAL_DDK,

  /** NFC flow to unseal ddk during lost app recovery */
  APP_DELAY_NOTIFY_UNSEAL_DDK,

  /** NFC flow to get hardware keys during app recovery */
  APP_DELAY_NOTIFY_GET_HW_KEYS,

  /** NFC flow to sign auth during app recovery */
  APP_DELAY_NOTIFY_SIGN_AUTH,

  /** NFC flow to sign auth during cloud backup recovery */
  CLOUD_BACKUP_SIGN_AUTH,

  /** NFC flow to provision app auth key during cloud backup recovery */
  CLOUD_BACKUP_PROVISION_APP_AUTH_KEY,

  /** NFC flow to provision app auth key after rotating auth keys */
  ROTATE_AUTH_KEYS_PROVISION_APP_AUTH_KEY,

  /** NFC flow to get the spending key during app recovery */
  APP_DELAY_NOTIFY_GET_INITIAL_SPENDING_KEY,

  /** NFC flow to unseal the cloud sealed encryption key (CSEK) */
  UNSEAL_CLOUD_BACKUP,

  /** NFC flow to unseal the server storage encryption key (SSEK) */
  UNSEAL_SSEK,

  /** NFC flow to seal the server storage encryption key (SSEK) */
  SEAL_SSEK,

  /** NFC flow to unseal the cloud sealed encryption key (CSEK) in Emergency Exit Kit Restore */
  UNSEAL_EMERGENCY_ACCESS_KIT_BACKUP,

  /** NFC flow to sign many transactions during the recovery flow */
  SIGN_MANY_TRANSACTIONS,

  /** NFC flow to sign a transaction during the send flow */
  SIGN_TRANSACTION,

  /** NFC flow for firmware update process */
  FWUP,

  /** NFC flow to verify proof of possession of the HW factor */
  HW_PROOF_OF_POSSESSION,

  /** NFC flow to get firmware metadata */
  METADATA,

  /** Debug menu NFC flows */
  DEBUG,

  /** Retrieving enrolled fingerprints */
  GET_ENROLLED_FINGERPRINTS,

  /** Updating the fingerprint label for an existing fingerprint */
  SAVE_FINGERPRINT_LABEL,

  /** Deleting an enrolled fingerprint. */
  DELETE_FINGERPRINT,

  /** Creating the grant request for resetting fingerprints */
  RESET_FINGERPRINTS_CREATE_GRANT_REQUEST,

  /** Providing the signed grant for resetting fingerprints */
  RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT,

  /** Enrolling an additional fingerprint */
  ENROLLING_NEW_FINGERPRINT,

  /** Checking the enrollment status of an additional fingerprint. */
  CHECKING_FINGERPRINT_ENROLLMENT_STATUS,

  /** NFC flow to wipe a device */
  WIPE_DEVICE,

  /** NFC flow to classify a tapped device before wiping */
  WIPE_DEVICE_CLASSIFY_DEVICE,

  /** NFC flow to sign a transaction during the utxo consolidation flow */
  UTXO_CONSOLIDATION_SIGN_TRANSACTION,

  /** NFC flow to encrypt the delegated decryption key when accepting a beneficiary invite */
  SEAL_DELEGATED_DECRYPTION_KEY,

  /** NFC flow to generate a replacement spending key during keyset repair */
  KEYSET_REPAIR_GENERATE_HW_KEY,

  /** NFC flow to get address from hardware for verification */
  ADDRESS_VERIFICATION,

  /** NFC flow to build hardware descriptor for W3 devices during onboarding */
  VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR,

  /** NFC flow to sign an action proof payload for privileged action verification */
  SIGN_ACTION_PROOF,

  /** NFC flow to deliver hardware wallet descriptor after cloud backup restore */
  DELIVER_HARDWARE_DESCRIPTOR,

  /** NFC flow to verify found hardware during lost hardware recovery */
  HW_DELAY_NOTIFY_VERIFY_FOUND_HARDWARE,

  /** NFC flow for W3 lost app recovery composite (unseal SSEK + action proof + spending key) */
  LOST_APP_RECOVERY,

  /** NFC flow to confirm hardware presence before canceling an inheritance claim */
  CANCEL_INHERITANCE_CLAIM,

  /** NFC flow for recovery proof-and-key-transfer during lost app recovery */
  RECOVERY_PROOF_AND_KEY_TRANSFER_LOST_APP,

  /** NFC flow for recovery proof-and-key-transfer during lost hardware recovery */
  RECOVERY_PROOF_AND_KEY_TRANSFER_LOST_HARDWARE,

  /** NFC flow for W3 sign challenge and seal SEKs (confirmable tap 1) */
  W3_SIGN_CHALLENGE_AND_SEAL_SEKS,

  /** NFC flow for W3 recovery authorize lost app (confirmable tap 2) */
  W3_RECOVERY_AUTHORIZE_LOST_APP,

  /** NFC flow for W3 recovery authorize lost hw (confirmable tap 2) */
  W3_RECOVERY_AUTHORIZE_LOST_HW,
}
