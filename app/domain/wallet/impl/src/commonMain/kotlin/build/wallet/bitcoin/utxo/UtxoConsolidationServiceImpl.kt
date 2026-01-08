package build.wallet.bitcoin.utxo

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.getTransactionData
import build.wallet.bitcoin.transactions.toDuration
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.feature.flags.UtxoMaxConsolidationCountFeatureFlag
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class UtxoConsolidationServiceImpl(
  private val accountService: AccountService,
  private val bitcoinWalletService: BitcoinWalletService,
  private val bitcoinAddressService: BitcoinAddressService,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
  private val utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag,
) : UtxoConsolidationService {
  private val consolidationTransactionPriority = SIXTY_MINUTES

  override suspend fun prepareUtxoConsolidation(): Result<List<UtxoConsolidationParams>, Throwable> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

      val wallet = bitcoinWalletService.spendingWallet().first()
      ensureNotNull(wallet) { Error("SpendingWallet is null.") }

      val utxos = bitcoinWalletService.getTransactionData().utxos

      // UTXO Consolidation requires UTXOs from confirmed transactions only.
      val confirmedUtxosCount = utxos.confirmed.size
      ensure(confirmedUtxosCount > 1) {
        NotEnoughUtxosToConsolidateError(utxoCount = confirmedUtxosCount)
      }

      // After a certain number of UTXOs, the signing process can timeout on iOS. We enforce a
      // maximum number in a single consolidation.
      val maxUtxos = utxoMaxConsolidationCountFeatureFlag.flagValue().value.value.toInt()
      val walletExceedsMaxUtxoCount = if (maxUtxos > 0) {
        confirmedUtxosCount > maxUtxos
      } else {
        // If for some reason maxUtxos <= 0, don't do any filtering because that probably means we've
        // turned off the max limit.
        false
      }

      // Take the N lowest value to consolidate.
      val selectedUtxos = if (walletExceedsMaxUtxoCount) {
        utxos.confirmed.sortedBy { it.txOut.value }
          .take(maxUtxos)
          .toSet()
      } else {
        utxos.confirmed
      }

      val utxoBalance = BitcoinMoney.sats(selectedUtxos.sumOf { it.txOut.value })

      // An address belonging to this wallet to which the consolidated UTXOs will be sent.
      val targetAddress = bitcoinAddressService.generateAddress().bind()

      val feeRate = bitcoinFeeRateEstimator.estimatedFeeRateForTransaction(
        networkType = account.config.bitcoinNetworkType,
        estimatedTransactionPriority = consolidationTransactionPriority
      )

      val psbt = wallet
        .createSignedPsbt(
          PsbtConstructionMethod.DrainAllFromUtxos(
            recipientAddress = targetAddress,
            feePolicy = FeePolicy.Rate(feeRate),
            utxos = selectedUtxos
          )
        )
        .bind()

      // Currently, only ConsolidateAll type is supported.
      // TODO(W-9710): implement support for different consolidation types.
      listOf(
        UtxoConsolidationParams(
          type = UtxoConsolidationType.ConsolidateAll,
          targetAddress = targetAddress,
          eligibleUtxoCount = selectedUtxos.size,
          balance = utxoBalance,
          consolidationCost = psbt.fee.amount,
          appSignedPsbt = psbt,
          transactionPriority = consolidationTransactionPriority,
          walletHasUnconfirmedUtxos = utxos.unconfirmed.isNotEmpty(),
          walletExceedsMaxUtxoCount = walletExceedsMaxUtxoCount,
          maxUtxoCount = maxUtxos
        )
      )
    }

  override suspend fun broadcastConsolidation(
    signedConsolidation: Psbt,
  ): Result<UtxoConsolidationTransactionDetail, Error> =
    coroutineBinding {
      val broadcastDetail = bitcoinWalletService
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
