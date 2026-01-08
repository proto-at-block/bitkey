package build.wallet.bitcoin.wallet

import bitkey.account.AccountConfigService
import build.wallet.bitcoin.bdk.BdkTransactionMapperV2
import build.wallet.bitcoin.bdk.BdkWalletProvider
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

@BitkeyInject(AppScope::class)
class WalletV2ProviderImpl(
  private val bdkWalletProvider: BdkWalletProvider,
  private val appSessionManager: AppSessionManager,
  private val accountConfigService: AccountConfigService,
  private val bdkTransactionMapperV2: BdkTransactionMapperV2,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
) : WalletV2Provider {
  override fun getWallet(walletDescriptor: WalletDescriptor): Result<SpendingWallet, Throwable> {
    return createWallet(walletDescriptor)
  }

  private fun createWallet(walletDescriptor: WalletDescriptor): Result<WalletV2Impl, Throwable> {
    val bitcoinNetwork = accountConfigService.activeOrDefaultConfig().value.bitcoinNetworkType
    if (bitcoinNetwork != walletDescriptor.networkType) {
      return Err(Error("Wallet descriptor bitcoin network (${walletDescriptor.networkType}) does not match app's bitcoin network ($bitcoinNetwork)."))
    }

    return bdkWalletProvider.getBdkWalletV2(walletDescriptor)
      .map { bdkWallet ->
        WalletV2Impl(
          identifier = walletDescriptor.identifier,
          networkType = walletDescriptor.networkType,
          bdkWallet = bdkWallet,
          persister = bdkWalletProvider.getPersister(walletDescriptor.identifier),
          appSessionManager = appSessionManager,
          bdkTransactionMapperV2 = bdkTransactionMapperV2,
          bitcoinFeeRateEstimator = bitcoinFeeRateEstimator
        )
      }
  }
}
