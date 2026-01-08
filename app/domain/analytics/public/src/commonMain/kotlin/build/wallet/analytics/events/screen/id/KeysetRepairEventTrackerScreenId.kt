package build.wallet.analytics.events.screen.id

enum class KeysetRepairEventTrackerScreenId : EventTrackerScreenId {
  /** Loading screen shown while checking for private keysets */
  KEYSET_REPAIR_CHECKING_KEYSETS,

  /** Explanation screen describing what keyset repair will do */
  KEYSET_REPAIR_EXPLANATION,

  /** Explanation screen shown when key regeneration is required */
  KEYSET_REPAIR_KEY_REGENERATION_EXPLANATION,

  /** Loading screen shown while executing the repair */
  KEYSET_REPAIR_EXECUTING,

  /** Loading screen shown while checking for funds to sweep */
  KEYSET_REPAIR_CHECKING_FOR_SWEEP,

  /** Loading screen shown while generating a missing app key */
  KEYSET_REPAIR_GENERATING_APP_KEY,

  /** Success screen shown when repair completes */
  KEYSET_REPAIR_SUCCESS,

  /** Error screen shown when repair fails */
  KEYSET_REPAIR_FAILED,
}
