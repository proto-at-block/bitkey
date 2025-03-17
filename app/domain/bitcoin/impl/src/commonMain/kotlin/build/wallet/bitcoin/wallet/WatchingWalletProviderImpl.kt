package build.wallet.bitcoin.wallet

import bitkey.account.AccountConfigService
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bitcoin.bdk.BdkTransactionMapper
import build.wallet.bitcoin.bdk.BdkWalletProvider
import build.wallet.bitcoin.bdk.BdkWalletSyncer
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingChecker
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.logging.logFailure
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class WatchingWalletProviderImpl(
  private val bdkWalletProvider: BdkWalletProvider,
  private val bdkTransactionMapper: BdkTransactionMapper,
  private val bdkWalletSyncer: BdkWalletSyncer,
  private val bdkPsbtBuilder: BdkPartiallySignedTransactionBuilder,
  private val bdkTxBuilderFactory: BdkTxBuilderFactory,
  private val bdkAddressBuilder: BdkAddressBuilder,
  private val bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  private val appSessionManager: AppSessionManager,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
  private val feeBumpAllowShrinkingChecker: FeeBumpAllowShrinkingChecker,
  private val accountConfigService: AccountConfigService,
) : WatchingWalletProvider {
  override suspend fun getWallet(
    walletDescriptor: WatchingWalletDescriptor,
  ): Result<WatchingWallet, Throwable> =
    coroutineBinding {
      val bitcoinNetwork = accountConfigService.activeOrDefaultConfig().value.bitcoinNetworkType
      ensure(bitcoinNetwork == walletDescriptor.networkType) {
        Error("Wallet descriptor bitcoin network (${walletDescriptor.networkType}) does not match app's bitcoin network ($bitcoinNetwork).")
      }
      // TODO(W-4257): create actual WatchingWalletImpl instance.
      //         Unfortunately, we cannot reuse BdkWallet instance between spending and watching
      //         descriptors so ideally we will need to create WatchingWalletImpl instance which
      //         will manage BdkWallet instances. Since the logic between SpendingWalletImpl and
      //         WatchingWalletImpl is largely shared, we should figure out a way to implement
      //         this without code duplication. For now, we will just return SpendingWalletImpl
      //         instance.
      SpendingWalletImpl(
        identifier = walletDescriptor.identifier,
        networkType = walletDescriptor.networkType,
        bdkWallet = bdkWalletProvider.getBdkWallet(walletDescriptor).bind(),
        bdkTransactionMapper = bdkTransactionMapper,
        bdkWalletSyncer = bdkWalletSyncer,
        bdkPsbtBuilder = bdkPsbtBuilder,
        bdkTxBuilderFactory = bdkTxBuilderFactory,
        bdkAddressBuilder = bdkAddressBuilder,
        bdkBumpFeeTxBuilderFactory = bdkBumpFeeTxBuilderFactory,
        appSessionManager = appSessionManager,
        bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
        feeBumpAllowShrinkingChecker = feeBumpAllowShrinkingChecker
      )
    }.logFailure { "Error creating spending wallet." }
}
