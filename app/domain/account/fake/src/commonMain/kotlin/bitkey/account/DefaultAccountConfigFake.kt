package bitkey.account

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.f8e.F8eEnvironment.Development

val DefaultAccountConfigFake = DefaultAccountConfig(
  bitcoinNetworkType = SIGNET,
  isHardwareFake = true,
  f8eEnvironment = Development,
  isUsingSocRecFakes = true,
  isTestAccount = true
)
