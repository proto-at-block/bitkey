package build.wallet.bitcoin.utxo

import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.BroadcastDetail
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.time.someInstant
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class UtxoConsolidationServiceFake : UtxoConsolidationService {
  private val defaultConsolidationParams = UtxoConsolidationParams(
    type = UtxoConsolidationType.ConsolidateAll,
    targetAddress = someBitcoinAddress,
    eligibleUtxoCount = 2,
    balance = sats(100),
    consolidationCost = sats(5),
    appSignedPsbt = PsbtMock,
    transactionPriority = SIXTY_MINUTES,
    walletHasUnconfirmedUtxos = false,
    walletExceedsMaxUtxoCount = false,
    maxUtxoCount = 150
  )

  var prepareUtxoConsolidationResult = Ok(listOf(defaultConsolidationParams))

  override suspend fun prepareUtxoConsolidation(): Result<List<UtxoConsolidationParams>, Throwable> {
    return prepareUtxoConsolidationResult
  }

  override suspend fun broadcastConsolidation(
    signedConsolidation: Psbt,
  ): Result<UtxoConsolidationTransactionDetail, Error> {
    return Ok(
      UtxoConsolidationTransactionDetail(
        BroadcastDetail(
          broadcastTime = someInstant,
          transactionId = "txid-fake"
        ),
        estimatedConfirmationTime = someInstant
      )
    )
  }

  fun reset() {
    prepareUtxoConsolidationResult = Ok(listOf(defaultConsolidationParams))
  }
}
