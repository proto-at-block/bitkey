package build.wallet.analytics.events.screen.id

enum class AuthEventTrackerScreenId : EventTrackerScreenId {
  /** Loading screen shown when refreshing auth tokens */
  REFRESHING_AUTH_TOKENS_FOR_HW_POP,

  /** Error screen shown when refreshing auth tokens fails */
  AUTH_TOKENS_REFRESH_FOR_HW_POP_ERROR,

  /** Loading screen shown while building and app-signing the action proof payload */
  ACTION_PROOF_BUILDING_PAYLOAD,

  /** Error screen shown when the action proof flow fails */
  ACTION_PROOF_ERROR,
}
