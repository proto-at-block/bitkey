package build.wallet.availability

import kotlinx.datetime.Instant

/**
 * Grouping of the states of core features in the app, used to relay that information
 * to the customer in the case of limited functionality in the app
 */
data class FunctionalityFeatureStates(
  val send: FeatureState,
  val receive: FeatureState,
  val deposit: FeatureState,
  val customElectrumServer: FeatureState,
  val mobilePay: FeatureState,
  val securityAndRecovery: FeatureState,
  val fiatExchangeRates: FeatureState,
  val notifications: FeatureState,
  val helpCenter: FeatureState,
  val cloudBackupHealth: FeatureState,
) {
  /**
   * The state a feature can be in.
   */
  sealed interface FeatureState {
    /**
     * The feature is fully available, show as enabled.
     */
    data object Available : FeatureState

    /**
     * The feature is unavailable to use, show as disabled.
     */
    data object Unavailable : FeatureState

    /**
     * The feature is out of date, show as disabled.
     */
    data class OutOfDate(val lastUpdated: Instant?) : FeatureState
  }
}
