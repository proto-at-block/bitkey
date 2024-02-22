package build.wallet.ktor.result.client

import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team
import io.ktor.client.plugins.logging.LogLevel

class KtorLogLevelPolicyImpl(
  private val appVariant: AppVariant,
) : KtorLogLevelPolicy {
  override fun level(): LogLevel {
    return when (appVariant) {
      Development -> LogLevel.ALL
      Team, Beta, Customer, Emergency -> LogLevel.INFO
    }
  }
}
