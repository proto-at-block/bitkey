package build.wallet.smoke.bitcoin.account

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.logging.logTesting
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getActiveWallet
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.setupMobilePay
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.ServerSmoke
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec

const val ALLOWED_BDK_ERROR_ALREADY_IN_BLOCK_CHAIN = "Transaction already in block chain"
const val ALLOWED_BDK_ERROR_INPUTS_MISSING_OR_SPENT = "bad-txns-inputs-missingorspent"

class OnboardingAndMobilePaySmokeTests : FunSpec({
  tags(ServerSmoke)

  test("smoke") {
    val app = launchNewApp()

    /**
     * Onboard a new fake hardware and app
     */
    val account = app.onboardFullAccountWithFakeHardware(shouldSetUpNotifications = true)
    val keyboxWallet = app.getActiveWallet()

    /**
     * Fund the new spending keyset with some satoshis from our treasury
     */
    val treasury = app.treasuryWallet
    val fundingResult = treasury.fund(keyboxWallet, BitcoinMoney.sats(10_000))
    logTesting { "Sending coins to ${fundingResult.depositAddress.address}" }
    logTesting { "Funding txid ${fundingResult.tx.id}" }

    logTesting { "Syncing wallet" }
    keyboxWallet.sync().shouldBeOk()

    logTesting { "Setting up Mobile Pay for $100" }
    app.setupMobilePay(FiatMoney.usd(100.0))

    /**
     * Send the money back to the treasury
     */
    logTesting { "spending coins via server-spend" }
    val appSignedPsbt =
      keyboxWallet
        .createSignedPsbt(
          SpendingWallet.PsbtConstructionMethod.Regular(
            recipientAddress = treasury.getReturnAddress(),
            amount = SendAll,
            feePolicy = FeePolicy.MinRelayRate
          )
        ).getOrThrow()

    val serverSignedPsbt = app.mobilePaySigningF8eClient.signWithSpecificKeyset(
      account.config.f8eEnvironment,
      account.accountId,
      account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
      appSignedPsbt
    ).getOrThrow()

    app.bitcoinBlockchain.broadcast(serverSignedPsbt).fold(
      success = { /* broadcast succeeded */ },
      failure = { error ->
        if (error.isExpectedRaceError()) {
          logTesting { "F8e won publishing race, continue... Error: ${error.message}" }
        } else {
          throw error
        }
      }
    )

    logTesting { "\uD83C\uDF89 success! \uD83C\uDF89" }
  }
})

/**
 * Mobile Pay intentionally broadcasts both on F8e and App for redundancy. More often than not, F8e
 * would beat the App to a successful broadcast, and we'd receive either an `inputs-missing-or-spent`
 * error, or an error saying that the transaction is already in the blockchain.
 *
 * This extension function screens for those.
 */
private fun BdkError.isExpectedRaceError(): Boolean {
  val whiteListedErrorCodes = listOf(
    ALLOWED_BDK_ERROR_ALREADY_IN_BLOCK_CHAIN,
    ALLOWED_BDK_ERROR_INPUTS_MISSING_OR_SPENT
  )

  return whiteListedErrorCodes.any { code ->
    message?.contains(code) == true
  }
}
