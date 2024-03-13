package build.wallet.smoke.bitcoin.account

import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.testing.launchNewApp
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.ServerSmoke
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec

class OnboardingAndMobilePaySmokeTests : FunSpec({
  tags(ServerSmoke)

  test("smoke") {
    val appTester = launchNewApp()
    appTester.app.apply {
      /**
       * Onboard a new fake hardware and app
       */
      val account = appTester.onboardFullAccountWithFakeHardware(shouldSetUpNotifications = true)
      val keyboxWallet = appTester.getActiveWallet()

      /**
       * Fund the new spending keyset with some satoshis from our treasury
       */
      val treasury = appTester.treasuryWallet
      val fundingResult = treasury.fund(keyboxWallet, BitcoinMoney.sats(10_000))
      println("Sending coins to ${fundingResult.depositAddress.address}")
      println("Funding txid ${fundingResult.tx.id}")

      println("Syncing wallet")
      keyboxWallet.sync().shouldBeOk()

      println("Setting up Quickpay for $100")
      appTester.setupMobilePay(account, FiatMoney.usd(100.0))

      /**
       * Send the money back to the treasury
       */
      println("spending coins via server-spend")
      val appSignedPsbt =
        keyboxWallet
          .createSignedPsbt(
            SpendingWallet.PsbtConstructionMethod.Regular(
              recipientAddress = treasury.getReturnAddress(),
              amount = ExactAmount(BitcoinMoney.sats(9_500)),
              feePolicy = FeePolicy.MinRelayRate
            )
          ).getOrThrow()

      val serverSigned =
        mobilePaySigningService.signWithSpecificKeyset(
          account.config.f8eEnvironment,
          account.accountId,
          account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
          appSignedPsbt
        ).getOrThrow()

      bitcoinBlockchain.broadcast(serverSigned).getOrThrow()

      println("\uD83C\uDF89 success! \uD83C\uDF89")
    }
  }
})
