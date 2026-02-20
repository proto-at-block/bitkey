package build.wallet.utxo

import app.cash.turbine.test
import build.wallet.bitcoin.bdk.bitcoinAmount
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.mineBlock
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testForBdk1AndBdk2
import build.wallet.testing.ext.waitForFunds
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

class WalletUtxoFunctionalTests : FunSpec({

  fun AppTester.utxos(): Flow<Utxos> {
    return bitcoinWalletService.transactionsData()
      .mapNotNull { it?.utxos }
      .distinctUntilChanged()
  }

  testForBdk1AndBdk2("no utxos in empty wallet") { app ->
    app.onboardFullAccountWithFakeHardware()

    app.utxos().test {
      awaitItem().should { utxos ->
        utxos.confirmed.shouldBeEmpty()
        utxos.unconfirmed.shouldBeEmpty()
        utxos.all.shouldBeEmpty()
      }
    }
  }

  testForBdk1AndBdk2(
    "utxos are loaded after receiving funds",
    isIsolatedTest = true
  ) { app ->
    app.onboardFullAccountWithFakeHardware()

    app.utxos().test {
      awaitItem().all.shouldBeEmpty()

      // Add confirmed utxo
      app.addSomeFunds(amount = sats(1_000), waitForConfirmation = true)
      awaitUntil { it.confirmed.size == 1 && it.unconfirmed.isEmpty() }
        .should { utxos ->
          utxos.confirmed.single().bitcoinAmount.shouldBe(sats(1_000))
          utxos.unconfirmed.shouldBeEmpty()
          utxos.all.shouldContainExactly(utxos.confirmed)
        }

      // Add unconfirmed utxo
      val fundingResult = app.addSomeFunds(amount = sats(2_000), waitForConfirmation = false)
      awaitUntil { it.confirmed.size == 1 && it.unconfirmed.size == 1 }
        .should { utxos ->
          utxos.confirmed.single().bitcoinAmount.shouldBe(sats(1_000))
          utxos.unconfirmed.shouldHaveSize(1)
          utxos.unconfirmed.single().bitcoinAmount.shouldBe(sats(2_000))
          utxos.all.shouldContainExactly(utxos.confirmed + utxos.unconfirmed)
        }

      // Wait for confirmation
      app.mineBlock(txid = fundingResult.tx.id)
      app.waitForFunds { it.confirmed == sats(3_000) }

      // Both utxos should be confirmed
      awaitUntil { it.confirmed.size == 2 && it.unconfirmed.isEmpty() }
        .should { utxos ->
          utxos.confirmed.shouldHaveSize(2)
          utxos.confirmed.single { it.bitcoinAmount == sats(1_000) }
          utxos.confirmed.single { it.bitcoinAmount == sats(2_000) }
          utxos.unconfirmed.shouldBeEmpty()
          utxos.all.shouldContainExactly(utxos.confirmed)
        }

      cancelAndIgnoreRemainingEvents()
    }
  }
})
