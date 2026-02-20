package build.wallet.bitcoin.transactions

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.ext.testForBdk1AndBdk2
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull

class BitcoinWalletServiceFunctionalTests : FunSpec({

  // Disabled: These tests are currently disabled (xcontext)
  xcontext("exact amount - faster psbts should have all inputs from slower psbts") {
    testForBdk1AndBdk2("exact amount - faster psbts should have all inputs from slower psbts") { app ->
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

        psbts.shouldNotBeNull()
        psbts.fastest.shouldNotBeNull()
        psbts.thirtyMinutes.shouldNotBeNull()

        val sixtyMinuteInputs = psbts.sixtyMinutes.inputs
        val thirtyMinuteInputs = psbts.thirtyMinutes?.inputs.shouldNotBeNull()
        val fastestInputs = psbts.fastest?.inputs.shouldNotBeNull()

        // For exact amount, faster PSBTs should reuse inputs from slower ones
        thirtyMinuteInputs.containsAll(sixtyMinuteInputs).shouldBeTrue()
        fastestInputs.containsAll(thirtyMinuteInputs).shouldBeTrue()

        app.returnFundsToTreasury()
      }
    }
  }

  // Disabled: These tests are currently disabled (xcontext)
  xcontext("send all - creates psbts for all priorities") {
    testForBdk1AndBdk2("send all - creates psbts for all priorities") { app ->
      app.onboardFullAccountWithFakeHardware()

      app.bitcoinWalletService.transactionsData().test {
        // Add confirmed utxo
        app.addSomeFunds(amount = sats(5_000), waitForConfirmation = true)

        awaitUntil {
          it?.balance?.confirmed == sats(5_000)
        }

        val psbts = app.bitcoinWalletService.createPsbtsForSendAmount(
          sendAmount = BitcoinTransactionSendAmount.SendAll,
          recipientAddress = app.treasuryWallet.getReturnAddress()
        ).get()

        // All three PSBTs should be created for SendAll
        psbts.shouldNotBeNull()
        psbts.fastest.shouldNotBeNull()
        psbts.thirtyMinutes.shouldNotBeNull()
        psbts.sixtyMinutes.shouldNotBeNull()

        app.returnFundsToTreasury()
      }
    }
  }
})
