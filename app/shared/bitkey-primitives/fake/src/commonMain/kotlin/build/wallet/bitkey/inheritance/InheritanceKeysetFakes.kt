package build.wallet.bitkey.inheritance

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock

val InheritanceKeysetFake = InheritanceKeyset(
  network = BitcoinNetworkType.BITCOIN,
  appSpendingPublicKey = AppSpendingPublicKeyMock,
  appSpendingPrivateKey = AppSpendingPrivateKeyMock
)
