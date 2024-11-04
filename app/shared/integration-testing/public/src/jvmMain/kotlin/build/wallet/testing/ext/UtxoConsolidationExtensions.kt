package build.wallet.testing.ext

import build.wallet.bitcoin.utxo.UtxoConsolidationParams
import build.wallet.bitcoin.utxo.UtxoConsolidationTransactionDetail
import build.wallet.testing.AppTester
import build.wallet.testing.shouldBeOk
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.flow.first

suspend fun AppTester.consolidateAllUtxos(): Pair<UtxoConsolidationParams, UtxoConsolidationTransactionDetail> {
  val utxoConsolidationService = app.appComponent.utxoConsolidationService
  val consolidationParams = utxoConsolidationService.prepareUtxoConsolidation()
    .shouldBeOk()
    .single()

  val appAndHardwareSignedPsbt = signPsbtWithHardware(psbt = consolidationParams.appSignedPsbt)
  val spendingWallet = app.appComponent.transactionsService.spendingWallet().value.shouldNotBeNull()
  val totalBalanceBeforeConsolidation = spendingWallet.balance().first().total

  val consolidationTransactionDetail = utxoConsolidationService
    .broadcastConsolidation(appAndHardwareSignedPsbt)
    .shouldBeOk()
  mineBlock(txid = consolidationTransactionDetail.broadcastDetail.transactionId)

  val totalBalanceAfterConsolidation =
    totalBalanceBeforeConsolidation - consolidationParams.consolidationCost
  waitForFunds { it.confirmed == totalBalanceAfterConsolidation }

  return Pair(consolidationParams, consolidationTransactionDetail)
}
