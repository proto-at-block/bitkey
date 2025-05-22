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

  /** NFC flow to get the spending key during app recovery */
  APP_DELAY_NOTIFY_GET_INITIAL_SPENDING_KEY,

  /** NFC flow to unseal the cloud sealed encryption key (CSEK) */
  UNSEAL_CLOUD_BACKUP,

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

  /** Resetting all enrolled fingerprints. */
  RESET_FINGERPRINTS,

  /** Enrolling an additional fingerprint */
  ENROLLING_NEW_FINGERPRINT,

  /** Checking the enrollment status of an additional fingerprint. */
  CHECKING_FINGERPRINT_ENROLLMENT_STATUS,

  /** NFC flow to wipe a device */
  WIPE_DEVICE,

  /** NFC flow to sign a transaction during the utxo consolidation flow */
  UTXO_CONSOLIDATION_SIGN_TRANSACTION,

  /** NFC flow to encrypt the delegated decryption key when accepting a beneficiary invite */
  SEAL_DELEGATED_DECRYPTION_KEY,
}
