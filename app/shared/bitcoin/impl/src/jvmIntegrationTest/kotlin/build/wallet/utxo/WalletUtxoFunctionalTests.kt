package build.wallet.utxo

import app.cash.turbine.test
import build.wallet.bitcoin.bdk.bitcoinAmount
import build.wallet.bitcoin.transactions.TransactionsService
import build.wallet.bitcoin.transactions.transactionsLoadedData
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.mineBlock
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.waitForFunds
import build.wallet.testing.tags.TestTag.IsolatedTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class WalletUtxoFunctionalTests : FunSpec({
  coroutineTestScope = true

  lateinit var appTester: AppTester
  lateinit var transactionsService: TransactionsService

  beforeTest {
    appTester = launchNewApp()
    transactionsService = appTester.app.appComponent.transactionsService
  }

  fun utxos(): Flow<Utxos> {
    return transactionsService.transactionsLoadedData()
      .map { it.utxos }
      .distinctUntilChanged()
  }

  test("no utxos in empty wallet") {
    appTester.onboardFullAccountWithFakeHardware()

    utxos().test {
      awaitItem().should { utxos ->
        utxos.confirmed.shouldBeEmpty()
        utxos.unconfirmed.shouldBeEmpty()
        utxos.all.shouldBeEmpty()
      }
    }
  }

  test("utxos are loaded after receiving funds")
    .config(tags = setOf(IsolatedTest)) {
      appTester.onboardFullAccountWithFakeHardware()

      utxos().test {
        awaitItem().all.shouldBeEmpty()

        // Add confirmed utxo
        appTester.addSomeFunds(amount = sats(1_000), waitForConfirmation = true)
        awaitItem()
          .should { utxos ->
            utxos.confirmed.shouldHaveSize(1)
            utxos.confirmed.single().bitcoinAmount.shouldBe(sats(1_000))
            utxos.unconfirmed.shouldBeEmpty()
            utxos.all.shouldContainExactly(utxos.confirmed)
          }

        // Add unconfirmed utxo
        appTester.addSomeFunds(amount = sats(2_000), waitForConfirmation = false)
        awaitItem()
          .should { utxos ->
            utxos.confirmed.single().bitcoinAmount.shouldBe(sats(1_000))
            utxos.unconfirmed.shouldHaveSize(1)
            utxos.unconfirmed.single().bitcoinAmount.shouldBe(sats(2_000))
            utxos.all.shouldContainExactly(utxos.confirmed + utxos.unconfirmed)
          }

        // Wait for confirmation
        appTester.mineBlock()
        appTester.waitForFunds { it.confirmed == sats(3_000) }

        // Both utxos should be confirmed
        awaitItem()
          .should { utxos ->
            utxos.confirmed.shouldHaveSize(2)
            utxos.confirmed.single { it.bitcoinAmount == sats(1_000) }
            utxos.confirmed.single { it.bitcoinAmount == sats(2_000) }
            utxos.unconfirmed.shouldBeEmpty()
            utxos.all.shouldContainExactly(utxos.confirmed)
          }
      }
    }
})