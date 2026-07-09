package build.wallet.analytics.events.screen.id

/**
 * Screen IDs for the W3 two-tap hardware confirmation flow.
 *
 * Each [HardwareConfirmationContent] variant provides its own screen ID so analytics
 * can distinguish which flow the user was in (send, FWUP, W3 upgrade, recovery, etc.).
 */
enum class HardwareConfirmationEventTrackerScreenId : EventTrackerScreenId {
  /** Generic fallback — should not appear in practice if all content types set their own ID */
  HW_CONFIRMATION,
  HW_CONFIRMATION_CANCELED,

  /** Transaction signing (generic, e.g. sign-only without send) */
  HW_CONFIRMATION_SIGN_TRANSACTION,
  HW_CONFIRMATION_CANCELED_SIGN_TRANSACTION,

  /** UTXO consolidation */
  HW_CONFIRMATION_CONSOLIDATE_UTXOS,
  HW_CONFIRMATION_CANCELED_CONSOLIDATE_UTXOS,

  /** Firmware update */
  HW_CONFIRMATION_FWUP,
  HW_CONFIRMATION_CANCELED_FWUP,

  /** Action proof signing (W3 upgrade, auth rotation) */
  HW_CONFIRMATION_SIGN_ACTION_PROOF,
  HW_CONFIRMATION_CANCELED_SIGN_ACTION_PROOF,

  /** Lost app recovery */
  HW_CONFIRMATION_RECOVERY,
  HW_CONFIRMATION_CANCELED_RECOVERY,

  /** Device wipe */
  HW_CONFIRMATION_WIPE_DEVICE,
  HW_CONFIRMATION_CANCELED_WIPE_DEVICE,

  /** Lost app recovery sign challenge */
  HW_CONFIRMATION_RECOVERY_SIGN_CHALLENGE,
  HW_CONFIRMATION_CANCELED_RECOVERY_SIGN_CHALLENGE,

  /** Emergency exit kit restoration unseal */
  HW_CONFIRMATION_EEK_RESTORATION,
  HW_CONFIRMATION_CANCELED_EEK_RESTORATION,

  /** Cloud backup restoration */
  HW_CONFIRMATION_CLOUD_BACKUP_RESTORATION,
  HW_CONFIRMATION_CANCELED_CLOUD_BACKUP_RESTORATION,

  /** Stale keyset repair — unseal SSEK */
  HW_CONFIRMATION_KEYSET_REPAIR_UNSEAL,
  HW_CONFIRMATION_CANCELED_KEYSET_REPAIR_UNSEAL,

  /** Stale keyset repair — rotate HW spending key + sign access token composite */
  HW_CONFIRMATION_KEYSET_REPAIR_ROTATE_HW_KEY,
  HW_CONFIRMATION_CANCELED_KEYSET_REPAIR_ROTATE_HW_KEY,
}
