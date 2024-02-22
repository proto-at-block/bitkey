package build.wallet.bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.f8e.F8eEnvironment

/**
 * Defines environment configuration for [FullAccount]s.
 *
 * Note that [KeyboxConfig] type is equivalent to [FullAccountConfig]. The [KeyboxConfig] is what
 * we used to have primarily before we had different account types. Using [FullAccountConfig] is
 * preferred. Long term goal is to get rid of [KeyboxConfig] entirely in favor of
 * [FullAccountConfig] (TODO: BKR-490).
 *
 * @property isHardwareFake determines if real or fake hardware should be used for this account.
 */
data class FullAccountConfig(
  override val bitcoinNetworkType: BitcoinNetworkType,
  override val f8eEnvironment: F8eEnvironment,
  override val isTestAccount: Boolean,
  override val isUsingSocRecFakes: Boolean,
  val isHardwareFake: Boolean,
) : AccountConfig {
  companion object {
    /**
     * Convenience function to create [FullAccountConfig] based on provided [KeyboxConfig].
     */
    fun fromKeyboxConfig(config: KeyboxConfig) =
      FullAccountConfig(
        bitcoinNetworkType = config.networkType,
        f8eEnvironment = config.f8eEnvironment,
        isTestAccount = config.isTestAccount,
        isUsingSocRecFakes = config.isUsingSocRecFakes,
        isHardwareFake = config.isHardwareFake
      )
  }
}
