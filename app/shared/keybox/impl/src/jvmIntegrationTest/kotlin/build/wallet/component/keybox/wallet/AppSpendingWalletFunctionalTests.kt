@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.component.keybox.wallet

import app.cash.turbine.turbineScope
import build.wallet.LoadableValue.InitialLoading
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.coroutines.actualDelay
import build.wallet.money.BitcoinMoney
import build.wallet.money.matchers.shouldBeLessThan
import build.wallet.testing.fakeTransact
import build.wallet.testing.launchNewApp
import build.wallet.testing.shouldBeLoaded
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getOrThrow
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.time.Duration.Companion.seconds

class AppSpendingWalletFunctionalTests : FunSpec({
  val appTester = launchNewApp()
  val bitcoinBlockchain = appTester.app.bitcoinBlockchain

  test("wallet for active spending keyset") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    val wallet =
      appTester.app.appComponent.appSpendingWalletProvider.getSpendingWallet(
        keyset = account.keybox.activeSpendingKeyset
      ).getOrThrow()

    withClue("wallet and keybox keysets match") {
      wallet.identifier.shouldBe(account.keybox.activeSpendingKeyset.localId)
    }

    turbineScope(timeout = 10.seconds) {
      val balance = wallet.balance().testIn(this)
      val transactions = wallet.transactions().testIn(this)

      balance.awaitItem().shouldBe(InitialLoading)
      transactions.awaitItem().shouldBe(InitialLoading)

      withClue("sync initial balance and transaction history") {
        wallet.sync().shouldBeOk()

        balance.awaitItem().shouldBeLoaded(BitcoinBalance.ZeroBalance)
        transactions.awaitItem().shouldBeLoaded(emptyList())
      }

      withClue("new address is generated") {
        val address1 = wallet.getNewAddress().shouldBeOk()
        val address2 = wallet.getNewAddress().shouldBeOk()
        val address3 = wallet.getNewAddress().shouldBeOk()
        listOf(address1, address2, address3).shouldBeUnique()
      }

      val treasury = appTester.treasuryWallet

      withClue("fund wallet") {
        val fundingResult = treasury.fund(wallet, BitcoinMoney.sats(10_000))
        println("Sending coins to ${fundingResult.depositAddress}")
        println("Funding txid ${fundingResult.tx.id}")

        wallet.sync().shouldBeOk()
        balance.awaitItem()
          .shouldBeLoaded()
          .total
          .shouldBe(BitcoinMoney.sats(10_000))

        transactions.awaitItem()
          .shouldBeLoaded()
          .shouldBeSingleton { tx ->
            tx.id.shouldBe(fundingResult.tx.id)
            tx.recipientAddress
              .shouldNotBeNull().address.shouldBe(fundingResult.depositAddress.address)
            tx.fee
              .shouldNotBeNull()
              .shouldBe(fundingResult.tx.fee)
            tx.subtotal.fractionalUnitValue.shouldBe(
              fundingResult.tx.amountSats.toBigInteger()
            )
            tx.total.fractionalUnitValue.shouldBe(
              fundingResult.tx.amountSats.toBigInteger() + fundingResult.tx.fee.fractionalUnitValue
            )
            tx.incoming.shouldBeTrue()
          }
      }

      withClue("send some back to treasury") {
        val appSignedPsbt =
          wallet
            .createSignedPsbt(
              SpendingWallet.PsbtConstructionMethod.Regular(
                recipientAddress = treasury.getReturnAddress(),
                amount = ExactAmount(BitcoinMoney.sats(BigInteger(5_000))),
                feePolicy = FeePolicy.MinRelayRate
              )
            )
            .getOrThrow()

        val appAndHwSignedPsbt =
          appTester.app.nfcTransactor.fakeTransact(
            transaction = { session, commands ->
              commands.signTransaction(
                session = session,
                psbt = appSignedPsbt,
                spendingKeyset = account.keybox.activeSpendingKeyset
              )
            }
          ).getOrThrow()
        appAndHwSignedPsbt.amountSats.shouldBe(5_000UL)

        bitcoinBlockchain.broadcast(appAndHwSignedPsbt).getOrThrow()

        actualDelay(10.seconds) {
          "Let the transaction from app to treasury propagate to blockchain"
        }

        wallet.sync().shouldBeOk()

        balance.awaitItem().shouldBeLoaded().spendable.shouldBeLessThan(BitcoinMoney.sats(5_000))
        treasury.spendingWallet.sync().shouldBeOk()
      }

      balance.cancelAndIgnoreRemainingEvents()
      transactions.cancelAndIgnoreRemainingEvents()
    }

    appTester.returnFundsToTreasury(account)
  }
})
