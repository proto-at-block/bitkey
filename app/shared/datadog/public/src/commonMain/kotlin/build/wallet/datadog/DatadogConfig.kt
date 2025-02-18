package build.wallet.datadog

import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Alpha
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team

/**
 * @property environmentName sent with each event. Used to filter events on different environments
 * (for example "development" vs. "customer").
 */
data class DatadogConfig(
  val environmentName: String,
  val firstPartyHosts: List<String>,
  val siteName: String,
) {
  companion object {
    fun create(appVariant: AppVariant): DatadogConfig =
      DatadogConfig(
        environmentName =
          when (appVariant) {
            Development -> "development"
            Alpha -> "alpha"
            Team -> "team"
            Beta -> "beta"
            Customer -> "customer"
            Emergency -> "emergency"
          },
        firstPartyHosts =
          listOf(
            "api.bitkey.world",
            "api.bitkeystaging.com",
            "api.dev.wallet.build",
            "bitkey.build",
            "bitkey.world",
            "wallet.build"
          ),
        siteName = "US1"
      )
  }
}
