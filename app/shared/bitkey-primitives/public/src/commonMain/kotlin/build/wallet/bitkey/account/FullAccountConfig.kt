package build.wallet.bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import kotlin.time.Duration

/**
 * Defines environment configuration for [FullAccount]s.
 * @property bitcoinNetworkType network type to use for the account
 * @property f8eEnvironment - describes a f8e environment used for the account.
 * @property isTestAccount is this a test account
 * @property isUsingSocRecFakes if we should use real or fake socrec service implementation
 * @property isHardwareFake determines if real or fake hardware should be used for this account.
 * @property delayNotifyDuration - how long to delay during a recovery
 */
data class FullAccountConfig(
  override val bitcoinNetworkType: BitcoinNetworkType,
  override val f8eEnvironment: F8eEnvironment,
  override val isTestAccount: Boolean,
  override val isUsingSocRecFakes: Boolean,
  val isHardwareFake: Boolean,
  val delayNotifyDuration: Duration? = null,
) : AccountConfig
