package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bitcoin.bdk.BdkTransactionMapper
import build.wallet.bitcoin.bdk.BdkWalletProvider
import build.wallet.bitcoin.bdk.BdkWalletSyncer
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingChecker
import build.wallet.logging.logFailure
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

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
) : SpendingWalletProvider {
  override suspend fun getWallet(
    walletDescriptor: SpendingWalletDescriptor,
  ): Result<SpendingWallet, Throwable> =
    coroutineBinding {
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
