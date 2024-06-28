package build.wallet.bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment

/**
 * Defines environment configuration for a Software Account.
 */
data class SoftwareAccountConfig(
  override val bitcoinNetworkType: BitcoinNetworkType,
  override val f8eEnvironment: F8eEnvironment,
  override val isTestAccount: Boolean,
  override val isUsingSocRecFakes: Boolean,
) : AccountConfig
