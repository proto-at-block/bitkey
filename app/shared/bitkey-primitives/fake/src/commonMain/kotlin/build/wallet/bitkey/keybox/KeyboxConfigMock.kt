package build.wallet.bitkey.keybox

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.f8e.F8eEnvironment.Development

val KeyboxConfigMock =
  KeyboxConfig(
    networkType = SIGNET,
    isHardwareFake = true,
    f8eEnvironment = Development,
    isUsingSocRecFakes = true,
    isTestAccount = true
  )
