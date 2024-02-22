package build.wallet.bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment

sealed interface AccountConfig {
  /**
   * Determines the bitcoin network type to use for this account.
   */
  val bitcoinNetworkType: BitcoinNetworkType

  /**
   * Determines f8e environment to use for this account.
   */
  val f8eEnvironment: F8eEnvironment

  /**
   * Determines if this is a "test" account (some functionality is faked out).
   */
  val isTestAccount: Boolean

  /**
   * Determines if we want to use socrec fakes.
   */
  val isUsingSocRecFakes: Boolean
}
