package build.wallet.keybox.builder

import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.keybox.WithAppKeysAndHardwareKeysMock
import build.wallet.bitkey.spending.SpendingKeyset

class KeyCrossBuilderMock : KeyCrossBuilder {
  override suspend fun createNewKeyCross(config: KeyboxConfig): KeyCrossDraft.WithAppKeys =
    KeyCrossDraft.WithAppKeys(
      appKeyBundle = WithAppKeysAndHardwareKeysMock.appKeyBundle,
      config = config
    )

  override fun addHardwareKeyBundle(
    draft: KeyCrossDraft.WithAppKeys,
    hardwareKeyBundle: HwKeyBundle,
  ) = WithAppKeysAndHardwareKeysMock

  override suspend fun addServerKey(
    draft: WithAppKeysAndHardwareKeys,
    f8eSpendingKeyset: F8eSpendingKeyset,
  ): KeyCrossDraft.CompleteKeyCross =
    KeyCrossDraft.CompleteKeyCross(
      config = draft.config,
      appKeyBundle = draft.appKeyBundle,
      hardwareKeyBundle = draft.hardwareKeyBundle,
      spendingKeyset =
        SpendingKeyset(
          localId = "fake-spending-keyset-id",
          networkType = TESTNET,
          appKey = draft.appKeyBundle.spendingKey,
          hardwareKey = draft.hardwareKeyBundle.spendingKey,
          f8eSpendingKeyset = f8eSpendingKeyset
        )
    )
}
