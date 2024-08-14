package build.wallet.debug

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.f8e.F8eEnvironment.Development

val DebugOptionsFake = DebugOptions(
  bitcoinNetworkType = SIGNET,
  isHardwareFake = true,
  f8eEnvironment = Development,
  isUsingSocRecFakes = true,
  isTestAccount = true
)
