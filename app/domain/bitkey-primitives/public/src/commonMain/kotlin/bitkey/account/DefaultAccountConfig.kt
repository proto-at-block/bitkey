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
  /**
   * Hardware type for the account.
   * - `null`: Auto-detect from firmware device info during onboarding (defaults to W1 if unavailable)
   * - `W1`: Force W1 hardware type
   * - `W3`: Force W3 hardware type
   */
  val hardwareType: HardwareType? = null,
  val delayNotifyDuration: Duration? = null,
  val skipCloudBackupOnboarding: Boolean = false,
  val skipNotificationsOnboarding: Boolean = false,
) : AccountConfig {
  /**
   * Returns [FullAccountConfig] for given [DefaultAccountConfig].
   *
   * Note: This method defaults null hardwareType to W1. For proper hardware type resolution
   * during onboarding, use [AccountConfigService.resolveHardwareTypeAndCreateFullAccountConfig].
   */
  fun toFullAccountConfig(hardwareTypeOverride: HardwareType? = hardwareType): FullAccountConfig {
    val resolvedHardwareType = hardwareTypeOverride ?: HardwareType.W1
    return FullAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes,
      isHardwareFake = isHardwareFake,
      hardwareType = resolvedHardwareType,
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
