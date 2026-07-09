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
 * @param isCloudStoreFake indicates if test-account cloud storage should use local fake storage.
 */
data class DefaultAccountConfig(
  override val bitcoinNetworkType: BitcoinNetworkType,
  override val f8eEnvironment: F8eEnvironment,
  override val isTestAccount: Boolean,
  override val isUsingSocRecFakes: Boolean,
  val isHardwareFake: Boolean,
  val isCloudStoreFake: Boolean = false,
  /**
   * Hardware type for the account.
   * - `null`: Defaults to W1 in [toFullAccountConfig]; actual type is resolved from the
   *   paired device during account creation.
   * - `W1`: Force W1 hardware type
   * - `W3`: Force W3 hardware type
   */
  val hardwareType: HardwareType? = null,
  val delayNotifyDuration: Duration? = null,
  val skipCloudBackupOnboarding: Boolean = false,
  val skipNotificationsOnboarding: Boolean = false,
  val skipBuildHardwareDescriptorOnboarding: Boolean = false,
) : AccountConfig {
  /**
   * Returns [FullAccountConfig] for given [DefaultAccountConfig].
   *
   * Defaults null [hardwareType] to [HardwareType.W1]. During onboarding, the actual hardware
   * type is resolved from the paired device and applied when creating the account.
   */
  fun toFullAccountConfig(): FullAccountConfig {
    return FullAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes,
      isHardwareFake = isHardwareFake,
      hardwareType = hardwareType ?: HardwareType.W1,
      delayNotifyDuration = delayNotifyDuration
    )
  }

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
