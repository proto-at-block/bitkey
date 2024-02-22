package build.wallet.keybox.builder

import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.KeyCrossDraft.CompleteKeyCross
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeys
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.KeyboxConfig

interface KeyCrossBuilder {
  suspend fun createNewKeyCross(config: KeyboxConfig): WithAppKeys

  fun addHardwareKeyBundle(
    draft: WithAppKeys,
    hardwareKeyBundle: HwKeyBundle,
  ): WithAppKeysAndHardwareKeys

  suspend fun addServerKey(
    draft: WithAppKeysAndHardwareKeys,
    f8eSpendingKeyset: F8eSpendingKeyset,
  ): CompleteKeyCross
}
