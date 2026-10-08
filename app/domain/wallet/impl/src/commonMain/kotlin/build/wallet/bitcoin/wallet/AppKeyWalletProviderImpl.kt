package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.descriptor.AppKeyWalletDescriptorBuilder
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.isEnabled
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class AppKeyWalletProviderImpl(
  private val spendingWalletProvider: SpendingWalletProvider,
  private val spendingWalletV2Provider: SpendingWalletV2Provider,
  private val bdk2FeatureFlag: Bdk2FeatureFlag,
  private val descriptorBuilder: AppKeyWalletDescriptorBuilder,
  private val appPrivateKeyDao: AppPrivateKeyDao,
) : AppKeyWalletProvider {
  override suspend fun getAppKeyWallet(
    keyset: SpendingKeyset,
  ): Result<AppKeyWallet, Throwable> =
    coroutineBinding {
      val appSpendingPrivateKey = appPrivateKeyDao.getAppSpendingPrivateKey(keyset.appKey).bind()
        ?: com.github.michaelbull.result.Err(
          AppKeyWalletError.PrivateKeyUnavailable(keyset.localId)
        ).bind()

      val walletDescriptor = SpendingWalletDescriptor(
        identifier = "app-key-wallet-${keyset.localId}",
        networkType = keyset.networkType,
        receivingDescriptor = descriptorBuilder.receivingDescriptor(appSpendingPrivateKey.key),
        changeDescriptor = descriptorBuilder.changeDescriptor(appSpendingPrivateKey.key)
      )

      // Both providers validate the descriptor's network against the app's configured network, so a
      // mismatch surfaces as an error here rather than producing a wallet on the wrong chain.
      //
      // Note: failures are deliberately not logged with the descriptor in scope — the rescue
      // descriptor embeds an xprv, and logFailure would risk capturing it in a message.
      val spendingWallet = if (bdk2FeatureFlag.isEnabled()) {
        spendingWalletV2Provider.getWallet(walletDescriptor)
      } else {
        spendingWalletProvider.getWallet(walletDescriptor)
      }
        .mapError { AppKeyWalletError.WalletCreationFailed(it) }
        .bind()

      AppKeyWalletImpl(
        spendingWallet = spendingWallet,
        fullScanOperation = {
          if (spendingWallet is SpendingWalletV2Impl) {
            spendingWallet.fullScan()
          } else if (spendingWallet is SpendingWalletImpl) {
            spendingWallet.fullScan()
          } else {
            spendingWallet.sync()
          }
        }
      )
    }
}
