package build.wallet.bitcoin.utxo

import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.money.BitcoinMoney.Companion.sats
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class UtxoConsolidationParamsTests : FunSpec({
  test("UtxoConsolidationParams cannot be initialized with negative currentUtxoCount") {
    shouldThrow<IllegalArgumentException> {
      UtxoConsolidationParams(
        type = UtxoConsolidationType.ConsolidateAll,
        targetAddress = someBitcoinAddress,
        eligibleUtxoCount = -1,
        balance = sats(100),
        consolidationCost = sats(5),
        appSignedPsbt = PsbtMock,
        transactionPriority = SIXTY_MINUTES,
        walletHasUnconfirmedUtxos = false,
        walletExceedsMaxUtxoCount = false,
        maxUtxoCount = 150
      )
    }
  }

  test("UtxoConsolidationParams cannot be initialized with currentUtxoCount of 0") {
    shouldThrow<IllegalArgumentException> {
      UtxoConsolidationParams(
        type = UtxoConsolidationType.ConsolidateAll,
        targetAddress = someBitcoinAddress,
        eligibleUtxoCount = 0,
        balance = sats(100),
        consolidationCost = sats(5),
        appSignedPsbt = PsbtMock,
        transactionPriority = SIXTY_MINUTES,
        walletHasUnconfirmedUtxos = false,
        walletExceedsMaxUtxoCount = false,
        maxUtxoCount = 150
      )
    }
  }

  test("UtxoConsolidationParams cannot be initialized with currentUtxoCount of 1") {
    shouldThrow<IllegalArgumentException> {
      UtxoConsolidationParams(
        type = UtxoConsolidationType.ConsolidateAll,
        targetAddress = someBitcoinAddress,
        eligibleUtxoCount = 1,
        balance = sats(100),
        consolidationCost = sats(5),
        appSignedPsbt = PsbtMock,
        transactionPriority = SIXTY_MINUTES,
        walletHasUnconfirmedUtxos = false,
        walletExceedsMaxUtxoCount = false,
        maxUtxoCount = 150
      )
    }
  }

  test("UtxoConsolidationParams can be initialized with currentUtxoCount of 2") {
    UtxoConsolidationParams(
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
  }
})
