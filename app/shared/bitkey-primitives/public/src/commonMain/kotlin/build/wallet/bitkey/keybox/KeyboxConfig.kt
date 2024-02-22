package build.wallet.bitkey.keybox

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.f8e.F8eEnvironment
import kotlin.time.Duration

/**
 * Defines keybox environment variables:
 * @property networkType network type to use for the keybox
 * @property f8eEnvironment - describes a f8e environment used for the keybox.
 * @property isHardwareFake if we should use real or mocked hardware
 * @property isUsingSocRecFakes if we should use real or fake socrec service implementation
 * @property isTestAccount is this a test account
 * @property delayNotifyDuration - how long to delay during a recovery
 *
 * Note that [KeyboxConfig] type is equivalent to [FullAccountConfig]. The [KeyboxConfig] is what
 * we used to have primarily before we had different account types. Using [FullAccountConfig] is
 * preferred. Long term goal is to get rid of [KeyboxConfig] entirely in favor of
 * [FullAccountConfig] (TODO: BKR-490).
 *
 * Default [KeyboxConfig] is defined in [TemplateKeyboxConfigDao].
 */
data class KeyboxConfig(
  val networkType: BitcoinNetworkType,
  val f8eEnvironment: F8eEnvironment,
  val isHardwareFake: Boolean,
  val isUsingSocRecFakes: Boolean,
  val isTestAccount: Boolean,
  val delayNotifyDuration: Duration? = null,
)
