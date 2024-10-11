package build.wallet.bitcoin.treasury

import app.cash.turbine.test
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.blockchain.BlockchainControl
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.money.BitcoinMoney
import build.wallet.money.matchers.shouldBeGreaterThanOrEqualTo
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.fail
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import kotlinx.coroutines.flow.first
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

class TreasuryWallet(
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val blockchainControl: BlockchainControl,
  val spendingWallet: SpendingWallet,
) : SpendingWallet by spendingWallet {
  /**
   * @param waitForConfirmation if true (default), waits for the transaction to be confirmed in the
   * blockchain.
   */
  suspend fun fund(
    destinationWallet: WatchingWallet,
    amount: BitcoinMoney,
    waitForConfirmation: Boolean = true,
  ): FundingResult {
    val address = destinationWallet.getNewAddress().unwrap()
    println("Using Treasury to fund $address with $amount")

    // Retry double-spends caused by concurrent test runs trying to spend from the same
    // UTXO from the treasury.
    val fundingResult =
      eventually(
        eventuallyConfig {
          duration = 180.seconds
          interval = 5.seconds
          retries = 10
          expectedExceptions = setOf(BdkError::class)
          listener = {
              k,
              throwable,
            ->
            println("Iteration $k failed, with cause $throwable of type ${throwable::class}")
          }
        }
      ) {
        fund(address, amount)
      }

    if (waitForConfirmation) {
      // (Regtest only) Advance the blockchain
      blockchainControl.mineBlocks(1)
      println("Successfully broadcast funding transaction, waiting for tx to appear in mempool")
    } else {
      println("Successfully broadcast funding transaction, not waiting for confirmation")
    }

    destinationWallet.balance().test {
      eventually(
        eventuallyConfig {
          duration = 30.seconds
          interval = 1.seconds
          initialDelay = 1.seconds
          listener = {
              _,
              throwable,
            ->
            println("Still waiting for transaction to propagate... $throwable of type ${throwable::class}")
          }
        }
      ) {
        val timeTaken = measureTimeMillis {
          destinationWallet.sync()
        }

        println("Sync time: $timeTaken ms")
        awaitItem().total.shouldBeGreaterThanOrEqualTo(amount)
      }
      cancelAndIgnoreRemainingEvents()
    }

    println("Treasury funding complete")

    return fundingResult
  }

  private suspend fun fund(
    depositAddress: BitcoinAddress,
    amount: BitcoinMoney,
  ): FundingResult {
    require(amount.isPositive)

    val timeTaken = measureTimeMillis {
      spendingWallet.sync().getOrThrow()
    }

    println("Sync time: $timeTaken ms")
    val treasuryBalance = spendingWallet.balance().first()
    println("Treasury has Confirmed: ${treasuryBalance.confirmed} UntrustedPending: ${treasuryBalance.untrustedPending} TrustedPending: ${treasuryBalance.trustedPending} sats available")
    val treasuryAddress = spendingWallet.getLastUnusedAddress().getOrThrow()

    val spendableBalance =
      treasuryBalance.confirmed + treasuryBalance.trustedPending + treasuryBalance.untrustedPending
    if (spendableBalance.fractionalUnitValue < 10_000) {
      fail(
        "Not enough sats in treasury wallet - $spendableBalance. Send coins to ${treasuryAddress.address}"
      )
    }

    val psbt =
      spendingWallet
        .createSignedPsbt(
          SpendingWallet.PsbtConstructionMethod.Regular(
            recipientAddress = depositAddress,
            amount = ExactAmount(amount),
            feePolicy = FeePolicy.MinRelayRate
          )
        )
        .getOrThrow()

    bitcoinBlockchain.broadcast(psbt).getOrThrow()

    return FundingResult(depositAddress, psbt)
  }

  suspend fun getReturnAddress(): BitcoinAddress {
    return spendingWallet.getLastUnusedAddress().getOrThrow()
  }
}

data class FundingResult(
  val depositAddress: BitcoinAddress,
  val tx: Psbt,
)
