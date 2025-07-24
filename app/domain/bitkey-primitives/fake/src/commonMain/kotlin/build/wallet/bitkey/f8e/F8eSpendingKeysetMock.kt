package build.wallet.bitkey.f8e

import build.wallet.bitcoin.keys.DescriptorPublicKeyMock

val F8eSpendingPublicKeyMock =
  F8eSpendingPublicKey(DescriptorPublicKeyMock(identifier = "server-dpub"))

val F8eSpendingKeysetMock = F8eSpendingKeyset(
  keysetId = "f8e-spending-keyset-id",
  spendingPublicKey = F8eSpendingPublicKeyMock
)
