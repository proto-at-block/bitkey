package build.wallet.keybox.wallet

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class WatchingWalletDescriptorProviderImpl(
  private val descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
) : WatchingWalletDescriptorProvider {
  override suspend fun walletDescriptor(
    keyset: SpendingKeyset,
  ): Result<WatchingWalletDescriptor, Throwable> =
    coroutineBinding {
      val receivingDescriptor =
        descriptorBuilder.watchingReceivingDescriptor(
          appPublicKey = keyset.appKey.key,
          hardwareKey = keyset.hardwareKey.key,
          serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
        )
      val changeDescriptor =
        descriptorBuilder.watchingChangeDescriptor(
          appPublicKey = keyset.appKey.key,
          hardwareKey = keyset.hardwareKey.key,
          serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
        )
      WatchingWalletDescriptor(
        identifier = keyset.localId,
        networkType = keyset.networkType,
        receivingDescriptor = receivingDescriptor,
        changeDescriptor = changeDescriptor
      )
    }
}
