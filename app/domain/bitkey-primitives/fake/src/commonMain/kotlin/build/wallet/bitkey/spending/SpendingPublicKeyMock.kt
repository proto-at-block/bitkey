package build.wallet.bitkey.spending

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey

val AppSpendingPublicKeyMock =
  AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "app-dpub"))
val AppSpendingPublicKeyMock2 =
  AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "app-dpub-2"))
val AppSpendingPrivateKeyMock =
  AppSpendingPrivateKey(ExtendedPrivateKey(xprv = "xprv123", mnemonic = "mnemonic123"))
val AppSpendingKeypair = AppSpendingKeypair(AppSpendingPublicKeyMock, AppSpendingPrivateKeyMock)

val HwSpendingPublicKeyMock =
  HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub"))

val SpendingKeysetMock =
  SpendingKeyset(
    localId = "spending-public-keyset-fake-id-1",
    f8eSpendingKeyset = F8eSpendingKeysetMock,
    networkType = SIGNET,
    appKey = AppSpendingPublicKeyMock,
    hardwareKey = HwSpendingPublicKeyMock
  )
val PrivateWalletSpendingKeysetMock = SpendingKeysetMock.copy(
  f8eSpendingKeyset = SpendingKeysetMock.f8eSpendingKeyset.copy(
    serverIntegritySignature = "test-signature"
  )
)

val SpendingKeysetMock2 =
  SpendingKeyset(
    localId = "spending-public-keyset-fake-id-2",
    f8eSpendingKeyset =
      F8eSpendingKeyset(
        keysetId = "spending-public-keyset-fake-server-id-2",
        spendingPublicKey =
          F8eSpendingPublicKey(
            DescriptorPublicKeyMock(identifier = "server-dpub-2")
          )
      ),
    networkType = SIGNET,
    appKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "app-dpub-2")),
    hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-2"))
  )
