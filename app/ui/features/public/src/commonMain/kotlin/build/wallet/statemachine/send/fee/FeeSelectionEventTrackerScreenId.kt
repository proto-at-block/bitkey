package build.wallet.statemachine.send.fee

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class FeeSelectionEventTrackerScreenId : EventTrackerScreenId {
  /** Error screen shown when user tries to construct a PSBT they do not have the outputs for (including fees.) **/
  FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN,

  /** Error screen shown when user tries to construct a PSBT with outputs below dust limit */
  FEE_ESTIMATION_BELOW_DUST_LIMIT_ERROR_SCREEN,

  /** Generic error screen shown from PSBT construction */
  FEE_ESTIMATION_PSBT_CONSTRUCTION_ERROR_SCREEN,

  /** Error screen shown when we could not load the fee estimates */
  FEE_ESTIMATION_LOAD_FEES_ERROR_SCREEN,

  /** Error screen shown when we could not bump a fee because the fee rate is too low */
  FEE_ESTIMATION_FEE_RATE_TOO_LOW_ERROR_SCREEN,
}
