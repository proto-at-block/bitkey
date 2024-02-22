package build.wallet.keybox.builder

import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.KeyCrossDraft.CompleteKeyCross
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeys
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.platform.random.Uuid
import com.github.michaelbull.result.getOrThrow

class KeyCrossBuilderImpl(
  private val appKeysGenerator: AppKeysGenerator,
  private val uuid: Uuid,
) : KeyCrossBuilder {
  override suspend fun createNewKeyCross(
    config: KeyboxConfig, // CHANGE THIS TO JUST NETWORK TYPE
  ): WithAppKeys {
    val networkType = config.networkType
    val keyBundle = appKeysGenerator.generateKeyBundle(networkType).getOrThrow()
    return WithAppKeys(keyBundle, config)
  }

  override fun addHardwareKeyBundle(
    draft: WithAppKeys,
    hardwareKeyBundle: HwKeyBundle,
  ): WithAppKeysAndHardwareKeys {
    return WithAppKeysAndHardwareKeys(
      appKeyBundle = draft.appKeyBundle,
      hardwareKeyBundle = hardwareKeyBundle,
      config = draft.config
    )
  }

  override suspend fun addServerKey(
    draft: WithAppKeysAndHardwareKeys,
    f8eSpendingKeyset: F8eSpendingKeyset,
  ): CompleteKeyCross {
    val spendingKeyset =
      SpendingKeyset(
        localId = uuid.random(),
        appKey = draft.appKeyBundle.spendingKey,
        networkType = draft.config.networkType,
        hardwareKey = draft.hardwareKeyBundle.spendingKey,
        f8eSpendingKeyset = f8eSpendingKeyset
      )

    return CompleteKeyCross(
      config = draft.config,
      appKeyBundle = draft.appKeyBundle,
      hardwareKeyBundle = draft.hardwareKeyBundle,
      spendingKeyset = spendingKeyset
    )
  }
}
