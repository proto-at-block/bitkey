package build.wallet.availability

import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.OutOfDate
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Unavailable
import kotlinx.datetime.Instant

/**
 * The cause of loss of functionality in the app
 * (which caused it to go from full -> limited functionality)
 */
sealed interface LimitedFunctionalityCause {
  /**
   * The state of core features in the app based on the cause of the loss in app functionality.
   */
  val featureStates: FunctionalityFeatureStates
    get() =
      when (this) {
        is F8eUnreachable ->
          FunctionalityFeatureStates(
            send = Available,
            receive = Available,
            deposit = Unavailable,
            customElectrumServer = Available,
            mobilePay = Unavailable,
            securityAndRecovery = Unavailable,
            fiatExchangeRates = OutOfDate(lastUpdated = lastReachableTime),
            notifications = Unavailable,
            helpCenter = Unavailable
          )

        is InternetUnreachable ->
          FunctionalityFeatureStates(
            send = Unavailable,
            receive = Available,
            deposit = Unavailable,
            customElectrumServer = Unavailable,
            mobilePay = Unavailable,
            securityAndRecovery = Unavailable,
            fiatExchangeRates = OutOfDate(lastUpdated = lastReachableTime),
            notifications = Unavailable,
            helpCenter = Unavailable
          )

        EmergencyAccessMode ->
          FunctionalityFeatureStates(
            send = Available,
            receive = Available,
            deposit = Unavailable,
            customElectrumServer = Available,
            mobilePay = Unavailable,
            securityAndRecovery = Unavailable,
            fiatExchangeRates = Unavailable,
            notifications = Unavailable,
            helpCenter = Unavailable
          )

        InactiveApp ->
          FunctionalityFeatureStates(
            send = Available,
            receive = Available,
            deposit = Unavailable,
            customElectrumServer = Available,
            mobilePay = Unavailable,
            securityAndRecovery = Unavailable,
            fiatExchangeRates = Unavailable,
            notifications = Unavailable,
            helpCenter = Unavailable
          )
      }
}

/**
 * A [LimitedFunctionalityCause] that related to having issues with connectivity to remote services.
 */
sealed interface ConnectivityCause : LimitedFunctionalityCause

/**
 * There is limited functionality in the app because F8e is specifically unreachable.
 * This means there is a general internet connection available to the device, but there
 * is something wrong only with the connection to F8e.
 *
 * @property lastReachableTime - The last time the f8e connection reported REACHABLE.
 */
data class F8eUnreachable(val lastReachableTime: Instant?) : ConnectivityCause

/**
 * Indicates that the app is operating without connectivity to F8e.
 *
 * This mode is used for the Emergency Access Kit, where the app may have
 * an internet connection, but no valid tokens to use with F8e.
 */
data object EmergencyAccessMode : ConnectivityCause

/**
 * There is limited functionality in the app because there is no internet connection.
 *
 * @property lastReachableTime - The last time any connection reported REACHABLE.
 * @property lastElectrumSyncReachableTime - The last time the Electrum Sync connection reported REACHABLE.
 */
data class InternetUnreachable(
  val lastReachableTime: Instant?,
  val lastElectrumSyncReachableTime: Instant?,
) : ConnectivityCause

/**
 * There is limited functionality in the app because the App auth key is no longer valid and
 * the app can no longer authenticate with the server.
 */
data object InactiveApp : LimitedFunctionalityCause
