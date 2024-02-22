package build.wallet.bitkey.f8e

import build.wallet.bitcoin.keys.DescriptorPublicKeyMock

val F8eSpendingKeysetMock =
  F8eSpendingKeyset(
    keysetId = "f8e-spending-keyset-id",
    spendingPublicKey =
      F8eSpendingPublicKey(
        key = DescriptorPublicKeyMock("f8e-spending-keyset-dpub")
      )
  )
