package bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import kotlin.time.Duration

/**
 * Provides various account configurations that can be customized in non-Customer builds through
 * debug menu, for testing and debugging purposes.
 *
 * @param skipCloudBackupOnboarding indicates if cloud backup onboarding step should be skipped.
 * @param skipNotificationsOnboarding indicates if notifications onboarding step should be skipped.
 */
data class DefaultAccountConfig(
  override val bitcoinNetworkType: BitcoinNetworkType,
  override val f8eEnvironment: F8eEnvironment,
  override val isTestAccount: Boolean,
  override val isUsingSocRecFakes: Boolean,
  val isHardwareFake: Boolean,
  val delayNotifyDuration: Duration? = null,
  val skipCloudBackupOnboarding: Boolean = false,
  val skipNotificationsOnboarding: Boolean = false,
) : AccountConfig {
  /**
   * Returns [FullAccountConfig] for given [DefaultAccountConfig].
   */
  fun toFullAccountConfig() =
    FullAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes,
      isHardwareFake = isHardwareFake,
      delayNotifyDuration = delayNotifyDuration
    )

  /**
   * Returns [LiteAccountConfig] for given [DefaultAccountConfig].
   */
  fun toLiteAccountConfig() =
    LiteAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes
    )

  /**
   * Returns [SoftwareAccountConfig] for given [DefaultAccountConfig].
   */
  fun toSoftwareAccountConfig() =
    SoftwareAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes
    )
}
