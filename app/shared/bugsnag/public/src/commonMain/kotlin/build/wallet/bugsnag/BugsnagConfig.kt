package build.wallet.bugsnag

import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team

/**
 * Common shared Bugsnag configuration that does not rely/expose platform specific Bugsnag SDK APIs.
 *
 * @property releaseStage defines the release stage for Bugsnag based on the app variant. We use
 * this release stage value to distinguish between errors that happen in different stages of the
 * application release process.
 */
data class BugsnagConfig internal constructor(
  val releaseStage: String,
) {
  constructor(appVariant: AppVariant) : this(
    releaseStage = appVariant.releaseStage()
  )
}

private fun AppVariant.releaseStage(): String =
  when (this) {
    Development -> "development"
    Team -> "team"
    Beta -> "beta"
    Customer -> "customer"
    Emergency -> "emergency"
  }
