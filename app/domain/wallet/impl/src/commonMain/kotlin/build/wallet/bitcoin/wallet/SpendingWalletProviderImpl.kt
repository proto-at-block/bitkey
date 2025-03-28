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
class SpendingWalletProviderImpl(
  private val bdkWalletProvider: BdkWalletProvider,
  private val bdkTransactionMapper: BdkTransactionMapper,
  private val bdkWalletSyncer: BdkWalletSyncer,
  private val bdkPsbtBuilder: BdkPartiallySignedTransactionBuilder,
  private val bdkTxBuilderFactory: BdkTxBuilderFactory,
  private val bdkAddressBuilder: BdkAddressBuilder,
  private val bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
  private val appSessionManager: AppSessionManager,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
  private val feeBumpAllowShrinkingCheckerImpl: FeeBumpAllowShrinkingChecker,
  private val accountConfigService: AccountConfigService,
) : SpendingWalletProvider {
  override suspend fun getWallet(
    walletDescriptor: SpendingWalletDescriptor,
  ): Result<SpendingWallet, Throwable> =
    coroutineBinding {
      val bitcoinNetwork = accountConfigService.activeOrDefaultConfig().value.bitcoinNetworkType
      ensure(bitcoinNetwork == walletDescriptor.networkType) {
        Error("Wallet descriptor bitcoin network (${walletDescriptor.networkType}) does not match app's bitcoin network ($bitcoinNetwork).")
      }
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
        feeBumpAllowShrinkingChecker = feeBumpAllowShrinkingCheckerImpl
      )
    }.logFailure { "Error creating spending wallet." }
}
