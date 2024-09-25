package build.wallet.bitcoin.utxo

import build.wallet.account.AccountService
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionsData.TransactionsLoadedData
import build.wallet.bitcoin.transactions.TransactionsService
import build.wallet.bitcoin.transactions.toDuration
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.bitkey.account.FullAccount
import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

class UtxoConsolidationServiceImpl(
  private val accountService: AccountService,
  private val transactionsService: TransactionsService,
  private val bitcoinAddressService: BitcoinAddressService,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
) : UtxoConsolidationService {
  private val consolidationTransactionPriority = SIXTY_MINUTES

  override suspend fun prepareUtxoConsolidation(): Result<List<UtxoConsolidationParams>, Throwable> =
    coroutineBinding {
      val account = accountService.activeAccount().first()
      ensure(account is FullAccount) { Error("No active full account present.") }

      val wallet = transactionsService.spendingWallet().first()
      ensureNotNull(wallet) { Error("SpendingWallet is null.") }

      val utxoCount = wallet.unspentOutputs().first().count()
      ensure(utxoCount > 1) { NotEnoughUtxosToConsolidateError(utxoCount = utxoCount) }

      val transactionsData = transactionsService.transactionsData()
        .filterIsInstance<TransactionsLoadedData>()
        .first()

      val walletBalance = transactionsData.balance.spendable

      // An address belonging to this wallet to which the consolidated UTXOs will be sent.
      val targetAddress = bitcoinAddressService.generateAddress(account).bind()

      val feeRate = bitcoinFeeRateEstimator.estimatedFeeRateForTransaction(
        networkType = account.config.bitcoinNetworkType,
        estimatedTransactionPriority = consolidationTransactionPriority
      )

      val psbt = wallet
        .createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = targetAddress,
            amount = SendAll,
            feePolicy = FeePolicy.Rate(feeRate)
          )
        )
        .bind()

      // Currently, only ConsolidateAll type is supported.
      // TODO(W-9710): implement support for different consolidation types.
      listOf(
        UtxoConsolidationParams(
          type = UtxoConsolidationType.ConsolidateAll,
          targetAddress = targetAddress,
          currentUtxoCount = utxoCount,
          balance = walletBalance,
          consolidationCost = psbt.fee,
          appSignedPsbt = psbt
        )
      )
    }

  override suspend fun broadcastConsolidation(
    signedConsolidation: Psbt,
  ): Result<UtxoConsolidationTransactionDetail, Error> =
    coroutineBinding {
      val broadcastDetail = transactionsService
        .broadcast(
          psbt = signedConsolidation,
          estimatedTransactionPriority = consolidationTransactionPriority
        )
        .logFailure { "Error broadcasting consolidation transaction." }
        .bind()

      UtxoConsolidationTransactionDetail(
        broadcastDetail = broadcastDetail,
        estimatedConfirmationTime = broadcastDetail.broadcastTime + consolidationTransactionPriority.toDuration()
      )
    }
}
