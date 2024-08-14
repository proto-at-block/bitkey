package build.wallet.debug

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.account.SoftwareAccountConfig
import build.wallet.f8e.F8eEnvironment
import kotlin.time.Duration

/**
 * Provides various debug options that can be used to configure the app through the debug menu
 * for testing and debugging purposes.
 *
 * @param skipCloudBackupOnboarding indicates if cloud backup onboarding step should be skipped.
 * @param skipNotificationsOnboarding indicates if notifications onboarding step should be skipped.
 */
data class DebugOptions(
  val bitcoinNetworkType: BitcoinNetworkType,
  val f8eEnvironment: F8eEnvironment,
  val isTestAccount: Boolean,
  val isUsingSocRecFakes: Boolean,
  val isHardwareFake: Boolean,
  val delayNotifyDuration: Duration? = null,
  val skipCloudBackupOnboarding: Boolean = false,
  val skipNotificationsOnboarding: Boolean = false,
) {
  /**
   * Returns [FullAccountConfig] for given [DebugOptions].
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
   * Returns [LiteAccountConfig] for given [DebugOptions].
   */
  fun toLiteAccountConfig() =
    LiteAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes
    )

  /**
   * Returns [SoftwareAccountConfig] for given [DebugOptions].
   */
  fun toSoftwareAccountConfig() =
    SoftwareAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes
    )
}
