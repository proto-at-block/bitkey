package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.feature.flags.setBdk2Enabled
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.getActiveWallet
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.ext.waitForFunds
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.IsolatedTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty

class SpendingWalletV2FunctionalTests : FunSpec({

  test("createPsbt creates unsigned PSBT with correct fee, baseSize, and amount")
    .config(tags = setOf(IsolatedTest)) {
      val app = launchNewApp()
      app.bdk2FeatureFlag.setBdk2Enabled(true)
      app.onboardFullAccountWithFakeHardware()

      val fundingAmount = sats(50_000L)
      val sendAmount = sats(10_000L)

      app.addSomeFunds(amount = fundingAmount)
      app.waitForFunds { it.total == fundingAmount }

      val spendingWallet = app.getActiveWallet()
      val treasuryAddress = app.treasuryWallet.getReturnAddress()

      val psbt = spendingWallet.createPsbt(
        recipientAddress = treasuryAddress,
        amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
        feePolicy = FeePolicy.Rate(FeeRate(5.0f)),
        coinSelectionStrategy = CoinSelectionStrategy.Default
      ).shouldBeOk()

      psbt.should {
        it.id.shouldNotBeEmpty()
        it.base64.shouldNotBeEmpty()
        it.amountSats.shouldBe(sendAmount.fractionalUnitValue.longValue().toULong())
        it.numOfInputs.shouldBeGreaterThan(0)
        it.fee.amount.fractionalUnitValue.longValue().shouldBeGreaterThan(0L)
        it.vsize.shouldBeGreaterThan(0L)
      }

      app.returnFundsToTreasury()
    }

  test("createPsbt with SendAll drains wallet minus fees")
    .config(tags = setOf(IsolatedTest)) {
      val app = launchNewApp()
      app.bdk2FeatureFlag.setBdk2Enabled(true)
      app.onboardFullAccountWithFakeHardware()

      val fundingAmount = sats(30_000L)

      app.addSomeFunds(amount = fundingAmount)
      app.waitForFunds { it.total == fundingAmount }

      val spendingWallet = app.getActiveWallet()
      val treasuryAddress = app.treasuryWallet.getReturnAddress()

      val psbt = spendingWallet.createPsbt(
        recipientAddress = treasuryAddress,
        amount = BitcoinTransactionSendAmount.SendAll,
        feePolicy = FeePolicy.Rate(FeeRate(2.0f)),
        coinSelectionStrategy = CoinSelectionStrategy.Default
      ).shouldBeOk()

      val expectedSendAmount = fundingAmount - psbt.fee.amount
      psbt.amountSats.shouldBe(expectedSendAmount.fractionalUnitValue.longValue().toULong())

      app.returnFundsToTreasury()
    }

  test("signPsbt signs PSBT and recomputes metadata correctly")
    .config(tags = setOf(IsolatedTest)) {
      val app = launchNewApp()
      app.bdk2FeatureFlag.setBdk2Enabled(true)
      app.onboardFullAccountWithFakeHardware()

      val fundingAmount = sats(50_000L)
      val sendAmount = sats(10_000L)

      app.addSomeFunds(amount = fundingAmount)
      app.waitForFunds { it.total == fundingAmount }

      val spendingWallet = app.getActiveWallet()
      val treasuryAddress = app.treasuryWallet.getReturnAddress()

      val unsignedPsbt = spendingWallet.createPsbt(
        recipientAddress = treasuryAddress,
        amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
        feePolicy = FeePolicy.Rate(FeeRate(5.0f)),
        coinSelectionStrategy = CoinSelectionStrategy.Default
      ).shouldBeOk()

      val signedPsbt = spendingWallet.signPsbt(unsignedPsbt).shouldBeOk()

      signedPsbt.should {
        // ID should remain the same (txid doesn't change with signing)
        it.id.shouldBe(unsignedPsbt.id)
        // Base64 should be different (contains signature now)
        it.base64.shouldNotBeEmpty()
        // Amount and fee should be preserved
        it.amountSats.shouldBe(unsignedPsbt.amountSats)
        it.fee.shouldBe(unsignedPsbt.fee)
        it.numOfInputs.shouldBe(unsignedPsbt.numOfInputs)
        // baseSize should be valid (recomputed from signed tx)
        it.vsize.shouldBeGreaterThan(0L)
      }

      app.returnFundsToTreasury()
    }

  test("getNewAddressInfo persists reveal index; peekAddress does not reveal") {
    var app = launchNewApp()
    app.bdk2FeatureFlag.setBdk2Enabled(true)
    app.onboardFullAccountWithFakeHardware()

    val wallet = app.getActiveWallet()

    val firstInfo = wallet.getNewAddressInfo().shouldBeOk()
    firstInfo.index.shouldBe(0u)

    val peeked = wallet.peekAddress(10u).shouldBeOk()

    val secondInfo = wallet.getNewAddressInfo().shouldBeOk()
    secondInfo.index.shouldBe(1u)
    secondInfo.address.shouldNotBe(peeked)

    app = app.relaunchApp()
    app.bdk2FeatureFlag.setBdk2Enabled(true)

    val relaunchedWallet = app.getActiveWallet()
    val thirdInfo = relaunchedWallet.getNewAddressInfo().shouldBeOk()
    thirdInfo.index.shouldBe(2u)
    thirdInfo.address.shouldNotBe(secondInfo.address)
  }
})
