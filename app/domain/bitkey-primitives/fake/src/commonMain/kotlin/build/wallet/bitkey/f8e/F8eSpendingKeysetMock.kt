package build.wallet.bitkey.f8e

import build.wallet.bitcoin.keys.DescriptorPublicKeyMock

val F8eSpendingPublicKeyMock =
  F8eSpendingPublicKey(DescriptorPublicKeyMock(identifier = "server-dpub"))

val F8eSpendingKeysetMock = F8eSpendingKeyset(
  keysetId = "f8e-spending-keyset-id",
  spendingPublicKey = F8eSpendingPublicKeyMock,
  privateWalletRootXpub = null
)

val F8eSpendingKeysetPrivateWalletMock = F8eSpendingKeyset(
  keysetId = "f8e-spending-keyset-id",
  spendingPublicKey = F8eSpendingPublicKeyMock,
  privateWalletRootXpub = "tpubD6NzVbkrYhZ4XPMXVToEroepyTscQmHYrdSDbvZvAFonusog8TjTB3iTQZ2Ds8atDfdxzN7DAioQ8Z4KBa4RD16FX7caE5hxiMbvkVr9Fom"
)
