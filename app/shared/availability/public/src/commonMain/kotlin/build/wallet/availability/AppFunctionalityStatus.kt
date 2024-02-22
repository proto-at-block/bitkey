package build.wallet.availability

import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available

/**
 * The status of the functionality of the app, used to determine when to show an informative
 * banner and show certain features of the app in a disabled state in order to proactively
 * show the customer what is available to use (instead of them trying and it failing).
 */
sealed interface AppFunctionalityStatus {
  /**
   * The app is fully working as expected, without any limitations.
   * No banner or disabled states should be shown.
   */
  data object FullFunctionality : AppFunctionalityStatus

  /**
   * The app has limited functionality, due to the given [LimitedFunctionalityCause].
   * This cause helps determine the specific UI / copy that is appropriate to show
   * to the customer.
   *
   * @property cause - The cause for the loss of functionality in the app
   */
  data class LimitedFunctionality(
    val cause: LimitedFunctionalityCause,
  ) : AppFunctionalityStatus

  val featureStates: FunctionalityFeatureStates
    get() =
      when (this) {
        is FullFunctionality ->
          FunctionalityFeatureStates(
            send = Available,
            receive = Available,
            deposit = Available,
            customElectrumServer = Available,
            mobilePay = Available,
            securityAndRecovery = Available,
            fiatExchangeRates = Available,
            notifications = Available,
            helpCenter = Available
          )

        is LimitedFunctionality -> cause.featureStates
      }
}
