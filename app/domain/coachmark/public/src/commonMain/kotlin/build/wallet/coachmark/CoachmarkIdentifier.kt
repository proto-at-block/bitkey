package build.wallet.coachmark

import build.wallet.analytics.v1.Action

/**
 * Coachmark identifiers
 */
enum class CoachmarkIdentifier(
  val value: String,
  val action: Action,
) {
  /** Add new coachmark identifiers here. These string values need to be stable or else coachmarks
   * will reappear for users who have already seen them. Do not change them!
   */
  HiddenBalanceCoachmark("hidden_balance_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_HIDE_BALANCE),
  MultipleFingerprintsCoachmark("multiple_fingerprints_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_MULTIPLE_FINGERPRINTS),
  BiometricUnlockCoachmark("biometric_unlock_coachmark", Action.ACTION_APP_COACHMARK_VIEWIED_BIOMETRIC_UNLOCK),
  BitcoinPriceChartCoachmark("bitcoin_price_chart_coachmark", Action.ACTION_APP_COACHMARK_VIEWIED_BITCOIN_PRICE_CARD),
  InheritanceCoachmark("inheritance_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_INHERITANCE),
}
