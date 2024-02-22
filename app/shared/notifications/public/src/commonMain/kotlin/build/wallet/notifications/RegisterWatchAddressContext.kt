package build.wallet.notifications

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.f8e.F8eEnvironment

/**
 * Object to represent the context needed to register an address with f8e for notifications. This
 * is persisted locally on failure and retransmitted.
 */
data class RegisterWatchAddressContext(
  val address: BitcoinAddress,
  val spendingKeysetId: String,
  val accountId: String,
  val f8eEnvironment: F8eEnvironment,
)
