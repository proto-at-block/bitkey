package build.wallet.bitcoin.transactions

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.tags.TestTag.IsolatedTest
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BitcoinWalletServiceFunctionalTests : FunSpec({
  xtest("faster psbts should have all inputs from slower psbts").config(tags = setOf(IsolatedTest)) {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    app.bitcoinWalletService.transactionsData().test {
      // Add confirmed utxo
      app.addSomeFunds(amount = sats(1_000), waitForConfirmation = true)
      app.addSomeFunds(amount = sats(2_000), waitForConfirmation = true)
      app.addSomeFunds(amount = sats(6_000), waitForConfirmation = true)

      awaitUntil {
        it?.balance?.confirmed == sats(9_000)
      }

      val psbts = app.bitcoinWalletService.createPsbtsForSendAmount(
        sendAmount = BitcoinTransactionSendAmount.ExactAmount(sats(2_500)),
        recipientAddress = app.treasuryWallet.getReturnAddress()
      ).get()

      psbts.shouldNotBeNull().size.shouldBe(3)
      val sixtyMinuteInputs = psbts[EstimatedTransactionPriority.SIXTY_MINUTES]
        ?.inputs
        .shouldNotBeNull()

      val thirtyMinuteInputs = psbts[EstimatedTransactionPriority.THIRTY_MINUTES]
        ?.inputs
        .shouldNotBeNull()

      val fastestInputs = psbts[EstimatedTransactionPriority.FASTEST]
        ?.inputs
        .shouldNotBeNull()

      thirtyMinuteInputs.containsAll(sixtyMinuteInputs).shouldBeTrue()
      fastestInputs.containsAll(thirtyMinuteInputs).shouldBeTrue()

      app.returnFundsToTreasury()
    }
  }
})
