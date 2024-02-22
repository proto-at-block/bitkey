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
import build.wallet.testing.shouldBeLoaded
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.fail
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class TreasuryWallet(
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val blockchainControl: BlockchainControl,
  val spendingWallet: SpendingWallet,
) : SpendingWallet by spendingWallet {
  suspend fun fund(
    destinationWallet: WatchingWallet,
    amount: BitcoinMoney,
  ): FundingResult {
    val address = destinationWallet.getNewAddress().unwrap()
    println("Using Treasury to fund $address with $amount")

    // Retry double-spends caused by concurrent test runs trying to spend from the same
    // UTXO from the treasury.
    val fundingResult =
      eventually(
        eventuallyConfig {
          duration = 60.seconds
          interval = 5.seconds
          expectedExceptions = setOf(BdkError::class)
        }
      ) {
        fund(address, amount)
      }

    // (Regtest only) Advance the blockchain
    blockchainControl.mineBlocks(1)

    println("Successfully broadcast funding transaction, waiting for tx to appear in mempool")

    destinationWallet.balance().test {
      eventually(
        eventuallyConfig {
          duration = 10.seconds
          interval = 1.seconds
          initialDelay = 1.seconds
        }
      ) {
        destinationWallet.sync()
        awaitItem().shouldBeLoaded().total.shouldBeGreaterThanOrEqualTo(amount)
      }
    }

    println("Treasury funding complete")

    return fundingResult
  }

  private suspend fun fund(
    depositAddress: BitcoinAddress,
    amount: BitcoinMoney,
  ): FundingResult {
    require(amount.isPositive)

    spendingWallet.sync().getOrThrow()
    val treasuryBalance = spendingWallet.balance().first().shouldBeLoaded()
    println("Treasury has ${treasuryBalance.confirmed} sats available")
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
