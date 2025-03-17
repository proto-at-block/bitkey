package build.wallet.analytics.events.screen.id

enum class UtxoConsolidationEventTrackerScreenId : EventTrackerScreenId {
  /**
   * Error screen for when user opens the utxo consolidation setting but only has one UTXO.
   */
  NOT_ENOUGH_UTXOS_TO_CONSOLIDATE,

  /**
   * Error screen for when user opens the utxo consolidation setting but has zero UTXOs.
   */
  NO_UTXOS_TO_CONSOLIDATE,

  /**
   * Screen shown after the utxo consolidation has been broadcasted.
   */
  UTXO_CONSOLIDATION_TRANSACTION_SENT,

  /**
   * Screen shown when the user first opens the utxo consolidation feature that shows the details
   * of the possible consolidation.
   */
  UTXO_CONSOLIDATION_CONFIRMATION,

  /**
   * Loading screen shown when loading the utxo consolidation details.
   */
  LOADING_UTXO_CONSOLIDATION_DETAILS,

  /**
   * Loading screen shown when broadcasting the utxo consolidation.
   */
  BROADCASTING_UTXO_CONSOLIDATION,

  /**
   * Bottom sheet displaying helper information about the consolidation time.
   */
  UTXO_CONSOLIDATION_TIME_INFO,

  /**
   * Bottom sheet displaying helper information about the consolidation cost.
   */
  UTXO_CONSOLIDATION_COST_INFO,

  /**
   * Screen asking the user to confirm whether they'd like to speed up their UTXO consolidation.
   */
  UTXO_CONSOLIDATION_SPEED_UP_CONFIRMATION,

  /**
   * The UTXO consolidation was successfully sped up.
   */
  UTXO_CONSOLIDATION_SPEED_UP_SENT,

  /**
   * Screen telling the user they have too many UTXOs and will have to consolidate multiple times.
   */
  UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT,

  /**
   * Showing the Tap & Hold to consolidate sheet before hardware interaction, explaining
   * consolidations can take awhile.
   */
  TAP_AND_HOLD_TO_CONSOLIDATE_SHEET,
}
